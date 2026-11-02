package by.bsuir.travel.tour.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "directions")
public class DirectionsEntity {
  protected DirectionsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "country", length = 100)
  private String country;

  @Column(name = "city", length = 100)
  private String city;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "is_active")
  private Boolean isActive;

  public UUID getId() {
    return id;
  }

  public String getCountry() {
    return country;
  }

  public String getCity() {
    return city;
  }

  public String getDescription() {
    return description;
  }

  public Boolean getIsActive() {
    return isActive;
  }
}
