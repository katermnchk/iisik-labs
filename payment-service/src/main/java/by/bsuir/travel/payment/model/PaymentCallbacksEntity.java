package by.bsuir.travel.payment.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_callbacks")
public class PaymentCallbacksEntity {
  protected PaymentCallbacksEntity() {}

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(name = "provider_event_id", length = 120)
  private String providerEventId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @Column(name = "received_at")
  private OffsetDateTime receivedAt;

  public UUID getId() {
    return id;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public String getPayload() {
    return payload;
  }

  public OffsetDateTime getReceivedAt() {
    return receivedAt;
  }
}
