package by.bsuir.travel.gateway.mock;

import by.bsuir.travel.contracts.Clients.AnalyticsClient;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockAnalyticsClient implements AnalyticsClient {
  private final AnalyticsData data;
  private final Clock clock;

  public MockAnalyticsClient(MockData source, Clock clock) {
    data = source.object("analytics", AnalyticsData.class);
    this.clock = clock;
  }

  public SalesAnalytics analytics(GetAnalyticsRequest r) {
    return AnalyticsCalculation.calculate(data, r.filter(), OffsetDateTime.now(clock));
  }

  public List<DirectionPopularity> popularity(GetPopularityRequest r) {
    return AnalyticsCalculation.popularity(data, r.from(), r.to());
  }
}
