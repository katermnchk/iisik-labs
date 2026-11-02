package by.bsuir.travel.tour.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Identity.PaymentAdmission;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.transport.*;
import org.slf4j.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReservationRecovery {
  private final JdbcTemplate j;
  private final BookingClient bookings;
  private final PaymentAdmission authority;
  private final PersistentReservations reservations;
  private final Logger log = LoggerFactory.getLogger(ReservationRecovery.class);

  public ReservationRecovery(
      JdbcTemplate j, RpcClient rpc, Environment e, PersistentReservations r) {
    this.j = j;
    reservations = r;
    bookings = rpc.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), BookingClient.class);
    authority = rpc.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), PaymentAdmission.class);
  }

  @Scheduled(fixedDelay = 10000)
  public void reconcile() {
    for (var row :
        j.queryForList(
            "select booking_id from rpc_reservations where status='HELD' and"
                + " created_at<now()-interval '2 minutes'")) {
      String id = row.get("booking_id").toString();
      try {
        var b = bookings.booking(new GetBookingRequest(id));
        if (b.status() == BookingStatus.CANCELLED) reservations.release(id);
      } catch (DomainException e) {
        if ("NOT_FOUND".equals(e.getCode())) {
          try {
            if (authority.releaseOrphanReservation(id))
              log.info("recovered orphan reservation bookingId={}", id);
          } catch (Exception failure) {
            log.warn(
                "orphan reconciliation deferred bookingId={} error={}", id, failure.toString());
          }
        } else
          log.warn("reservation reconciliation deferred bookingId={} code={}", id, e.getCode());
      }
    }
  }
}
