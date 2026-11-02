package by.bsuir.travel.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.slf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.serializer.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@EnableScheduling
public class EventConfig {

  @Bean
  ProducerFactory<String, String> eventProducer(Environment e) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            e.getProperty("KAFKA_BOOTSTRAP", "localhost:9192"),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.ACKS_CONFIG,
            "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
            true,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
            10000,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
            3000,
            ProducerConfig.MAX_BLOCK_MS_CONFIG,
            3000));
  }

  @Bean
  KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> p) {
    return new KafkaTemplate<>(p);
  }

  @Bean
  KafkaAdmin kafkaAdmin(Environment e) {
    return new KafkaAdmin(
        Map.of("bootstrap.servers", e.getProperty("KAFKA_BOOTSTRAP", "localhost:9192")));
  }

  @Bean
  KafkaAdmin.NewTopics domainTopics() {
    var topics = new ArrayList<NewTopic>();
    for (String name : TopicCatalog.topics())
      topics.add(
          org.springframework.kafka.config.TopicBuilder.name(name)
              .partitions(TopicCatalog.PARTITIONS)
              .replicas(1)
              .config("retention.ms", "604800000")
              .build());
    TopicCatalog.SUBSCRIPTIONS.forEach(
        (consumer, sources) ->
            sources.forEach(
                source ->
                    topics.add(
                        org.springframework.kafka.config.TopicBuilder.name(
                                TopicCatalog.dlt(source, consumer))
                            .partitions(TopicCatalog.PARTITIONS)
                            .replicas(1)
                            .config("retention.ms", "1209600000")
                            .build())));
    return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      Environment e, KafkaTemplate<String, String> k) {
    var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
    f.setConsumerFactory(
        new DefaultKafkaConsumerFactory<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                e.getProperty("KAFKA_BOOTSTRAP", "localhost:9192"),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest")));
    var recoverer =
        new DeadLetterPublishingRecoverer(
            k,
            (record, ex) ->
                new TopicPartition(
                    TopicCatalog.dlt(
                        record.topic(),
                        e.getRequiredProperty("spring.application.name").replace("-service", "")),
                    record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(12));
    var errors = new DefaultErrorHandler(recoverer, new FixedBackOff(500, 2));
    errors.setBackOffFunction(
        (record, error) -> {
          // A valid event must not be abandoned because its RPC dependency is temporarily down.
          // Keep the offset pending; malformed data still has a finite retry budget and a DLT.
          for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof by.bsuir.travel.contracts.DomainException domain
                && "SERVICE_UNAVAILABLE".equals(domain.getCode()))
              return new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS);
          }
          return new FixedBackOff(500, 2);
        });
    f.setCommonErrorHandler(errors);
    f.setConcurrency(3);
    f.getContainerProperties().setDeliveryAttemptHeader(true);
    f.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return f;
  }

  @Bean
  Relay relay(
      JdbcTemplate j,
      PlatformTransactionManager tm,
      KafkaTemplate<String, String> k,
      ObjectMapper m,
      Environment env) {
    return new Relay(
        j, new TransactionTemplate(tm), k, m, env.getRequiredProperty("spring.application.name"));
  }

  public static final class Relay {
    private final JdbcTemplate j;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> k;
    private final ObjectMapper mapper;
    private final Logger log = LoggerFactory.getLogger(Relay.class);
    private final String owner;

    Relay(
        JdbcTemplate j,
        TransactionTemplate t,
        KafkaTemplate<String, String> k,
        ObjectMapper m,
        String owner) {
      this.owner = owner;
      this.j = j;
      tx = t;
      this.k = k;
      mapper = m;
    }

    @Scheduled(fixedDelay = 1500)
    public void publish() {
      try {
        tx.execute(
            s -> {
              // Only one relay per owning service publishes at once, even with multiple replicas.
              // A committed sequence and aggregate key preserve ordering across outbox batches.
              if (!Boolean.TRUE.equals(
                  j.queryForObject(
                      "select pg_try_advisory_xact_lock(hashtext(?))",
                      Boolean.class,
                      "outbox:" + owner))) return null;
              var rows =
                  j.queryForList(
                      "select * from integration_outbox where published_at is null order by"
                          + " sequence limit 20 for update skip locked");
              for (var row : rows) {
                String id = row.get("id").toString();
                try {
                  var event =
                      new Outbox.Event(
                          id,
                          row.get("event_type").toString(),
                          ((Number) row.get("schema_version")).intValue(),
                          ((java.sql.Timestamp) row.get("occurred_at"))
                              .toInstant()
                              .atOffset(java.time.ZoneOffset.UTC),
                          row.get("aggregate_id").toString(),
                          ((Number) row.get("aggregate_version")).longValue(),
                          row.get("correlation_id").toString(),
                          mapper.readTree(row.get("payload").toString()));
                  String topic = row.get("topic").toString();
                  if (!TopicCatalog.topic(event.eventType()).equals(topic)
                      || !TopicCatalog.owner(topic).equals(owner))
                    throw new IllegalStateException("Publisher does not own topic " + topic);
                  var metadata =
                      k.send(topic, event.aggregateId(), mapper.writeValueAsString(event))
                          .get(12, TimeUnit.SECONDS)
                          .getRecordMetadata();
                  j.update("update integration_outbox set published_at=now() where id=?::uuid", id);
                  log.info(
                      "kafka published topic={} partition={} offset={} key={} eventId={} type={}"
                          + " correlationId={}",
                      topic,
                      metadata.partition(),
                      metadata.offset(),
                      event.aggregateId(),
                      id,
                      event.eventType(),
                      event.correlationId());
                } catch (Exception e) {
                  throw new IllegalStateException("Kafka publish failed; event retained " + id, e);
                }
              }
              return null;
            });
      } catch (Exception e) {
        log.error("outbox relay will retry", e);
      }
    }
  }
}
