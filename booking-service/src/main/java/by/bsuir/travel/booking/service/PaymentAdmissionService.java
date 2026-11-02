package by.bsuir.travel.booking.service;

import by.bsuir.travel.business.Rules;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.transport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAdmissionService implements PaymentAdmission {
  private final JdbcTemplate j;
  private final BookingReadService read;
  private final Reservations seats;
  private final ObjectMapper mapper;

  @org.springframework.beans.factory.annotation.Autowired
  public PaymentAdmissionService(
      JdbcTemplate j, BookingReadService r, RpcClient rpc, Environment env, ObjectMapper mapper) {
    this(
        j, r, rpc.proxy(env.getProperty("TOUR_RPC", "localhost:9092"), Reservations.class), mapper);
  }

  PaymentAdmissionService(
      JdbcTemplate j, BookingReadService r, Reservations seats, ObjectMapper mapper) {
    this.j = j;
    read = r;
    this.mapper = mapper;
    this.seats = seats;
  }

  @Transactional
  public Boolean claimPayment(PaymentClaim i) {
    Rules.role(i.actor(), RoleCode.CLIENT);
    ReadSupport.id(i.paymentId());
    j.execute("select pg_advisory_xact_lock(417204)");
    var b = read.booking(new GetBookingRequest(i.bookingId()));
    Rules.own(b, i.actor());
    Rules.require(
        b.status() == BookingStatus.PENDING_PAYMENT
            && b.holdExpiresAt() != null
            && b.holdExpiresAt().isAfter(OffsetDateTime.now()),
        "INVALID_STATE",
        "Booking is not payable");
    var old =
        j.queryForList("select payment_id from payment_intents where booking_id=?::uuid", b.id());
    if (!old.isEmpty()) {
      Rules.require(
          old.get(0).get("payment_id").toString().equals(i.paymentId()),
          "CONFLICT",
          "Another payment is in progress");
      return true;
    }
    j.update(
        "insert into payment_intents(booking_id,payment_id) values (?::uuid,?::uuid)",
        b.id(),
        i.paymentId());
    return true;
  }

  @Transactional
  public Boolean expireBooking(String bookingId) {
    ReadSupport.id(bookingId);
    j.execute("select pg_advisory_xact_lock(417204)");
    var b = read.booking(new GetBookingRequest(bookingId));
    if (b.status() == BookingStatus.CANCELLING || b.status() == BookingStatus.CANCELLED)
      return true; // Retry after Booking committed but the Payment transaction failed.
    if (b.status() != BookingStatus.PENDING_PAYMENT
        || b.holdExpiresAt() == null
        || b.holdExpiresAt().isAfter(OffsetDateTime.now())) return false;
    j.update(
        "update bookings set status='CANCELLING',hold_expires_at=null,"
            + "version=version+1,updated_at=now() where id=?::uuid",
        b.id());
    j.update(
        "insert into booking_status_history(id,booking_id,from_status,to_status,reason,changed_at)"
            + " values (?::uuid,?::uuid,'PENDING_PAYMENT','CANCELLING','Payment hold"
            + " expired',now())",
        java.util.UUID.randomUUID().toString(),
        b.id());
    j.update("delete from payment_intents where booking_id=?::uuid", b.id());
    var cancelling =
        new Booking(
            b.id(),
            b.clientId(),
            b.scheduleId(),
            b.directionId(),
            b.tourTitle(),
            b.directionName(),
            b.startsAt(),
            b.travelerCount(),
            b.unitPrice(),
            b.total(),
            BookingStatus.CANCELLING,
            null,
            b.createdAt(),
            b.version() + 1,
            java.util.List.of());
    Outbox.add(j, mapper, "BookingChanged", b.id(), cancelling);
    return true;
  }

  @Transactional
  public Boolean releaseOrphanReservation(String bookingId) {
    ReadSupport.id(bookingId);
    // A read-only NOT_FOUND can observe an uncommitted create. Wait for its writer first,
    // and keep the lock through release so a retry cannot create a booking in between.
    j.execute("select pg_advisory_xact_lock(417204)");
    if (!j.queryForList("select id from bookings where id=?::uuid", bookingId).isEmpty())
      return false;
    return seats.release(bookingId);
  }
}
