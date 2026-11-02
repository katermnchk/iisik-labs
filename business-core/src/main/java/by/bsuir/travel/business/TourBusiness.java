package by.bsuir.travel.business;

import static by.bsuir.travel.business.Rules.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public final class TourBusiness implements TourCommands, SeatPort {
  private final Store<Tour> store;
  private final Supplier<List<Direction>> directions;
  private final Clock clock;
  private final Map<String, Reservation> holds = new HashMap<>();

  public TourBusiness(Store<Tour> store, Supplier<List<Direction>> directions, Clock clock) {
    this.store = store;
    this.directions = directions;
    this.clock = clock;
  }

  public Tour get(String id) {
    String key = id(id);
    return ReadSupport.found(
        store.all().stream().filter(t -> t.id().equalsIgnoreCase(key)).findFirst());
  }

  public TourSchedule schedule(String id) {
    String key = id(id);
    return ReadSupport.found(
        store.all().stream()
            .flatMap(t -> t.schedules().stream())
            .filter(s -> s.id().equalsIgnoreCase(key))
            .findFirst());
  }

  public Tour createTour(CreateTourInput i, Principal p) {
    role(p, RoleCode.EMPLOYEE, RoleCode.ADMIN);
    return store.atomic(
        () -> {
          text(i.title(), 180);
          require(i.description() != null, "BAD_REQUEST", "Description is required");
          require(
              i.durationDays() > 0 && i.durationDays() <= 365,
              "BAD_REQUEST",
              "Duration must be in 1..365");
          String did = id(i.directionId());
          var d =
              ReadSupport.found(
                  directions.get().stream().filter(x -> x.id().equalsIgnoreCase(did)).findFirst());
          require(d.isActive(), "CONFLICT", "Direction is inactive");
          var t =
              new Tour(
                  UUID.randomUUID().toString(),
                  d,
                  i.title().trim(),
                  i.description(),
                  i.durationDays(),
                  TourStatus.DRAFT,
                  1,
                  p.employeeId() == null ? p.userId() : p.employeeId(),
                  List.of());
          store.save(t);
          return t;
        });
  }

  public Tour updateTour(UpdateTourInput i, Principal p) {
    role(p, RoleCode.EMPLOYEE, RoleCode.ADMIN);
    return store.atomic(
        () -> {
          var t = get(i.id());
          version(i.expectedVersion(), t.version());
          require(
              t.status() != TourStatus.ARCHIVED,
              "INVALID_STATE",
              "Archived tour cannot be changed");
          if (i.title() != null) text(i.title(), 180);
          int days = i.durationDays() == null ? t.durationDays() : i.durationDays();
          require(days > 0 && days <= 365, "BAD_REQUEST", "Duration must be in 1..365");
          var status = i.status() == null ? t.status() : i.status();
          if (status == TourStatus.PUBLISHED)
            require(
                t.direction().isActive()
                    && t.schedules().stream()
                        .anyMatch(
                            s ->
                                s.status() == ScheduleStatus.OPEN
                                    && s.startsAt().isAfter(OffsetDateTime.now(clock))),
                "INVALID_STATE",
                "Publication requires an open future schedule");
          if (status == TourStatus.ARCHIVED || status == TourStatus.DRAFT)
            require(
                t.schedules().stream().allMatch(s -> s.reservedSeats() == 0),
                "CONFLICT",
                "Tour has reserved seats");
          var result =
              new Tour(
                  t.id(),
                  t.direction(),
                  i.title() == null ? t.title() : i.title().trim(),
                  i.description() == null ? t.description() : i.description(),
                  days,
                  status,
                  t.version() + 1,
                  t.createdByEmployeeId(),
                  t.schedules());
          store.save(result);
          return result;
        });
  }

  private void validSchedule(OffsetDateTime start, OffsetDateTime end, int cap, long price) {
    require(
        start != null
            && end != null
            && end.isAfter(start)
            && start.isAfter(OffsetDateTime.now(clock)),
        "BAD_REQUEST",
        "Expected future start < end");
    require(
        cap > 0 && price >= 0, "BAD_REQUEST", "Capacity must be positive and price non-negative");
  }

  public TourSchedule createSchedule(CreateScheduleInput i, Principal p) {
    role(p, RoleCode.EMPLOYEE, RoleCode.ADMIN);
    return store.atomic(
        () -> {
          var t = get(i.tourId());
          require(t.status() != TourStatus.ARCHIVED, "INVALID_STATE", "Tour is archived");
          validSchedule(i.startsAt(), i.endsAt(), i.capacity(), i.basePriceMinor());
          var s =
              new TourSchedule(
                  UUID.randomUUID().toString(),
                  t.id(),
                  i.startsAt(),
                  i.endsAt(),
                  i.capacity(),
                  0,
                  i.capacity(),
                  new Money(i.basePriceMinor(), "BYN"),
                  1,
                  1,
                  ScheduleStatus.OPEN,
                  List.of(),
                  List.of());
          replace(t, s);
          return s;
        });
  }

  public TourSchedule updateSchedule(UpdateScheduleInput i, Principal p) {
    role(p, RoleCode.EMPLOYEE, RoleCode.ADMIN);
    return store.atomic(
        () -> {
          var s = schedule(i.id());
          var t = get(s.tourId());
          require(t.status() != TourStatus.ARCHIVED, "INVALID_STATE", "Tour is archived");
          version(i.expectedVersion(), s.version());
          var start = i.startsAt() == null ? s.startsAt() : i.startsAt();
          var end = i.endsAt() == null ? s.endsAt() : i.endsAt();
          int cap = i.capacity() == null ? s.capacity() : i.capacity();
          long price = i.basePriceMinor() == null ? s.price().amountMinor() : i.basePriceMinor();
          var status = i.status() == null ? s.status() : i.status();
          require(
              s.status() != ScheduleStatus.CANCELLED,
              "INVALID_STATE",
              "Cancelled schedule cannot be changed");
          validSchedule(start, end, cap, price);
          require(cap >= s.reservedSeats(), "CONFLICT", "Capacity is below reserved seats");
          if (s.reservedSeats() > 0)
            require(
                start.isEqual(s.startsAt())
                    && end.isEqual(s.endsAt())
                    && status != ScheduleStatus.CANCELLED,
                "CONFLICT",
                "Cannot move or cancel a schedule with reservations");
          var result =
              new TourSchedule(
                  s.id(),
                  s.tourId(),
                  start,
                  end,
                  cap,
                  s.reservedSeats(),
                  cap - s.reservedSeats(),
                  new Money(price, "BYN"),
                  s.priceVersion() + (price == s.price().amountMinor() ? 0 : 1),
                  s.version() + 1,
                  status,
                  s.accommodations(),
                  s.transports());
          replace(t, result);
          return result;
        });
  }

  private void replace(Tour t, TourSchedule s) {
    var list = new ArrayList<>(t.schedules());
    list.removeIf(x -> x.id().equals(s.id()));
    list.add(s);
    list.sort(Comparator.comparing(TourSchedule::startsAt).thenComparing(TourSchedule::id));
    store.save(
        new Tour(
            t.id(),
            t.direction(),
            t.title(),
            t.description(),
            t.durationDays(),
            t.status(),
            t.version() + 1,
            t.createdByEmployeeId(),
            List.copyOf(list)));
  }

  public Reservation reserve(String bid, CreateBookingInput i) {
    return store.atomic(
        () -> {
          var old = holds.get(bid);
          if (old != null) return old;
          var s = schedule(i.scheduleId());
          var t = get(s.tourId());
          require(
              i.travelerCount() > 0 && i.travelerCount() <= 100,
              "BAD_REQUEST",
              "Travelers must be in 1..100");
          require(
              t.status() == TourStatus.PUBLISHED
                  && t.direction().isActive()
                  && s.status() == ScheduleStatus.OPEN
                  && s.startsAt().isAfter(OffsetDateTime.now(clock)),
              "INVALID_STATE",
              "Schedule is not bookable");
          require(
              s.priceVersion() == i.expectedPriceVersion(), "PRICE_CHANGED", "Price has changed");
          require(
              s.availableSeats() >= i.travelerCount(), "INSUFFICIENT_SEATS", "Not enough seats");
          var result = new Reservation(bid, t, s, i.travelerCount());
          seats(t, s, i.travelerCount());
          holds.put(bid, result);
          return result;
        });
  }

  public void release(String bid) {
    store.atomic(
        () -> {
          var hold = holds.get(bid);
          require(hold != null, "CONFLICT", "Reservation not found in local stub");
          var s = schedule(hold.schedule().id());
          seats(get(s.tourId()), s, -hold.seats());
          holds.remove(bid);
          return null;
        });
  }

  public void release(Booking b) {
    store.atomic(
        () -> {
          var s = schedule(b.scheduleId());
          seats(get(s.tourId()), s, -b.travelerCount());
          holds.remove(b.id());
          return null;
        });
  }

  private void seats(Tour t, TourSchedule s, int delta) {
    int n = s.reservedSeats() + delta;
    require(n >= 0 && n <= s.capacity(), "CONFLICT", "Invalid reservation count");
    replace(
        t,
        new TourSchedule(
            s.id(),
            s.tourId(),
            s.startsAt(),
            s.endsAt(),
            s.capacity(),
            n,
            s.capacity() - n,
            s.price(),
            s.priceVersion(),
            s.version() + 1,
            s.status(),
            s.accommodations(),
            s.transports()));
  }
}
