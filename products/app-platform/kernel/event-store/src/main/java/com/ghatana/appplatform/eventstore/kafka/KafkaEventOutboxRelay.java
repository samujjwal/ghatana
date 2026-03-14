package com.ghatana.appplatform.eventstore.kafka;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.port.AggregateEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Polls the PostgreSQL event store for events that have not yet been published to Kafka
 * and delivers them using a {@link KafkaEventPublisher}.
 *
 * <p>This relay implements the <em>outbox pattern</em> for K-05 (STORY-K05-009):
 * events are first persisted to PostgreSQL via {@link AggregateEventStore#appendEvent},
 * then asynchronously forwarded to Kafka by this relay. This guarantees durability
 * even if Kafka is temporarily unavailable.
 *
 * <h2>Delivery guarantees</h2>
 * <ul>
 *   <li><strong>At-least-once</strong> — retries on Kafka failure.</li>
 *   <li><strong>Ordering</strong> — per aggregate, events are replayed in sequence order.</li>
 *   <li><strong>DLQ routing</strong> — after {@code maxAttempts} failures, the event is
 *       forwarded to a DLQ topic ({@code siddhanta.{aggregate_type}.dlq}).</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose K-05 outbox relay: polls PostgreSQL, publishes to Kafka (STORY-K05-009)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KafkaEventOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventOutboxRelay.class);

    private final AggregateEventStore eventStore;
    private final KafkaEventPublisher publisher;
    private final KafkaEventPublisher dlqPublisher;  // same broker, DLQ topics
    private final KafkaOutboxCursor cursor;
    private final int batchSize;
    private final int maxAttempts;
    private final ScheduledExecutorService scheduler;
    /** Optional backpressure monitor; null when not configured (K05-026). */
    private final KafkaProducerFlowControl flowControl;

    private final AtomicLong totalPublished = new AtomicLong();
    private final AtomicLong totalDlqRouted  = new AtomicLong();

    /**
     * @param eventStore  source of unpublished events
     * @param publisher   Kafka publisher for successful deliveries
     * @param dlqPublisher publisher used to route failed events to DLQ topics
     * @param cursor      tracks the last-published sequence per aggregate type
     * @param batchSize   events processed per poll cycle (default: 100)
     * @param maxAttempts relay attempts before DLQ routing (default: 5)
     */
    public KafkaEventOutboxRelay(
            AggregateEventStore eventStore,
            KafkaEventPublisher publisher,
            KafkaEventPublisher dlqPublisher,
            KafkaOutboxCursor cursor,
            int batchSize,
            int maxAttempts) {
        this(eventStore, publisher, dlqPublisher, cursor, batchSize, maxAttempts, null);
    }

    /**
     * @param eventStore   source of unpublished events
     * @param publisher    Kafka publisher for successful deliveries
     * @param dlqPublisher publisher used to route failed events to DLQ topics
     * @param cursor       tracks the last-published sequence per aggregate type
     * @param batchSize    events processed per poll cycle (default: 100)
     * @param maxAttempts  relay attempts before DLQ routing (default: 5)
     * @param flowControl  optional producer buffer monitor; null to disable flow control (K05-026)
     */
    public KafkaEventOutboxRelay(
            AggregateEventStore eventStore,
            KafkaEventPublisher publisher,
            KafkaEventPublisher dlqPublisher,
            KafkaOutboxCursor cursor,
            int batchSize,
            int maxAttempts,
            KafkaProducerFlowControl flowControl) {
        this.eventStore   = eventStore;
        this.publisher    = publisher;
        this.dlqPublisher = dlqPublisher;
        this.cursor       = cursor;
        this.batchSize    = batchSize;
        this.maxAttempts  = maxAttempts;
        this.flowControl  = flowControl;
        this.scheduler    = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kafka-outbox-relay");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the relay with the specified polling interval in milliseconds. */
    public void start(long pollIntervalMs) {
        log.info("KafkaEventOutboxRelay starting: batchSize={}, pollIntervalMs={}, maxAttempts={}",
                batchSize, pollIntervalMs, maxAttempts);
        scheduler.scheduleWithFixedDelay(this::processBatch, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** Stops the relay gracefully, waiting up to 5 seconds for the current batch. */
    public void stop() {
        log.info("KafkaEventOutboxRelay stopping (totalPublished={}, totalDlqRouted={})",
                totalPublished.get(), totalDlqRouted.get());
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    /** Returns total events successfully published to Kafka since start. */
    public long totalPublished() { return totalPublished.get(); }

    /** Returns total events forwarded to DLQ topics since start. */
    public long totalDlqRouted() { return totalDlqRouted.get(); }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void processBatch() {
        try {
            // K05-026: back off when the producer buffer is under pressure
            if (flowControl != null && flowControl.shouldThrottle()) {
                Thread.sleep(flowControl.throttleDelayMs());
                return;  // skip this cycle; scheduler will retry after pollIntervalMs
            }

            List<OutboxCandidate> candidates = cursor.nextBatch(batchSize);
            if (candidates.isEmpty()) {
                return;
            }
            log.debug("Processing {} outbox candidates", candidates.size());

            for (OutboxCandidate candidate : candidates) {
                publishWithRetryOrDlq(candidate);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.debug("Outbox relay interrupted during throttle sleep");
        } catch (Exception e) {
            log.error("Outbox relay batch failed: {}", e.getMessage(), e);
        }
    }

    private void publishWithRetryOrDlq(OutboxCandidate candidate) {
        try {
            publisher.publish(candidate.record());
            cursor.markPublished(candidate.cursorId());
            totalPublished.incrementAndGet();
        } catch (KafkaPublishException ex) {
            int attempts = cursor.incrementAttempt(candidate.cursorId());
            if (attempts >= maxAttempts) {
                routeToDlq(candidate, ex);
            } else {
                log.warn("Kafka publish failed for event {} (attempt {}/{}): {}",
                        candidate.record().eventId(), attempts, maxAttempts, ex.getMessage());
            }
        }
    }

    private void routeToDlq(OutboxCandidate candidate, KafkaPublishException ex) {
        log.error("Max attempts ({}) reached for event {}. Routing to DLQ.",
                maxAttempts, candidate.record().eventId());
        try {
            dlqPublisher.publish(candidate.record());
            cursor.markDlqRouted(candidate.cursorId());
            totalDlqRouted.incrementAndGet();
        } catch (Exception dlqEx) {
            log.error("DLQ routing also failed for event {}: {}", candidate.record().eventId(), dlqEx.getMessage());
        }
    }

    /**
     * Represents a pending outbox entry with its relay cursor ID.
     *
     * @doc.type record
     * @doc.purpose Pairs an AggregateEventRecord with the cursor ID for marking progress
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record OutboxCandidate(String cursorId, AggregateEventRecord record) {}
}
