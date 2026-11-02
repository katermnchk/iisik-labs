package by.bsuir.travel.gateway.mock;

import by.bsuir.travel.contracts.Clients.TourClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockTourClient implements TourClient {
  private final List<Tour> tours;
  private final List<Direction> directions;

  public MockTourClient(MockData data) {
    tours = data.list("tours", Tour.class);
    directions = data.list("directions", Direction.class);
  }

  public Page<Tour> tours(ListToursRequest r) {
    return TourSearch.search(tours, r);
  }

  public Tour tour(GetTourRequest r) {
    ReadSupport.id(r.tourId());
    return ReadSupport.found(
        tours.stream()
            .filter(
                t ->
                    ReadSupport.sameId(t.id(), r.tourId())
                        && t.status() == TourStatus.PUBLISHED
                        && t.direction().isActive())
            .findFirst());
  }

  public List<Direction> directions(ListDirectionsRequest r) {
    return directions.stream()
        .filter(d -> !r.activeOnly() || d.isActive())
        .sorted(Comparator.comparing(Direction::id))
        .toList();
  }
}
