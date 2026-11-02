package by.bsuir.travel.tour.service;

import by.bsuir.travel.business.*;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.persistence.JdbcStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class BusinessConfiguration {
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictJson() {
    return builder ->
        builder
            .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .featuresToEnable(
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  TourBusiness commands(
      TourReadService read, JdbcTemplate j, PlatformTransactionManager manager, EntityManager em) {
    var store =
        new JdbcStore<Tour>(
            j,
            new TransactionTemplate(manager),
            () -> {
              em.clear();
              return read.all();
            },
            t -> {
              j.update(
                  "insert into"
                      + " tours(id,direction_id,title,description,duration_days,status,created_by_employee_id,version)"
                      + " values (?::uuid,?::uuid,?,?,?,?,?::uuid,?) on conflict(id) do update set"
                      + " title=excluded.title,description=excluded.description,duration_days=excluded.duration_days,status=excluded.status,version=excluded.version",
                  t.id(),
                  t.direction().id(),
                  t.title(),
                  t.description(),
                  t.durationDays(),
                  t.status().name(),
                  t.createdByEmployeeId(),
                  t.version());
              for (var s : t.schedules())
                j.update(
                    "insert into"
                        + " tour_schedules(id,tour_id,starts_at,ends_at,capacity,reserved_seats,base_price_minor,currency,price_version,version,status)"
                        + " values (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?) on conflict(id) do update"
                        + " set starts_at=excluded.starts_at,ends_at=excluded.ends_at,capacity=excluded.capacity,reserved_seats=excluded.reserved_seats,base_price_minor=excluded.base_price_minor,price_version=excluded.price_version,version=excluded.version,status=excluded.status",
                    s.id(),
                    s.tourId(),
                    s.startsAt(),
                    s.endsAt(),
                    s.capacity(),
                    s.reservedSeats(),
                    s.price().amountMinor(),
                    s.price().currency(),
                    s.priceVersion(),
                    s.version(),
                    s.status().name());
            });
    return new TourBusiness(
        store, () -> read.directions(new ListDirectionsRequest(false)), clock());
  }
}
