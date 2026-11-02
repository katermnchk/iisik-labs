package by.bsuir.travel.tour.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "tours")
public class ToursEntity {
  protected ToursEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "direction_id")
  private UUID directionId;

  @Column(name = "title", length = 180)
  private String title;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "duration_days")
  private Short durationDays;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "created_by_employee_id")
  private UUID createdByEmployeeId;

  @Column(name = "version")
  private Long version;

  public UUID getId() {
    return id;
  }

  public UUID getDirectionId() {
    return directionId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Short getDurationDays() {
    return durationDays;
  }

  public String getStatus() {
    return status;
  }

  public UUID getCreatedByEmployeeId() {
    return createdByEmployeeId;
  }

  public Long getVersion() {
    return version;
  }
}
