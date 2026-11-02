package by.bsuir.travel.payment.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.*;
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
public class PaymentSettlement implements Settlement {
  private final JdbcTemplate j;
  private final PaymentReadService read;
  private final BookingClient bookings;
  private final PaymentAdmission admission;
  private final ObjectMapper mapper;

  @org.springframework.beans.factory.annotation.Autowired
  public PaymentSettlement(
      JdbcTemplate j, PaymentReadService r, RpcClient rpc, Environment e, ObjectMapper m) {
    this(
        j,
        r,
        rpc.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), BookingClient.class),
        rpc.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), PaymentAdmission.class),
        m);
  }

  PaymentSettlement(
      JdbcTemplate j,
      PaymentReadService r,
      BookingClient bookings,
      PaymentAdmission admission,
      ObjectMapper m) {
    this.j = j;
    read = r;
    this.bookings = bookings;
    this.admission = admission;
    mapper = m;
  }

  @Transactional
  public Payment confirmPayment(String id, Principal actor) {
    by.bsuir.travel.business.Rules.role(actor, RoleCode.ADMIN, RoleCode.EMPLOYEE);
    ReadSupport.id(id);
    j.execute("select pg_advisory_xact_lock(417204)");
    var p = ReadSupport.found(read.all().stream().filter(x -> x.id().equals(id)).findFirst());
    if (p.status() == PaymentStatus.SUCCEEDED) return p;
    if (p.status() != PaymentStatus.PENDING)
      throw new DomainException("INVALID_STATE", "Payment is not pending");
    var b = bookings.booking(new GetBookingRequest(p.bookingId()));
    if (b.status() != BookingStatus.PENDING_PAYMENT
        || b.holdExpiresAt() == null
        || !b.holdExpiresAt().isAfter(OffsetDateTime.now()))
      throw new DomainException("INVALID_STATE", "Booking is not payable");
    j.update("update payments set status='SUCCEEDED',updated_at=now() where id=?::uuid", id);
    var result =
        new Payment(
            p.id(),
            p.bookingId(),
            p.amount(),
            PaymentStatus.SUCCEEDED,
            p.paymentUrl(),
            p.createdAt());
    Outbox.add(j, mapper, "PaymentSucceeded", id, new PaymentSettled(result, b));
    return result;
  }

  /** The same payment lock serializes create, confirm and expiry across both databases. */
  @Transactional
  public Boolean expirePayment(String bookingId) {
    ReadSupport.id(bookingId);
    j.execute("select pg_advisory_xact_lock(417204)");
    if (read.all().stream()
        .anyMatch(
            p ->
                ReadSupport.sameId(p.bookingId(), bookingId)
                    && (p.status() == PaymentStatus.SUCCEEDED
                        || p.status() == PaymentStatus.REFUND_PENDING
                        || p.status() == PaymentStatus.REFUNDED))) return false;
    // Do not release the payment lock before Booking commits the terminal decision.
    if (!admission.expireBooking(bookingId)) return false;
    j.update(
        "update payments set status='FAILED',updated_at=now()"
            + " where booking_id=?::uuid and status='PENDING'",
        bookingId);
    return true;
  }
}
