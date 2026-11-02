package by.bsuir.travel.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopicCatalogTest {
  final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  String event(String type, int version, Object payload) throws Exception {
    return mapper.writeValueAsString(
        java.util.Map.of(
            "eventId",
            UUID.randomUUID().toString(),
            "eventType",
            type,
            "schemaVersion",
            version,
            "occurredAt",
            "2026-11-02T23:59:59.123456789Z",
            "aggregateId",
            UUID.randomUUID().toString(),
            "aggregateVersion",
            7,
            "correlationId",
            UUID.randomUUID().toString(),
            "payload",
            payload));
  }

  @Test
  void separateDomainRoutesAndOwners() {
    assertEquals(4, TopicCatalog.topics().size());
    assertEquals(TopicCatalog.PAYMENT, TopicCatalog.topic("PaymentSucceeded"));
    assertEquals("booking-service", TopicCatalog.owner(TopicCatalog.BOOKING));
    assertFalse(TopicCatalog.topics().contains("travel.events.v1"));
    assertEquals(3, TopicCatalog.PARTITIONS);
  }

  @Test
  void independentDeadLettersForEachSubscription() {
    assertNotEquals(
        TopicCatalog.dlt(TopicCatalog.PAYMENT, "booking"),
        TopicCatalog.dlt(TopicCatalog.PAYMENT, "analytics"));
    assertThrows(
        IllegalArgumentException.class, () -> TopicCatalog.dlt(TopicCatalog.USER, "booking"));
  }

  @Test
  void wrongTopicIsRejectedBeforeProjection() throws Exception {
    String raw = event("PaymentSucceeded", 1, java.util.Map.of("id", "x"));
    assertThrows(
        IllegalArgumentException.class, () -> Outbox.read(mapper, raw, TopicCatalog.BOOKING));
  }

  @Test
  void unknownSchemaAndNestedJsonStringAreRejected() throws Exception {
    String raw = event("PaymentSucceeded", 2, java.util.Map.of("id", "x"));
    assertThrows(
        IllegalArgumentException.class, () -> Outbox.read(mapper, raw, TopicCatalog.PAYMENT));
    String nested = event("PaymentSucceeded", 1, "{\"id\":\"x\"}");
    assertThrows(
        IllegalArgumentException.class, () -> Outbox.read(mapper, nested, TopicCatalog.PAYMENT));
  }

  @Test
  void occurrenceTimeAndLargeMoneyArePreserved() throws Exception {
    String raw = event("PaymentSucceeded", 1, java.util.Map.of("amountMinor", Long.MAX_VALUE));
    var e = Outbox.read(mapper, raw, TopicCatalog.PAYMENT);
    assertEquals(OffsetDateTime.parse("2026-11-02T23:59:59.123456789Z"), e.occurredAt());
    assertEquals(Long.MAX_VALUE, e.payload().get("amountMinor").longValue());
  }
}
