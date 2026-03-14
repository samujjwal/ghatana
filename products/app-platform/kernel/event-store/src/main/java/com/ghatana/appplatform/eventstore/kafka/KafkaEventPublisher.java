package com.ghatana.appplatform.eventstore.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Kafka implementation of the K-05 event publisher.
 *
 * <p>Serializes {@link AggregateEventRecord} to JSON and sends to a Kafka topic
 * using the aggregateId as the partition key (ensures ordering within one aggregate).
 *
 * <p>Topic naming (STORY-K05-009): {@code siddhanta.{aggregate_type_lower}.events}
 *
 * <p>Exactly-once semantics are approximated by the {@code OutboxRelay} pattern:
 * the outbox row is marked published only AFTER {@code producer.send().get()} returns
 * successfully. If the publisher throws, the relay increments the attempt counter and
 * retries on the next poll cycle — thus guaranteeing at-least-once delivery.
 *
 * <p>This publisher is NOT called directly from the write path. It is invoked by
 * an {@link KafkaEventOutboxRelay} which polls the event store outbox table.
 *
 * @doc.type class
 * @doc.purpose Kafka producer adapter for the K-05 event bus outbox relay (STORY-K05-009)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class KafkaEventPublisher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    /**
     * Topic naming convention: {@code siddhanta.{aggregate_type_lower}.events}
     * e.g. Order → {@code siddhanta.order.events}
     */
    public static String topicFor(String aggregateType) {
        return "siddhanta." + aggregateType.toLowerCase() + ".events";
    }

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;

    /**
     * Creates a publisher from a configuration map.
     *
     * <p>Required config keys: {@code bootstrap.servers}.
     * Idempotence ({@code enable.idempotence=true}) and acks ({@code acks=all})
     * are set by default for exactly-once semantic support.
     *
     * @param config Kafka producer configuration (at minimum: bootstrap.servers)
     */
    public KafkaEventPublisher(Map<String, Object> config) {
        Properties props = new Properties();
        props.putAll(Objects.requireNonNull(config, "config"));
        // Defaults for strong delivery guarantees
        props.putIfAbsent("acks", "all");
        props.putIfAbsent("enable.idempotence", "true");
        props.putIfAbsent("max.in.flight.requests.per.connection", "5");
        props.putIfAbsent("retries", "Integer.MAX_VALUE");
        props.putIfAbsent("delivery.timeout.ms", "120000");
        props.put("key.serializer",   StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());

        this.producer = new KafkaProducer<>(props);
        this.mapper   = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Package-private constructor for testing with an injected producer.
     */
    KafkaEventPublisher(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.mapper   = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Publishes one {@link AggregateEventRecord} to Kafka synchronously.
     *
     * <p>The aggregate_id is used as the partition key to guarantee ordering
     * within a single aggregate. Blocks until the broker acknowledges the write.
     *
     * @param record the event to publish
     * @throws KafkaPublishException if the broker rejects or times out
     */
    public void publish(AggregateEventRecord record) {
        String topic      = topicFor(record.aggregateType());
        String partitionKey = record.aggregateId().toString();
        String payload;
        try {
            payload = mapper.writeValueAsString(toPublishPayload(record));
        } catch (Exception e) {
            throw new KafkaPublishException("Failed to serialize event " + record.eventId(), e);
        }

        ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(topic, partitionKey, payload);
        // Inject standard Kafka headers for tracing
        record.metadata().forEach((k, v) -> {
            if (v != null) {
                kafkaRecord.headers().add(k, v.toString().getBytes());
            }
        });
        kafkaRecord.headers().add("event_id",       record.eventId().toString().getBytes());
        kafkaRecord.headers().add("event_type",     record.eventType().getBytes());
        kafkaRecord.headers().add("sequence_number", Long.toString(record.sequenceNumber()).getBytes());

        try {
            Future<RecordMetadata> future = producer.send(kafkaRecord);
            RecordMetadata meta = future.get(); // synchronous — relay is not on the eventloop
            log.debug("Published event {} to {}[{}]@{}", record.eventId(), topic,
                    meta.partition(), meta.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("Interrupted while publishing event " + record.eventId(), e);
        } catch (ExecutionException e) {
            throw new KafkaPublishException("Kafka publish failed for event " + record.eventId(), e.getCause());
        }
    }

    @Override
    public void close() {
        producer.close();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private static Map<String, Object> toPublishPayload(AggregateEventRecord record) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId",        record.eventId().toString());
        payload.put("eventType",      record.eventType());
        payload.put("aggregateId",    record.aggregateId().toString());
        payload.put("aggregateType",  record.aggregateType());
        payload.put("sequenceNumber", record.sequenceNumber());
        payload.put("data",           record.data());
        payload.put("metadata",       record.metadata());
        payload.put("createdAtUtc",   record.createdAtUtc().toString());
        payload.put("createdAtBs",    record.createdAtBs());
        return payload;
    }
}
