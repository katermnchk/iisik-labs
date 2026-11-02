package by.bsuir.travel.gateway.controller;

import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.gateway.config.AuthClient;
import java.util.Map;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
public class AuthController {
  private final AuthClient auth;

  public AuthController(AuthClient a) {
    auth = a;
  }

  @MutationMapping
  public AuthPayload register(@Argument RegisterInput input) {
    return auth.call("/auth/register", input, null, AuthPayload.class);
  }

  @MutationMapping
  public AuthPayload authenticate(@Argument AuthenticateInput input) {
    return auth.call("/auth/login", input, null, AuthPayload.class);
  }

  @MutationMapping
  public Boolean logout(@ContextValue String token) {
    return auth.call("/auth/logout", Map.of(), token, Boolean.class);
  }
}
