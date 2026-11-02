package by.bsuir.travel.analytics.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "schedule_load_facts")
public class ScheduleLoadFactsEntity {
  protected ScheduleLoadFactsEntity() {}

  @Id
  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "direction_id")
  private UUID directionId;

  @Column(name = "starts_at")
  private OffsetDateTime startsAt;

  @Column(name = "capacity")
  private Integer capacity;

  @Column(name = "confirmed_seats")
  private Integer confirmedSeats;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getDirectionId() {
    return directionId;
  }

  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public Integer getConfirmedSeats() {
    return confirmedSeats;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
