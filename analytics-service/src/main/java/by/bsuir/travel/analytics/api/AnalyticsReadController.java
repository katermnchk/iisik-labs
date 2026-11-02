package by.bsuir.travel.analytics.api;

import by.bsuir.travel.analytics.service.AnalyticsReadService;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
public class AnalyticsReadController {
  private final AnalyticsReadService service;

  public AnalyticsReadController(AnalyticsReadService service) {
    this.service = service;
  }

  @GetMapping("/analytics")
  public SalesAnalytics analytics(
      @RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to) {
    return service.analytics(new GetAnalyticsRequest(new AnalyticsFilter(from, to)));
  }

  @GetMapping("/popularity")
  public List<DirectionPopularity> popularity(
      @RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to) {
    return service.popularity(new GetPopularityRequest(from, to));
  }
}
