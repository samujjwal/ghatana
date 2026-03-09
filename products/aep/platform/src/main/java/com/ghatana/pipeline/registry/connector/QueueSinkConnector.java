package com.ghatana.pipeline.registry.connector;

import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Queue sink connector operator.
 *
 * <p>
 * <b>Purpose</b><br>
 * Writes processed events to message queues (Kafka, RabbitMQ, SQS). Acts as the
 * exit point for event egress to queues, enabling multi-hop event processing
 * pipelines.
 *
 * <p>
 * <b>Lifecycle</b><br>
 * GIVEN: Connected to queue endpoint<br>
 * WHEN: initialize() is called<br>
 * THEN: Producer created and ready to send messages<br>
 *
 * <p>
 * <b>Operations</b><br>
 * - initialize(): Create queue producer<br>
 * - connect(): Verify connectivity to queue<br>
 * - disconnect(): Close connection temporarily<br>
 * - close(): Release all resources<br>
 * - publish(event): Write event to queue<br>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Sink operator in connector layer (P3-02 queue sink). Integrates with queue
 * adapters (Kafka, RabbitMQ, SQS). Enables event routing between pipelines.
 * Metrics tracked: aep.connector.queue_sink.messages.sent,
 * aep.connector.queue_sink.errors, aep.connector.queue_sink.latency_ms
 *
 * @doc.type class
 * @doc.purpose Queue message sink connector
 * @doc.layer product
 * @doc.pattern Operator
 */
@Slf4j
public class QueueSinkConnector implements ConnectorOperator {

    /**
     * Lifecycle state for the connector.
     */
    private enum ConnectorState {
        CREATED, INITIALIZED, CONNECTED, DISCONNECTED, CLOSED
    }

    private final String id;
    private final ConnectorSpec spec;
    private final MetricsCollector metricsCollector;
    private final QueueProducerStrategy producerStrategy;
    private final AtomicReference<ConnectorState> state = new AtomicReference<>(ConnectorState.CREATED);

    /**
     * Creates a QueueSinkConnector with a producer strategy.
     *
     * @param id               connector identifier
     * @param spec             connector specification
     * @param metricsCollector metrics collection service
     * @param producerStrategy queue producer strategy for message publishing
     */
    public QueueSinkConnector(String id, ConnectorSpec spec,
                              MetricsCollector metricsCollector,
                              QueueProducerStrategy producerStrategy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.spec = Objects.requireNonNull(spec, "spec cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
        this.producerStrategy = producerStrategy;
    }

    /**
     * Creates a QueueSinkConnector without a producer strategy (for testing/placeholder).
     */
    public QueueSinkConnector(String id, ConnectorSpec spec, MetricsCollector metricsCollector) {
        this(id, spec, metricsCollector, null);
    }

    /**
     * Create sink connector from spec.
     *
     * @param spec             the connector specification
     * @param metricsCollector metrics collection service
     * @return configured sink connector
     */
    public static QueueSinkConnector of(
            ConnectorSpec spec,
            MetricsCollector metricsCollector) {
        return new QueueSinkConnector(spec.getId(), spec, metricsCollector);
    }

    /**
     * Create sink connector from spec with producer strategy.
     *
     * @param spec             the connector specification
     * @param metricsCollector metrics collection service
     * @param producerStrategy the queue producer strategy
     * @return configured sink connector
     */
    public static QueueSinkConnector of(
            ConnectorSpec spec,
            MetricsCollector metricsCollector,
            QueueProducerStrategy producerStrategy) {
        return new QueueSinkConnector(spec.getId(), spec, metricsCollector, producerStrategy);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.QUEUE_SINK;
    }

    /**
     * Initialize queue sink connector.
     *
     * GIVEN: Valid queue endpoint and topic/queue specified<br>
     * WHEN: initialize() is called<br>
     * THEN: Producer created and ready to send messages<br>
     *
     * @return Promise that completes when ready to send
     */
    @Override
    public Promise<Void> initialize() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Initializing queue sink connector. endpoint={}, topic={}",
                    spec.getEndpoint(), spec.getTopicOrStream());

            if (!state.compareAndSet(ConnectorState.CREATED, ConnectorState.INITIALIZED)) {
                log.warn("Cannot initialize connector in state: {}", state.get());
                return Promise.ofException(
                        new IllegalStateException("Cannot initialize connector in state: " + state.get()));
            }

            metricsCollector.incrementCounter("aep.connector.queue_sink.initialize.count",
                    "connector_id", id,
                    "status", "initialized");

