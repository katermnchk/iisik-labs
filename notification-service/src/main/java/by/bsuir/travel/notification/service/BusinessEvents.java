package by.bsuir.travel.notification.service;

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
  private final Logger log = LoggerFactory.getLogger(BusinessEvents.class);

  public BusinessEvents(JdbcTemplate j, ObjectMapper m) {
    this.j = j;
    mapper = m;
  }

  @KafkaListener(
      topics = {TopicCatalog.USER, TopicCatalog.BOOKING, TopicCatalog.PAYMENT},
      groupId = "travel-notification-v1")
  @Transactional(rollbackFor = Exception.class)
  public void consume(ConsumerRecord<String, String> record) throws Exception {
    var event = Outbox.read(mapper, record);
    MDC.put("correlationId", event.correlationId());
    try {
      if (event.eventType().equals("UserUpdated")) {
        log.info("kafka filtered type=UserUpdated eventId={}", event.eventId());
        return;
      }
      if (!EventInbox.first(j, "notification", event)) {
        log.info("kafka duplicate eventId={}", event.eventId());
        return;
      }
      String uid, bid = null, kind, title, body;
      switch (event.eventType()) {
        case "UserRegistered" -> {
          var u = mapper.treeToValue(event.payload(), User.class);
          if (!event.aggregateId().equals(u.id()))
            throw new IllegalArgumentException("User identity");
          uid = u.id();
          kind = "WELCOME";
          title = "Добро пожаловать";
          body = u.firstName() + ", регистрация завершена";
        }
        case "BookingStatusChanged" -> {
          var b = mapper.treeToValue(event.payload(), Booking.class);
          if (!event.aggregateId().equals(b.id()))
            throw new IllegalArgumentException("Booking identity");
          uid = b.clientId();
          bid = b.id();
          kind = "BOOKING_STATUS";
          title = "Бронирование " + b.status().name();
          body = b.tourTitle();
        }
        case "PaymentSucceeded" -> {
          var value = mapper.treeToValue(event.payload(), PaymentSettled.class);
          if (!event.aggregateId().equals(value.payment().id()))
            throw new IllegalArgumentException("Payment identity");
          uid = value.booking().clientId();
          bid = value.booking().id();
          kind = "PAYMENT_STATUS";
          title = "Платёж SUCCEEDED";
          body = value.booking().tourTitle();
        }
        default -> throw new IllegalArgumentException("Unsupported notification event");
      }
      j.update(
          "insert into notifications(id,user_id,booking_id,kind,title,body,status,created_at)"
              + " values (?::uuid,?::uuid,?::uuid,?,?,?,'SENT',?)",
          event.eventId(),
          uid,
          bid,
          kind,
          title,
          body,
          event.occurredAt());
      j.update(
          "insert into"
              + " notification_deliveries(id,notification_id,channel,status,attempt_no,sent_at)"
              + " values (?::uuid,?::uuid,'IN_APP','SENT',1,now())",
          java.util.UUID.randomUUID().toString(),
          event.eventId());
      log.info(
          "kafka consumed service=notification topic={} partition={} offset={} eventId={}"
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
