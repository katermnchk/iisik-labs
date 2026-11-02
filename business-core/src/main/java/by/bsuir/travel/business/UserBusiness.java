package by.bsuir.travel.business;

import static by.bsuir.travel.business.Rules.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;

public final class UserBusiness implements UserCommands {
  private final Store<User> store;

  public UserBusiness(Store<User> store) {
    this.store = store;
  }

  public User updateUser(UpdateUserInput i, Principal p) {
    role(p, RoleCode.ADMIN);
    return store.atomic(
        () -> {
          String uid = id(i.userId());
          var u =
              ReadSupport.found(
                  store.all().stream().filter(x -> x.id().equalsIgnoreCase(uid)).findFirst());
          if (i.firstName() != null) text(i.firstName(), 80);
          if (i.lastName() != null) text(i.lastName(), 80);
          if (i.position() != null) text(i.position(), 120);
          if (i.phone() != null) text(i.phone(), 32);
          var role = i.role() == null ? u.role().code() : i.role();
          var status = i.status() == null ? u.status() : i.status();
          if (u.role().code() == RoleCode.ADMIN
              && (role != RoleCode.ADMIN || status == UserStatus.BLOCKED))
            require(
                store.all().stream()
                    .anyMatch(
                        x ->
                            !x.id().equals(u.id())
                                && x.role().code() == RoleCode.ADMIN
                                && x.status() == UserStatus.ACTIVE),
                "CONFLICT",
                "Cannot remove the last active administrator");
          var employee = u.employeeProfile();
          var client = u.clientProfile();
          if (i.position() != null) employee = new EmployeeProfile(u.id(), i.position().trim());
          if (i.phone() != null) client = new ClientProfile(u.id(), i.phone().trim());
          require(
              role != RoleCode.EMPLOYEE || employee != null,
              "BAD_REQUEST",
              "Employee position is required");
          require(
              role != RoleCode.CLIENT || client != null, "BAD_REQUEST", "Client phone is required");
          var result =
              new User(
                  u.id(),
                  new Role(role, role.name()),
                  u.email(),
                  i.firstName() == null ? u.firstName() : i.firstName().trim(),
                  i.lastName() == null ? u.lastName() : i.lastName().trim(),
                  status,
                  client,
                  employee);
          store.save(result);
          return result;
        });
  }
}
