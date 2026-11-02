package by.bsuir.travel.gateway.controller;

import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
public class MutationController {
  private final TourCommands tours;
  private final BookingCommands bookings;
  private final PaymentCommands payments;
  private final UserCommands users;

  public MutationController(TourCommands t, BookingCommands b, PaymentCommands p, UserCommands u) {
    tours = t;
    bookings = b;
    payments = p;
    users = u;
  }

  @MutationMapping
  public Tour createTour(@Argument CreateTourInput input, @ContextValue Principal principal) {
    return tours.createTour(input, principal);
  }

  @MutationMapping
  public Tour updateTour(@Argument UpdateTourInput input, @ContextValue Principal principal) {
    return tours.updateTour(input, principal);
  }

  @MutationMapping
  public TourSchedule createSchedule(
      @Argument CreateScheduleInput input, @ContextValue Principal principal) {
    return tours.createSchedule(input, principal);
  }

  @MutationMapping
  public TourSchedule updateSchedule(
      @Argument UpdateScheduleInput input, @ContextValue Principal principal) {
    return tours.updateSchedule(input, principal);
  }

  @MutationMapping
  public Booking createBooking(
      @Argument CreateBookingInput input,
      @Argument String idempotencyKey,
      @ContextValue Principal principal) {
    return bookings.createBooking(input, idempotencyKey, principal);
  }

  @MutationMapping
  public Booking cancelBooking(
      @Argument CancelBookingInput input,
      @Argument String idempotencyKey,
      @ContextValue Principal principal) {
    return bookings.cancelBooking(input, idempotencyKey, principal);
  }

  @MutationMapping
  public Booking changeBookingStatus(
      @Argument ChangeBookingStatusInput input, @ContextValue Principal principal) {
    return bookings.changeBookingStatus(input, principal);
  }

  @MutationMapping
  public Payment createPayment(
      @Argument CreatePaymentInput input,
      @Argument String idempotencyKey,
      @ContextValue Principal principal) {
    return payments.createPayment(input, idempotencyKey, principal);
  }

  @MutationMapping
  public User updateUser(@Argument UpdateUserInput input, @ContextValue Principal principal) {
    return users.updateUser(input, principal);
  }
}
