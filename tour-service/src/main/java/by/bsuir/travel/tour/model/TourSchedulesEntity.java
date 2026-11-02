package by.bsuir.travel.tour.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "tour_schedules")
public class TourSchedulesEntity {
  protected TourSchedulesEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "tour_id")
  private UUID tourId;

  @Column(name = "starts_at")
  private OffsetDateTime startsAt;

  @Column(name = "ends_at")
  private OffsetDateTime endsAt;

  @Column(name = "capacity")
  private Integer capacity;

  @Column(name = "reserved_seats")
  private Integer reservedSeats;

  @Column(name = "base_price_minor")
  private Long basePriceMinor;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "price_version")
  private Long priceVersion;

  @Column(name = "version")
  private Long version;

  @Column(name = "status", length = 20)
  private String status;

  public UUID getId() {
    return id;
  }

  public UUID getTourId() {
    return tourId;
  }

  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public Integer getReservedSeats() {
    return reservedSeats;
  }

  public Long getBasePriceMinor() {
    return basePriceMinor;
  }

  public String getCurrency() {
    return currency;
  }

  public Long getPriceVersion() {
    return priceVersion;
  }

  public Long getVersion() {
    return version;
  }

  public String getStatus() {
    return status;
  }
}
