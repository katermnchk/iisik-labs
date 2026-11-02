package by.bsuir.travel.gateway.service;

import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TravelQueryService {
  private final UserClient users;
  private final TourClient tours;
  private final BookingClient bookings;
  private final PaymentClient payments;
  private final NotificationClient notifications;
  private final AnalyticsClient analytics;
  private final Clock clock;

  public TravelQueryService(
      UserClient users,
      TourClient tours,
      BookingClient bookings,
      PaymentClient payments,
      NotificationClient notifications,
      AnalyticsClient analytics,
      Clock clock) {
    this.users = users;
    this.tours = tours;
    this.bookings = bookings;
    this.payments = payments;
    this.notifications = notifications;
    this.analytics = analytics;
    this.clock = clock;
  }

  private void role(Principal p, RoleCode... allowed) {
    if (Arrays.stream(allowed).noneMatch(x -> x == p.role()))
      throw new DomainException("FORBIDDEN", "Operation is not permitted for this role");
  }

  public User me(Principal p) {
    return users.user(new GetUserRequest(p.userId()));
  }

  public Page<Tour> tours(TourFilter filter, TourSort sort, PageInput page, Principal p) {
    List<DirectionPopularity> popularity = List.of();
    OffsetDateTime asOf = null;
    if (sort != null && sort.field() == TourSortField.POPULARITY) {
      asOf = OffsetDateTime.now(clock);
      popularity = analytics.popularity(new GetPopularityRequest(asOf.minusDays(30), asOf));
    }
    return tours.tours(new ListToursRequest(filter, sort, page, popularity, asOf));
  }

  public Tour tour(String id, Principal p) {
    return tours.tour(new GetTourRequest(id, p.role()));
  }

  public Booking booking(String id, Principal p) {
    Booking b = bookings.booking(new GetBookingRequest(id));
    if (p.role() == RoleCode.CLIENT && !b.clientId().equals(p.clientId()))
      throw new DomainException("FORBIDDEN", "Booking belongs to another client");
    return b;
  }

  public Page<Booking> myBookings(PageInput page, Principal p) {
    role(p, RoleCode.CLIENT);
    return bookings.bookings(new ListBookingsRequest(p.clientId(), page));
  }

  public Page<Notification> notifications(PageInput page, Principal p) {
    return notifications.notifications(new ListNotificationsRequest(p.userId(), page));
  }

  public List<Direction> directions(boolean activeOnly, Principal p) {
    return tours.directions(new ListDirectionsRequest(activeOnly));
  }

  public SalesAnalytics salesAnalytics(AnalyticsFilter filter, Principal p) {
    role(p, RoleCode.ADMIN);
    ReadSupport.period(filter);
    return analytics.analytics(new GetAnalyticsRequest(filter));
  }

  public Map<Booking, List<Payment>> payments(List<Booking> source) {
    List<Payment> values =
        payments.payments(new ListPaymentsRequest(source.stream().map(Booking::id).toList()));
    Map<Booking, List<Payment>> out = new LinkedHashMap<>();
    for (Booking b : source)
      out.put(b, values.stream().filter(p -> p.bookingId().equals(b.id())).toList());
    return out;
  }
}
