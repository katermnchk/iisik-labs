package by.bsuir.travel.gateway.mock;

import by.bsuir.travel.contracts.Clients.BookingClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockBookingClient implements BookingClient {
  private final List<Booking> bookings;

  public MockBookingClient(MockData data) {
    bookings = data.list("bookings", Booking.class);
  }

  public Booking booking(GetBookingRequest r) {
    ReadSupport.id(r.bookingId());
    return ReadSupport.found(
        bookings.stream().filter(b -> ReadSupport.sameId(b.id(), r.bookingId())).findFirst());
  }

  public Page<Booking> bookings(ListBookingsRequest r) {
    return ReadSupport.page(
        bookings.stream()
            .filter(b -> ReadSupport.sameId(b.clientId(), r.clientId()))
            .sorted(Comparator.comparing(Booking::createdAt).reversed().thenComparing(Booking::id))
            .toList(),
        r.page());
  }
}
