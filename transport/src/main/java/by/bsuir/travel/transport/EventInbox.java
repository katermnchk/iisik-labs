package by.bsuir.travel.transport;

import org.springframework.jdbc.core.JdbcTemplate;

public final class EventInbox {
  private EventInbox() {}

  public static boolean first(JdbcTemplate j, String consumer, Outbox.Event event) {
    return j.update(
            "insert into integration_inbox(consumer,event_id) values (?,?::uuid) on conflict do"
                + " nothing",
            consumer,
            event.eventId())
        == 1;
  }

  public static boolean latest(JdbcTemplate j, String consumer, Outbox.Event event) {
    return j.update(
            "insert into integration_projection_versions(consumer,aggregate_id,version) values"
                + " (?,?::uuid,?) on conflict(consumer,aggregate_id) do update set"
                + " version=excluded.version where"
                + " integration_projection_versions.version<excluded.version",
            consumer,
            event.aggregateId(),
            event.aggregateVersion())
        == 1;
  }
}
