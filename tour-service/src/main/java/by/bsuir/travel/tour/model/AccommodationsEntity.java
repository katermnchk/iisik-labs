package by.bsuir.travel.tour.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "accommodations")
public class AccommodationsEntity {
  protected AccommodationsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "name", length = 180)
  private String name;

  @Column(name = "address", length = 255)
  private String address;

  @Column(name = "check_in_at")
  private OffsetDateTime checkInAt;

  @Column(name = "check_out_at")
  private OffsetDateTime checkOutAt;

  @Column(name = "room_type", length = 80)
  private String roomType;

  public UUID getId() {
    return id;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public String getName() {
    return name;
  }

  public String getAddress() {
    return address;
  }

  public OffsetDateTime getCheckInAt() {
    return checkInAt;
  }

  public OffsetDateTime getCheckOutAt() {
    return checkOutAt;
  }

  public String getRoomType() {
    return roomType;
  }
}
