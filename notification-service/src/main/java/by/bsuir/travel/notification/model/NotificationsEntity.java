package by.bsuir.travel.notification.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationsEntity {
  protected NotificationsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(name = "kind", length = 40)
  private String kind;

  @Column(name = "title", length = 180)
  private String title;

  @Column(name = "body", columnDefinition = "text")
  private String body;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getBookingId() {
    return bookingId;
  }

  public String getKind() {
    return kind;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
