package by.bsuir.travel.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class EventJournal {
  private EventJournal() {}

  public static void append(
      JdbcTemplate j, ObjectMapper m, String type, String topic, String id, Object payload) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Business state and outbox must share a transaction");
    try {
      UUID.fromString(id);
      var json = m.valueToTree(payload);
      if (!json.isObject()) throw new IllegalArgumentException("Event payload must be an object");
      Long version =
          j.queryForObject(
              "insert into integration_aggregate_versions(aggregate_id,version) values (?::uuid,1)"
                  + " on conflict(aggregate_id) do update set"
                  + " version=integration_aggregate_versions.version+1 returning version",
              Long.class,
              id);
      String cid =
          Optional.ofNullable(MDC.get("correlationId"))
              .filter(v -> v.matches("[a-zA-Z0-9-]{1,64}"))
              .orElseGet(() -> UUID.randomUUID().toString());
      j.update(
          "insert into"
              + " integration_outbox(id,aggregate_id,event_type,topic,schema_version,aggregate_version,payload,correlation_id,occurred_at)"
              + " values (?::uuid,?,?,?,1,?,?,?,clock_timestamp())",
          UUID.randomUUID().toString(),
          id,
          type,
          topic,
          version,
          m.writeValueAsString(json),
          cid);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot record event " + type, e);
    }
  }
}
