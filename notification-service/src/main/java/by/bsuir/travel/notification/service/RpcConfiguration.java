package by.bsuir.travel.notification.service;

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
  RpcServer rpcServer(NotificationReadService read, ObjectMapper mapper, Environment env) {
    return new RpcServer(
            mapper,
            Integer.parseInt(env.getProperty("GRPC_PORT", "9095")),
            env.getRequiredProperty("SERVICE_KEY"))
        .expose(NotificationClient.class, read);
  }
}
