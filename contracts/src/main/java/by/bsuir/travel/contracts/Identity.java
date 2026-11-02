package by.bsuir.travel.contracts;

import by.bsuir.travel.contracts.Model.*;

public final class Identity {
  public record RegisterInput(
      String email, String password, String firstName, String lastName, String phone) {}

  public record AuthenticateInput(String email, String password) {}

  public record AuthPayload(String accessToken, User user) {}

  public record RegisterProfile(
      String id, String email, String firstName, String lastName, String phone) {}

  public interface Directory {
    User registerProfile(RegisterProfile input);
  }

  public interface BookingQueries {
    java.util.List<Booking> filtered(
        Principal actor, Commands.BookingFilter filter, Commands.BookingSort sort);

    Commands.BookingSummary summary(Principal actor, Commands.BookingFilter filter);
  }

  public record ReserveRequest(String bookingId, Commands.CreateBookingInput input) {}

  public record PaymentClaim(String bookingId, String paymentId, Principal actor) {}

  public interface ScheduleDirectory {
    TourSchedule schedule(String id);
  }

  public interface PaymentAdmission {
    Boolean claimPayment(PaymentClaim input);

    Boolean expireBooking(String bookingId);

    Boolean releaseOrphanReservation(String bookingId);
  }

  public interface Settlement {
    Payment confirmPayment(String paymentId, Principal actor);

    Boolean expirePayment(String bookingId);
  }

  public record PaymentSettled(Payment payment, Booking booking) {}

  public interface Reservations {
    Commands.Reservation reserve(ReserveRequest input);

    Boolean release(String bookingId);
  }
}
