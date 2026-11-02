package by.bsuir.travel.tour.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
public class SeatHoldsEntity {
  protected SeatHoldsEntity() {}

  @Id
  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "seats")
  private Short seats;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "price_version")
  private Long priceVersion;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public UUID getBookingId() {
    return bookingId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public Short getSeats() {
    return seats;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public Long getPriceVersion() {
    return priceVersion;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
