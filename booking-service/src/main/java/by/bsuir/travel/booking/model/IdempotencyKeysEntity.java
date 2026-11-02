package by.bsuir.travel.booking.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeysEntityKey.class)
public class IdempotencyKeysEntity {
  protected IdempotencyKeysEntity() {}

  @Id
  @Column(name = "scope", length = 80)
  private String scope;

  @Id
  @Column(name = "key", length = 100)
  private String key;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(name = "request_hash", length = 64)
  private String requestHash;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "response_json", columnDefinition = "jsonb")
  private String responseJson;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  public String getScope() {
    return scope;
  }

  public String getKey() {
    return key;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public String getResponseJson() {
    return responseJson;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }
}
