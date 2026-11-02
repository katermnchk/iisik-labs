package by.bsuir.travel.tour.service;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.util.*;
import java.util.stream.*;

public final class TourSearch {
  private TourSearch() {}

  public static Page<Tour> search(List<Tour> source, ListToursRequest r) {
    TourFilter f = r.filter();
    if (f != null) {
      if (f.directionId() != null) ReadSupport.id(f.directionId());
      if (f.from() != null && f.to() != null && f.from().isAfter(f.to()))
        throw new by.bsuir.travel.contracts.DomainException("BAD_REQUEST", "Invalid date range");
      if ((f.minPrice() != null && f.minPrice() < 0)
          || (f.maxPrice() != null && f.maxPrice() < 0)
          || (f.minPrice() != null && f.maxPrice() != null && f.minPrice() > f.maxPrice()))
        throw new by.bsuir.travel.contracts.DomainException("BAD_REQUEST", "Invalid price range");
    }
    List<Tour> items = new ArrayList<>();
    for (Tour t : source) {
      if (t.status() != TourStatus.PUBLISHED || !t.direction().isActive()) continue;
      if (f != null && f.directionId() != null && !ReadSupport.sameId(t.direction().id(), f.directionId()))
        continue;
      List<TourSchedule> schedules =
          t.schedules().stream()
              .filter(s -> matches(s, f))
              .sorted(Comparator.comparing(TourSchedule::startsAt).thenComparing(TourSchedule::id))
              .toList();
      if (schedules.isEmpty()) continue;
      items.add(
          new Tour(
              t.id(),
              t.direction(),
              t.title(),
              t.description(),
              t.durationDays(),
              t.status(),
              t.version(),
              t.createdByEmployeeId(),
              schedules));
    }
    TourSortField field =
        r.sort() == null || r.sort().field() == null ? TourSortField.STARTS_AT : r.sort().field();
    Map<String, Integer> popularity =
        r.popularity() == null
            ? Map.of()
            : r.popularity().stream()
                .collect(
                    Collectors.toMap(
                        DirectionPopularity::directionId, DirectionPopularity::confirmedTourists));
    Comparator<Tour> comparator =
        switch (field) {
          case PRICE ->
              Comparator.comparingLong(
                  t ->
                      t.schedules().stream()
                          .mapToLong(s -> s.price().amountMinor())
                          .min()
                          .orElseThrow());
          case DURATION -> Comparator.comparingInt(Tour::durationDays);
          case STARTS_AT -> Comparator.comparing(t -> t.schedules().get(0).startsAt());
          case POPULARITY ->
              Comparator.comparingInt(t -> popularity.getOrDefault(t.direction().id(), 0));
        };
    if (r.sort() != null && r.sort().direction() == SortDirection.DESC)
      comparator = comparator.reversed();
    items.sort(comparator.thenComparing(Tour::id));
    return ReadSupport.page(
        items, r.page(), field == TourSortField.POPULARITY ? r.popularityAsOf() : null);
  }

  private static boolean matches(TourSchedule s, TourFilter f) {
    if (s.status() != ScheduleStatus.OPEN) return false;
    if ((f == null || !Boolean.FALSE.equals(f.onlyAvailable())) && s.availableSeats() <= 0)
      return false;
    if (f == null) return true;
    var day = s.startsAt().withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDate();
    return (f.from() == null || !day.isBefore(f.from()))
        && (f.to() == null || !day.isAfter(f.to()))
        && (f.minPrice() == null || s.price().amountMinor() >= f.minPrice())
        && (f.maxPrice() == null || s.price().amountMinor() <= f.maxPrice());
  }
}
