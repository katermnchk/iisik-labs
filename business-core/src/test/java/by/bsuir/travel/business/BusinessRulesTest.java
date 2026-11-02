package by.bsuir.travel.business;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BusinessRulesTest {
  @Test
  void emptyRpcSortUsesDefaultFields() {
    var business = bookings(tours(0, 100), store());
    business.createBooking(new CreateBookingInput(sid, 1, 1), "first", client);
    assertEquals(
        business.filtered(client, null, null),
        business.filtered(client, null, new BookingSort(null, null)));
  }

  @Test
  void absentRpcSortDirectionDefaultsToDescending() {
    var business = bookings(tours(0, 100), store());
    business.createBooking(new CreateBookingInput(sid, 1, 1), "first", client);
    business.createBooking(new CreateBookingInput(sid, 2, 1), "second", client);
    var values = business.filtered(client, null, new BookingSort(BookingSortField.TOTAL, null));
    assertEquals(200, values.get(0).total().amountMinor());
    assertEquals(100, values.get(1).total().amountMinor());
  }

  final Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);
  final OffsetDateTime now = OffsetDateTime.now(clock);
  final String tid = id(201), sid = id(301), did = id(101);

  String id(int n) {
    return String.format("00000000-0000-0000-0000-%012d", n);
  }

  Principal actor(RoleCode r, int n) {
    return new Principal(
        id(n),
        r,
        UserStatus.ACTIVE,
        "a@b.c",
        r == RoleCode.CLIENT ? id(n) : null,
        r == RoleCode.EMPLOYEE ? id(n) : null);
  }

  final Principal client = actor(RoleCode.CLIENT, 1),
      staff = actor(RoleCode.EMPLOYEE, 3),
      admin = actor(RoleCode.ADMIN, 4);

  Direction direction() {
    return new Direction(did, "Беларусь", "Брест", "", true);
  }

  Tour tour(int reserved, long price) {
    return new Tour(
        tid,
        direction(),
        "Тур",
        "Описание",
        2,
        TourStatus.PUBLISHED,
        1,
        id(3),
        List.of(
            new TourSchedule(
                sid,
                tid,
                now.plusDays(60),
                now.plusDays(62),
                10,
                reserved,
                10 - reserved,
                new Money(price, "BYN"),
                1,
                1,
                ScheduleStatus.OPEN,
                List.of(),
                List.of())));
  }

  TourBusiness tours(int reserved, long price) {
    return new TourBusiness(
        new MemoryStore<>(new ArrayList<>(List.of(tour(reserved, price))), Tour::id),
        () -> List.of(direction()),
        clock);
  }

  BookingBusiness bookings(TourBusiness tours, Store<Booking> store) {
    return new BookingBusiness(store, tours, x -> List.of(), clock);
  }

  Store<Booking> store() {
    return new MemoryStore<>(new ArrayList<>(), Booking::id);
  }

  void code(String expected, Runnable action) {
    assertEquals(expected, assertThrows(DomainException.class, action::run).getCode());
  }

  @Test
  void twentyRequestsCannotOversellSevenSeats() throws Exception {
    var t = tours(3, 90000);
    var b = bookings(t, store());
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> jobs = new ArrayList<>();
      for (int n = 0; n < 20; n++) {
        String key = "c" + n;
        jobs.add(
            () -> {
              try {
                b.createBooking(new CreateBookingInput(sid, 1, 1), key, client);
                return true;
              } catch (DomainException e) {
                assertEquals("INSUFFICIENT_SEATS", e.getCode());
                return false;
              }
            });
      }
      int ok = 0;
      for (var f : pool.invokeAll(jobs)) if (f.get()) ok++;
      assertEquals(7, ok);
      assertEquals(0, t.schedule(sid).availableSeats());
      assertEquals(10, t.schedule(sid).reservedSeats());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void priceVersionConflictDoesNotReserve() {
    var t = tours(3, 90000);
    var b = bookings(t, store());
    code(
        "PRICE_CHANGED", () -> b.createBooking(new CreateBookingInput(sid, 1, 2), "price", client));
    assertEquals(7, t.schedule(sid).availableSeats());
  }

  @Test
  void moneyOverflowCompensatesReservation() {
    var t = tours(0, Long.MAX_VALUE);
    var b = bookings(t, store());
    code(
        "BAD_REQUEST",
        () -> b.createBooking(new CreateBookingInput(sid, 2, 1), "overflow", client));
    assertEquals(10, t.schedule(sid).availableSeats());
  }

  @Test
  void cancellationAfterRestartUsesPersistedBooking() {
    var data = store();
    var first = tours(0, 90000);
    var b =
        bookings(first, data).createBooking(new CreateBookingInput(sid, 2, 1), "restore", client);
    var restored = tours(2, 90000);
    var service = bookings(restored, data);
    assertEquals(
        BookingStatus.CANCELLED,
        service.cancelBooking(new CancelBookingInput(b.id(), "Отмена"), "cancel", client).status());
    assertEquals(10, restored.schedule(sid).availableSeats());
  }

  @Test
  void foreignClientCannotCancel() {
    var t = tours(0, 90000);
    var s = bookings(t, store());
    var b = s.createBooking(new CreateBookingInput(sid, 1, 1), "own", client);
    code(
        "FORBIDDEN",
        () ->
            s.cancelBooking(
                new CancelBookingInput(b.id(), "Отмена"), "c", actor(RoleCode.CLIENT, 2)));
    assertEquals(9, t.schedule(sid).availableSeats());
  }

  @Test
  void staleScheduleVersionDoesNotChangeCapacity() {
    var t = tours(3, 90000);
    code(
        "CONFLICT",
        () -> t.updateSchedule(new UpdateScheduleInput(sid, null, null, 20, null, null, 2), staff));
    assertEquals(10, t.schedule(sid).capacity());
  }

  @Test
  void changingPriceIncrementsPriceVersion() {
    var t = tours(3, 90000);
    var s =
        t.updateSchedule(new UpdateScheduleInput(sid, null, null, null, 91000L, null, 1), staff);
    assertEquals(2, s.priceVersion());
    assertEquals(2, s.version());
    assertEquals(3, s.reservedSeats());
  }

  @Test
  void capacityCannotBeBelowReservations() {
    var t = tours(3, 90000);
    code(
        "CONFLICT",
        () -> t.updateSchedule(new UpdateScheduleInput(sid, null, null, 2, null, null, 1), staff));
  }

  @Test
  void reservedScheduleCannotMoveOrBeCancelled() {
    var t = tours(3, 90000);
    code(
        "CONFLICT",
        () ->
            t.updateSchedule(
                new UpdateScheduleInput(
                    sid, now.plusDays(61), now.plusDays(63), null, null, null, 1),
                staff));
    code(
        "CONFLICT",
        () ->
            t.updateSchedule(
                new UpdateScheduleInput(sid, null, null, null, null, ScheduleStatus.CANCELLED, 1),
                staff));
  }

  @Test
  void draftNeedsScheduleBeforePublication() {
    var t = tours(0, 90000);
    var d = t.createTour(new CreateTourInput(did, "Новый тур", "Описание", 2), staff);
    code(
        "INVALID_STATE",
        () ->
            t.updateTour(
                new UpdateTourInput(d.id(), null, null, null, TourStatus.PUBLISHED, 1), staff));
    var s =
        t.createSchedule(
            new CreateScheduleInput(d.id(), now.plusDays(50), now.plusDays(52), 5, 10000L), staff);
    assertEquals(5, s.availableSeats());
    assertEquals(
        TourStatus.PUBLISHED,
        t.updateTour(new UpdateTourInput(d.id(), null, null, null, TourStatus.PUBLISHED, 2), staff)
            .status());
  }

  @Test
  void completionCannotBypassPayment() {
    var t = tours(0, 90000);
    var s = bookings(t, store());
    var b = s.createBooking(new CreateBookingInput(sid, 1, 1), "complete", client);
    code(
        "INVALID_STATE",
        () ->
            s.changeBookingStatus(
                new ChangeBookingStatusInput(b.id(), BookingStatus.COMPLETED, "done", 1), staff));
    code(
        "FORBIDDEN",
        () ->
            s.changeBookingStatus(
                new ChangeBookingStatusInput(b.id(), BookingStatus.CONFIRMED, "done", 1), client));
  }

  @Test
  void lastAdministratorCannotBeBlocked() {
    User u =
        new User(
            id(4),
            new Role(RoleCode.ADMIN, "Admin"),
            "a@b.c",
            "A",
            "B",
            UserStatus.ACTIVE,
            null,
            null);
    var s = new UserBusiness(new MemoryStore<>(new ArrayList<>(List.of(u)), User::id));
    code(
        "CONFLICT",
        () ->
            s.updateUser(
                new UpdateUserInput(id(4), null, UserStatus.BLOCKED, null, null, null, null),
                admin));
  }

  @Test
  void summaryAndFilterReferOnlyToOwner() {
    var t = tours(0, 90000);
    var s = bookings(t, store());
    s.createBooking(new CreateBookingInput(sid, 1, 1), "a", client);
    s.createBooking(new CreateBookingInput(sid, 2, 1), "b", client);
    s.createBooking(new CreateBookingInput(sid, 1, 1), "c", actor(RoleCode.CLIENT, 2));
    assertEquals(2, s.summary(client, null).count());
    assertEquals(270000, s.summary(client, null).total().amountMinor());
    assertEquals(
        0, s.summary(client, new BookingFilter(BookingStatus.CANCELLED, null, null)).count());
    assertEquals(
        180000,
        s.filtered(client, null, new BookingSort(BookingSortField.TOTAL, SortDirection.DESC))
            .get(0)
            .total()
            .amountMinor());
  }

  @Test
  void concurrentReadersNeverLoseAnUpdatedObject() throws Exception {
    var values = new CopyOnWriteArrayList<>(List.of(tour(0, 90000)));
    var store = new MemoryStore<Tour>(values, Tour::id);
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var writer =
          pool.submit(
              () -> {
                try {
                  gate.await();
                  for (int n = 0; n < 20000; n++) store.save(tour(0, 90000));
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });
      var reader =
          pool.submit(
              () -> {
                try {
                  gate.await();
                  for (int n = 0; n < 20000; n++)
                    assertEquals(
                        1,
                        values.toArray().length,
                        "Existing object disappeared during replacement");
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });
      gate.countDown();
      writer.get();
      reader.get();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void cancellationPassesReasonAndActorToStorage() {
    var delegate = store();
    var saved = new ArrayList<String>();
    Store<Booking> recording =
        new Store<Booking>() {
          public List<Booking> all() {
            return delegate.all();
          }

          public void save(Booking b) {
            delegate.save(b);
          }

          public void save(Booking b, String reason, String actor) {
            delegate.save(b);
            saved.add(reason + ":" + actor);
          }

          public String receipt(String k, String f) {
            return delegate.receipt(k, f);
          }

          public void receipt(String k, String f, String id) {
            delegate.receipt(k, f, id);
          }

          public <R> R atomic(Supplier<R> a) {
            return delegate.atomic(a);
          }
        };
    var b = bookings(tours(0, 90000), recording);
    var created = b.createBooking(new CreateBookingInput(sid, 1, 1), "audit-create", client);
    b.cancelBooking(
        new CancelBookingInput(created.id(), "Изменение планов"), "audit-cancel", client);
    assertTrue(saved.contains("Изменение планов:" + client.userId()), saved.toString());
  }

  @Test
  void holdCannotOutliveDeparture() {
    var old = tour(0, 90000);
    var s = old.schedules().get(0);
    var soon =
        new TourSchedule(
            s.id(),
            s.tourId(),
            now.plusMinutes(5),
            now.plusDays(2),
            s.capacity(),
            0,
            s.capacity(),
            s.price(),
            1,
            1,
            ScheduleStatus.OPEN,
            List.of(),
            List.of());
    var t =
        new Tour(
            old.id(),
            old.direction(),
            old.title(),
            old.description(),
            2,
            old.status(),
            1,
            old.createdByEmployeeId(),
            List.of(soon));
    var service =
        new TourBusiness(
            new MemoryStore<>(new ArrayList<>(List.of(t)), Tour::id),
            () -> List.of(direction()),
            clock);
    var b =
        bookings(service, store()).createBooking(new CreateBookingInput(sid, 1, 1), "soon", client);
    assertEquals(soon.startsAt(), b.holdExpiresAt());
  }

  @Test
  void equalInstantsWithDifferentOffsetsAreNotScheduleMoves() {
    var t = tours(3, 90000);
    var s = t.schedule(sid);
    var result =
        t.updateSchedule(
            new UpdateScheduleInput(
                sid,
                s.startsAt().withOffsetSameInstant(ZoneOffset.ofHours(3)),
                s.endsAt().withOffsetSameInstant(ZoneOffset.ofHours(3)),
                null,
                null,
                null,
                1),
            staff);
    assertTrue(result.startsAt().isEqual(s.startsAt()));
    assertEquals(3, result.reservedSeats());
  }

  @Test
  void summaryOverflowIsReportedAsDomainError() {
    var t = tours(0, Long.MAX_VALUE);
    var b = bookings(t, store());
    b.createBooking(new CreateBookingInput(sid, 1, 1), "large-a", client);
    b.createBooking(new CreateBookingInput(sid, 1, 1), "large-b", client);
    code("AMOUNT_OVERFLOW", () -> b.summary(client, null));
  }

  @Test
  void failedSeatReleaseLeavesRecoverableStateAndBlocksPayment() {
    var t = tours(0, 90000);
    var data = store();
    var original = bookings(t, data);
    var b = original.createBooking(new CreateBookingInput(sid, 1, 1), "recover-cancel", client);
    SeatPort failing =
        new SeatPort() {
          public Reservation reserve(String id, CreateBookingInput input) {
            return t.reserve(id, input);
          }

          public void release(String id) {
            throw new DomainException("SERVICE_UNAVAILABLE", "Tour offline");
          }
        };
    var service = new BookingBusiness(data, failing, id -> List.of(), clock);
    code(
        "SERVICE_UNAVAILABLE",
        () ->
            service.cancelBooking(
                new CancelBookingInput(b.id(), "Отмена"), "recover-cancel", client));
    assertEquals(BookingStatus.CANCELLING, service.get(b.id()).status());
    var payment =
        new PaymentBusiness(new MemoryStore<>(new ArrayList<>(), Payment::id), service::get, clock);
    code(
        "INVALID_STATE",
        () -> payment.createPayment(new CreatePaymentInput(b.id()), "blocked-payment", client));
    assertEquals(
        BookingStatus.CANCELLED,
        original
            .cancelBooking(new CancelBookingInput(b.id(), "Отмена"), "recover-cancel", client)
            .status());
    assertEquals(10, t.schedule(sid).availableSeats());
  }

  @Test
  void interruptedCancellationBindsKeyBeforeReleasingSeats() {
    var t = tours(0, 90000);
    var data = store();
    var service = bookings(t, data);
    var a = service.createBooking(new CreateBookingInput(sid, 1, 1), "cancel-a", client);
    var b = service.createBooking(new CreateBookingInput(sid, 1, 1), "cancel-b", client);
    SeatPort offline =
        new SeatPort() {
          public Reservation reserve(String id, CreateBookingInput input) {
            return t.reserve(id, input);
          }

          public void release(String id) {
            throw new DomainException("SERVICE_UNAVAILABLE", "Tour offline");
          }
        };
    var interrupted = new BookingBusiness(data, offline, id -> List.of(), clock);
    var input = new CancelBookingInput(a.id(), "Original reason");
    code("SERVICE_UNAVAILABLE", () -> interrupted.cancelBooking(input, "shared-cancel", client));
    assertEquals(BookingStatus.CANCELLING, service.get(a.id()).status());
    code(
        "CONFLICT",
        () ->
            service.cancelBooking(
                new CancelBookingInput(b.id(), "Original reason"), "shared-cancel", client));
    code(
        "CONFLICT",
        () ->
            service.cancelBooking(
                new CancelBookingInput(a.id(), "Changed reason"), "shared-cancel", client));
    assertEquals(BookingStatus.PENDING_PAYMENT, service.get(b.id()).status());
    assertEquals(
        BookingStatus.CANCELLED, service.cancelBooking(input, "shared-cancel", client).status());
    assertEquals(
        BookingStatus.CANCELLED, service.cancelBooking(input, "shared-cancel", client).status());
    assertEquals(9, t.schedule(sid).availableSeats());
  }
}
