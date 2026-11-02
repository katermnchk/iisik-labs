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
    throw new DomainException("UNAUTHENTICATED", "Use Auth Service JWT validation");
  }
}
