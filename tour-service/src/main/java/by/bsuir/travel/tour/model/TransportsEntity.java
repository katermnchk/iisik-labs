package by.bsuir.travel.tour.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "transports")
public class TransportsEntity {
  protected TransportsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "kind", length = 20)
  private String kind;

  @Column(name = "carrier", length = 120)
  private String carrier;

  @Column(name = "route_number", length = 40)
  private String routeNumber;

  @Column(name = "departure_at")
  private OffsetDateTime departureAt;

  @Column(name = "arrival_at")
  private OffsetDateTime arrivalAt;

  public UUID getId() {
    return id;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public String getKind() {
    return kind;
  }

  public String getCarrier() {
    return carrier;
  }

  public String getRouteNumber() {
    return routeNumber;
  }

  public OffsetDateTime getDepartureAt() {
    return departureAt;
  }

  public OffsetDateTime getArrivalAt() {
    return arrivalAt;
  }
}
