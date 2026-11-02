package by.bsuir.travel.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PopularityWindowTest {
  @Test
  void popularitySortUsesCompleteUtcDaysAtNoon() {
    var analytics = mock(AnalyticsClient.class);
    when(analytics.popularity(any()))
        .thenAnswer(
            call -> {
              GetPopularityRequest r = call.getArgument(0);
              ReadSupport.period(new AnalyticsFilter(r.from(), r.to()));
              assertEquals(OffsetDateTime.parse("2026-11-02T00:00:00Z"), r.to());
              assertEquals(r.to().minusDays(30), r.from());
              return List.of();
            });
    var tours = mock(TourClient.class);
    var service =
        new TravelQueryService(
            null,
            tours,
            null,
            null,
            null,
            analytics,
            Clock.fixed(Instant.parse("2026-11-02T12:34:56Z"), ZoneOffset.UTC));
    assertDoesNotThrow(
        () ->
            service.tours(
                null,
                new TourSort(TourSortField.POPULARITY, SortDirection.DESC),
                new PageInput(0, 10),
                null));
    verify(tours).tours(any());
  }
}
