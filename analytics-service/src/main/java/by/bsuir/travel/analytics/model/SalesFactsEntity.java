package by.bsuir.travel.analytics.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "sales_facts")
public class SalesFactsEntity {
  protected SalesFactsEntity() {}

  @Id
  @Column(name = "fact_id")
  private UUID factId;

  @Column(name = "source_event_id")
  private UUID sourceEventId;

  @Column(name = "source_operation_id")
  private UUID sourceOperationId;

  @Column(name = "payment_id")
  private UUID paymentId;

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

  @Column(name = "fact_type", length = 20)
  private String factType;

  @Column(name = "occurred_at")
  private OffsetDateTime occurredAt;

  public UUID getFactId() {
    return factId;
  }

  public UUID getSourceEventId() {
    return sourceEventId;
  }

  public UUID getSourceOperationId() {
    return sourceOperationId;
  }

  public UUID getPaymentId() {
    return paymentId;
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

  public String getFactType() {
    return factType;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }
}
