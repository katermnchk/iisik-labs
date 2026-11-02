package by.bsuir.travel.transport;

import by.bsuir.travel.business.SeatPort;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Identity.*;

public final class RemoteSeats implements SeatPort {
  private final Reservations rpc;

  public RemoteSeats(Reservations r) {
    rpc = r;
  }

  public Reservation reserve(String id, CreateBookingInput i) {
    return rpc.reserve(new ReserveRequest(id, i));
  }

  public void release(String id) {
    rpc.release(id);
  }
}
