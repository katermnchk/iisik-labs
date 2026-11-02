package by.bsuir.travel.auth;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Clients.*;
import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.transport.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final TokenService tokens;
  private final Directory directory;
  private final UserClient users;
  private final long ttl;
  private final TransactionTemplate tx;

  public AuthService(
      JdbcTemplate j,
      StringRedisTemplate r,
      RpcClient rpc,
      Environment e,
      PlatformTransactionManager tm) {
    jdbc = j;
    redis = r;
    ttl = Long.parseLong(e.getProperty("SESSION_TTL_SECONDS", "1800"));
    tokens = new TokenService(e.getRequiredProperty("JWT_SECRET"), Clock.systemUTC(), ttl);
    String target = e.getProperty("USER_RPC", "localhost:9091");
    directory = rpc.proxy(target, Directory.class);
    users = rpc.proxy(target, UserClient.class);
    tx = new TransactionTemplate(tm);
  }

  private String email(String value) {
    if (value == null || value.length() > 254 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new DomainException("BAD_REQUEST", "Invalid email");
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public AuthPayload register(RegisterInput i) {
    if (i == null) throw new DomainException("BAD_REQUEST", "Registration input is required");
    String email = email(i.email());
    if (i.password() == null
        || i.password().length() < 10
        || i.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || i.firstName() == null
        || i.firstName().isBlank()
        || i.firstName().length() > 80
        || i.lastName() == null
        || i.lastName().isBlank()
        || i.lastName().length() > 80
        || i.phone() == null
        || i.phone().isBlank()
        || i.phone().length() > 32)
      throw new DomainException("BAD_REQUEST", "Invalid registration fields");

    String hash = passwords.encode(i.password());
    String uid;
    try {
      uid =
          tx.execute(
              status -> {
                var old =
                    jdbc.queryForList("select * from credentials where email=? for update", email);
                if (!old.isEmpty()) {
                  var row = old.get(0);
                  if (Boolean.TRUE.equals(row.get("profile_ready"))
                      || !passwords.matches(i.password(), row.get("password_hash").toString()))
                    throw new DomainException("CONFLICT", "Email already registered");
                  return row.get("user_id").toString();
                }
                String id = UUID.randomUUID().toString();
                jdbc.update(
                    "insert into"
                        + " credentials(user_id,email,password_hash,role,profile_ready,first_name,last_name,phone)"
                        + " values (?::uuid,?,?,'CLIENT',false,?,?,?)",
                    id,
                    email,
                    hash,
                    i.firstName(),
                    i.lastName(),
                    i.phone());
                return id;
              });
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw new DomainException("CONFLICT", "Email already registered");
    }
    return completeRegistration(uid);
  }

  private AuthPayload completeRegistration(String uid) {
    var row = jdbc.queryForMap("select * from credentials where user_id=?::uuid", uid);
    if (!Boolean.TRUE.equals(row.get("profile_ready"))) {
      directory.registerProfile(
          new RegisterProfile(
              uid,
              row.get("email").toString(),
              row.get("first_name").toString(),
              row.get("last_name").toString(),
              row.get("phone").toString()));
      jdbc.update("update credentials set profile_ready=true where user_id=?::uuid", uid);
    }
    return session(users.user(new GetUserRequest(uid)));
  }

  public AuthPayload login(AuthenticateInput i) {
    if (i == null
        || i.password() == null
        || i.password().isEmpty()
        || i.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new DomainException("UNAUTHENTICATED", "Invalid credentials");
    String email = email(i.email());
    var rows =
        jdbc.queryForList("select user_id,password_hash from credentials where email=?", email);
    String hash =
        rows.isEmpty()
            ? "$2a$10$9Dzf6VaSMWyZmiQyUdwQ.eT8B5c6u3F4QlVPI4M91B9hV1pvkG3Ju"
            : rows.get(0).get("password_hash").toString();
    boolean match = i.password() != null && passwords.matches(i.password(), hash);
    if (rows.isEmpty() || !match)
      throw new DomainException("UNAUTHENTICATED", "Invalid credentials");
    return completeRegistration(rows.get(0).get("user_id").toString());
  }

  private Principal principal(User u) {
    if (u.status() != UserStatus.ACTIVE) throw new DomainException("FORBIDDEN", "User is blocked");
    return new Principal(
        u.id(),
        u.role().code(),
        u.status(),
        u.email(),
        u.role().code() == RoleCode.CLIENT ? u.id() : null,
        u.role().code() == RoleCode.EMPLOYEE ? u.id() : null);
  }

  private AuthPayload session(User u) {
    Principal p = principal(u);
    String sid = UUID.randomUUID().toString();
    try {
      redis.opsForValue().set("travel:session:" + sid, u.id(), Duration.ofSeconds(ttl));
    } catch (Exception e) {
      throw new DomainException("SERVICE_UNAVAILABLE", "Session store unavailable");
    }
    return new AuthPayload(tokens.issue(p, sid), u);
  }

  public Principal validate(String token) {
    var c = tokens.verify(token);
    try {
      String uid = redis.opsForValue().get("travel:session:" + c.getJWTID());
      if (!c.getSubject().equals(uid))
        throw new DomainException("UNAUTHENTICATED", "Session expired; authenticate again");
    } catch (DomainException e) {
      throw e;
    } catch (Exception e) {
      throw new DomainException("SERVICE_UNAVAILABLE", "Session store unavailable");
    }
    return principal(users.user(new GetUserRequest(c.getSubject())));
  }

  public Boolean logout(String token) {
    var c = tokens.verify(token);
    try {
      redis.delete("travel:session:" + c.getJWTID());
      return true;
    } catch (Exception e) {
      throw new DomainException("SERVICE_UNAVAILABLE", "Session store unavailable");
    }
  }
}
