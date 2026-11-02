package by.bsuir.travel.user.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventsEntity {
  protected OutboxEventsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "aggregate_id")
  private UUID aggregateId;

  @Column(name = "aggregate_version")
  private Long aggregateVersion;

  @Column(name = "event_type", length = 80)
  private String eventType;

  @Column(name = "schema_version")
  private Integer schemaVersion;

  @Column(name = "correlation_id")
  private UUID correlationId;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @Column(name = "occurred_at")
  private OffsetDateTime occurredAt;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  public UUID getId() {
    return id;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public Long getAggregateVersion() {
    return aggregateVersion;
  }

  public String getEventType() {
    return eventType;
  }

  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public String getPayload() {
    return payload;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }
}
