package by.bsuir.travel.analytics.service;

import by.bsuir.travel.contracts.Identity.*;
import by.bsuir.travel.contracts.Model.*;
import by.bsuir.travel.transport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
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
  private final ScheduleDirectory schedules;
  private final Logger log = LoggerFactory.getLogger(BusinessEvents.class);

  public BusinessEvents(
      JdbcTemplate j, ObjectMapper m, RpcClient rpc, org.springframework.core.env.Environment e) {
    this.j = j;
    mapper = m;
    schedules = rpc.proxy(e.getProperty("TOUR_RPC", "localhost:9092"), ScheduleDirectory.class);
  }

  @KafkaListener(
      topics = {TopicCatalog.PAYMENT, TopicCatalog.TOUR},
      groupId = "travel-analytics-v1")
  @Transactional(rollbackFor = Exception.class)
  public void consume(ConsumerRecord<String, String> record) throws Exception {
    var event = Outbox.read(mapper, record);
    MDC.put("correlationId", event.correlationId());
    try {
      if (!EventInbox.first(j, "analytics", event)) {
        log.info("kafka duplicate eventId={}", event.eventId());
        return;
      }
      if (event.eventType().equals("TourChanged")) {
        var tour = mapper.treeToValue(event.payload(), Tour.class);
        if (!event.aggregateId().equals(tour.id()))
          throw new IllegalArgumentException("Tour event identity");
        if (!EventInbox.latest(j, "tour-catalog", event)) {
          log.info("kafka stale projection eventId={}", event.eventId());
          return;
        }
        var d = tour.direction();
        j.update(
            "insert into direction_catalog_projection(direction_id,country,city,is_active,version)"
                + " values (?::uuid,?,?,?,1) on conflict(direction_id) do update set"
                + " country=excluded.country,city=excluded.city,is_active=excluded.is_active",
            d.id(),
            d.country(),
            d.city(),
            d.isActive());
        for (var schedule : tour.schedules()) {
          boolean included =
              tour.status() == TourStatus.PUBLISHED
                  && d.isActive()
                  && schedule.status() != ScheduleStatus.CANCELLED;
          j.update(
              "insert into"
                  + " schedule_load_facts(schedule_id,direction_id,starts_at,capacity,confirmed_seats,updated_at,included)"
                  + " values (?::uuid,?::uuid,?,?,0,now(),?) on conflict(schedule_id) do update set"
                  + " starts_at=excluded.starts_at,capacity=excluded.capacity,updated_at=now(),included=excluded.included",
              schedule.id(),
              d.id(),
              schedule.startsAt(),
              schedule.capacity(),
              included);
        }
      } else if (event.eventType().equals("PaymentSucceeded")) {
        var settled = mapper.treeToValue(event.payload(), PaymentSettled.class);
        var p = settled.payment();
        var b = settled.booking();
        if (!event.aggregateId().equals(p.id())
            || !b.id().equals(p.bookingId())
            || p.status() != PaymentStatus.SUCCEEDED)
          throw new IllegalArgumentException("Payment event identity");
        int inserted =
            j.update(
                "insert into"
                    + " sales_facts(fact_id,source_event_id,source_operation_id,payment_id,booking_id,schedule_id,direction_id,amount_minor,currency,fact_type,occurred_at)"
                    + " values"
                    + " (?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?,'BYN','SALE',?)"
                    + " on conflict do nothing",
                event.eventId(),
                event.eventId(),
                p.id(),
                p.id(),
                b.id(),
                b.scheduleId(),
                b.directionId(),
                p.amount().amountMinor(),
                event.occurredAt());
        if (inserted == 0) return;
        // Different source topics may arrive in either order. Bootstrap the schedule if
        // its catalog snapshot is still in flight; later TourChanged preserves seats.
        var schedule = schedules.schedule(b.scheduleId());
        j.update(
            "insert into"
                + " schedule_load_facts(schedule_id,direction_id,starts_at,capacity,confirmed_seats,updated_at,included)"
                + " values (?::uuid,?::uuid,?,?,?,now(),true) on conflict(schedule_id) do update"
                + " set confirmed_seats=schedule_load_facts.confirmed_seats+excluded.confirmed_seats,updated_at=now()",
            b.scheduleId(),
            b.directionId(),
            b.startsAt(),
            schedule.capacity(),
            b.travelerCount());
        j.update(
            "insert into direction_popularity_daily(day,direction_id,confirmed_tourists) values"
                + " (?,?::uuid,?) on conflict(day,direction_id) do update set"
                + " confirmed_tourists=direction_popularity_daily.confirmed_tourists+excluded.confirmed_tourists",
            event.occurredAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(),
            b.directionId(),
            b.travelerCount());
      } else throw new IllegalArgumentException("Unsupported analytics event");
      j.update("update analytics_projection_state set snapshot_at=clock_timestamp() where id=true");
      log.info(
          "kafka consumed service=analytics topic={} partition={} offset={} eventId={}"
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
