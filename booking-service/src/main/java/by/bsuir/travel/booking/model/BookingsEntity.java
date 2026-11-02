package by.bsuir.travel.booking.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bookings")
public class BookingsEntity {
  protected BookingsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "client_id")
  private UUID clientId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "direction_id_snapshot")
  private UUID directionIdSnapshot;

  @Column(name = "tour_title_snapshot", length = 180)
  private String tourTitleSnapshot;

  @Column(name = "direction_name_snapshot", length = 180)
  private String directionNameSnapshot;

  @Column(name = "starts_at_snapshot")
  private OffsetDateTime startsAtSnapshot;

  @Column(name = "traveler_count")
  private Short travelerCount;

  @Column(name = "unit_price_minor")
  private Long unitPriceMinor;

  @Column(name = "total_amount_minor")
  private Long totalAmountMinor;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "status", length = 24)
  private String status;

  @Column(name = "hold_expires_at")
  private OffsetDateTime holdExpiresAt;

  @Column(name = "version")
  private Long version;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public UUID getId() {
    return id;
  }

  public UUID getClientId() {
    return clientId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getDirectionIdSnapshot() {
    return directionIdSnapshot;
  }

  public String getTourTitleSnapshot() {
    return tourTitleSnapshot;
  }

  public String getDirectionNameSnapshot() {
    return directionNameSnapshot;
  }

  public OffsetDateTime getStartsAtSnapshot() {
    return startsAtSnapshot;
  }

  public Short getTravelerCount() {
    return travelerCount;
  }

  public Long getUnitPriceMinor() {
    return unitPriceMinor;
  }

  public Long getTotalAmountMinor() {
    return totalAmountMinor;
  }

  public String getCurrency() {
    return currency;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getHoldExpiresAt() {
    return holdExpiresAt;
  }

  public Long getVersion() {
    return version;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
