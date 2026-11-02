package by.bsuir.travel.auth;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class LabAccounts {
  @Bean
  ApplicationRunner seedLabAccounts(JdbcTemplate j, Environment e) {
    return args -> {
      String password = e.getProperty("LAB_PASSWORD");
      if (password == null) return;
      var encoder = new BCryptPasswordEncoder();
      for (int n = 1; n <= 5; n++)
        j.update(
            "insert into credentials(user_id,email,password_hash,role) values (?::uuid,?,?,?) on"
                + " conflict do nothing",
            String.format("00000000-0000-0000-0000-%012d", n),
            "user" + n + "@example.test",
            encoder.encode(password),
            n < 3 || n == 5 ? "CLIENT" : n == 3 ? "EMPLOYEE" : "ADMIN");
    };
  }
}
