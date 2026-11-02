package by.bsuir.travel.booking.service;

import by.bsuir.travel.business.*;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.persistence.JdbcStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  BookingBusiness commands(
      BookingReadService read,
      JdbcTemplate j,
      PlatformTransactionManager manager,
      EntityManager em,
      ObjectMapper mapper,
      by.bsuir.travel.transport.RpcClient rpc,
      org.springframework.core.env.Environment env)
      throws Exception {
    var seats =
        new by.bsuir.travel.transport.RemoteSeats(
            rpc.proxy(
                env.getProperty("TOUR_RPC", "localhost:9092"),
                by.bsuir.travel.contracts.Identity.Reservations.class));
    var payments =
        rpc.proxy(
            env.getProperty("PAYMENT_RPC", "localhost:9094"),
            by.bsuir.travel.contracts.Clients.PaymentClient.class);
    var store =
        new JdbcStore<Booking>(
            j,
            new TransactionTemplate(manager),
            () -> {
              em.clear();
              return read.all();
            },
            (b, change) -> {
              var previous = j.queryForList("select status from bookings where id=?::uuid", b.id());
              j.update(
                  "insert into"
                      + " bookings(id,client_id,schedule_id,direction_id_snapshot,tour_title_snapshot,direction_name_snapshot,starts_at_snapshot,traveler_count,unit_price_minor,total_amount_minor,currency,status,hold_expires_at,version,created_at,updated_at)"
                      + " values (?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?) on"
                      + " conflict(id) do update set"
                      + " status=excluded.status,hold_expires_at=excluded.hold_expires_at,version=excluded.version,updated_at=excluded.updated_at",
                  b.id(),
                  b.clientId(),
                  b.scheduleId(),
                  b.directionId(),
                  b.tourTitle(),
                  b.directionName(),
                  b.startsAt(),
                  b.travelerCount(),
                  b.unitPrice().amountMinor(),
                  b.total().amountMinor(),
                  b.total().currency(),
                  b.status().name(),
                  b.holdExpiresAt(),
                  b.version(),
                  b.createdAt(),
                  OffsetDateTime.now(clock()));
              String old = previous.isEmpty() ? null : previous.get(0).get("status").toString();
              if (!b.status().name().equals(old))
                j.update(
                    "insert into"
                        + " booking_status_history(id,booking_id,from_status,to_status,reason,changed_by_user_id,changed_at)"
                        + " values (?::uuid,?::uuid,?,?,?,?::uuid,?)",
                    UUID.randomUUID().toString(),
                    b.id(),
                    old,
                    b.status().name(),
                    change == null ? "Business operation" : change.reason(),
                    change == null ? null : change.actorId(),
                    OffsetDateTime.now(clock()));
            });
    return new BookingBusiness(
        store,
        seats,
        id -> {
          if (!j.queryForList("select payment_id from payment_intents where booking_id=?::uuid", id)
              .isEmpty()) throw new DomainException("CONFLICT", "Booking has an admitted payment");
          return payments.payments(new ListPaymentsRequest(List.of(id)));
        },
        clock());
  }
}