            return Promise.complete();
        }
    }

    /**
     * Connect to queue endpoint.
     *
     * GIVEN: Connector initialized<br>
     * WHEN: connect() is called<br>
     * THEN: Connection verified and ready for publish operations<br>
     *
     * @return Promise that completes when connected
     */
    @Override
    public Promise<Void> connect() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Connecting to queue sink. endpoint={}", spec.getEndpoint());

            ConnectorState current = state.get();
            if (current != ConnectorState.INITIALIZED && current != ConnectorState.DISCONNECTED) {
                return Promise.ofException(
                        new IllegalStateException("Cannot connect in state: " + current));
            }

            // Start the producer strategy if available
            Promise<Void> startPromise = (producerStrategy != null)
                    ? producerStrategy.start()
                    : Promise.complete();

            return startPromise.then(v -> {
                state.set(ConnectorState.CONNECTED);
                metricsCollector.incrementCounter("aep.connector.queue_sink.connect.count",
                        "connector_id", id,
                        "status", "connected");
                log.info("Queue sink connected: endpoint={}", spec.getEndpoint());
                return Promise.complete();
            }).whenException(ex -> {
                log.error("Failed to connect queue sink: {}", ex.getMessage(), ex);
                metricsCollector.incrementCounter("aep.connector.queue_sink.errors",
                        "connector_id", id,
                        "phase", "connect");
            });
        }
    }

    /**
     * Disconnect from queue endpoint.
     *
     * GIVEN: Connector connected<br>
     * WHEN: disconnect() is called<br>
     * THEN: Connection closed, pending messages flushed<br>
     *
     * @return Promise that completes when disconnected
     */
    @Override
    public Promise<Void> disconnect() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Disconnecting from queue sink");

            if (state.get() != ConnectorState.CONNECTED) {
                log.debug("Connector not connected, skipping disconnect");
                return Promise.complete();
            }

            // Flush pending messages then stop the producer
            Promise<Void> stopPromise;
            if (producerStrategy != null) {
                stopPromise = producerStrategy.flush()
                        .then(v -> producerStrategy.stop());
            } else {
                stopPromise = Promise.complete();
            }

            return stopPromise.then(v -> {
                state.set(ConnectorState.DISCONNECTED);
                metricsCollector.incrementCounter("aep.connector.queue_sink.disconnect.count",
                        "connector_id", id);
                return Promise.complete();
            });
        }
    }

    /**
     * Close sink connector and release resources.
     *
     * GIVEN: Connector connected or initialized<br>
     * WHEN: close() is called<br>
     * THEN: All connections closed, resources released<br>
     *
     * @return Promise that completes when closed
     */
    @Override
    public Promise<Void> close() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            log.info("Closing queue sink connector");

            ConnectorState current = state.get();
            if (current == ConnectorState.CLOSED) {
                return Promise.complete();
            }

            // Disconnect first if still connected
            Promise<Void> disconnectFirst = (current == ConnectorState.CONNECTED)
                    ? disconnect()
                    : Promise.complete();

            return disconnectFirst.then(v -> {
                state.set(ConnectorState.CLOSED);
                metricsCollector.incrementCounter("aep.connector.queue_sink.close.count",
                        "connector_id", id);
                log.info("Queue sink connector closed");
                return Promise.complete();
            });
        }
    }

    /**
     * Check connector health.
     *
     * GIVEN: Connector connected<br>
     * WHEN: isHealthy() is called<br>
     * THEN: Return current connection state<br>
     *
     * @return Promise containing health status (true = healthy)
     */
    @Override
    public Promise<Boolean> isHealthy() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id)) {
            if (state.get() != ConnectorState.CONNECTED) {
                return Promise.of(false);
            }

            if (producerStrategy != null) {
                QueueProducerStrategy.ProducerStatus status = producerStrategy.getStatus();
                boolean healthy = status == QueueProducerStrategy.ProducerStatus.RUNNING;
                if (!healthy) {
                    log.warn("Queue sink unhealthy: producer status={}", status);
                }
                return Promise.of(healthy);
            }

            return Promise.of(true);
        }
    }

    /**
     * Publish event to queue.
     *
     * <p>
     * <b>Purpose</b><br>
     * Send a processed event to queue for downstream consumption.
     *
     * GIVEN: Event ready for egress and connector connected<br>
     * WHEN: publish(event) is called<br>
     * THEN: Event published to queue with partition routing<br>
     *
     * @param event the event to publish
     * @return Promise that completes when event is durably sent
     */
    public Promise<Void> publish(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        try (MDC.MDCCloseable ignored = MDC.putCloseable("connectorId", id);
                MDC.MDCCloseable ignored2 = MDC.putCloseable("eventId", event.getId().toString())) {

            log.debug("Publishing event to queue. topic={}", spec.getTopicOrStream());

            if (state.get() != ConnectorState.CONNECTED) {
                return Promise.ofException(
                        new IllegalStateException("Cannot publish: connector not connected (state: " + state.get() + ")"));
            }

            long startTime = System.currentTimeMillis();
            String eventKey = event.getId() != null ? event.getId().toString() : "";
            String eventPayload = event.toString();

            // Publish via producer strategy
            Promise<Void> publishPromise;
            if (producerStrategy != null) {
                publishPromise = producerStrategy.send(eventKey, eventPayload)
                        .map(messageId -> {
                            log.debug("Event published to queue: messageId={}", messageId);
                            return (Void) null;
                        });
            } else {
                publishPromise = Promise.complete();
            }

            return publishPromise.whenResult(v -> {
                long latencyMs = System.currentTimeMillis() - startTime;
                metricsCollector.incrementCounter("aep.connector.queue_sink.messages.published",
                        "connector_id", id,
                        "event_type", event.getType());
                metricsCollector.recordTimer("aep.connector.queue_sink.latency_ms",
                        latencyMs,
                        "connector_id", id);
            }).whenException(ex -> {
                metricsCollector.incrementCounter("aep.connector.queue_sink.errors",
                        "connector_id", id,
                        "phase", "publish",
                        "error_type", ex.getClass().getSimpleName());
                log.error("Failed to publish event to queue: {}", ex.getMessage(), ex);
            });
        }
    }
}
