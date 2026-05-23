package com.ghatana.kernel.plugin;

import com.ghatana.kernel.interaction.ProductInteractionEventProvider;
import com.ghatana.kernel.interaction.ProductInteractionEvidenceWriter;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import io.activej.async.exception.AsyncTimeoutException;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central broker for plugin-to-plugin event interactions.
 *
 * <p>This broker provides the same capabilities as {@link com.ghatana.kernel.interaction.ProductInteractionEventBroker}
 * but for plugin interactions. It handles event publishing, policy evaluation, evidence writing,
 * and event persistence for replay, DLQ, and idempotency.</p>
 *
 * <p>PLUG-001 hardening:</p>
 * <ul>
 *   <li>Per-subscriber timeout with configurable duration</li>
 *   <li>Circuit breaker for subscriber failure isolation</li>
 *   <li>Backpressure through bounded concurrency</li>
 *   <li>Durable pub/sub with event persistence and DLQ</li>
 * </ul>
 *
 * <p>Plugin interactions follow the same contract model as product interactions, enabling
 * plugins to communicate with each other through typed contracts with policy enforcement.</p>
 *
 * @doc.type class
 * @doc.purpose Central broker for plugin-to-plugin event interactions with policy enforcement and resilience
 * @doc.layer kernel
 * @doc.pattern Broker
 */
public final class PluginInteractionEventBroker {

    private final Eventloop eventloop;
    private final PluginInteractionHandlerRegistry handlerRegistry;
    private final ProductInteractionEventProvider eventProvider;
    private final ProductInteractionEvidenceWriter evidenceWriter;
    private final PluginInteractionPolicyEvaluator policyEvaluator;
    private final Duration subscriberTimeout;
    private final int maxConcurrentDispatches;
    private final Semaphore backpressureSemaphore;

    private final Map<String, List<PluginInteractionSubscriber>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, PluginInteractionMetrics> metrics = new ConcurrentHashMap<>();
    private final Map<String, PluginCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final AtomicInteger activeDispatches = new AtomicInteger(0);

    public PluginInteractionEventBroker(
            Eventloop eventloop,
            PluginInteractionHandlerRegistry handlerRegistry,
            ProductInteractionEventProvider eventProvider,
            ProductInteractionEvidenceWriter evidenceWriter,
            PluginInteractionPolicyEvaluator policyEvaluator) {
        this(eventloop, handlerRegistry, eventProvider, evidenceWriter, policyEvaluator,
             Duration.ofSeconds(5), 100);
    }

    public PluginInteractionEventBroker(
            Eventloop eventloop,
            PluginInteractionHandlerRegistry handlerRegistry,
            ProductInteractionEventProvider eventProvider,
            ProductInteractionEvidenceWriter evidenceWriter,
            PluginInteractionPolicyEvaluator policyEvaluator,
            Duration subscriberTimeout,
            int maxConcurrentDispatches) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry must not be null");
        this.eventProvider = Objects.requireNonNull(eventProvider, "eventProvider must not be null");
        this.evidenceWriter = Objects.requireNonNull(evidenceWriter, "evidenceWriter must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
        this.subscriberTimeout = Objects.requireNonNull(subscriberTimeout, "subscriberTimeout must not be null");
        this.maxConcurrentDispatches = maxConcurrentDispatches;
        this.backpressureSemaphore = new Semaphore(maxConcurrentDispatches);
    }

    /**
     * Publishes a plugin interaction event to all subscribers.
     *
     * @param envelope the event envelope
     * @return a Promise that completes when all subscribers have been notified
     */
    public Promise<Void> publish(PluginInteractionEventEnvelope<?> envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");

        // Pre-flight validation
        if (!handlerRegistry.hasHandler(envelope.contractId())) {
            return Promise.ofException(new IllegalStateException(
                "No handler registered for contract: " + envelope.contractId()));
        }

        // Policy evaluation
        PluginInteractionPolicyDecision policyDecision = policyEvaluator.evaluate(envelope);
        if (!policyDecision.allowed()) {
            return writeEvidence(envelope, policyDecision)
                .then(() -> Promise.ofException(new PluginInteractionPolicyDeniedException(
                    envelope.contractId(),
                    policyDecision.reasonCode())));
        }

        // Store event for replay/DLQ/idempotency
        return Promise.of(eventProvider.store(toProductInteractionEnvelope(envelope), ProductInteractionStatus.ALLOWED))
            .then(() -> dispatchToSubscribers(envelope))
            .then(() -> writeEvidence(envelope, policyDecision))
            .whenException(error -> sendToDlq(envelope, error));
    }

    /**
     * Subscribes a handler to plugin interaction events for a contract.
     *
     * @param contractId the contract ID to subscribe to
     * @param subscriber the subscriber to add
     */
    public void subscribe(String contractId, PluginInteractionSubscriber subscriber) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        Objects.requireNonNull(subscriber, "subscriber must not be null");

