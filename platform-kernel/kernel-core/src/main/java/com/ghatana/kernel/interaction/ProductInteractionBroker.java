package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Governed broker for product-to-product request/response interactions.
 *
 * <p>The broker owns fail-closed request validation, contract version checks, policy
 * evaluation, handler dispatch, timeout handling, idempotent replay, evidence
 * persistence, and execution counters. Product code registers handlers through the
 * public SPI and consumers never call another product's implementation directly.</p>
 *
 * @doc.type class
 * @doc.purpose Broker product interaction requests through Kernel policy and evidence controls
 * @doc.layer kernel
 * @doc.pattern Broker
 */
public final class ProductInteractionBroker implements AutoCloseable {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0.0";
    private static final Set<String> DEFAULT_SUPPORTED_CONTRACT_VERSIONS = Set.of("1.0.0");

    private final Map<String, ProductInteractionHandler<?, ?>> handlersByContractId;
    private final Map<String, ProductInteractionOutcome<?>> completedByInteraction;
    private final Set<String> supportedContractVersions;
    private final ProductInteractionPolicyEvaluator policyEvaluator;
    private final ProductInteractionEvidenceWriter evidenceWriter;
    private final Duration requestTimeout;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong requested;
    private final AtomicLong succeeded;
    private final AtomicLong blocked;
    private final AtomicLong timedOut;
    private final AtomicLong evidenceFailures;
    private final AtomicLong totalLatencyMs;
    private final AtomicLong maxLatencyMs;

