package by.bsuir.travel.auth;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthApi {
  private final AuthService auth;

  public AuthApi(AuthService a) {
    auth = a;
  }

  private String token(String h) {
    if (h == null || !h.startsWith("Bearer "))
      throw new DomainException("UNAUTHENTICATED", "Bearer JWT required");
    return h.substring(7);
  }

  @PostMapping("/register")
  public AuthPayload register(@RequestBody RegisterInput i) {
    return auth.register(i);
  }

  @PostMapping("/login")
  public AuthPayload login(@RequestBody AuthenticateInput i) {
    return auth.login(i);
  }

  @PostMapping("/validate")
  public Principal validate(@RequestHeader(value = "Authorization", required = false) String h) {
    return auth.validate(token(h));
  }

  @PostMapping("/logout")
  public Boolean logout(@RequestHeader(value = "Authorization", required = false) String h) {
    return auth.logout(token(h));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<?> error(DomainException e) {
    int status =
        switch (e.getCode()) {
          case "UNAUTHENTICATED" -> 401;
          case "FORBIDDEN" -> 403;
          case "CONFLICT" -> 409;
          case "SERVICE_UNAVAILABLE" -> 503;
          default -> 400;
        };
    return ResponseEntity.status(status)
        .body(Map.of("code", e.getCode(), "message", e.getMessage()));
  }
}
