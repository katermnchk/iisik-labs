package by.bsuir.travel.auth;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
  private final String secret = "0123456789abcdef0123456789abcdef";
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);

  private Principal actor() {
    return new Principal(
        UUID.randomUUID().toString(), RoleCode.CLIENT, UserStatus.ACTIVE, "a@b.test", null, null);
  }

  @Test
  void validSignatureAndIdentity() {
    var s = new TokenService(secret, clock, 60);
    var p = actor();
    var id = UUID.randomUUID().toString();
    var c = s.verify(s.issue(p, id));
    assertEquals(p.userId(), c.getSubject());
    assertEquals(id, c.getJWTID());
  }

  @Test
  void expiredTokenIsRejected() {
    var s = new TokenService(secret, clock, 60);
    String t = s.issue(actor(), UUID.randomUUID().toString());
    assertThrows(
        DomainException.class,
        () -> new TokenService(secret, Clock.offset(clock, Duration.ofSeconds(60)), 60).verify(t));
  }

  @Test
  void differentSigningKeyIsRejected() {
    var s = new TokenService(secret, clock, 60);
    String t = s.issue(actor(), UUID.randomUUID().toString());
    assertThrows(
        DomainException.class, () -> new TokenService(secret + "wrong", clock, 60).verify(t));
  }

  @Test
  void nonJwtIsRejected() {
    assertThrows(
        DomainException.class, () -> new TokenService(secret, clock, 60).verify("demo-admin"));
  }
}
