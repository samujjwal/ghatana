package com.ghatana.appplatform.eventstore.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Routes failed events to a DLQ topic enriched with failure metadata.
 *
 * <p>DLQ topic naming convention (STORY-K05-027):
 * {@code siddhanta.{source_aggregate_type}.dlq}
 * e.g. {@code siddhanta.order.events} → {@code siddhanta.order.dlq}
 *
 * <h2>DLQ message headers</h2>
 * Each routed message carries Kafka headers:
 * <ul>
 *   <li>{@code dlq.original-topic} — source topic the message came from</li>
 *   <li>{@code dlq.error-message}  — last error description</li>
 *   <li>{@code dlq.retry-count}    — number of delivery attempts made</li>
 *   <li>{@code dlq.consumer-group} — consumer group that failed</li>
 *   <li>{@code dlq.failed-at}      — ISO-8601 UTC timestamp of the final failure</li>
 * </ul>
 *
 * <p>The DLQ message key and value are preserved from the original record so
 * dead-letter consumers can replay or inspect original content without data loss.
 *
 * @doc.type class
 * @doc.purpose Routes failed events to DLQ topics with enriched failure metadata (STORY-K05-027)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DlqTopicRouter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DlqTopicRouter.class);

    /** Header name constants to avoid typo drift across consumers and producers. */
    public static final String HDR_ORIGINAL_TOPIC = "dlq.original-topic";
    public static final String HDR_ERROR_MESSAGE  = "dlq.error-message";
    public static final String HDR_RETRY_COUNT    = "dlq.retry-count";
    public static final String HDR_CONSUMER_GROUP = "dlq.consumer-group";
    public static final String HDR_FAILED_AT      = "dlq.failed-at";

    private final KafkaProducer<String, String> producer;

    /**
     * Creates a router backed by a dedicated Kafka producer for DLQ topics.
     *
     * @param kafkaConfig base producer config (bootstrap.servers required)
     */
    public DlqTopicRouter(Map<String, Object> kafkaConfig) {
        Objects.requireNonNull(kafkaConfig, "kafkaConfig");
        Properties props = new Properties();
        props.putAll(kafkaConfig);
        props.setProperty("acks", "all");
        props.setProperty("retries", "3");
        this.producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
    }

    /**
     * Derives the DLQ topic name from a source topic.
     * {@code siddhanta.foo.events} → {@code siddhanta.foo.dlq}
     * {@code any.other.topic}      → {@code any.other.topic.dlq}
     */
    public static String dlqTopicFor(String sourceTopic) {
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        if (sourceTopic.endsWith(".events")) {
            return sourceTopic.substring(0, sourceTopic.length() - ".events".length()) + ".dlq";
        }
        return sourceTopic + ".dlq";
    }

    /**
     * Routes a failed consumer record to its DLQ topic with enriched failure metadata.
     *
     * @param original      the failed Kafka record
     * @param errorMessage  description of the last failure
     * @param retryCount    number of delivery attempts already made
     * @param consumerGroup the consumer group that failed to process the record
     */
    public void route(ConsumerRecord<String, String> original,
                      String errorMessage,
                      int retryCount,
                      String consumerGroup) {
        Objects.requireNonNull(original, "original");
        String dlqTopic = dlqTopicFor(original.topic());

        ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, original.key(), original.value());
        addHeader(dlqRecord, HDR_ORIGINAL_TOPIC, original.topic());
        addHeader(dlqRecord, HDR_ERROR_MESSAGE,  truncate(errorMessage, 1024));
        addHeader(dlqRecord, HDR_RETRY_COUNT,    String.valueOf(retryCount));
        addHeader(dlqRecord, HDR_CONSUMER_GROUP, consumerGroup);
        addHeader(dlqRecord, HDR_FAILED_AT,      Instant.now().toString());

        try {
            producer.send(dlqRecord).get();
            log.warn("[DlqTopicRouter] Routed to DLQ: topic={} key={} retries={} error={}",
                dlqTopic, original.key(), retryCount, truncate(errorMessage, 200));
        } catch (Exception e) {
            log.error("[DlqTopicRouter] Failed to publish to DLQ topic={} key={}",
                dlqTopic, original.key(), e);
            throw new KafkaPublishException("DLQ routing failed for topic=" + dlqTopic, e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (value != null) {
            record.headers().add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