        subscribers.computeIfAbsent(contractId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    /**
     * Unsubscribes a handler from plugin interaction events.
     *
     * @param contractId the contract ID to unsubscribe from
     * @param subscriber the subscriber to remove
     * @return true if removed, false if not found
     */
    public boolean unsubscribe(String contractId, PluginInteractionSubscriber subscriber) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        Objects.requireNonNull(subscriber, "subscriber must not be null");

        List<PluginInteractionSubscriber> contractSubscribers = subscribers.get(contractId);
        if (contractSubscribers == null) {
            return false;
        }
        return contractSubscribers.remove(subscriber);
    }

    /**
     * Gets all registered contract IDs.
     *
     * @return set of contract IDs
     */
    public Set<String> subscribedContractIds() {
        return subscribers.keySet();
    }

    /**
     * Gets metrics for a contract.
     *
     * @param contractId the contract ID
     * @return metrics for the contract
     */
    public PluginInteractionMetrics getMetrics(String contractId) {
        return metrics.getOrDefault(contractId, new PluginInteractionMetrics(0, 0, 0, 0));
    }

    private Promise<Void> dispatchToSubscribers(PluginInteractionEventEnvelope<?> envelope) {
        List<PluginInteractionSubscriber> contractSubscribers = subscribers.get(envelope.contractId());
        if (contractSubscribers == null || contractSubscribers.isEmpty()) {
            return Promise.complete();
        }

        // Apply backpressure - wait for available dispatch slot
        if (!backpressureSemaphore.tryAcquire()) {
            metrics.put(envelope.contractId(), 
                metrics.getOrDefault(envelope.contractId(), new PluginInteractionMetrics(0, 0, 0, 0))
                    .incrementBackpressureCount());
            return Promise.ofException(new PluginInteractionBackpressureException(
                "Backpressure: maximum concurrent dispatches reached"));
        }

        activeDispatches.incrementAndGet();
        PluginInteractionMetrics currentMetrics = metrics.getOrDefault(
            envelope.contractId(),
            new PluginInteractionMetrics(0, 0, 0, 0)
        );

        // Dispatch with timeout and circuit breaker to each subscriber
        Promise<Void> dispatchPromise = Promise.complete();
        for (PluginInteractionSubscriber subscriber : contractSubscribers) {
            dispatchPromise = dispatchPromise.then(() -> {
                PluginCircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(
                    envelope.contractId() + ":" + subscriberKey(subscriber),
                    id -> new PluginCircuitBreaker(5, Duration.ofSeconds(30))
                );

                if (circuitBreaker.isOpen()) {
                    metrics.put(envelope.contractId(), currentMetrics.incrementCircuitBreakerCount());
                    return Promise.complete(); // Skip subscriber when circuit is open
                }

                try {
                    return Promises.timeout(subscriberTimeout, subscriber.handle(envelope))
                        .whenResult(result -> {
                            circuitBreaker.recordSuccess();
                            metrics.put(envelope.contractId(), currentMetrics.incrementSuccessCount());
                        })
                        .whenException(error -> {
                            if (isTimeout(error)) {
                                circuitBreaker.recordFailure();
                                metrics.put(envelope.contractId(), currentMetrics.incrementTimeoutCount());
                            } else {
                                circuitBreaker.recordFailure();
                                metrics.put(envelope.contractId(), currentMetrics.incrementFailureCount());
                            }
                        });
                } catch (Exception error) {
                    circuitBreaker.recordFailure();
                    metrics.put(envelope.contractId(), currentMetrics.incrementFailureCount());
                    return Promise.ofException(error);
                }
            });
        }

        return dispatchPromise.whenComplete(($, error) -> {
            activeDispatches.decrementAndGet();
            backpressureSemaphore.release();
        });
    }

