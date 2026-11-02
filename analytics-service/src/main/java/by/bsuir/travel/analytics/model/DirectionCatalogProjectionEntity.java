package by.bsuir.travel.analytics.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "direction_catalog_projection")
public class DirectionCatalogProjectionEntity {
  protected DirectionCatalogProjectionEntity() {}

  @Id
  @Column(name = "direction_id")
  private UUID directionId;

  @Column(name = "country", length = 100)
  private String country;

  @Column(name = "city", length = 100)
  private String city;

  @Column(name = "is_active")
  private Boolean isActive;

  @Column(name = "version")
  private Long version;

  public UUID getDirectionId() {
    return directionId;
  }

  public String getCountry() {
    return country;
  }

  public String getCity() {
    return city;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public Long getVersion() {
    return version;
  }
}
