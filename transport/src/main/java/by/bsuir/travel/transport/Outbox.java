package by.bsuir.travel.transport;

import by.bsuir.travel.persistence.EventJournal;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class Outbox {
  public record Event(
      String eventId,
      String eventType,
      int schemaVersion,
      OffsetDateTime occurredAt,
      String aggregateId,
      long aggregateVersion,
      String correlationId,
      JsonNode payload) {
    public Event {
      UUID.fromString(eventId);
      UUID.fromString(aggregateId);
      if (schemaVersion != 1
          || aggregateVersion < 1
          || occurredAt == null
          || correlationId == null
          || !correlationId.matches("[a-zA-Z0-9-]{1,64}")
          || payload == null
          || !payload.isObject()) throw new IllegalArgumentException("Invalid event envelope");
      TopicCatalog.topic(eventType);
    }
  }

  public static Event read(
      ObjectMapper mapper,
      org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
    var event = read(mapper, record.value(), record.topic());
    if (!event.aggregateId().equals(record.key()))
      throw new IllegalArgumentException("Kafka key must equal aggregateId");
    return event;
  }

  public static Event read(ObjectMapper mapper, String raw, String sourceTopic) {
    try {
      var event = mapper.readValue(raw, Event.class);
      if (!TopicCatalog.topic(event.eventType()).equals(sourceTopic))
        throw new IllegalArgumentException("Event type does not belong to this topic");
      return event;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid versioned event in " + sourceTopic, e);
    }
  }

  public static void add(JdbcTemplate j, ObjectMapper m, String type, String id, Object payload) {
    String canonical =
        switch (type) {
          case "BookingChanged" -> "BookingStatusChanged";
          case "PaymentSettled" -> "PaymentSucceeded";
          default -> type;
        };
    EventJournal.append(j, m, canonical, TopicCatalog.topic(canonical), id, payload);
  }
}
