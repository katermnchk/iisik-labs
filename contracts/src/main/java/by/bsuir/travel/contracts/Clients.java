package by.bsuir.travel.contracts;

import by.bsuir.travel.contracts.Model.*;
import java.util.List;

public final class Clients {
  private Clients() {}

  public interface UserClient {
    Principal principal(GetPrincipalRequest request);

    User user(GetUserRequest request);
  }

  public interface TourClient {
    Page<Tour> tours(ListToursRequest request);

    Tour tour(GetTourRequest request);

    List<Direction> directions(ListDirectionsRequest request);
  }

  public interface BookingClient {
    Booking booking(GetBookingRequest request);

    Page<Booking> bookings(ListBookingsRequest request);
  }

  public interface PaymentClient {
    List<Payment> payments(ListPaymentsRequest request);
  }

  public interface NotificationClient {
    Page<Notification> notifications(ListNotificationsRequest request);
  }

  public interface AnalyticsClient {
    SalesAnalytics analytics(GetAnalyticsRequest request);

    List<DirectionPopularity> popularity(GetPopularityRequest request);
  }
}
