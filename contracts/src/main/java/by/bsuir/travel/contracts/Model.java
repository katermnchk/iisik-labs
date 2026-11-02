package by.bsuir.travel.contracts;

import java.time.*;
import java.util.List;

public final class Model {
  private Model() {}

  public enum RoleCode {
    CLIENT,
    EMPLOYEE,
    ADMIN
  }

  public enum UserStatus {
    ACTIVE,
    BLOCKED
  }

  public enum TourStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
  }

  public enum ScheduleStatus {
    OPEN,
    CLOSED,
    CANCELLED
  }

  public enum TransportKind {
    AIR,
    RAIL,
    BUS,
    OTHER
  }

  public enum BookingStatus {
    RESERVING,
    PENDING_PAYMENT,
    CONFIRMED,
    EXPIRED,
    CANCELLING,
    REFUND_PENDING,
    CANCELLED,
    COMPLETED,
    REJECTED
  }

  public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUND_PENDING,
    REFUNDED
  }

  public enum RefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED
  }

  public enum NotificationKind {
    WELCOME,
    BOOKING_STATUS,
    TOUR_CHANGE,
    PAYMENT_STATUS
  }

  public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED
  }

  public enum TourSortField {
    STARTS_AT,
    PRICE,
    DURATION,
    POPULARITY
  }

  public enum SortDirection {
    ASC,
    DESC
  }

  public record PageInput(int page, int size) {
    public PageInput {
      if (page < 0 || size < 1 || size > 100)
        throw new IllegalArgumentException("Page must be >= 0 and size in 1..100");
    }
  }

  public record Money(long amountMinor, String currency) {}

  public record Role(RoleCode code, String name) {}

  public record ClientProfile(String userId, String phone) {}

  public record EmployeeProfile(String userId, String position) {}

  public record User(
      String id,
      Role role,
      String email,
      String firstName,
      String lastName,
      UserStatus status,
      ClientProfile clientProfile,
      EmployeeProfile employeeProfile) {}

  public record Principal(
      String userId,
      RoleCode role,
      UserStatus status,
      String email,
      String clientId,
      String employeeId) {}

  public record Direction(
      String id, String country, String city, String description, boolean isActive) {}

  public record Accommodation(
      String id,
      String scheduleId,
      String name,
      String address,
      OffsetDateTime checkInAt,
      OffsetDateTime checkOutAt,
      String roomType) {}

  public record Transport(
      String id,
      String scheduleId,
      TransportKind kind,
      String carrier,
      String routeNumber,
      OffsetDateTime departureAt,
      OffsetDateTime arrivalAt) {}

  public record TourSchedule(
      String id,
      String tourId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      int capacity,
      int reservedSeats,
      int availableSeats,
      Money price,
      long priceVersion,
      long version,
      ScheduleStatus status,
      List<Accommodation> accommodations,
      List<Transport> transports) {}

  public record Tour(
      String id,
      Direction direction,
      String title,
      String description,
      int durationDays,
      TourStatus status,
      long version,
      String createdByEmployeeId,
      List<TourSchedule> schedules) {}

  public record Payment(
      String id,
      String bookingId,
      Money amount,
      PaymentStatus status,
      String paymentUrl,
      OffsetDateTime createdAt) {}

  public record Booking(
      String id,
      String clientId,
      String scheduleId,
      String directionId,
      String tourTitle,
      String directionName,
      OffsetDateTime startsAt,
      int travelerCount,
      Money unitPrice,
      Money total,
      BookingStatus status,
      OffsetDateTime holdExpiresAt,
      OffsetDateTime createdAt,
      long version,
      List<Payment> payments) {}

  public record Notification(
      String id,
      String userId,
      String bookingId,
      NotificationKind kind,
      String title,
      String body,
      DeliveryStatus status,
      OffsetDateTime createdAt) {}

  public record PageInfo(int page, int size, boolean hasNext) {}

  public record Page<T>(
      List<T> items, int totalCount, PageInfo page, OffsetDateTime popularityAsOf) {}

  public record TourFilter(
      String directionId,
      LocalDate from,
      LocalDate to,
      Long minPrice,
      Long maxPrice,
      Boolean onlyAvailable) {}

  public record TourSort(TourSortField field, SortDirection direction) {}

  public record AnalyticsFilter(OffsetDateTime from, OffsetDateTime to) {}

  public record DirectionPopularity(
      String directionId, String directionName, int confirmedTourists, int rank) {}

  public record SalesAnalytics(
      Money grossSales,
      Money refunded,
      Money netSales,
      double occupancy,
      List<DirectionPopularity> directions,
      OffsetDateTime asOf) {}

  public record SaleFact(
      String factId,
      String sourceEventId,
      String sourceOperationId,
      String paymentId,
      String bookingId,
      String scheduleId,
      String directionId,
      long amountMinor,
      String currency,
      String factType,
      OffsetDateTime occurredAt) {}

  public record ScheduleLoad(
      String scheduleId,
      String directionId,
      OffsetDateTime startsAt,
      int capacity,
      int confirmedSeats) {}

  public record PopularityDaily(LocalDate day, String directionId, int confirmedTourists) {}

  public record AnalyticsData(
      List<SaleFact> facts,
      List<ScheduleLoad> loads,
      List<PopularityDaily> popularity,
      List<Direction> directions) {}

  public record GetPrincipalRequest(String accessToken) {}

  public record GetUserRequest(String userId) {}

  public record ListDirectionsRequest(boolean activeOnly) {}

  public record ListToursRequest(
      TourFilter filter,
      TourSort sort,
      PageInput page,
      List<DirectionPopularity> popularity,
      OffsetDateTime popularityAsOf) {}

  public record GetTourRequest(String tourId, RoleCode viewerRole) {}

  public record GetBookingRequest(String bookingId) {}

  public record ListBookingsRequest(String clientId, PageInput page) {}

  public record ListPaymentsRequest(List<String> bookingIds) {}

  public record ListNotificationsRequest(String userId, PageInput page) {}

  public record GetAnalyticsRequest(AnalyticsFilter filter) {}

  public record GetPopularityRequest(OffsetDateTime from, OffsetDateTime to) {}
}
