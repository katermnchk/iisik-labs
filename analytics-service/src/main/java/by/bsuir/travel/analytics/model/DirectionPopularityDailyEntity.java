package by.bsuir.travel.analytics.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "direction_popularity_daily")
@IdClass(DirectionPopularityDailyEntityKey.class)
public class DirectionPopularityDailyEntity {
  protected DirectionPopularityDailyEntity() {}

  @Id
  @Column(name = "day")
  private LocalDate day;

  @Id
  @Column(name = "direction_id")
  private UUID directionId;

  @Column(name = "confirmed_tourists")
  private Integer confirmedTourists;

  public LocalDate getDay() {
    return day;
  }

  public UUID getDirectionId() {
    return directionId;
  }

  public Integer getConfirmedTourists() {
    return confirmedTourists;
  }
}
