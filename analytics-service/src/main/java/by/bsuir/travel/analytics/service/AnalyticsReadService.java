package by.bsuir.travel.analytics.service;

import by.bsuir.travel.analytics.model.*;
import by.bsuir.travel.analytics.repository.*;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.AnalyticsClient;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsReadService implements AnalyticsClient {
  private final SalesFactsEntityRepository salesfacts;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final ScheduleLoadFactsEntityRepository scheduleloadfacts;
  private final DirectionPopularityDailyEntityRepository directionpopularitydaily;
  private final DirectionCatalogProjectionEntityRepository directioncatalogprojection;

  public AnalyticsReadService(
      SalesFactsEntityRepository salesfacts,
      ScheduleLoadFactsEntityRepository scheduleloadfacts,
      DirectionPopularityDailyEntityRepository directionpopularitydaily,
      DirectionCatalogProjectionEntityRepository directioncatalogprojection,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.salesfacts = salesfacts;
    this.scheduleloadfacts = scheduleloadfacts;
    this.directionpopularitydaily = directionpopularitydaily;
    this.directioncatalogprojection = directioncatalogprojection;
  }

  private AnalyticsData data() {
    List<SaleFact> facts =
        salesfacts.findAll().stream()
            .map(
                f ->
                    new SaleFact(
                        f.getFactId().toString(),
                        f.getSourceEventId().toString(),
                        f.getSourceOperationId().toString(),
                        f.getPaymentId().toString(),
                        f.getBookingId().toString(),
                        f.getScheduleId().toString(),
                        f.getDirectionId().toString(),
                        f.getAmountMinor(),
                        f.getCurrency(),
                        f.getFactType(),
                        f.getOccurredAt()))
            .toList();
    var includedIds =
        jdbc.queryForList(
            "select schedule_id from schedule_load_facts where included=true",
            java.util.UUID.class);
    List<ScheduleLoad> loads =
        scheduleloadfacts.findAll().stream()
            .filter(l -> includedIds.contains(l.getScheduleId()))
            .map(
                l ->
                    new ScheduleLoad(
                        l.getScheduleId().toString(),
                        l.getDirectionId().toString(),
                        l.getStartsAt(),
                        l.getCapacity(),
                        l.getConfirmedSeats()))
            .toList();
    List<PopularityDaily> popularity =
        directionpopularitydaily.findAll().stream()
            .map(
                p ->
                    new PopularityDaily(
                        p.getDay(), p.getDirectionId().toString(), p.getConfirmedTourists()))
            .toList();
    List<Direction> dirs =
        directioncatalogprojection.findAll().stream()
            .map(
                d ->
                    new Direction(
                        d.getDirectionId().toString(),
                        d.getCountry(),
                        d.getCity(),
                        "",
                        d.getIsActive()))
            .toList();
    return new AnalyticsData(facts, loads, popularity, dirs);
  }

  public SalesAnalytics analytics(GetAnalyticsRequest r) {
    return AnalyticsCalculation.calculate(
        data(),
        r.filter(),
        jdbc.queryForObject(
            "select snapshot_at from analytics_projection_state where id=true",
            OffsetDateTime.class));
  }

  public List<DirectionPopularity> popularity(GetPopularityRequest r) {
    ReadSupport.period(new AnalyticsFilter(r.from(), r.to()));
    return AnalyticsCalculation.popularity(data(), r.from(), r.to());
  }
}
