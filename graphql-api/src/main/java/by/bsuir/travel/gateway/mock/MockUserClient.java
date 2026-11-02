package by.bsuir.travel.gateway.mock;

import by.bsuir.travel.contracts.Clients.UserClient;
import by.bsuir.travel.contracts.DomainException;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.contracts.ReadSupport;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MockUserClient implements UserClient {
  private final List<User> users;

  public MockUserClient(MockData data) {
    users = data.list("users", User.class);
  }

  public User user(GetUserRequest r) {
    ReadSupport.id(r.userId());
    return ReadSupport.found(users.stream().filter(u -> ReadSupport.sameId(u.id(), r.userId())).findFirst());
  }

  public Principal principal(GetPrincipalRequest r) {
    String token = r.accessToken();
    int n =
        switch (token == null ? "" : token) {
          case "demo-client" -> 1;
          case "demo-client2" -> 2;
          case "demo-employee" -> 3;
          case "demo-admin" -> 4;
          case "demo-blocked" -> 5;
          default -> 0;
        };
    if (n == 0) throw new DomainException("UNAUTHENTICATED", "Unknown demo token");
    User u = user(new GetUserRequest(String.format("00000000-0000-0000-0000-%012d", n)));
    if (u.status() == UserStatus.BLOCKED) throw new DomainException("FORBIDDEN", "User is blocked");
    return new Principal(
        u.id(),
        u.role().code(),
        u.status(),
        u.email(),
        u.role().code() == RoleCode.CLIENT ? u.id() : null,
        u.role().code() == RoleCode.EMPLOYEE ? u.id() : null);
  }
}
