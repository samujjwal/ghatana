package com.ghatana.appplatform.eventstore.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base for Kafka event consumers with built-in retry, DLQ routing,
 * backpressure hooks, and manual offset commit-after-ack semantics.
 *
 * <p>Subclasses implement {@link #handle} and optionally override
 * {@link #classifyError} to refine {@link ConsumerErrorType} per exception type.
 *
 * <p>The poll loop runs on a dedicated non-eventloop thread to never block ActiveJ.
 * Offset is committed only after {@link #handle} completes without error.
 *
 * @doc.type class
 * @doc.purpose Abstract Kafka consumer with retry / DLQ / backpressure (STORY-K05-010)
 * @doc.layer product
 * @doc.pattern Template
 */
public abstract class EventConsumerBase {

    private static final Logger LOG = Logger.getLogger(EventConsumerBase.class.getName());

    private static final int DEFAULT_MAX_TRANSIENT_RETRIES = 3;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final long BASE_BACKOFF_MS = 200;

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final String groupId;
    private final int maxTransientRetries;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    // ── Construction ─────────────────────────────────────────────────────────

    protected EventConsumerBase(Map<String, Object> kafkaConfig, String topic, String groupId) {
        this(kafkaConfig, topic, groupId, DEFAULT_MAX_TRANSIENT_RETRIES);
    }

    protected EventConsumerBase(Map<String, Object> kafkaConfig,
                                String topic,
                                String groupId,
                                int maxTransientRetries) {
        this.topic = topic;
        this.groupId = groupId;
        this.maxTransientRetries = maxTransientRetries;

        Map<String, Object> config = new HashMap<>(kafkaConfig);
        config.put("group.id", groupId);
        config.put("enable.auto.commit", "false");
        config.put("auto.offset.reset", "earliest");
        config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        this.consumer = new KafkaConsumer<>(config);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "event-consumer-" + groupId);
            t.setDaemon(true);
            return t;
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Start polling. Safe to call once per instance. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        consumer.subscribe(Collections.singletonList(topic));
        executor.submit(this::pollLoop);
        LOG.info("[EventConsumerBase] Started consumer group=" + groupId + " topic=" + topic);
    }

    /** Graceful stop: drains current batch before exiting. */
    public void stop() {
        running.set(false);
        consumer.wakeup();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        consumer.close();
        LOG.info("[EventConsumerBase] Stopped consumer group=" + groupId);
    }

    /** Pause consumption (backpressure relief). */
    public void pause() {
        paused.set(true);
    }

    /** Resume consumption. */
    public void resume() {
        paused.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Extension Points ──────────────────────────────────────────────────────

    /**
     * Process a single event. Returning normally commits the offset.
     * Throwing a RuntimeException triggers retry / DLQ routing based on
     * {@link #classifyError}.
     *
     * @param key   Kafka message key (usually aggregateId)
     * @param value Kafka message value (JSON-encoded event)
     */
    protected abstract void handle(String key, String value);

    /**
     * Classify an exception to decide retry vs DLQ routing.
     * Override to map specific exception types.
     */
    protected ConsumerErrorType classifyError(Exception e) {
        return ConsumerErrorType.TRANSIENT;
    }

    /**
     * Called when a record is routed to the DLQ after exhausting retries or
     * a PERMANENT error. Override for custom DLQ side effects.
     */
    protected void onDlqRouted(ConsumerRecord<String, String> record, ConsumerError error) {
        LOG.warning("[EventConsumerBase] DLQ routed: topic=" + record.topic()
            + " partition=" + record.partition()
            + " offset=" + record.offset()
            + " errorType=" + error.errorType()
            + " message=" + error.message());
    }

    // ── Poll Loop ─────────────────────────────────────────────────────────────

    private void pollLoop() {
        while (running.get()) {
            if (paused.get()) {
                sleepQuietly(500);
                continue;
            }
            try {
                ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
                for (ConsumerRecord<String, String> record : records) {
                    processWithRetry(record);
                }
            } catch (org.apache.kafka.common.errors.WakeupException e) {
                // expected during stop()
                break;
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[EventConsumerBase] Unexpected poll error", e);
            }
        }
        LOG.info("[EventConsumerBase] Poll loop exited for group=" + groupId);
    }

    private void processWithRetry(ConsumerRecord<String, String> record) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxTransientRetries) {
            try {
                handle(record.key(), record.value());
                commitOffset(record);
                return; // success
            } catch (Exception e) {
                lastException = e;
                ConsumerErrorType errorType = classifyError(e);

                if (errorType == ConsumerErrorType.PERMANENT) {
                    ConsumerError error = ConsumerError.permanent(
                        e.getMessage(), e.getClass(), record.topic(), record.partition(), record.offset());
                    onDlqRouted(record, error);
                    commitOffset(record);
                    return;
                }

                attempt++;
                if (attempt > maxTransientRetries) {
                    break;
                }
                backoff(attempt);
            }
        }

        // exhausted transient retries — route to DLQ
        ConsumerError error = ConsumerError.transient_(
            lastException != null ? lastException.getMessage() : "unknown",
            record.topic(), record.partition(), record.offset(), maxTransientRetries);
        onDlqRouted(record, error);
        commitOffset(record);
    }

    private void commitOffset(ConsumerRecord<String, String> record) {
        TopicPartition tp = new TopicPartition(record.topic(), record.partition());
        consumer.commitSync(Collections.singletonMap(tp, new OffsetAndMetadata(record.offset() + 1)));
    }

    private void backoff(int attempt) {
        long delayMs = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 200ms, 400ms, 800ms
        sleepQuietly(delayMs);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
