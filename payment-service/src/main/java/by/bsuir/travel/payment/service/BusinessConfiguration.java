package by.bsuir.travel.payment.service;

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
  PaymentBusiness commands(
      PaymentReadService read,
      JdbcTemplate j,
      PlatformTransactionManager manager,
      EntityManager em,
      ObjectMapper mapper,
      by.bsuir.travel.transport.RpcClient rpc,
      org.springframework.core.env.Environment env)
      throws Exception {
    var remote =
        rpc.proxy(
            env.getProperty("BOOKING_RPC", "localhost:9093"),
            by.bsuir.travel.contracts.Clients.BookingClient.class);
    Function<String, Booking> booking = id -> remote.booking(new GetBookingRequest(id));
    var store =
        new JdbcStore<Payment>(
            j,
            new TransactionTemplate(manager),
            () -> {
              em.clear();
              return read.all();
            },
            p -> {
              var b = booking.apply(p.bookingId());
              j.update(
                  "insert into"
                      + " payments(id,booking_id,schedule_id,direction_id,amount_minor,currency,status,payment_url,provider,provider_payment_id,external_key,created_at,updated_at)"
                      + " values (?::uuid,?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?)",
                  p.id(),
                  p.bookingId(),
                  b.scheduleId(),
                  b.directionId(),
                  p.amount().amountMinor(),
                  p.amount().currency(),
                  p.status().name(),
                  p.paymentUrl(),
                  "LAB_PROVIDER",
                  p.id(),
                  p.id(),
                  p.createdAt(),
                  p.createdAt());
            });
    var admission =
        rpc.proxy(
            env.getProperty("BOOKING_RPC", "localhost:9093"),
            by.bsuir.travel.contracts.Identity.PaymentAdmission.class);
    return new PaymentBusiness(store, booking, clock(), admission::claimPayment);
  }
}
