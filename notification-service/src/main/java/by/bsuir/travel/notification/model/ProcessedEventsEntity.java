package by.bsuir.travel.notification.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventsEntityKey.class)
public class ProcessedEventsEntity {
  protected ProcessedEventsEntity() {}

  @Id
  @Column(name = "consumer_name", length = 80)
  private String consumerName;

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  public String getConsumerName() {
    return consumerName;
  }

  public UUID getEventId() {
    return eventId;
  }

  public OffsetDateTime getProcessedAt() {
    return processedAt;
  }
}