    private ProductInteractionBroker(Builder builder) {
        this.handlersByContractId = new ConcurrentHashMap<>(builder.handlersByContractId);
        this.completedByInteraction = new ConcurrentHashMap<>();
        this.supportedContractVersions = Set.copyOf(builder.supportedContractVersions);
        this.policyEvaluator = builder.policyEvaluator;
        this.evidenceWriter = builder.evidenceWriter;
        this.requestTimeout = builder.requestTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new BrokerThreadFactory());
        this.requested = new AtomicLong();
        this.succeeded = new AtomicLong();
        this.blocked = new AtomicLong();
        this.timedOut = new AtomicLong();
        this.evidenceFailures = new AtomicLong();
        this.totalLatencyMs = new AtomicLong();
        this.maxLatencyMs = new AtomicLong();
    }

    public static Builder builder() {
        return new Builder();
    }

    public <Req, Res> Promise<ProductInteractionOutcome<Res>> execute(ProductInteractionRequest<Req> request) {
        Objects.requireNonNull(request, "request must not be null");
        requested.incrementAndGet();
        long startedNanos = System.nanoTime();

        String interactionKey = interactionKey(request);
        @SuppressWarnings("unchecked")
        ProductInteractionOutcome<Res> cached = (ProductInteractionOutcome<Res>) completedByInteraction.get(interactionKey);
        if (cached != null) {
            return Promise.of(cached);
        }

        Optional<ProductInteractionOutcome<Res>> preflightFailure = validatePreflight(request);
        if (preflightFailure.isPresent()) {
            return complete(request, interactionKey, preflightFailure.get(), startedNanos);
        }

        @SuppressWarnings("unchecked")
        ProductInteractionHandler<Req, Res> handler = (ProductInteractionHandler<Req, Res>) handlersByContractId.get(request.contractId());
        if (handler == null) {
            return complete(request, interactionKey, blocked(request, "product_interaction.handler_unavailable"), startedNanos);
        }
        if (!Objects.equals(handler.schemaVersion(), request.contractVersion())) {
            return complete(request, interactionKey, blocked(request, "product_interaction.handler_version_mismatch"), startedNanos);
        }
        if (!handler.requestType().isInstance(request.payload())) {
            return complete(request, interactionKey, blocked(request, "product_interaction.invalid_payload"), startedNanos);
        }

        return Promise.ofCallback(cb -> {
            AtomicBoolean completed = new AtomicBoolean(false);
            long timeoutMs = Math.max(1L, requestTimeout.toMillis());

            scheduler.schedule(() -> {
                if (completed.compareAndSet(false, true)) {
                    ProductInteractionOutcome<Res> timeoutOutcome = blocked(request, "product_interaction.timeout");
                    timedOut.incrementAndGet();
                    complete(request, interactionKey, timeoutOutcome, startedNanos).whenComplete((outcome, error) -> {
                        if (error != null) {
                            cb.setException(error);
                            return;
                        }
                        cb.set(outcome);
                    });
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);

            handler.handle(request).whenComplete((outcome, error) -> {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                if (error != null) {
                    complete(request, interactionKey, ProductInteractionBroker.<Req, Res>blocked(
                                    request,
                                    "product_interaction.runtime_error"),
                            startedNanos)
                            .whenComplete((failedOutcome, evidenceError) -> {
                                if (evidenceError != null) {
                                    cb.setException(evidenceError);
                                    return;
                                }
                                cb.set(failedOutcome);
                            });
                    return;
                }
                complete(request, interactionKey, normalizeProviderOutcome(request, outcome), startedNanos)
                        .whenComplete((finalOutcome, evidenceError) -> {
                            if (evidenceError != null) {
                                cb.setException(evidenceError);
                                return;
                            }
                            cb.set(finalOutcome);
                        });
            });
        });
    }

    public ProductInteractionBrokerMetrics metrics() {
        return new ProductInteractionBrokerMetrics(
                requested.get(),
                succeeded.get(),
                blocked.get(),
                timedOut.get(),
                evidenceFailures.get(),
                totalLatencyMs.get(),
                maxLatencyMs.get());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private <Req, Res> Optional<ProductInteractionOutcome<Res>> validatePreflight(ProductInteractionRequest<Req> request) {
        if (!SUPPORTED_SCHEMA_VERSION.equals(request.schemaVersion())) {
            return Optional.of(blocked(request, "product_interaction.schema_version_unsupported"));
        }
        if (!supportedContractVersions.contains(request.contractVersion())) {
            return Optional.of(blocked(request, "product_interaction.contract_version_unsupported"));
        }
        if (isBlank(request.interactionId())) {
            return Optional.of(blocked(request, "product_interaction.interaction_id_required"));
        }
        if (isBlank(request.contractId())) {
            return Optional.of(blocked(request, "product_interaction.contract_required"));
        }
        if (isBlank(request.providerProductId())) {
            return Optional.of(blocked(request, "product_interaction.provider_required"));
        }
        if (isBlank(request.consumerProductId())) {
            return Optional.of(blocked(request, "product_interaction.consumer_required"));
        }
        if (isBlank(request.productUnitId())) {
            return Optional.of(blocked(request, "product_interaction.product_unit_required"));
        }
        if (isBlank(request.tenantId())) {
            return Optional.of(blocked(request, "product_interaction.tenant_required"));
        }
        if (isBlank(request.workspaceId())) {
            return Optional.of(blocked(request, "product_interaction.workspace_required"));
        }
        if (isBlank(request.runId())) {
            return Optional.of(blocked(request, "product_interaction.run_required"));
        }
        if (isBlank(request.correlationId())) {
            return Optional.of(blocked(request, "product_interaction.correlation_required"));
        }
        if (request.requestedAt() == null) {
            return Optional.of(blocked(request, "product_interaction.request_time_required"));
        }
        if (request.policyContext() == null) {
            return Optional.of(blocked(request, "product_interaction.policy_context_required"));
        }

        ProductInteractionPolicyDecision policyDecision = policyEvaluator.evaluate(request);
        if (!policyDecision.allowed()) {
            return Optional.of(blocked(request, policyDecision.reasonCode()));
        }
        return Optional.empty();
    }

    private <Req, Res> Promise<ProductInteractionOutcome<Res>> complete(
            ProductInteractionRequest<Req> request,
            String interactionKey,
            ProductInteractionOutcome<Res> outcome,
            long startedNanos) {
        ProductInteractionOutcome<Res> terminalOutcome = normalizeBrokerOutcome(request, outcome);
        return Promise.ofCallback(cb -> evidenceWriter.write(request, terminalOutcome).whenComplete(($, error) -> {
            ProductInteractionOutcome<Res> finalOutcome = terminalOutcome;
            if (error != null) {
                evidenceFailures.incrementAndGet();
                finalOutcome = blocked(request, "product_interaction.evidence_persistence_failed");
            }
            completedByInteraction.put(interactionKey, finalOutcome);
            recordMetrics(finalOutcome, startedNanos);
            cb.set(finalOutcome);
        }));
    }

    private <Res> ProductInteractionOutcome<Res> normalizeProviderOutcome(
            ProductInteractionRequest<?> request,
            ProductInteractionOutcome<Res> outcome) {
        if (outcome == null) {
            return blocked(request, "product_interaction.outcome_required");
        }
        if (!Objects.equals(outcome.interactionId(), request.interactionId())) {
            return blocked(request, "product_interaction.outcome_interaction_mismatch");
        }
        if (outcome.status() == ProductInteractionStatus.SUCCEEDED && outcome.evidenceRefs().isEmpty()) {
            return blocked(request, "product_interaction.evidence_required");
        }
        return outcome;
    }

    private <Res> ProductInteractionOutcome<Res> normalizeBrokerOutcome(
            ProductInteractionRequest<?> request,
            ProductInteractionOutcome<Res> outcome) {
        if (outcome.reasonCode() == null && outcome.status() != ProductInteractionStatus.SUCCEEDED) {
            return blocked(request, "product_interaction.reason_required");
        }
        return outcome;
    }

    private void recordMetrics(ProductInteractionOutcome<?> outcome, long startedNanos) {
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        totalLatencyMs.addAndGet(latencyMs);
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
        if (outcome.status() == ProductInteractionStatus.SUCCEEDED) {
            succeeded.incrementAndGet();
            return;
        }
        blocked.incrementAndGet();
    }

    private static <Req, Res> ProductInteractionOutcome<Res> blocked(ProductInteractionRequest<Req> request, String reasonCode) {
        String interactionId = request.interactionId() == null ? "unknown" : request.interactionId();
        return ProductInteractionOutcome.failed(
                interactionId,
                ProductInteractionStatus.BLOCKED,
                reasonCode,
                List.of());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String interactionKey(ProductInteractionRequest<?> request) {
        return request.contractId() + "::" + request.interactionId() + "::" + request.tenantId() + "::" + request.workspaceId();
    }

    public static final class Builder {
        private final Map<String, ProductInteractionHandler<?, ?>> handlersByContractId = new ConcurrentHashMap<>();
        private Set<String> supportedContractVersions = DEFAULT_SUPPORTED_CONTRACT_VERSIONS;
        private ProductInteractionPolicyEvaluator policyEvaluator = ProductInteractionPolicyEvaluator.defaultEvaluator();
        private ProductInteractionEvidenceWriter evidenceWriter = ProductInteractionEvidenceWriter.noop();
        private Duration requestTimeout = Duration.ofSeconds(2);

        public Builder register(ProductInteractionHandler<?, ?> handler) {
            Objects.requireNonNull(handler, "handler must not be null");
            String contractId = handler.contractId();
            if (isBlank(contractId)) {
                throw new IllegalArgumentException("handler contractId must not be blank");
            }
            ProductInteractionHandler<?, ?> previous = handlersByContractId.putIfAbsent(contractId, handler);
            if (previous != null) {
                throw new IllegalArgumentException("handler already registered for contractId " + contractId);
            }
            return this;
        }

        public Builder supportedContractVersions(Set<String> versions) {
            Objects.requireNonNull(versions, "versions must not be null");
            if (versions.isEmpty() || versions.stream().anyMatch(ProductInteractionBroker::isBlank)) {
                throw new IllegalArgumentException("supported contract versions must contain non-blank values");
            }
            this.supportedContractVersions = Set.copyOf(versions);
            return this;
        }

        public Builder policyEvaluator(ProductInteractionPolicyEvaluator evaluator) {
            this.policyEvaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
            return this;
        }

        public Builder evidenceWriter(ProductInteractionEvidenceWriter writer) {
            this.evidenceWriter = Objects.requireNonNull(writer, "writer must not be null");
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("request timeout must be positive");
            }
            this.requestTimeout = timeout;
            return this;
        }

        public ProductInteractionBroker build() {
            return new ProductInteractionBroker(this);
        }
    }

    private static final class BrokerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "product-interaction-broker-timeout");
            thread.setDaemon(true);
            return thread;
        }
    }
}
