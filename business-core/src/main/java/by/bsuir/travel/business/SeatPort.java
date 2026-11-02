package by.bsuir.travel.business;

import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model;

public interface SeatPort {
  Reservation reserve(String bookingId, CreateBookingInput input);

  void release(String bookingId);

  default void release(Model.Booking booking) {
    release(booking.id());
  }
}
