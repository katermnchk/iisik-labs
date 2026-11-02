package by.bsuir.travel.tour.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.TourClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.tour.model.*;
import by.bsuir.travel.tour.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TourReadService implements TourClient {
  private final DirectionsEntityRepository directions;
  private final ToursEntityRepository tours;
  private final TourSchedulesEntityRepository tourschedules;
  private final AccommodationsEntityRepository accommodations;
  private final TransportsEntityRepository transports;

  public TourReadService(
      DirectionsEntityRepository directions,
      ToursEntityRepository tours,
      TourSchedulesEntityRepository tourschedules,
      AccommodationsEntityRepository accommodations,
      TransportsEntityRepository transports) {
    this.directions = directions;
    this.tours = tours;
    this.tourschedules = tourschedules;
    this.accommodations = accommodations;
    this.transports = transports;
  }

  private Tour map(ToursEntity x) {
    var d = directions.findById(x.getDirectionId()).orElseThrow();
    return new Tour(
        x.getId().toString(),
        direction(d),
        x.getTitle(),
        x.getDescription(),
        x.getDurationDays(),
        TourStatus.valueOf(x.getStatus()),
        x.getVersion(),
        x.getCreatedByEmployeeId().toString(),
        tourschedules.findByTourId(x.getId()).stream()
            .map(this::schedule)
            .sorted(Comparator.comparing(TourSchedule::startsAt).thenComparing(TourSchedule::id))
            .toList());
  }

  private Direction direction(DirectionsEntity d) {
    return new Direction(
        d.getId().toString(), d.getCountry(), d.getCity(), d.getDescription(), d.getIsActive());
  }

  private TourSchedule schedule(TourSchedulesEntity s) {
    List<Accommodation> a =
        accommodations.findByScheduleId(s.getId()).stream()
            .sorted(Comparator.comparing(AccommodationsEntity::getId))
            .map(
                x ->
                    new Accommodation(
                        x.getId().toString(),
                        x.getScheduleId().toString(),
                        x.getName(),
                        x.getAddress(),
                        x.getCheckInAt(),
                        x.getCheckOutAt(),
                        x.getRoomType()))
            .toList();
    List<Transport> t =
        transports.findByScheduleId(s.getId()).stream()
            .sorted(Comparator.comparing(TransportsEntity::getId))
            .map(
                x ->
                    new Transport(
                        x.getId().toString(),
                        x.getScheduleId().toString(),
                        TransportKind.valueOf(x.getKind()),
                        x.getCarrier(),
                        x.getRouteNumber(),
                        x.getDepartureAt(),
                        x.getArrivalAt()))
            .toList();
    return new TourSchedule(
        s.getId().toString(),
        s.getTourId().toString(),
        s.getStartsAt(),
        s.getEndsAt(),
        s.getCapacity(),
        s.getReservedSeats(),
        s.getCapacity() - s.getReservedSeats(),
        new Money(s.getBasePriceMinor(), s.getCurrency()),
        s.getPriceVersion(),
        s.getVersion(),
        ScheduleStatus.valueOf(s.getStatus()),
        a,
        t);
  }

  public Page<Tour> tours(ListToursRequest r) {
    return TourSearch.search(tours.findAll().stream().map(this::map).toList(), r);
  }

  public Tour tour(GetTourRequest r) {
    ReadSupport.id(r.tourId());
    return ReadSupport.found(
        tours
            .findById(UUID.fromString(r.tourId()))
            .filter(t -> t.getStatus().equals("PUBLISHED"))
            .map(this::map)
            .filter(t -> t.direction().isActive()));
  }

  public List<Direction> directions(ListDirectionsRequest r) {
    return directions.findAll().stream()
        .map(this::direction)
        .filter(d -> !r.activeOnly() || d.isActive())
        .sorted(Comparator.comparing(Direction::id))
        .toList();
  }
}
