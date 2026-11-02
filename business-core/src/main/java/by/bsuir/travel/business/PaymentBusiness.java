package by.bsuir.travel.business;

import static by.bsuir.travel.business.Rules.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class PaymentBusiness implements PaymentCommands {
  private final Store<Payment> store;
  private final Function<String, Booking> bookings;
  private final Clock clock;
  private final java.util.function.Consumer<by.bsuir.travel.contracts.Identity.PaymentClaim>
      admission;

  public PaymentBusiness(Store<Payment> store, Function<String, Booking> bookings, Clock clock) {
    this(store, bookings, clock, request -> {});
  }

  public PaymentBusiness(
      Store<Payment> store,
      Function<String, Booking> bookings,
      Clock clock,
      java.util.function.Consumer<by.bsuir.travel.contracts.Identity.PaymentClaim> admission) {
    this.admission = admission;
    this.store = store;
    this.bookings = bookings;
    this.clock = clock;
  }

  public Payment createPayment(CreatePaymentInput i, String key, Principal p) {
    role(p, RoleCode.CLIENT);
    return store.atomic(
        () -> {
          String bid = id(i.bookingId());
          var b = bookings.apply(bid);
          own(b, p);
          String k = key(p, "createPayment", key), f = bid;
          String old = store.receipt(k, f);
          if (old != null)
            return ReadSupport.found(
                store.all().stream().filter(x -> x.id().equals(old)).findFirst());
          require(
              b.status() == BookingStatus.PENDING_PAYMENT
                  && b.holdExpiresAt() != null
                  && b.holdExpiresAt().isAfter(OffsetDateTime.now(clock)),
              "INVALID_STATE",
              "Booking is not payable or hold expired");
          require(
              store.all().stream()
                  .noneMatch(
                      x ->
                          ReadSupport.sameId(x.bookingId(), b.id())
                              && (x.status() == PaymentStatus.PENDING
                                  || x.status() == PaymentStatus.SUCCEEDED
                                  || x.status() == PaymentStatus.REFUND_PENDING)),
              "CONFLICT",
              "Active payment already exists");
          String pid =
              UUID.nameUUIDFromBytes(k.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                  .toString();
          admission.accept(new by.bsuir.travel.contracts.Identity.PaymentClaim(b.id(), pid, p));
          var result =
              new Payment(
                  pid,
                  b.id(),
                  b.total(),
                  PaymentStatus.PENDING,
                  "https://payments.example.invalid/demo/" + pid,
                  OffsetDateTime.now(clock));
          store.save(result);
          store.receipt(k, f, pid);
          return result;
        });
  }
}
