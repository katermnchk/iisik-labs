package by.bsuir.travel.business;

import static by.bsuir.travel.business.Rules.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class BookingBusiness
    implements BookingCommands, by.bsuir.travel.contracts.Identity.BookingQueries {
  private final Store<Booking> store;
  private final SeatPort seats;
  private final Function<String, List<Payment>> payments;
  private final Clock clock;

  public BookingBusiness(
      Store<Booking> store, SeatPort seats, Function<String, List<Payment>> payments, Clock clock) {
    this.store = store;
    this.seats = seats;
    this.payments = payments;
    this.clock = clock;
  }

  public Booking get(String id) {
    String key = id(id);
    return ReadSupport.found(
        store.all().stream().filter(b -> b.id().equalsIgnoreCase(key)).findFirst());
  }

  public Booking createBooking(CreateBookingInput i, String key, Principal p) {
    role(p, RoleCode.CLIENT);
    return store.atomic(
        () -> {
          String sid = id(i.scheduleId());
          require(
              i.travelerCount() > 0 && i.travelerCount() <= 100,
              "BAD_REQUEST",
              "Travelers must be in 1..100");
          String k = key(p, "createBooking", key);
          String fingerprint = sid + ":" + i.travelerCount() + ":" + i.expectedPriceVersion();
          String old = store.receipt(k, fingerprint);
          if (old != null) return get(old);
          String bid =
              UUID.nameUUIDFromBytes(k.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                  .toString();
          var reservation = seats.reserve(bid, i);
          var s = reservation.schedule();
          var t = reservation.tour();
          Booking b;
          try {
            long total = Math.multiplyExact(s.price().amountMinor(), i.travelerCount());
            var now = OffsetDateTime.now(clock);
            b =
                new Booking(
                    bid,
                    p.clientId(),
                    s.id(),
                    t.direction().id(),
                    t.title(),
                    t.direction().country() + ", " + t.direction().city(),
                    s.startsAt(),
                    i.travelerCount(),
                    s.price(),
                    new Money(total, "BYN"),
                    BookingStatus.PENDING_PAYMENT,
                    (now.plusMinutes(15).isBefore(s.startsAt())
                        ? now.plusMinutes(15)
                        : s.startsAt()),
                    now,
                    1,
                    List.of());
            store.save(b, "Создание бронирования", p.userId());
            store.receipt(k, fingerprint, bid);
          } catch (RuntimeException e) {
            seats.release(bid);
            if (e instanceof ArithmeticException)
              throw new DomainException("BAD_REQUEST", "Total amount overflows Long");
            throw e;
          }
          return b;
        });
  }

  public Booking cancelBooking(CancelBookingInput i, String key, Principal p) {
    role(p, RoleCode.CLIENT, RoleCode.ADMIN, RoleCode.EMPLOYEE);
    var prepared =
        store.atomic(
            () -> {
              var b = get(i.bookingId());
              own(b, p);
              text(i.reason(), 255);
              String k = key(p, "cancelBooking", key), f = b.id() + ":" + i.reason();
              String old = store.receipt(k, f);
              if (old != null) return get(old);
              if (b.status() == BookingStatus.CANCELLED) {
                store.receipt(k, f, b.id());
                return b;
              }
              if (b.status() == BookingStatus.CANCELLING) {
                store.receipt(k, f, b.id());
                return b;
              }
              require(
                  b.status() == BookingStatus.PENDING_PAYMENT,
                  "INVALID_STATE",
                  "Only an unpaid pending booking can be cancelled");
              require(
                  payments.apply(b.id()).stream()
                      .noneMatch(
                          x ->
                              x.status() == PaymentStatus.PENDING
                                  || x.status() == PaymentStatus.SUCCEEDED
                                  || x.status() == PaymentStatus.REFUND_PENDING),
                  "CONFLICT",
                  "Booking has an active payment");
              var next = state(b, BookingStatus.CANCELLING);
              store.save(next, i.reason(), p.userId());
              store.receipt(k, f, b.id());
              return next;
            });
    if (prepared.status() == BookingStatus.CANCELLED) return prepared;
    seats.release(prepared);
    return store.atomic(
        () -> {
          var current = get(prepared.id());
          if (current.status() == BookingStatus.CANCELLED) return current;
          require(
              current.status() == BookingStatus.CANCELLING,
              "INVALID_STATE",
              "Cancellation state changed");
          var result = state(current, BookingStatus.CANCELLED);
          store.save(result, i.reason(), p.userId());
          return result;
        });
  }

  public Booking changeBookingStatus(ChangeBookingStatusInput i, Principal p) {
    role(p, RoleCode.EMPLOYEE, RoleCode.ADMIN);
    return store.atomic(
        () -> {
          var b = get(i.bookingId());
          text(i.reason(), 255);
          version(i.expectedVersion(), b.version());
          require(
              b.status() == BookingStatus.CONFIRMED
                  && i.targetStatus() == BookingStatus.COMPLETED
                  && !b.startsAt().isAfter(OffsetDateTime.now(clock)),
              "INVALID_STATE",
              "Only a started confirmed booking can be completed");
          var result = state(b, BookingStatus.COMPLETED);
          store.save(result, i.reason(), p.userId());
          return result;
        });
  }

  private Booking state(Booking b, BookingStatus status) {
    return new Booking(
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
        status,
        null,
        b.createdAt(),
        b.version() + 1,
        List.of());
  }

  public List<Booking> filtered(Principal p, BookingFilter filter, BookingSort sort) {
    role(p, RoleCode.CLIENT);
    if (filter != null && filter.from() != null && filter.to() != null)
      require(!filter.from().isAfter(filter.to()), "BAD_REQUEST", "Expected from <= to");
    var list =
        store.all().stream()
            .filter(b -> ReadSupport.sameId(b.clientId(), p.clientId()))
            .filter(
                b ->
                    filter == null
                        || (filter.status() == null || filter.status() == b.status())
                            && (filter.from() == null || !b.createdAt().isBefore(filter.from()))
                            && (filter.to() == null || b.createdAt().isBefore(filter.to())));
    var field = sort == null ? BookingSortField.CREATED_AT : sort.field();
    Comparator<Booking> c =
        switch (field) {
          case CREATED_AT -> Comparator.comparing(Booking::createdAt);
          case TOTAL -> Comparator.comparingLong(b -> b.total().amountMinor());
          case STARTS_AT -> Comparator.comparing(Booking::startsAt);
        };
    if (sort == null || sort.direction() == SortDirection.DESC) c = c.reversed();
    return list.sorted(c.thenComparing(Booking::id)).toList();
  }

  public BookingSummary summary(Principal p, BookingFilter f) {
    var all = filtered(p, f, null);
    long total = 0;
    try {
      for (var b : all) total = Math.addExact(total, b.total().amountMinor());
    } catch (ArithmeticException e) {
      throw new DomainException(
          "AMOUNT_OVERFLOW", "Selected booking total exceeds the supported monetary range");
    }
    return new BookingSummary(
        all.size(),
        (int) all.stream().filter(b -> b.status() == BookingStatus.PENDING_PAYMENT).count(),
        (int) all.stream().filter(b -> b.status() == BookingStatus.CONFIRMED).count(),
        (int) all.stream().filter(b -> b.status() == BookingStatus.CANCELLED).count(),
        new Money(total, "BYN"));
  }
}
