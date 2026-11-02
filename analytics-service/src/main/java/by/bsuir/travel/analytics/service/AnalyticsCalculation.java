package by.bsuir.travel.analytics.service;

import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;

public final class AnalyticsCalculation {
  private AnalyticsCalculation() {}

  public static List<DirectionPopularity> popularity(
      AnalyticsData d, OffsetDateTime from, OffsetDateTime to) {
    ReadSupport.period(new AnalyticsFilter(from, to));
    var fromDay = from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
    var toDay = to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
    Map<String, Integer> counts = new HashMap<>();
    for (PopularityDaily p : d.popularity())
      if (!p.day().isBefore(fromDay) && p.day().isBefore(toDay))
        counts.merge(p.directionId(), p.confirmedTourists(), Integer::sum);
    List<Direction> directions =
        d.directions().stream()
            .filter(Direction::isActive)
            .sorted(
                Comparator.<Direction>comparingInt(x -> counts.getOrDefault(x.id(), 0))
                    .reversed()
                    .thenComparing(Direction::id))
            .toList();
    List<DirectionPopularity> out = new ArrayList<>();
    for (int i = 0; i < directions.size(); i++) {
      Direction x = directions.get(i);
      out.add(
          new DirectionPopularity(
              x.id(), x.country() + ", " + x.city(), counts.getOrDefault(x.id(), 0), i + 1));
    }
    return List.copyOf(out);
  }

  public static SalesAnalytics calculate(AnalyticsData d, AnalyticsFilter f, OffsetDateTime asOf) {
    ReadSupport.period(f);
    long gross = 0, refund = 0;
    for (SaleFact fact : d.facts())
      if (!fact.occurredAt().isBefore(f.from()) && fact.occurredAt().isBefore(f.to())) {
        if (fact.factType().equals("SALE")) gross = Math.addExact(gross, fact.amountMinor());
        else if (fact.factType().equals("REFUND"))
          refund = Math.addExact(refund, fact.amountMinor());
      }
    long capacity = 0, seats = 0;
    for (ScheduleLoad l : d.loads())
      if (!l.startsAt().isBefore(f.from()) && l.startsAt().isBefore(f.to())) {
        capacity += l.capacity();
        seats += l.confirmedSeats();
      }
    return new SalesAnalytics(
        new Money(gross, "BYN"),
        new Money(refund, "BYN"),
        new Money(gross - refund, "BYN"),
        capacity == 0 ? 0 : (double) seats / capacity,
        popularity(d, f.from(), f.to()),
        asOf);
  }
}
