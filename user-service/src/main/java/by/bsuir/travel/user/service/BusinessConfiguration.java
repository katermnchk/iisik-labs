package by.bsuir.travel.user.service;

import by.bsuir.travel.business.*;
import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Commands.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.persistence.JdbcStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class BusinessConfiguration {
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictJson() {
    return builder ->
        builder
            .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .featuresToEnable(
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  UserBusiness commands(
      UserReadService read, JdbcTemplate j, PlatformTransactionManager manager, EntityManager em) {
    return new UserBusiness(
        new JdbcStore<>(
            j,
            new TransactionTemplate(manager),
            () -> {
              em.clear();
              return read.all();
            },
            u -> {
              j.update(
                  "update users set role_id=(select id from roles where"
                      + " code=?),first_name=?,last_name=?,status=?,updated_at=? where id=?::uuid",
                  u.role().code().name(),
                  u.firstName(),
                  u.lastName(),
                  u.status().name(),
                  OffsetDateTime.now(clock()),
                  u.id());
              if (u.employeeProfile() != null)
                j.update(
                    "insert into employees(user_id,position) values (?::uuid,?) on"
                        + " conflict(user_id) do update set position=excluded.position",
                    u.id(),
                    u.employeeProfile().position());
              if (u.clientProfile() != null)
                j.update(
                    "insert into clients(user_id,phone) values (?::uuid,?) on conflict(user_id) do"
                        + " update set phone=excluded.phone",
                    u.id(),
                    u.clientProfile().phone());
            }));
  }
}
