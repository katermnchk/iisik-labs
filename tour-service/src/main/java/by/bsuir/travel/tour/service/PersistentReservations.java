package by.bsuir.travel.tour.service;

import by.bsuir.travel.business.TourBusiness;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Identity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentReservations implements Reservations {
  private final JdbcTemplate j;
  private final TourBusiness tours;
  private final ObjectMapper mapper;

  public PersistentReservations(JdbcTemplate j, TourBusiness t, ObjectMapper m) {
    this.j = j;
    tours = t;
    mapper = m;
  }

  private Reservation decode(String s) {
    try {
      return mapper.readValue(s, Reservation.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Reservation reserve(ReserveRequest i) {
    j.execute("select pg_advisory_xact_lock(417204)");
    var rows =
        j.queryForList(
            "select payload,status from rpc_reservations where booking_id=?::uuid", i.bookingId());
    if (!rows.isEmpty()) {
      var row = rows.get(0);
      var old = decode(row.get("payload").toString());
      if (!old.schedule().id().equals(i.input().scheduleId())
          || old.seats() != i.input().travelerCount()
          || old.schedule().priceVersion() != i.input().expectedPriceVersion())
        throw new DomainException("CONFLICT", "Reservation input differs");
      if (!"HELD".equals(row.get("status")))
        throw new DomainException("CONFLICT", "Reservation already released");
      return old;
    }
    var input = i.input();
    var schedule = tours.schedule(input.scheduleId());
    var tour = tours.get(schedule.tourId());
    if (input.travelerCount() < 1 || input.travelerCount() > 100)
      throw new DomainException("BAD_REQUEST", "Travelers must be in 1..100");
    if (tour.status() != by.bsuir.travel.contracts.Model.TourStatus.PUBLISHED
        || !tour.direction().isActive()
        || schedule.status() != by.bsuir.travel.contracts.Model.ScheduleStatus.OPEN
        || !schedule.startsAt().isAfter(java.time.OffsetDateTime.now()))
      throw new DomainException("INVALID_STATE", "Schedule is not bookable");
    if (schedule.priceVersion() != input.expectedPriceVersion())
      throw new DomainException("PRICE_CHANGED", "Price has changed");
    try {
      Math.multiplyExact(schedule.price().amountMinor(), (long) input.travelerCount());
    } catch (ArithmeticException e) {
      throw new DomainException("BAD_REQUEST", "Total amount overflows Long");
    }
    int changed =
        j.update(
            "update tour_schedules set reserved_seats=reserved_seats+?,version=version+1 where"
                + " id=?::uuid and capacity-reserved_seats>=?",
            input.travelerCount(),
            schedule.id(),
            input.travelerCount());
    if (changed != 1) throw new DomainException("INSUFFICIENT_SEATS", "Not enough seats");
    var result = new Reservation(i.bookingId(), tour, schedule, input.travelerCount());
    try {
      j.update(
          "insert into rpc_reservations(booking_id,payload,status) values (?::uuid,?,'HELD')",
          i.bookingId(),
          mapper.writeValueAsString(result));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return result;
  }

  @Transactional
  public Boolean release(String id) {
    j.execute("select pg_advisory_xact_lock(417204)");
    var rows =
        j.queryForList("select payload,status from rpc_reservations where booking_id=?::uuid", id);
    if (rows.isEmpty()) {
      // Existing laboratory data predates durable RPC reservations. Its holds remain
      // authoritative until released; keep the counter and hold state in this transaction.
      var legacy =
          j.queryForList(
              "select schedule_id,seats,status from seat_holds where booking_id=?::uuid", id);
      if (legacy.isEmpty() || "RELEASED".equals(legacy.get(0).get("status"))) return true;
      var hold = legacy.get(0);
      releaseSeats(hold.get("schedule_id").toString(), ((Number) hold.get("seats")).intValue());
      j.update("update seat_holds set status='RELEASED' where booking_id=?::uuid", id);
      return true;
    }
    if ("RELEASED".equals(rows.get(0).get("status"))) return true;
    var h = decode(rows.get(0).get("payload").toString());
    releaseSeats(h.schedule().id(), h.seats());
    j.update("update rpc_reservations set status='RELEASED' where booking_id=?::uuid", id);
    return true;
  }

  private void releaseSeats(String scheduleId, int seats) {
    int changed =
        j.update(
            "update tour_schedules set reserved_seats=reserved_seats-?,version=version+1 where"
                + " id=?::uuid and reserved_seats>=?",
            seats,
            scheduleId,
            seats);
    if (changed != 1) throw new DomainException("CONFLICT", "Invalid reservation count");
  }
}
