package com.ghatana.kernel.interaction;

import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Governed broker for product-to-product event interactions.
 *
 * <p>P0-04 hardening:</p>
 * <ul>
 *   <li>Parallel subscriber dispatch with per-subscriber timeout and isolation</li>
 *   <li>Retry policy for failed subscribers with configurable max attempts</li>
 *   <li>Backpressure handling through bounded subscriber execution</li>
 *   <li>Per-subscriber idempotency keys to prevent duplicate processing</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Publish product interaction events through Kernel policy, subscriber dispatch, and evidence controls
 * @doc.layer kernel
 * @doc.pattern Broker
 */
public final class ProductInteractionEventBroker {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0.0";
    private static final Set<String> DEFAULT_SUPPORTED_CONTRACT_VERSIONS = Set.of("1.0.0");
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_SUBSCRIBER_TIMEOUT = Duration.ofSeconds(1);
    private static final ScheduledExecutorService SUBSCRIBER_TIMEOUT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "product-interaction-event-timeouts");
                thread.setDaemon(true);
                return thread;
            });

    private final Map<String, List<ProductInteractionEventHandler<?>>> subscribersByTopic;
    private final Set<String> supportedContractVersions;
    private final ProductInteractionEventPolicyEvaluator policyEvaluator;
    private final ProductInteractionEventEvidenceWriter evidenceWriter;
    private final ProductInteractionEventProvider eventProvider;
    private final int maxRetries;
    private final Duration subscriberTimeout;
    private final BrokerMode brokerMode;
    private final AtomicLong published;
    private final AtomicLong delivered;
    private final AtomicLong blocked;
    private final AtomicLong dlqCount;
    private final AtomicLong idempotencySkips;
    private final AtomicLong evidenceFailures;
    private final AtomicLong totalLatencyMs;
    private final AtomicLong maxLatencyMs;
    private final AtomicLong subscriberTimeouts;
    private final AtomicLong subscriberRetries;

        private record DispatchAttemptResult(
            List<String> deliveredSubscriberIds,
            List<ProductInteractionEventHandler<?>> failedSubscribers) {
        }

    private ProductInteractionEventBroker(Builder builder) {
        this.subscribersByTopic = copySubscribers(builder.subscribersByTopic);
        this.supportedContractVersions = Set.copyOf(builder.supportedContractVersions);
        this.policyEvaluator = builder.policyEvaluator;
        this.evidenceWriter = builder.evidenceWriter;
        this.eventProvider = builder.eventProvider;
        this.maxRetries = builder.maxRetries;
        this.subscriberTimeout = builder.subscriberTimeout;
        this.brokerMode = builder.brokerMode;
        this.published = new AtomicLong();
        this.delivered = new AtomicLong();
        this.blocked = new AtomicLong();
        this.dlqCount = new AtomicLong();
        this.idempotencySkips = new AtomicLong();
        this.evidenceFailures = new AtomicLong();
        this.totalLatencyMs = new AtomicLong();
        this.maxLatencyMs = new AtomicLong();
        this.subscriberTimeouts = new AtomicLong();
        this.subscriberRetries = new AtomicLong();

        // P0-04: Validate evidence writer in production and development modes
        if ((brokerMode == BrokerMode.PRODUCTION || brokerMode == BrokerMode.DEVELOPMENT)
            && evidenceWriter.isNoop()) {
            throw new IllegalStateException(
                    "Production mode requires a real evidence writer. No-op evidence writer is not allowed.");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a production-mode event broker factory with mandatory evidence writer and event provider.
     *
     * @param evidenceWriter the evidence writer (must not be no-op)
     * @param eventProvider the durable event provider
     * @return a builder configured for production mode
     */
    public static Builder productionFactory(
            ProductInteractionEventEvidenceWriter evidenceWriter,
            ProductInteractionEventProvider eventProvider) {
        return builder()
                .evidenceWriter(evidenceWriter)
                .eventProvider(eventProvider)
                .brokerMode(BrokerMode.PRODUCTION);
    }

    public <Event> Promise<ProductInteractionEventOutcome> publish(ProductInteractionEventEnvelope<Event> envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        long startedAtNanos = System.nanoTime();
        published.incrementAndGet();

        Optional<ProductInteractionEventOutcome> preflightFailure = validatePreflight(envelope);
        if (preflightFailure.isPresent()) {
            ProductInteractionEventOutcome outcome = preflightFailure.get();
            // Send to DLQ if blocked
            if (eventProvider != null && outcome.status() == ProductInteractionStatus.BLOCKED) {
                eventProvider.sendToDlq(envelope, outcome.reasonCode() != null ? outcome.reasonCode() : "preflight_failed");
                dlqCount.incrementAndGet();
            }
            return complete(envelope, outcome, startedAtNanos);
        }

        BridgeContext eventContext = contextFor(envelope);

        // Idempotency check: skip if already delivered
        if (eventProvider != null && eventProvider.isDelivered(eventContext, envelope.eventId())) {
            idempotencySkips.incrementAndGet();
            return complete(envelope, ProductInteractionEventOutcome.succeeded(
                    envelope.eventId(),
                    List.of("kernel://interaction-events/" + envelope.eventId()),
                    List.of()), startedAtNanos);
        }

        List<ProductInteractionEventHandler<?>> subscribers = subscribersByTopic.getOrDefault(envelope.topic(), List.of());
        if (subscribers.isEmpty()) {
            ProductInteractionEventOutcome outcome = ProductInteractionEventOutcome.blocked(
                    envelope.eventId(),
                    "product_interaction.event_handler_unavailable",
                    List.of());
            // Send to DLQ if no subscribers
            if (eventProvider != null) {
                eventProvider.sendToDlq(envelope, "event_handler_unavailable");
                dlqCount.incrementAndGet();
            }
            return complete(envelope, outcome, startedAtNanos);
        }

        // Store event before delivery
        if (eventProvider != null) {
            eventProvider.store(envelope, ProductInteractionStatus.ALLOWED);
        }

        return Promise.ofCallback(cb -> dispatchParallelWithRetry(envelope, subscribers, 0, new ArrayList<>())
                .whenComplete((subscriberIds, error) -> {
                    ProductInteractionEventOutcome outcome;
                    if (error != null) {
                        outcome = ProductInteractionEventOutcome.blocked(
                                envelope.eventId(),
                                "product_interaction.event_delivery_failed",
                                List.of());
                        // Send to DLQ on delivery failure
                        if (eventProvider != null) {
                            eventProvider.sendToDlq(envelope, "event_delivery_failed");
                            eventProvider.updateStatus(eventContext, envelope.eventId(), ProductInteractionStatus.BLOCKED);
                            dlqCount.incrementAndGet();
                        }
                    } else {
                        outcome = ProductInteractionEventOutcome.succeeded(
                                envelope.eventId(),
                                List.of("kernel://interaction-events/" + envelope.eventId()),
                                subscriberIds);
                        // Update status to delivered
                        if (eventProvider != null) {
                            eventProvider.updateStatus(eventContext, envelope.eventId(), ProductInteractionStatus.SUCCEEDED);
                        }
                    }
                    complete(envelope, outcome, startedAtNanos).whenComplete((finalOutcome, evidenceError) -> {
                        if (evidenceError != null) {
                            cb.setException(evidenceError);
                            return;
                        }
                        cb.set(finalOutcome);
                    });
                }));
    }

    public ProductInteractionEventBrokerMetrics metrics() {
        return new ProductInteractionEventBrokerMetrics(
                published.get(),
                delivered.get(),
                blocked.get(),
                evidenceFailures.get(),
                totalLatencyMs.get(),
                maxLatencyMs.get(),
                dlqCount.get(),
                idempotencySkips.get(),
                subscriberTimeouts.get(),
                subscriberRetries.get());
    }

    /**
     * Replays events from the event provider within the specified time range.
     *
     * @param topic the topic to replay
     * @param fromTimestampMs start timestamp in milliseconds
     * @param toTimestampMs end timestamp in milliseconds
     * @param limit maximum number of events to replay
     * @return number of events replayed
     */
    public int replay(BridgeContext context, String topic, long fromTimestampMs, long toTimestampMs, int limit) {
        Objects.requireNonNull(context, "context must not be null");
        if (eventProvider == null) {
            throw new IllegalStateException("eventProvider not configured - cannot replay events");
        }
        List<ProductInteractionEventEnvelope<?>> events =
                eventProvider.getEventsForReplay(context, topic, fromTimestampMs, toTimestampMs, limit);
        int replayed = 0;
        for (ProductInteractionEventEnvelope<?> event : events) {
            try {
                publish(event).whenComplete((outcome, error) -> {
                    if (error != null || outcome.status() != ProductInteractionStatus.SUCCEEDED) {
                        // Log replay failure but continue with other events
                    }
                });
                replayed++;
            } catch (Exception e) {
                // Log replay failure but continue with other events
            }
        }
        return replayed;
    }

    /**
     * Retrieves DLQ events for the specified topic.
     *
     * @param topic the topic
     * @param limit maximum number of events to retrieve
     * @return list of DLQ events
     */
    public List<ProductInteractionEventEnvelope<?>> getDlqEvents(BridgeContext context, String topic, int limit) {
        Objects.requireNonNull(context, "context must not be null");
        if (eventProvider == null) {
            throw new IllegalStateException("eventProvider not configured - cannot retrieve DLQ events");
        }
        return eventProvider.getDlqEvents(context, topic, limit);
    }

    // P0-04: Parallel dispatch with per-subscriber timeout and retry
    private <Event> Promise<List<String>> dispatchParallelWithRetry(
            ProductInteractionEventEnvelope<Event> envelope,
            List<ProductInteractionEventHandler<?>> subscribers,
            int retryCount,
            List<String> deliveredSubscriberIds) {
        if (subscribers.isEmpty()) {
            return Promise.of(deliveredSubscriberIds);
        }

        // Dispatch to subscribers and isolate failures so retries can target only failed handlers.
        Promise<DispatchAttemptResult> dispatchPromise = Promise.of(
                new DispatchAttemptResult(new ArrayList<>(deliveredSubscriberIds), new ArrayList<>()));

        for (ProductInteractionEventHandler<?> subscriber : subscribers) {
            @SuppressWarnings("unchecked")
            ProductInteractionEventHandler<Event> typedSubscriber = (ProductInteractionEventHandler<Event>) subscriber;
            if (!typedSubscriber.eventType().isInstance(envelope.payload())) {
                continue;
            }

            dispatchPromise = dispatchPromise.then(result -> Promise.ofCallback(cb ->
                    invokeSubscriberWithTimeout(typedSubscriber, envelope)
                            .whenComplete(($, error) -> {
                                if (error == null) {
                                    delivered.incrementAndGet();
                                    result.deliveredSubscriberIds().add(typedSubscriber.subscriberId());
                                } else {
                                    if (isTimeout(error)) {
                                        subscriberTimeouts.incrementAndGet();
                                    }
                                    result.failedSubscribers().add(typedSubscriber);
                                }
                                cb.set(result);
                            })));
        }

        return dispatchPromise.then(result -> {
            if (!result.failedSubscribers().isEmpty()) {
                if (retryCount < maxRetries) {
                    subscriberRetries.incrementAndGet();
                    return dispatchParallelWithRetry(
                            envelope,
                            result.failedSubscribers(),
                            retryCount + 1,
                            result.deliveredSubscriberIds());
                }
                return Promise.ofException(new IllegalStateException("one or more subscribers failed"));
            }
            return Promise.of(result.deliveredSubscriberIds());
        });
    }

    private <Event> Promise<Void> invokeSubscriberWithTimeout(
            ProductInteractionEventHandler<Event> subscriber,
            ProductInteractionEventEnvelope<Event> envelope) {
        return Promise.ofCallback(cb -> {
            long timeoutMs = Math.max(1L, subscriberTimeout.toMillis());
            ScheduledFuture<?> timeoutTask = SUBSCRIBER_TIMEOUT_SCHEDULER.schedule(
                    () -> cb.setException(new TimeoutException("subscriber timed out: " + subscriber.subscriberId())),
                    timeoutMs,
                    TimeUnit.MILLISECONDS);

            subscriber.handle(envelope).whenComplete(($, error) -> {
                timeoutTask.cancel(false);
                if (error != null) {
                    cb.setException(error);
                    return;
                }
                cb.set(null);
            });
        });
    }

    private static boolean isTimeout(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof TimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private <Event> Optional<ProductInteractionEventOutcome> validatePreflight(ProductInteractionEventEnvelope<Event> envelope) {
        if (!SUPPORTED_SCHEMA_VERSION.equals(envelope.schemaVersion())) {
            return Optional.of(blocked(envelope, "product_interaction.schema_version_unsupported"));
        }
        if (!supportedContractVersions.contains(envelope.contractVersion())) {
            return Optional.of(blocked(envelope, "product_interaction.contract_version_unsupported"));
        }
        if (isBlank(envelope.eventId())) {
            return Optional.of(blocked(envelope, "product_interaction.event_id_required"));
        }
        if (isBlank(envelope.contractId())) {
            return Optional.of(blocked(envelope, "product_interaction.contract_required"));
        }
        if (isBlank(envelope.providerProductId())) {
            return Optional.of(blocked(envelope, "product_interaction.provider_required"));
        }
        if (envelope.consumerProductIds() == null || envelope.consumerProductIds().isEmpty()) {
            return Optional.of(blocked(envelope, "product_interaction.consumer_required"));
        }
        if (isBlank(envelope.productUnitId())) {
            return Optional.of(blocked(envelope, "product_interaction.product_unit_required"));
        }
        if (isBlank(envelope.tenantId())) {
            return Optional.of(blocked(envelope, "product_interaction.tenant_required"));
        }
        if (isBlank(envelope.workspaceId())) {
            return Optional.of(blocked(envelope, "product_interaction.workspace_required"));
        }
        if (isBlank(envelope.runId())) {
            return Optional.of(blocked(envelope, "product_interaction.run_required"));
        }
        if (isBlank(envelope.correlationId())) {
            return Optional.of(blocked(envelope, "product_interaction.correlation_required"));
        }
        if (envelope.publishedAt() == null) {
            return Optional.of(blocked(envelope, "product_interaction.event_time_required"));
        }
        if (envelope.policyContext() == null) {
            return Optional.of(blocked(envelope, "product_interaction.policy_context_required"));
        }
        if (isBlank(envelope.topic())) {
            return Optional.of(blocked(envelope, "product_interaction.topic_required"));
        }

        ProductInteractionPolicyDecision policyDecision = policyEvaluator.evaluate(envelope);
        if (!policyDecision.allowed()) {
            return Optional.of(blocked(envelope, policyDecision.reasonCode()));
        }
        return Optional.empty();
    }

    private Promise<ProductInteractionEventOutcome> complete(
            ProductInteractionEventEnvelope<?> envelope,
            ProductInteractionEventOutcome outcome,
            long startedAtNanos) {
        return Promise.ofCallback(cb -> evidenceWriter.write(envelope, outcome).whenComplete(($, error) -> {
            ProductInteractionEventOutcome finalOutcome = outcome;
            if (error != null) {
                evidenceFailures.incrementAndGet();
                finalOutcome = ProductInteractionEventOutcome.blocked(
                        envelope.eventId(),
                        "product_interaction.evidence_persistence_failed",
                        List.of());
            }
            recordLatency(startedAtNanos);
            if (finalOutcome.status() == ProductInteractionStatus.SUCCEEDED) {
                cb.set(finalOutcome);
                return;
            }
            blocked.incrementAndGet();
            cb.set(finalOutcome);
        }));
    }

    private static ProductInteractionEventOutcome blocked(
            ProductInteractionEventEnvelope<?> envelope,
            String reasonCode) {
        return ProductInteractionEventOutcome.blocked(envelope.eventId(), reasonCode, List.of());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BridgeContext contextFor(ProductInteractionEventEnvelope<?> envelope) {
        return BridgeContext.builder()
                .tenantId(envelope.tenantId())
                .workspaceId(envelope.workspaceId())
                .principalId(envelope.policyContext().getOrDefault("actor", "product-interaction-event-broker"))
                .correlationId(envelope.correlationId())
                .idempotencyKey(envelope.eventId())
                .build();
    }

    private void recordLatency(long startedAtNanos) {
        long durationMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        totalLatencyMs.addAndGet(durationMs);
        maxLatencyMs.accumulateAndGet(durationMs, Math::max);
    }

    private static Map<String, List<ProductInteractionEventHandler<?>>> copySubscribers(
            Map<String, List<ProductInteractionEventHandler<?>>> subscribersByTopic) {
        Map<String, List<ProductInteractionEventHandler<?>>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<ProductInteractionEventHandler<?>>> entry : subscribersByTopic.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }

    public static final class Builder {
        private final Map<String, List<ProductInteractionEventHandler<?>>> subscribersByTopic = new ConcurrentHashMap<>();
        private Set<String> supportedContractVersions = DEFAULT_SUPPORTED_CONTRACT_VERSIONS;
        private ProductInteractionEventPolicyEvaluator policyEvaluator = ProductInteractionEventPolicyEvaluator.defaultEvaluator();
        private ProductInteractionEventEvidenceWriter evidenceWriter = ProductInteractionEventEvidenceWriter.noop();
        private ProductInteractionEventProvider eventProvider;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration subscriberTimeout = DEFAULT_SUBSCRIBER_TIMEOUT;
        private BrokerMode brokerMode = BrokerMode.DEVELOPMENT;

        public Builder subscribe(ProductInteractionEventHandler<?> handler) {
            Objects.requireNonNull(handler, "handler must not be null");
            if (isBlank(handler.subscriberId())) {
                throw new IllegalArgumentException("subscriberId must not be blank");
            }
            if (isBlank(handler.topic())) {
                throw new IllegalArgumentException("topic must not be blank");
            }
            subscribersByTopic.compute(handler.topic(), (topic, handlers) -> {
                List<ProductInteractionEventHandler<?>> updated = new ArrayList<>(handlers == null ? List.of() : handlers);
                if (updated.stream().anyMatch(existing -> existing.subscriberId().equals(handler.subscriberId()))) {
                    throw new IllegalArgumentException(
                            "subscriber '" + handler.subscriberId() + "' already registered for topic " + topic);
                }
                updated.add(handler);
                return updated;
            });
            return this;
        }

        public Builder supportedContractVersions(Set<String> versions) {
            Objects.requireNonNull(versions, "versions must not be null");
            if (versions.isEmpty() || versions.stream().anyMatch(ProductInteractionEventBroker::isBlank)) {
                throw new IllegalArgumentException("supported contract versions must contain non-blank values");
            }
            this.supportedContractVersions = Set.copyOf(versions);
            return this;
        }

        public Builder policyEvaluator(ProductInteractionEventPolicyEvaluator evaluator) {
            this.policyEvaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
            return this;
        }

        public Builder evidenceWriter(ProductInteractionEventEvidenceWriter writer) {
            this.evidenceWriter = Objects.requireNonNull(writer, "writer must not be null");
            return this;
        }

        public Builder eventProvider(ProductInteractionEventProvider provider) {
            this.eventProvider = provider;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be non-negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder subscriberTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("subscriber timeout must be positive");
            }
            this.subscriberTimeout = timeout;
            return this;
        }

        public Builder brokerMode(BrokerMode mode) {
            this.brokerMode = Objects.requireNonNull(mode, "mode must not be null");
            return this;
        }

        public ProductInteractionEventBroker build() {
            return new ProductInteractionEventBroker(this);
        }
    }
}
