package by.bsuir.travel.gateway.controller;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.gateway.service.TravelQueryService;
import java.util.*;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
public class QueryController {
  private final TravelQueryService service;

  public QueryController(TravelQueryService service) {
    this.service = service;
  }

  @QueryMapping
  public User me(@ContextValue Principal principal) {
    return service.me(principal);
  }

  @QueryMapping
  public Page<Tour> tours(
      @Argument TourFilter filter,
      @Argument TourSort sort,
      @Argument PageInput page,
      @ContextValue Principal principal) {
    return service.tours(filter, sort, page, principal);
  }

  @QueryMapping
  public Tour tour(@Argument String id, @ContextValue Principal principal) {
    return service.tour(id, principal);
  }

  @QueryMapping
  public Booking booking(@Argument String id, @ContextValue Principal principal) {
    return service.booking(id, principal);
  }

  @QueryMapping
  public Page<Booking> myBookings(@Argument PageInput page, @ContextValue Principal principal) {
    return service.myBookings(page, principal);
  }

  @QueryMapping
  public Page<Notification> notifications(
      @Argument PageInput page, @ContextValue Principal principal) {
    return service.notifications(page, principal);
  }

  @QueryMapping
  public List<Direction> directions(
      @Argument Boolean activeOnly, @ContextValue Principal principal) {
    return service.directions(activeOnly == null || activeOnly, principal);
  }

  @QueryMapping
  public SalesAnalytics salesAnalytics(
      @Argument AnalyticsFilter filter, @ContextValue Principal principal) {
    return service.salesAnalytics(filter, principal);
  }

  @BatchMapping(typeName = "Booking", field = "payments")
  public Map<Booking, List<Payment>> payments(List<Booking> source) {
    return service.payments(source);
  }
}
