package by.bsuir.travel.notification.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDeliveriesEntity {
  protected NotificationDeliveriesEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "notification_id")
  private UUID notificationId;

  @Column(name = "channel", length = 20)
  private String channel;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "attempt_no")
  private Short attemptNo;

  @Column(name = "sent_at")
  private OffsetDateTime sentAt;

  @Column(name = "error_message", length = 255)
  private String errorMessage;

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public String getChannel() {
    return channel;
  }

  public String getStatus() {
    return status;
  }

  public Short getAttemptNo() {
    return attemptNo;
  }

  public OffsetDateTime getSentAt() {
    return sentAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