    private static String subscriberKey(PluginInteractionSubscriber subscriber) {
        return subscriber.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(subscriber));
    }

    private static boolean isTimeout(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof AsyncTimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private Promise<Void> writeEvidence(
            PluginInteractionEventEnvelope<?> envelope,
            PluginInteractionPolicyDecision policyDecision) {
        ProductInteractionRequest<Object> request = toEvidenceRequest(envelope);
        ProductInteractionOutcome<Object> outcome = policyDecision.allowed()
            ? ProductInteractionOutcome.succeeded(envelope.eventId(), List.of(), envelope.payload())
            : ProductInteractionOutcome.failed(
                envelope.eventId(),
                ProductInteractionStatus.BLOCKED,
                policyDecision.reasonCode(),
                List.of());
        return evidenceWriter.write(request, outcome);
    }

    private Promise<Void> sendToDlq(PluginInteractionEventEnvelope<?> envelope, Throwable error) {
        try {
            eventProvider.sendToDlq(
                toProductInteractionEnvelope(envelope),
                "plugin_interaction_failed: " + error.getMessage()
            );
            return Promise.complete();
        } catch (Exception dlqError) {
            return Promise.ofException(dlqError);
        }
    }

    private com.ghatana.kernel.interaction.ProductInteractionEventEnvelope<?> toProductInteractionEnvelope(
            PluginInteractionEventEnvelope<?> envelope) {
        return new com.ghatana.kernel.interaction.ProductInteractionEventEnvelope<>(
            envelope.schemaVersion(),
            envelope.eventId(),
            envelope.contractId(),
            envelope.contractVersion(),
            envelope.pluginId(),
            envelope.consumerPluginIds(),
            envelope.pluginUnitId(),
            envelope.tenantId(),
            envelope.workspaceId(),
            envelope.runId(),
            envelope.correlationId(),
            envelope.publishedAt(),
            envelope.policyContext(),
            envelope.topic(),
            envelope.payload()
        );
    }

    private ProductInteractionRequest<Object> toEvidenceRequest(PluginInteractionEventEnvelope<?> envelope) {
        return new ProductInteractionRequest<>(
            envelope.schemaVersion(),
            envelope.eventId(),
            envelope.contractId(),
            envelope.contractVersion(),
            envelope.pluginId(),
            envelope.consumerPluginIds().isEmpty() ? "plugin-consumer" : envelope.consumerPluginIds().get(0),
            envelope.pluginUnitId(),
            envelope.tenantId(),
            envelope.workspaceId(),
            envelope.runId(),
            envelope.correlationId(),
            envelope.publishedAt(),
            envelope.policyContext(),
            envelope.payload()
        );
    }

    /**
     * Metrics for plugin interactions.
     */
    public static final class PluginInteractionMetrics {
        private final long totalPublished;
        private final long successCount;
        private final long failureCount;
        private final long dlqCount;

        public PluginInteractionMetrics(long totalPublished, long successCount, long failureCount, long dlqCount) {
            this.totalPublished = totalPublished;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.dlqCount = dlqCount;
        }

        public long totalPublished() { return totalPublished; }
        public long successCount() { return successCount; }
        public long failureCount() { return failureCount; }
        public long dlqCount() { return dlqCount; }

        public PluginInteractionMetrics incrementSuccessCount() {
            return new PluginInteractionMetrics(
                totalPublished + 1,
                successCount + 1,
                failureCount,
                dlqCount
            );
        }

        public PluginInteractionMetrics incrementFailureCount() {
            return new PluginInteractionMetrics(
                totalPublished + 1,
                successCount,
                failureCount + 1,
                dlqCount
            );
        }

        public PluginInteractionMetrics incrementTimeoutCount() {
            return incrementFailureCount();
        }

        public PluginInteractionMetrics incrementBackpressureCount() {
            return incrementFailureCount();
        }

        public PluginInteractionMetrics incrementCircuitBreakerCount() {
            return incrementFailureCount();
        }
    }

    /**
     * Policy evaluation result.
     */
    public static final class PluginInteractionPolicyDecision {
        private final boolean allowed;
        private final String reasonCode;

        public PluginInteractionPolicyDecision(boolean allowed, String reasonCode) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
        }

        public boolean allowed() { return allowed; }
        public String reasonCode() { return reasonCode; }
    }

    /**
     * Exception thrown when plugin interaction is denied by policy.
     */
    public static final class PluginInteractionPolicyDeniedException extends RuntimeException {
        private final String contractId;
        private final String reasonCode;

        public PluginInteractionPolicyDeniedException(String contractId, String reasonCode) {
            super("Plugin interaction denied for contract " + contractId + ": " + reasonCode);
            this.contractId = contractId;
            this.reasonCode = reasonCode;
        }

        public String contractId() { return contractId; }
        public String reasonCode() { return reasonCode; }
    }

    /**
     * Exception thrown when backpressure limit is reached.
     */
    public static final class PluginInteractionBackpressureException extends RuntimeException {
        public PluginInteractionBackpressureException(String message) {
            super(message);
        }
    }

    /**
     * Simple circuit breaker for plugin subscriber isolation.
     */
    private static final class PluginCircuitBreaker {
        private final int failureThreshold;
        private final Duration openTimeout;
        private int consecutiveFailures;
        private long lastFailureTime;
        private State state;

        private enum State { CLOSED, OPEN, HALF_OPEN }

        PluginCircuitBreaker(int failureThreshold, Duration openTimeout) {
            this.failureThreshold = failureThreshold;
            this.openTimeout = openTimeout;
            this.state = State.CLOSED;
            this.consecutiveFailures = 0;
        }

        boolean isOpen() {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime > openTimeout.toMillis()) {
                    state = State.HALF_OPEN;
                    consecutiveFailures = 0;
                    return false;
                }
                return true;
            }
            return false;
        }

        void recordSuccess() {
            consecutiveFailures = 0;
            state = State.CLOSED;
        }

        void recordFailure() {
            consecutiveFailures++;
            lastFailureTime = System.currentTimeMillis();
            if (consecutiveFailures >= failureThreshold) {
                state = State.OPEN;
            }
        }
    }
}
