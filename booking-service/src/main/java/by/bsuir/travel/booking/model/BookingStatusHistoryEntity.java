package by.bsuir.travel.booking.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "booking_status_history")
public class BookingStatusHistoryEntity {
  protected BookingStatusHistoryEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(name = "from_status", length = 24)
  private String fromStatus;

  @Column(name = "to_status", length = 24)
  private String toStatus;

  @Column(name = "reason", length = 255)
  private String reason;

  @Column(name = "changed_by_user_id")
  private UUID changedByUserId;

  @Column(name = "changed_at")
  private OffsetDateTime changedAt;

  public UUID getId() {
    return id;
  }

  public UUID getBookingId() {
    return bookingId;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public String getToStatus() {
    return toStatus;
  }

  public String getReason() {
    return reason;
  }

  public UUID getChangedByUserId() {
    return changedByUserId;
  }

  public OffsetDateTime getChangedAt() {
    return changedAt;
  }
}
