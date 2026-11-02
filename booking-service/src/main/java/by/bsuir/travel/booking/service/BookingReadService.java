package by.bsuir.travel.booking.service;

import by.bsuir.travel.booking.model.*;
import by.bsuir.travel.booking.repository.*;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.BookingClient;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingReadService implements BookingClient {
  private final BookingsEntityRepository bookings;

  public BookingReadService(BookingsEntityRepository bookings) {
    this.bookings = bookings;
  }

  public List<Booking> all() {
    return bookings.findAll().stream().map(this::map).toList();
  }

  private Booking map(BookingsEntity b) {
    return new Booking(
        b.getId().toString(),
        b.getClientId().toString(),
        b.getScheduleId().toString(),
        b.getDirectionIdSnapshot().toString(),
        b.getTourTitleSnapshot(),
        b.getDirectionNameSnapshot(),
        b.getStartsAtSnapshot(),
        b.getTravelerCount(),
        new Money(b.getUnitPriceMinor(), b.getCurrency()),
        new Money(b.getTotalAmountMinor(), b.getCurrency()),
        BookingStatus.valueOf(b.getStatus()),
        b.getHoldExpiresAt(),
        b.getCreatedAt(),
        b.getVersion(),
        List.of());
  }

  public Booking booking(GetBookingRequest r) {
    ReadSupport.id(r.bookingId());
    return map(ReadSupport.found(bookings.findById(UUID.fromString(r.bookingId()))));
  }

  public Page<Booking> bookings(ListBookingsRequest r) {
    ReadSupport.id(r.clientId());
    return ReadSupport.page(
        bookings.findByClientId(UUID.fromString(r.clientId())).stream()
            .map(this::map)
            .sorted(Comparator.comparing(Booking::createdAt).reversed().thenComparing(Booking::id))
            .toList(),
        r.page());
  }
}
