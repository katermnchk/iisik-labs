package by.bsuir.travel.auth;

import static org.junit.jupiter.api.Assertions.*;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Model.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "issuer",
        "audience",
        "no-exp",
        "no-iat",
        "future-iat",
        "no-sub",
        "bad-sub",
        "no-jti",
        "bad-jti"
      })
  void validSignatureDoesNotMakeInvalidClaimsTrusted(String invalid) throws Exception {
    var claims =
        new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer("travel-auth")
            .audience("travel-api")
            .subject(UUID.randomUUID().toString())
            .jwtID(UUID.randomUUID().toString())
            .issueTime(java.util.Date.from(clock.instant()))
            .expirationTime(java.util.Date.from(clock.instant().plusSeconds(60)));
    switch (invalid) {
      case "issuer" -> claims.issuer("other");
      case "audience" -> claims.audience("other");
      case "no-exp" -> claims.expirationTime(null);
      case "no-iat" -> claims.issueTime(null);
      case "future-iat" -> claims.issueTime(java.util.Date.from(clock.instant().plusSeconds(6)));
      case "no-sub" -> claims.subject(null);
      case "bad-sub" -> claims.subject("not-a-uuid");
      case "no-jti" -> claims.jwtID(null);
      case "bad-jti" -> claims.jwtID("not-a-uuid");
      default -> throw new AssertionError(invalid);
    }
    var jwt =
        new com.nimbusds.jwt.SignedJWT(
            new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), claims.build());
    jwt.sign(new com.nimbusds.jose.crypto.MACSigner(secret));
    var error =
        assertThrows(
            DomainException.class,
            () -> new TokenService(secret, clock, 60).verify(jwt.serialize()));
    assertEquals("UNAUTHENTICATED", error.getCode());
  }

  @Test
  void correctlySignedUnapprovedAlgorithmIsRejected() throws Exception {
    String longSecret = secret.repeat(2);
    var issued =
        new TokenService(longSecret, clock, 60).issue(actor(), UUID.randomUUID().toString());
    var jwt =
        new com.nimbusds.jwt.SignedJWT(
            new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS384),
            com.nimbusds.jwt.SignedJWT.parse(issued).getJWTClaimsSet());
    jwt.sign(new com.nimbusds.jose.crypto.MACSigner(longSecret));
    var error =
        assertThrows(
            DomainException.class,
            () -> new TokenService(longSecret, clock, 60).verify(jwt.serialize()));
    assertEquals("UNAUTHENTICATED", error.getCode());
  }
}
