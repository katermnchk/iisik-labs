package by.bsuir.travel.booking.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.transport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Recovery {
  private final JdbcTemplate j;
  private final Reservations seats;
  private final Settlement payments;
  private final TransactionTemplate tx;
  private final BookingReadService read;
  private final ObjectMapper mapper;
  private final Logger log = LoggerFactory.getLogger(Recovery.class);

  public Recovery(
      JdbcTemplate j,
      RpcClient rpc,
      Environment e,
      PlatformTransactionManager tm,
      BookingReadService r,
      ObjectMapper m) {
    this.j = j;
    tx = new TransactionTemplate(tm);
    read = r;
    mapper = m;
    seats = rpc.proxy(e.getProperty("TOUR_RPC", "localhost:9092"), Reservations.class);
    payments = rpc.proxy(e.getProperty("PAYMENT_RPC", "localhost:9094"), Settlement.class);
  }

  @Scheduled(fixedDelay = 10000)
  public void recover() {
    for (var row :
        j.queryForList(
            "select id from bookings where status='PENDING_PAYMENT'"
                + " and hold_expires_at<=now()")) {
      String id = row.get("id").toString();
      try {
        payments.expirePayment(id);
      } catch (Exception e) {
        log.warn("expiry deferred bookingId={} error={}", id, e.toString());
      }
    }
    for (var row : j.queryForList("select id from bookings where status='CANCELLING'")) {
      String id = row.get("id").toString();
      try {
        seats.release(id);
        tx.execute(
            s -> {
              j.execute("select pg_advisory_xact_lock(417204)");
              int changed =
                  j.update(
                      "update bookings set status='CANCELLED',version=version+1,updated_at=now()"
                          + " where id=?::uuid and status='CANCELLING'",
                      id);
              if (changed == 1) {
                j.update(
                    "insert into booking_status_history"
                        + "(id,booking_id,from_status,to_status,reason,changed_at) values"
                        + " (?::uuid,?::uuid,'CANCELLING','CANCELLED','Cancellation"
                        + " recovered',now())",
                    java.util.UUID.randomUUID().toString(),
                    id);
                Outbox.add(
                    j, mapper, "BookingChanged", id, read.booking(new GetBookingRequest(id)));
                log.info("recovered cancellation bookingId={}", id);
              }
              return null;
            });
      } catch (Exception e) {
        log.warn("cancellation recovery deferred bookingId={} error={}", id, e.toString());
      }
    }
  }
}
