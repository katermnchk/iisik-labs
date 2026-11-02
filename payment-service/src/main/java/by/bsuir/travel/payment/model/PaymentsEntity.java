package by.bsuir.travel.payment.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentsEntity {
  protected PaymentsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "direction_id")
  private UUID directionId;

  @Column(name = "amount_minor")
  private Long amountMinor;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "payment_url", length = 500)
  private String paymentUrl;

  @Column(name = "provider", length = 80)
  private String provider;

  @Column(name = "provider_payment_id", length = 120)
  private String providerPaymentId;

  @Column(name = "external_key", length = 120)
  private String externalKey;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public UUID getId() {
    return id;
  }

  public UUID getBookingId() {
    return bookingId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getDirectionId() {
    return directionId;
  }

  public Long getAmountMinor() {
    return amountMinor;
  }

  public String getCurrency() {
    return currency;
  }

  public String getStatus() {
    return status;
  }

  public String getPaymentUrl() {
    return paymentUrl;
  }

  public String getProvider() {
    return provider;
  }

  public String getProviderPaymentId() {
    return providerPaymentId;
  }

  public String getExternalKey() {
    return externalKey;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
