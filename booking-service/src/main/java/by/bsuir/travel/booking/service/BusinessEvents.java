package by.bsuir.travel.booking.service;

import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.transport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessEvents {
  private final JdbcTemplate j;
  private final ObjectMapper mapper;
  private final BookingReadService read;
  private final Logger log = LoggerFactory.getLogger(BusinessEvents.class);

  public BusinessEvents(JdbcTemplate j, ObjectMapper m, BookingReadService read) {
    this.j = j;
    mapper = m;
    this.read = read;
  }

  @KafkaListener(topics = TopicCatalog.PAYMENT, groupId = "travel-booking-v1")
  @Transactional(rollbackFor = Exception.class)
  public void consume(ConsumerRecord<String, String> record) throws Exception {
    var event = Outbox.read(mapper, record);
    MDC.put("correlationId", event.correlationId());
    try {
      if (!EventInbox.first(j, "booking", event)) {
        log.info("kafka duplicate eventId={}", event.eventId());
        return;
      }
      if (!event.eventType().equals("PaymentSucceeded"))
        throw new IllegalArgumentException("Unsupported payment event");
      var settled = mapper.treeToValue(event.payload(), PaymentSettled.class);
      var b = settled.booking();
      if (!event.aggregateId().equals(settled.payment().id())
          || !b.id().equals(settled.payment().bookingId())
          || settled.payment().status() != PaymentStatus.SUCCEEDED)
        throw new IllegalArgumentException("Payment event identity mismatch");
      j.execute("select pg_advisory_xact_lock(417204)");
      var current = read.booking(new GetBookingRequest(b.id()));
      if (current.status() != BookingStatus.CONFIRMED
          && current.status() != BookingStatus.COMPLETED) {
        int changed =
            j.update(
                "update bookings set"
                    + " status='CONFIRMED',hold_expires_at=null,version=version+1,updated_at=now()"
                    + " where id=?::uuid and status='PENDING_PAYMENT'",
                b.id());
        if (changed != 1)
          throw new IllegalStateException("Payment cannot be applied to booking " + b.id());
        j.update(
            "insert into"
                + " booking_status_history(id,booking_id,from_status,to_status,reason,changed_at)"
                + " values (?::uuid,?::uuid,'PENDING_PAYMENT','CONFIRMED','PaymentSucceeded via"
                + " Kafka',?)",
            java.util.UUID.randomUUID().toString(),
            b.id(),
            event.occurredAt());
        // JDBC read avoids returning an older JPA first-level cache after the update.
        var confirmed =
            new Booking(
                current.id(),
                current.clientId(),
                current.scheduleId(),
                current.directionId(),
                current.tourTitle(),
                current.directionName(),
                current.startsAt(),
                current.travelerCount(),
                current.unitPrice(),
                current.total(),
                BookingStatus.CONFIRMED,
                null,
                current.createdAt(),
                current.version() + 1,
                java.util.List.of());
        Outbox.add(j, mapper, "BookingStatusChanged", b.id(), confirmed);
      }
      log.info(
          "kafka consumed service=booking topic={} partition={} offset={} eventId={}"
              + " correlationId={}",
          record.topic(),
          record.partition(),
          record.offset(),
          event.eventId(),
          event.correlationId());
    } finally {
      MDC.remove("correlationId");
    }
  }
}
