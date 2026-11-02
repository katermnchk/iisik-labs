package by.bsuir.travel.booking.api;

import by.bsuir.travel.booking.service.BookingReadService;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class BookingReadController {
  private final BookingReadService service;

  public BookingReadController(BookingReadService service) {
    this.service = service;
  }

  @GetMapping("/bookings/{id}")
  public Booking booking(@PathVariable String id) {
    return service.booking(new GetBookingRequest(id));
  }

  @GetMapping("/bookings")
  public Page<Booking> bookings(
      @RequestParam String clientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.bookings(new ListBookingsRequest(clientId, new PageInput(page, size)));
  }
}
