package by.bsuir.travel.gateway.config;

import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.transport.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Import(TransportConfig.class)
public class RemoteConfig {
  @Bean
  Settlement settlement(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("PAYMENT_RPC", "localhost:9094"), Settlement.class);
  }

  @Bean
  UserClient userClient(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("USER_RPC", "localhost:9091"), UserClient.class);
  }

  @Bean
  TourClient tourClient(
      RpcClient c,
      Environment e,
      org.springframework.data.redis.core.StringRedisTemplate redis,
      com.fasterxml.jackson.databind.ObjectMapper mapper) {
    return new CachedTourClient(
        c.proxy(e.getProperty("TOUR_RPC", "localhost:9092"), TourClient.class), redis, mapper);
  }

  @Bean
  BookingClient bookingClient(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), BookingClient.class);
  }

  @Bean
  PaymentClient paymentClient(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("PAYMENT_RPC", "localhost:9094"), PaymentClient.class);
  }

  @Bean
  NotificationClient notificationClient(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("NOTIFICATION_RPC", "localhost:9095"), NotificationClient.class);
  }

  @Bean
  AnalyticsClient analyticsClient(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("ANALYTICS_RPC", "localhost:9096"), AnalyticsClient.class);
  }

  @Bean
  TourCommands tourCommands(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("TOUR_RPC", "localhost:9092"), TourCommands.class);
  }

  @Bean
  BookingCommands bookingCommands(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), BookingCommands.class);
  }

  @Bean
  PaymentCommands paymentCommands(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("PAYMENT_RPC", "localhost:9094"), PaymentCommands.class);
  }

  @Bean
  UserCommands userCommands(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("USER_RPC", "localhost:9091"), UserCommands.class);
  }

  @Bean
  BookingQueries bookingQueries(RpcClient c, Environment e) {
    return c.proxy(e.getProperty("BOOKING_RPC", "localhost:9093"), BookingQueries.class);
  }
}
