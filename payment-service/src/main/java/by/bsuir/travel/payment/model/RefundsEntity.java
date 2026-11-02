package by.bsuir.travel.payment.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class RefundsEntity {
  protected RefundsEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(name = "amount_minor")
  private Long amountMinor;

  @Column(name = "status", length = 20)
  private String status;

  @Column(name = "provider_refund_id", length = 120)
  private String providerRefundId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public UUID getId() {
    return id;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public Long getAmountMinor() {
    return amountMinor;
  }

  public String getStatus() {
    return status;
  }

  public String getProviderRefundId() {
    return providerRefundId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
