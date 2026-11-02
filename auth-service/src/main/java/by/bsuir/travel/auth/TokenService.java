package by.bsuir.travel.auth;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Model.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class TokenService {
  private final byte[] secret;
  private final Clock clock;
  private final long ttl;

  public TokenService(String secret, Clock clock, long ttl) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    if (this.secret.length < 32 || ttl < 1) throw new IllegalArgumentException("JWT configuration");
    this.clock = clock;
    this.ttl = ttl;
  }

  public String issue(Principal p, String session) {
    try {
      var now = clock.instant();
      var claims =
          new JWTClaimsSet.Builder()
              .issuer("travel-auth")
              .audience("travel-api")
              .subject(p.userId())
              .jwtID(session)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plusSeconds(ttl)))
              .claim("role", p.role().name())
              .build();
      var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(secret));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public JWTClaimsSet verify(String token) {
    try {
      var jwt = SignedJWT.parse(token);
      if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
          || !jwt.verify(new MACVerifier(secret))) throw new IllegalArgumentException();
      var c = jwt.getJWTClaimsSet();
      if (!"travel-auth".equals(c.getIssuer())
          || !c.getAudience().equals(List.of("travel-api"))
          || c.getExpirationTime() == null
          || !c.getExpirationTime().toInstant().isAfter(clock.instant())
          || c.getIssueTime() == null
          || c.getIssueTime().toInstant().isAfter(clock.instant().plusSeconds(5))
          || c.getJWTID() == null
          || c.getSubject() == null) throw new IllegalArgumentException();
      UUID.fromString(c.getSubject());
      UUID.fromString(c.getJWTID());
      return c;
    } catch (Exception e) {
      throw new DomainException("UNAUTHENTICATED", "Invalid or expired JWT");
    }
  }
}
