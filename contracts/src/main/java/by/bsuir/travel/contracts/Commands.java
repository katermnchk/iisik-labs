package by.bsuir.travel.contracts;

import by.bsuir.travel.contracts.Model.*;
import java.time.OffsetDateTime;

public final class Commands {
  private Commands() {}

  private static void required(Object value, String name) {
    if (value == null) throw new IllegalArgumentException(name + " is required");
  }

  private static void positive(long value, String name) {
    if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
  }

  public record CreateTourInput(
      String directionId, String title, String description, int durationDays) {
    public CreateTourInput {
      required(directionId, "directionId");
      required(title, "title");
      required(description, "description");
      positive(durationDays, "durationDays");
    }
  }

  public record UpdateTourInput(
      String id,
      String title,
      String description,
      Integer durationDays,
      TourStatus status,
      long expectedVersion) {
    public UpdateTourInput {
      required(id, "id");
      positive(expectedVersion, "expectedVersion");
    }
  }

  public record CreateScheduleInput(
      String tourId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      int capacity,
      Long basePriceMinor) {
    public CreateScheduleInput {
      required(tourId, "tourId");
      required(startsAt, "startsAt");
      required(endsAt, "endsAt");
      positive(capacity, "capacity");
      required(basePriceMinor, "basePriceMinor");
    }
  }

  public record UpdateScheduleInput(
      String id,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      Integer capacity,
      Long basePriceMinor,
      ScheduleStatus status,
      long expectedVersion) {
    public UpdateScheduleInput {
      required(id, "id");
      positive(expectedVersion, "expectedVersion");
    }
  }

  public record CreateBookingInput(
      String scheduleId, int travelerCount, long expectedPriceVersion) {
    public CreateBookingInput {
      required(scheduleId, "scheduleId");
      positive(travelerCount, "travelerCount");
      positive(expectedPriceVersion, "expectedPriceVersion");
    }
  }

  public record CancelBookingInput(String bookingId, String reason) {
    public CancelBookingInput {
      required(bookingId, "bookingId");
      required(reason, "reason");
    }
  }

  public record CreatePaymentInput(String bookingId) {
    public CreatePaymentInput {
      required(bookingId, "bookingId");
    }
  }

  public record ChangeBookingStatusInput(
      String bookingId, BookingStatus targetStatus, String reason, long expectedVersion) {
    public ChangeBookingStatusInput {
      required(bookingId, "bookingId");
      required(targetStatus, "targetStatus");
      required(reason, "reason");
      positive(expectedVersion, "expectedVersion");
    }
  }

  public record UpdateUserInput(
      String userId,
      RoleCode role,
      UserStatus status,
      String firstName,
      String lastName,
      String position,
      String phone) {
    public UpdateUserInput {
      required(userId, "userId");
    }
  }

  public record BookingFilter(BookingStatus status, OffsetDateTime from, OffsetDateTime to) {}

  public enum BookingSortField {
    CREATED_AT,
    TOTAL,
    STARTS_AT
  }

  public record BookingSort(BookingSortField field, SortDirection direction) {}

  public record BookingSummary(int count, int pending, int confirmed, int cancelled, Money total) {}

  public record Reservation(String bookingId, Tour tour, TourSchedule schedule, int seats) {}

  public interface TourCommands {
    Tour createTour(CreateTourInput input, Principal actor);

    Tour updateTour(UpdateTourInput input, Principal actor);

    TourSchedule createSchedule(CreateScheduleInput input, Principal actor);

    TourSchedule updateSchedule(UpdateScheduleInput input, Principal actor);
  }

  public interface BookingCommands {
    Booking createBooking(CreateBookingInput input, String key, Principal actor);

    Booking cancelBooking(CancelBookingInput input, String key, Principal actor);

    Booking changeBookingStatus(ChangeBookingStatusInput input, Principal actor);
  }

  public interface PaymentCommands {
    Payment createPayment(CreatePaymentInput input, String key, Principal actor);
  }

  public interface UserCommands {
    User updateUser(UpdateUserInput input, Principal actor);
  }
}
