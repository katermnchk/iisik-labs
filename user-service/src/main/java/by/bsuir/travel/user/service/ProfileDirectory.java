package by.bsuir.travel.user.service;

import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileDirectory implements Directory {
  private final JdbcTemplate j;
  private final UserReadService read;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;

  public ProfileDirectory(
      JdbcTemplate j, UserReadService r, com.fasterxml.jackson.databind.ObjectMapper m) {
    this.j = j;
    mapper = m;
    read = r;
  }

  @Transactional
  public User registerProfile(RegisterProfile i) {
    int created =
        j.update(
            "insert into"
                + " users(id,role_id,email,password_hash,first_name,last_name,status,created_at,updated_at)"
                + " values (?::uuid,(select id from roles where"
                + " code='CLIENT'),?,'AUTH_SERVICE',?,?,'ACTIVE',now(),now()) on conflict(id) do"
                + " nothing",
            i.id(),
            i.email(),
            i.firstName(),
            i.lastName());
    j.update(
        "insert into clients(user_id,phone) values (?::uuid,?) on conflict(user_id) do nothing",
        i.id(),
        i.phone());
    var user = read.user(new GetUserRequest(i.id()));
    if (created == 1)
      by.bsuir.travel.transport.Outbox.add(j, mapper, "UserRegistered", user.id(), user);
    return user;
  }
}
