package by.bsuir.travel.user.service;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.UserClient;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.user.model.*;
import by.bsuir.travel.user.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserReadService implements UserClient {
  private final RolesEntityRepository roles;
  private final UsersEntityRepository users;
  private final ClientsEntityRepository clients;
  private final EmployeesEntityRepository employees;

  public UserReadService(
      RolesEntityRepository roles,
      UsersEntityRepository users,
      ClientsEntityRepository clients,
      EmployeesEntityRepository employees) {
    this.roles = roles;
    this.users = users;
    this.clients = clients;
    this.employees = employees;
  }

  public User user(GetUserRequest r) {
    ReadSupport.id(r.userId());
    return map(ReadSupport.found(users.findById(UUID.fromString(r.userId()))));
  }

  public List<User> all() {
    return users.findAll().stream().map(this::map).sorted(Comparator.comparing(User::id)).toList();
  }

  private User map(UsersEntity x) {
    var role = roles.findById(x.getRoleId()).orElseThrow();
    return new User(
        x.getId().toString(),
        new Role(RoleCode.valueOf(role.getCode()), role.getName()),
        x.getEmail(),
        x.getFirstName(),
        x.getLastName(),
        UserStatus.valueOf(x.getStatus()),
        clients
            .findById(x.getId())
            .map(c -> new ClientProfile(c.getUserId().toString(), c.getPhone()))
            .orElse(null),
        employees
            .findById(x.getId())
            .map(e -> new EmployeeProfile(e.getUserId().toString(), e.getPosition()))
            .orElse(null));
  }

  public Principal principal(GetPrincipalRequest r) {
    int n =
        switch (r.accessToken() == null ? "" : r.accessToken()) {
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
