package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.Clients.TourClient;
import by.bsuir.travel.contracts.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.slf4j.*;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class CachedTourClient implements TourClient {
  private final TourClient remote;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final Logger log = LoggerFactory.getLogger(CachedTourClient.class);

  public CachedTourClient(TourClient r, StringRedisTemplate s, ObjectMapper m) {
    remote = r;
    redis = s;
    mapper = m;
  }

  public Page<Tour> tours(ListToursRequest r) {
    return remote.tours(r);
  }

  public Tour tour(GetTourRequest r) {
    return remote.tour(r);
  }

  public List<Direction> directions(ListDirectionsRequest r) {
    String key = "travel:cache:directions:" + r.activeOnly();
    try {
      String hit = redis.opsForValue().get(key);
      if (hit != null) {
        log.info("cache hit key={}", key);
        return mapper.readValue(
            hit, mapper.getTypeFactory().constructCollectionType(List.class, Direction.class));
      }
    } catch (Exception e) {
      log.warn("cache read unavailable; querying authority");
    }
    var value = remote.directions(r);
    try {
      redis.opsForValue().set(key, mapper.writeValueAsString(value), Duration.ofSeconds(30));
      log.info("cache miss key={}", key);
    } catch (Exception e) {
      log.warn("cache write unavailable");
    }
    return value;
  }
}
