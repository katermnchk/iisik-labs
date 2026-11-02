package by.bsuir.travel.booking.service;

import by.bsuir.travel.business.*;
import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.transport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Import({TransportConfig.class, EventConfig.class})
public class RpcConfiguration {
  @Bean
  RpcServer rpcServer(
      BookingReadService read,
      BookingBusiness commands,
      PaymentAdmissionService admission,
      ObjectMapper mapper,
      Environment env) {
    return new RpcServer(
            mapper,
            Integer.parseInt(env.getProperty("GRPC_PORT", "9093")),
            env.getRequiredProperty("SERVICE_KEY"))
        .expose(BookingClient.class, read)
        .expose(BookingCommands.class, commands)
        .expose(PaymentAdmission.class, admission)
        .expose(BookingQueries.class, commands);
  }
}
