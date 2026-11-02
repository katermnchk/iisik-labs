package by.bsuir.travel.tour.api;

import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.tour.service.TourReadService;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class TourReadController {
  private final TourReadService service;

  public TourReadController(TourReadService service) {
    this.service = service;
  }

  @GetMapping("/tours/{id}")
  public Tour tour(@PathVariable String id) {
    return service.tour(new GetTourRequest(id, RoleCode.CLIENT));
  }

  @GetMapping("/directions")
  public List<Direction> directions(@RequestParam(defaultValue = "true") boolean activeOnly) {
    return service.directions(new ListDirectionsRequest(activeOnly));
  }

  @GetMapping("/tours")
  public Page<Tour> tours(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String directionId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long minPrice,
      @RequestParam(required = false) Long maxPrice,
      @RequestParam(defaultValue = "true") boolean onlyAvailable,
      @RequestParam(defaultValue = "STARTS_AT") TourSortField sort,
      @RequestParam(defaultValue = "ASC") SortDirection order) {
    if (sort == TourSortField.POPULARITY)
      throw new DomainException(
          "BAD_REQUEST",
          "Popularity snapshot is required; use GraphQL demo or service client contract");
    return service.tours(
        new ListToursRequest(
            new TourFilter(directionId, from, to, minPrice, maxPrice, onlyAvailable),
            new TourSort(sort, order),
            new PageInput(page, size),
            List.of(),
            null));
  }
}
