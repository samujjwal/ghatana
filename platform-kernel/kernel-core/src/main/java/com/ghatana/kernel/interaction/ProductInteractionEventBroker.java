package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Governed broker for product-to-product event interactions.
 *
 * @doc.type class
 * @doc.purpose Publish product interaction events through Kernel policy, subscriber dispatch, and evidence controls
 * @doc.layer kernel
 * @doc.pattern Broker
 */
public final class ProductInteractionEventBroker {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0.0";
    private static final Set<String> DEFAULT_SUPPORTED_CONTRACT_VERSIONS = Set.of("1.0.0");

    private final Map<String, List<ProductInteractionEventHandler<?>>> subscribersByTopic;
    private final Set<String> supportedContractVersions;
    private final ProductInteractionEventPolicyEvaluator policyEvaluator;
    private final ProductInteractionEventEvidenceWriter evidenceWriter;
    private final AtomicLong published;
    private final AtomicLong delivered;
    private final AtomicLong blocked;
    private final AtomicLong evidenceFailures;
    private final AtomicLong totalLatencyMs;
    private final AtomicLong maxLatencyMs;

    private ProductInteractionEventBroker(Builder builder) {
        this.subscribersByTopic = copySubscribers(builder.subscribersByTopic);
        this.supportedContractVersions = Set.copyOf(builder.supportedContractVersions);
        this.policyEvaluator = builder.policyEvaluator;
        this.evidenceWriter = builder.evidenceWriter;
        this.published = new AtomicLong();
        this.delivered = new AtomicLong();
        this.blocked = new AtomicLong();
        this.evidenceFailures = new AtomicLong();
        this.totalLatencyMs = new AtomicLong();
        this.maxLatencyMs = new AtomicLong();
    }

    public static Builder builder() {
        return new Builder();
    }

    public <Event> Promise<ProductInteractionEventOutcome> publish(ProductInteractionEventEnvelope<Event> envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        long startedAtNanos = System.nanoTime();
        published.incrementAndGet();

        Optional<ProductInteractionEventOutcome> preflightFailure = validatePreflight(envelope);
        if (preflightFailure.isPresent()) {
            return complete(envelope, preflightFailure.get(), startedAtNanos);
        }

        List<ProductInteractionEventHandler<?>> subscribers = subscribersByTopic.getOrDefault(envelope.topic(), List.of());
        if (subscribers.isEmpty()) {
            return complete(envelope, ProductInteractionEventOutcome.blocked(
                    envelope.eventId(),
                    "product_interaction.event_handler_unavailable",
                    List.of()), startedAtNanos);
        }

        return Promise.ofCallback(cb -> dispatchSequentially(envelope, subscribers, 0, new ArrayList<>())
                .whenComplete((subscriberIds, error) -> {
                    ProductInteractionEventOutcome outcome;
                    if (error != null) {
                        outcome = ProductInteractionEventOutcome.blocked(
                                envelope.eventId(),
                                "product_interaction.event_delivery_failed",
                                List.of());
                    } else {
                        outcome = ProductInteractionEventOutcome.succeeded(
                                envelope.eventId(),
                                List.of("kernel://interaction-events/" + envelope.eventId()),
                                subscriberIds);
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
                maxLatencyMs.get());
    }

    private <Event> Promise<List<String>> dispatchSequentially(
            ProductInteractionEventEnvelope<Event> envelope,
            List<ProductInteractionEventHandler<?>> subscribers,
            int index,
            List<String> deliveredSubscriberIds) {
        if (index >= subscribers.size()) {
            return Promise.of(deliveredSubscriberIds);
        }

        @SuppressWarnings("unchecked")
        ProductInteractionEventHandler<Event> subscriber = (ProductInteractionEventHandler<Event>) subscribers.get(index);
        if (!subscriber.eventType().isInstance(envelope.payload())) {
            return Promise.ofException(new IllegalArgumentException("event payload does not match subscriber type"));
        }
        return subscriber.handle(envelope)
                .map($ -> {
                    delivered.incrementAndGet();
                    deliveredSubscriberIds.add(subscriber.subscriberId());
                    return deliveredSubscriberIds;
                })
                .then(ids -> dispatchSequentially(envelope, subscribers, index + 1, ids));
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

        public ProductInteractionEventBroker build() {
            return new ProductInteractionEventBroker(this);
        }
    }
}
