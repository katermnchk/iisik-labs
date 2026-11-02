package by.bsuir.travel.user.service;

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
      UserReadService read,
      UserBusiness commands,
      ProfileDirectory directory,
      ObjectMapper mapper,
      Environment env) {
    return new RpcServer(
            mapper,
            Integer.parseInt(env.getProperty("GRPC_PORT", "9091")),
            env.getRequiredProperty("SERVICE_KEY"))
        .expose(UserClient.class, read)
        .expose(UserCommands.class, commands)
        .expose(Directory.class, directory);
  }
}
