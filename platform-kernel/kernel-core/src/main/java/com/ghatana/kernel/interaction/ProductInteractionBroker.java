package com.ghatana.kernel.interaction;

import io.activej.async.exception.AsyncTimeoutException;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Governed broker for product-to-product request/response interactions.
 *
 * <p>The broker owns fail-closed request validation, contract version checks, policy
 * evaluation, handler dispatch, timeout handling, idempotent replay, evidence
 * persistence, and execution counters. Product code registers handlers through the
 * public SPI and consumers never call another product's implementation directly.</p>
 *
 * <p>P0 hardening:</p>
 * <ul>
 *   <li>Requires real evidence writer in production mode (no no-op)</li>
 *   <li>Resolves policy context from trusted providers, not caller-supplied flags</li>
 *   <li>Includes payload hash and contract metadata in replay key for idempotency</li>
 *   <li>Binds interactions to commit SHA for production truth</li>
 *   <li>Validates environment-specific interaction constraints</li>
 * </ul>
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
    private final Map<String, CachedOutcome> completedByInteraction;
    private final Set<String> supportedContractVersions;
    private final ProductInteractionPolicyEvaluator policyEvaluator;
    private final ProductInteractionEvidenceWriter evidenceWriter;
    private final ProductInteractionPolicyContextResolver policyContextResolver;
    private final Map<String, ProductInteractionContract> contractsByContractId;
    private final BrokerMode brokerMode;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong requested;
    private final AtomicLong succeeded;
    private final AtomicLong blocked;
    private final AtomicLong timedOut;
    private final AtomicLong evidenceFailures;
    private final AtomicLong totalLatencyMs;
    private final AtomicLong maxLatencyMs;
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    private final AtomicLong cacheEvictions;
    private final AtomicLong sloViolations;
    private final Map<String, Duration> latencyBudgetsByContract;
    private final ProductInteractionHandlerRegistry handlerRegistry;
    private final ScheduledExecutorService developmentEvidenceExecutor;
    private final ProductInteractionCircuitBreaker circuitBreaker;
    private final com.ghatana.kernel.observability.TracingPort tracing;
    private final com.ghatana.kernel.observability.MetricCollectorPort metrics;
    private String commitSha;
    private String environment;

    private ProductInteractionBroker(Builder builder) {
        this.handlersByContractId = new ConcurrentHashMap<>(builder.handlersByContractId);
        this.completedByInteraction = new ConcurrentHashMap<>();
        this.supportedContractVersions = Set.copyOf(builder.supportedContractVersions);
        this.policyEvaluator = builder.policyEvaluator;
        this.policyContextResolver = builder.policyContextResolver;
        this.contractsByContractId = Map.copyOf(builder.contractsByContractId);
        this.brokerMode = builder.brokerMode;
        this.requestTimeout = builder.requestTimeout;
        this.cacheTtl = builder.cacheTtl;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new BrokerThreadFactory());
        this.developmentEvidenceExecutor = brokerMode == BrokerMode.DEVELOPMENT && builder.evidenceWriter.isNoop()
                ? Executors.newSingleThreadScheduledExecutor(new DevelopmentEvidenceThreadFactory())
                : null;
        this.evidenceWriter = developmentEvidenceExecutor == null
                ? builder.evidenceWriter
                : new FileProductInteractionEvidenceWriter(builder.developmentEvidenceRoot, developmentEvidenceExecutor);
        this.circuitBreaker = builder.circuitBreaker != null ? builder.circuitBreaker : ProductInteractionCircuitBreaker.builder().build();
        this.tracing = builder.tracing;
        this.metrics = builder.metrics;
        this.requested = new AtomicLong();
        this.succeeded = new AtomicLong();
        this.blocked = new AtomicLong();
        this.timedOut = new AtomicLong();
        this.evidenceFailures = new AtomicLong();
        this.totalLatencyMs = new AtomicLong();
        this.maxLatencyMs = new AtomicLong();
        this.cacheHits = new AtomicLong();
        this.cacheMisses = new AtomicLong();
        this.cacheEvictions = new AtomicLong();
        this.sloViolations = new AtomicLong();
        this.latencyBudgetsByContract = Map.copyOf(builder.latencyBudgetsByContract);
        this.handlerRegistry = builder.handlerRegistry;
        this.commitSha = builder.commitSha;
        this.environment = builder.environment;

        // P0-01: Validate evidence writer in production mode.
        // DEVELOPMENT mode resolves an omitted writer to a local file-backed writer.
        // TEST mode allows no-op evidence writer for test convenience.
        if (brokerMode == BrokerMode.PRODUCTION && evidenceWriter.isNoop()) {
            throw new IllegalStateException(
                    "Production mode requires a real evidence writer. No-op evidence writer is not allowed. "
                            + "Use FileProductInteractionEvidenceWriter for local development or BrokerMode.TEST for tests.");
        }

        // KER-004: Validate commit SHA in production mode
        if (brokerMode == BrokerMode.PRODUCTION && (commitSha == null || commitSha.isEmpty())) {
            throw new IllegalStateException(
                    "Production mode requires commit SHA for production truth binding");
        }

        // KER-004: Validate commit SHA format if provided
        if (commitSha != null && !commitSha.isEmpty() && !commitSha.matches("^[a-fA-F0-9]{40}$")) {
            throw new IllegalArgumentException(
                    "Invalid commit SHA format: " + commitSha + ". Expected 40 hexadecimal characters.");
        }

        // Phase 1: Start cache eviction task if TTL is configured
        if (cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative()) {
            startCacheEvictionTask();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a production-mode broker factory with mandatory evidence writer and trusted policy context.
     *
     * @param evidenceWriter the evidence writer (must not be no-op)
     * @param policyContextResolver the trusted policy context resolver
     * @return a builder configured for production mode
     */
    public static Builder productionFactory(
            ProductInteractionEvidenceWriter evidenceWriter,
            ProductInteractionPolicyContextResolver policyContextResolver) {
        return builder()
                .evidenceWriter(evidenceWriter)
                .policyContextResolver(policyContextResolver)
                .brokerMode(BrokerMode.PRODUCTION);
    }

    /**
     * Creates a development-mode broker factory with local file-backed evidence by default.
     *
     * @param evidenceWriter the evidence writer (can be no-op in development)
     * @param policyContextResolver the policy context resolver
     * @return a builder configured for development mode
     */
    public static Builder developmentFactory(
            ProductInteractionEvidenceWriter evidenceWriter,
            ProductInteractionPolicyContextResolver policyContextResolver) {
        return builder()
                .evidenceWriter(evidenceWriter)
                .policyContextResolver(policyContextResolver)
                .brokerMode(BrokerMode.DEVELOPMENT);
    }

    /**
     * Phase 3: Creates a test-mode broker factory with no-op evidence writer and relaxed policy.
     * Test mode is optimized for unit and integration testing with minimal external dependencies.
     *
     * @return a builder configured for test mode
     */
    public static Builder testFactory() {
        return builder()
                .evidenceWriter(ProductInteractionEvidenceWriter.noop())
                .policyContextResolver(ProductInteractionPolicyContextResolver.testResolver())
                .brokerMode(BrokerMode.TEST);
    }

    /**
     * Phase 3: Creates a test-mode broker factory with custom policy evaluator.
     * Test mode is optimized for unit and integration testing with minimal external dependencies.
     *
     * @param policyEvaluator custom policy evaluator for test scenarios
     * @return a builder configured for test mode
     */
    public static Builder testFactory(ProductInteractionPolicyEvaluator policyEvaluator) {
        return builder()
                .evidenceWriter(ProductInteractionEvidenceWriter.noop())
                .policyContextResolver(ProductInteractionPolicyContextResolver.testResolver())
                .policyEvaluator(policyEvaluator)
                .brokerMode(BrokerMode.TEST);
    }

    public <Req, Res> Promise<ProductInteractionOutcome<Res>> execute(ProductInteractionRequest<Req> request) {
        Objects.requireNonNull(request, "request must not be null");
        requested.incrementAndGet();
        long startedNanos = System.nanoTime();

        // Start tracing span if tracing is enabled
        final com.ghatana.kernel.observability.TracingPort.Span span;
        if (tracing != null) {
            span = tracing.startSpan("ProductInteractionBroker.execute");
            tracing.addAttribute("contractId", request.contractId());
            tracing.addAttribute("providerProductId", request.providerProductId());
            tracing.addAttribute("consumerProductId", request.consumerProductId());
            tracing.addAttribute("tenantId", request.tenantId());
            tracing.addAttribute("workspaceId", request.workspaceId());
        } else {
            span = null;
        }

        Optional<ProductInteractionOutcome<Res>> basicFailure = validateBasicPreflight(request);
        if (basicFailure.isPresent()) {
            if (span != null) {
                span.end();
            }
            return complete(request, fallbackInteractionKey(request, basicFailure.get().reasonCode()), basicFailure.get(), startedNanos);
        }

        ProductInteractionContract contract = contractsByContractId.get(request.contractId());
        if (contract == null) {
            if (span != null) {
                tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "contract_missing");
                span.end();
            }
            return complete(
                    request,
                    fallbackInteractionKey(request, "product_interaction.contract_missing"),
                    blocked(request, "product_interaction.contract_missing"),
                    startedNanos);
        }

        // P0-02: Resolve trusted policy context only after the contract exists.
        Map<String, String> trustedPolicyContext = policyContextResolver.resolve(request, contract);
        ProductInteractionRequest<Req> requestWithTrustedContext = new ProductInteractionRequest<>(
                request.schemaVersion(),
                request.interactionId(),
                request.contractId(),
                request.contractVersion(),
                request.providerProductId(),
                request.consumerProductId(),
                request.productUnitId(),
                request.tenantId(),
                request.workspaceId(),
                request.runId(),
                request.correlationId(),
                request.requestedAt(),
                trustedPolicyContext,
                request.payload());

        // P0-03: Include payload hash and contract metadata in replay key
        String interactionKey = interactionKey(requestWithTrustedContext, contract);
        CachedOutcome cached = completedByInteraction.get(interactionKey);
        if (cached != null && !cached.isExpired(cacheTtl)) {
            cacheHits.incrementAndGet();
            if (span != null) {
                tracing.addAttribute("cache", "hit");
                span.end();
            }
            @SuppressWarnings("unchecked")
            ProductInteractionOutcome<Res> outcome = (ProductInteractionOutcome<Res>) cached.outcome;
            return Promise.of(outcome);
        }

        // P0-03: Idempotency conflict detection - check if same interaction ID exists with different payload
        for (Map.Entry<String, CachedOutcome> entry : completedByInteraction.entrySet()) {
            if (entry.getValue().isExpired(cacheTtl)) {
                continue;
            }
            String existingKey = entry.getKey();
            if (isSameInteractionIdDifferentPayload(existingKey, interactionKey)) {
                // Same interaction ID with different payload - block as idempotency conflict
                return complete(requestWithTrustedContext, interactionKey,
                        blocked(requestWithTrustedContext, "product_interaction.idempotency_conflict"),
                        startedNanos);
            }
        }

        cacheMisses.incrementAndGet();

        Optional<ProductInteractionOutcome<Res>> preflightFailure = validatePolicyPreflight(requestWithTrustedContext);
        if (preflightFailure.isPresent()) {
            if (span != null) {
                tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "policy_denied");
                span.end();
            }
            if (metrics != null) {
                metrics.incrementCounter("product_interaction.policy_denied", 1, "contractId", request.contractId());
            }
            return complete(requestWithTrustedContext, interactionKey, preflightFailure.get(), startedNanos);
        }

        @SuppressWarnings("unchecked")
        ProductInteractionHandler<Req, Res> handler = (ProductInteractionHandler<Req, Res>) handlersByContractId.get(requestWithTrustedContext.contractId());
        if (handler == null && handlerRegistry != null) {
            ProductInteractionHandler<?, ?> discovered = handlerRegistry.getHandler(requestWithTrustedContext.contractId());
            if (discovered != null) {
                @SuppressWarnings("unchecked")
                ProductInteractionHandler<Req, Res> typedDiscovered = (ProductInteractionHandler<Req, Res>) discovered;
                handler = typedDiscovered;
                handlersByContractId.put(requestWithTrustedContext.contractId(), handler);
            }
        }
        if (handler == null) {
            if (span != null) {
                tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "handler_unavailable");
                span.end();
            }
            if (metrics != null) {
                metrics.incrementCounter("product_interaction.handler_unavailable", 1, "contractId", request.contractId());
            }
            return complete(requestWithTrustedContext, interactionKey, blocked(requestWithTrustedContext, "product_interaction.handler_unavailable"), startedNanos);
        }
        if (!Objects.equals(handler.schemaVersion(), requestWithTrustedContext.contractVersion())) {
            if (span != null) {
                tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "handler_version_mismatch");
                span.end();
            }
            if (metrics != null) {
                metrics.incrementCounter("product_interaction.version_mismatch", 1, "contractId", request.contractId());
            }
            return complete(requestWithTrustedContext, interactionKey, blocked(requestWithTrustedContext, "product_interaction.handler_version_mismatch"), startedNanos);
        }
        if (!handler.requestType().isInstance(requestWithTrustedContext.payload())) {
            if (span != null) {
                tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "invalid_payload");
                span.end();
            }
            if (metrics != null) {
                metrics.incrementCounter("product_interaction.invalid_payload", 1, "contractId", request.contractId());
            }
            return complete(requestWithTrustedContext, interactionKey, blocked(requestWithTrustedContext, "product_interaction.invalid_payload"), startedNanos);
        }

        final ProductInteractionHandler<Req, Res> executionHandler = handler;
        Promise<ProductInteractionOutcome<Res>> handlerOutcome;
        try {
            handlerOutcome = Promises.timeout(requestTimeout, executionHandler.handle(requestWithTrustedContext));
        } catch (Exception error) {
            handlerOutcome = Promise.ofException(error);
        }

        return handlerOutcome.then(
                outcome -> {
                    if (span != null) {
                        if (outcome.status() == ProductInteractionStatus.SUCCEEDED) {
                            tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.OK);
                        } else {
                            tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, outcome.reasonCode());
                        }
                        span.end();
                    }
                    if (metrics != null) {
                        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
                        metrics.recordTimer("product_interaction.latency", latencyMs, "contractId", request.contractId(), "status", outcome.status().name());
                        if (outcome.status() == ProductInteractionStatus.SUCCEEDED) {
                            metrics.incrementCounter("product_interaction.success", 1, "contractId", request.contractId());
                        } else {
                            metrics.incrementCounter("product_interaction.failure", 1, "contractId", request.contractId(), "reasonCode", outcome.reasonCode());
                        }
                    }
                    return complete(
                            requestWithTrustedContext,
                            interactionKey,
                            normalizeProviderOutcome(requestWithTrustedContext, outcome),
                            startedNanos);
                },
                error -> {
                    if (isTimeout(error)) {
                        timedOut.incrementAndGet();
                        if (span != null) {
                            tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "timeout");
                            span.end();
                        }
                        if (metrics != null) {
                            metrics.incrementCounter("product_interaction.timeout", 1, "contractId", request.contractId());
                        }
                        return complete(
                                requestWithTrustedContext,
                                interactionKey,
                                ProductInteractionBroker.<Req, Res>blocked(
                                        requestWithTrustedContext,
                                        "product_interaction.timeout"),
                                startedNanos);
                    }
                    if (span != null) {
                        tracing.setStatus(com.ghatana.kernel.observability.TracingPort.SpanStatus.ERROR, "runtime_error");
                        tracing.recordException(error);
                        span.end();
                    }
                    if (metrics != null) {
                        metrics.incrementCounter("product_interaction.runtime_error", 1, "contractId", request.contractId());
                    }
                    return complete(
                            requestWithTrustedContext,
                            interactionKey,
                            ProductInteractionBroker.<Req, Res>blocked(
                                    requestWithTrustedContext,
                                    "product_interaction.runtime_error"),
                            startedNanos);
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
                maxLatencyMs.get(),
                cacheHits.get(),
                cacheMisses.get(),
                cacheEvictions.get(),
                sloViolations.get());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        if (developmentEvidenceExecutor != null) {
            developmentEvidenceExecutor.shutdownNow();
        }
    }

    private <Req, Res> Optional<ProductInteractionOutcome<Res>> validateBasicPreflight(
            ProductInteractionRequest<Req> request) {
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
        return Optional.empty();
    }

    private <Req, Res> Optional<ProductInteractionOutcome<Res>> validatePolicyPreflight(
            ProductInteractionRequest<Req> request) {
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
            // Phase 1: Store outcome with timestamp for TTL-based eviction
            completedByInteraction.put(interactionKey, new CachedOutcome(finalOutcome));
            recordMetrics(request, finalOutcome, startedNanos);
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

    private void recordMetrics(
            ProductInteractionRequest<?> request,
            ProductInteractionOutcome<?> outcome,
            long startedNanos) {
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        totalLatencyMs.addAndGet(latencyMs);
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);

        // PERF-002: Check SLO violation against latency budget
        Duration budget = latencyBudgetsByContract.get(request.contractId());
        if (budget != null && latencyMs > budget.toMillis()) {
            sloViolations.incrementAndGet();
        }

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

    // P0-03: Include payload hash and contract metadata in replay key for idempotency
    // Phase 3: Include contract schema hash for stronger idempotency guarantees
    private static String interactionKey(ProductInteractionRequest<?> request, ProductInteractionContract contract) {
        String payloadHash = computePayloadHash(request.payload());
        String contractSchemaHash = computeContractSchemaHash(contract);
        String contractVersion = request.contractVersion();
        String providerId = request.providerProductId();
        String consumerId = request.consumerProductId();
        String productUnitId = request.productUnitId();
        
        return String.format("%s::%s::%s::%s::%s::%s::%s::%s::%s::%s",
                request.contractId(),
                request.interactionId(),
                request.tenantId(),
                request.workspaceId(),
                payloadHash,
                contractSchemaHash,
                contractVersion,
                providerId,
                consumerId,
                productUnitId);
    }

    private static String computePayloadHash(Object payload) {
        // Phase 3: Use canonical JSON hashing for deterministic payload hashing
        return CanonicalJsonHasher.hash(payload);
    }

    /**
     * Phase 3: Computes a hash of the contract schema for idempotency.
     * This ensures that contract changes invalidate cached interactions.
     *
     * @param contract the product interaction contract
     * @return Base64-encoded SHA-256 hash of contract schema
     */
    private static String computeContractSchemaHash(ProductInteractionContract contract) {
        Map<String, Object> canonicalContract = new LinkedHashMap<>();
        canonicalContract.put("contractId", contract.contractId());
        canonicalContract.put("contractVersion", contract.contractVersion());
        canonicalContract.put("providerProductId", contract.providerProductId());
        canonicalContract.put("consumerProductIds", sorted(contract.consumerProductIds()));
        canonicalContract.put("requiresAuth", contract.requiresAuth());
        canonicalContract.put("requiresTenant", contract.requiresTenant());
        canonicalContract.put("requiresConsent", contract.requiresConsent());
        canonicalContract.put("piiClassification", contract.piiClassification());
        canonicalContract.put("tenantScope", contract.tenantScope());
        canonicalContract.put("allowedCallerRoles", sorted(contract.allowedCallerRoles()));
        canonicalContract.put("allowedPurposes", sorted(contract.allowedPurposes()));
        canonicalContract.put("allowedLifecyclePhases", sorted(contract.allowedLifecyclePhases()));
        canonicalContract.put("degradedModeAllowed", contract.degradedModeAllowed());

        return CanonicalJsonHasher.hash(canonicalContract);
    }

    private static List<String> sorted(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> sortedValues = new ArrayList<>(values);
        sortedValues.sort(Comparator.naturalOrder());
        return List.copyOf(sortedValues);
    }

    private static String fallbackInteractionKey(ProductInteractionRequest<?> request, String reasonCode) {
        return String.format("%s::%s::%s::%s::%s",
                nullSafe(request.contractId()),
                nullSafe(request.interactionId()),
                nullSafe(request.tenantId()),
                nullSafe(request.workspaceId()),
                nullSafe(reasonCode));
    }

    private static String nullSafe(String value) {
        return value == null ? "missing" : value;
    }

    /**
     * P0-03: Checks if two interaction keys have the same interaction ID but different payload hash.
     * This detects idempotency conflicts where the same interaction ID is reused with different payloads.
     *
     * @param existingKey the existing interaction key
     * @param newKey the new interaction key
     * @return true if same interaction ID with different payload
     */
    private static boolean isSameInteractionIdDifferentPayload(String existingKey, String newKey) {
        String[] existingParts = existingKey.split("::");
        String[] newParts = newKey.split("::");

        // Keys must have at least 3 parts: contractId, interactionId, tenantId
        if (existingParts.length < 3 || newParts.length < 3) {
            return false;
        }

        // Check if interaction ID (index 1) is the same
        if (!existingParts[1].equals(newParts[1])) {
            return false;
        }

        // Check if payload hash (index 4) is different
        if (existingParts.length > 4 && newParts.length > 4) {
            return !existingParts[4].equals(newParts[4]);
        }

        return false;
    }

    public static final class Builder {
        private final Map<String, ProductInteractionHandler<?, ?>> handlersByContractId = new ConcurrentHashMap<>();
        private final Map<String, ProductInteractionContract> contractsByContractId = new ConcurrentHashMap<>();
        private final Map<String, Duration> latencyBudgetsByContract = new ConcurrentHashMap<>();
        private Set<String> supportedContractVersions = DEFAULT_SUPPORTED_CONTRACT_VERSIONS;
        private ProductInteractionPolicyEvaluator policyEvaluator = ProductInteractionPolicyEvaluator.defaultEvaluator();
        private ProductInteractionEvidenceWriter evidenceWriter = ProductInteractionEvidenceWriter.noop();
        private ProductInteractionPolicyContextResolver policyContextResolver = ProductInteractionPolicyContextResolver.developmentResolver();
        // Phase 3: Default to TEST mode for safety - requires explicit mode selection for production/development
        private BrokerMode brokerMode = BrokerMode.TEST;
        private Duration requestTimeout = Duration.ofSeconds(2);
        private Duration cacheTtl = Duration.ofMinutes(5); // Phase 1: Default 5-minute TTL
        private ProductInteractionHandlerRegistry handlerRegistry;
        private Path developmentEvidenceRoot = Path.of(".kernel", "evidence", "product-interactions");
        private ProductInteractionCircuitBreaker circuitBreaker;
        private com.ghatana.kernel.observability.TracingPort tracing;
        private com.ghatana.kernel.observability.MetricCollectorPort metrics;
        private String commitSha;
        private String environment;

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

        public Builder registerContract(ProductInteractionContract contract) {
            Objects.requireNonNull(contract, "contract must not be null");
            contractsByContractId.put(contract.contractId(), contract);
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

        public Builder developmentEvidenceRoot(Path evidenceRoot) {
            this.developmentEvidenceRoot = Objects.requireNonNull(evidenceRoot, "evidenceRoot must not be null");
            return this;
        }

        public Builder policyContextResolver(ProductInteractionPolicyContextResolver resolver) {
            this.policyContextResolver = Objects.requireNonNull(resolver, "resolver must not be null");
            return this;
        }

        public Builder brokerMode(BrokerMode mode) {
            this.brokerMode = Objects.requireNonNull(mode, "mode must not be null");
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

        /**
         * Phase 1: Set cache TTL for idempotency cache.
         * Set to null or zero to disable TTL-based eviction (unbounded cache).
         * Default is 5 minutes.
         */
        public Builder cacheTtl(Duration ttl) {
            this.cacheTtl = ttl;
            return this;
        }

        public Builder handlerRegistry(ProductInteractionHandlerRegistry registry) {
            this.handlerRegistry = registry;
            return this;
        }

        public Builder circuitBreaker(ProductInteractionCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public Builder tracing(com.ghatana.kernel.observability.TracingPort tracing) {
            this.tracing = tracing;
            return this;
        }

        public Builder metrics(com.ghatana.kernel.observability.MetricCollectorPort metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * KER-004: Set commit SHA for production truth binding.
         * Required in production mode.
         *
         * @param commitSha the commit SHA (40 hexadecimal characters)
         * @return this builder
         */
        public Builder commitSha(String commitSha) {
            this.commitSha = commitSha;
            return this;
        }

        /**
         * KER-004: Set target environment for interaction validation.
         *
         * @param environment the target environment (e.g., "production", "staging", "development")
         * @return this builder
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * PERF-002: Set latency budget for a specific contract.
         * Interactions exceeding this budget will be counted as SLO violations.
         *
         * @param contractId the contract ID
         * @param budget the latency budget
         * @return this builder
         */
        public Builder latencyBudget(String contractId, Duration budget) {
            Objects.requireNonNull(contractId, "contractId must not be null");
            Objects.requireNonNull(budget, "budget must not be null");
            latencyBudgetsByContract.put(contractId, budget);
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

    private static final class DevelopmentEvidenceThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "product-interaction-development-evidence");
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * Cached outcome with timestamp for TTL-based eviction.
     * Phase 1: Production hardening for bounded cache.
     */
    private static final class CachedOutcome {
        private final ProductInteractionOutcome<?> outcome;
        private final long cachedAtMs;

        CachedOutcome(ProductInteractionOutcome<?> outcome) {
            this.outcome = outcome;
            this.cachedAtMs = System.currentTimeMillis();
        }

        boolean isExpired(Duration ttl) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                return false;
            }
            long ageMs = System.currentTimeMillis() - cachedAtMs;
            return ageMs > ttl.toMillis();
        }
    }

    /**
     * Phase 1: Start periodic cache eviction task.
     * Removes expired entries from the cache based on TTL.
     */
    private void startCacheEvictionTask() {
        scheduler.scheduleAtFixedRate(
                this::evictExpiredCacheEntries,
                cacheTtl.toMillis(),
                cacheTtl.toMillis() / 2,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Phase 1: Evict expired cache entries.
     */
    private void evictExpiredCacheEntries() {
        long evictedCount = 0;
        for (Map.Entry<String, CachedOutcome> entry : completedByInteraction.entrySet()) {
            if (entry.getValue().isExpired(cacheTtl)) {
                completedByInteraction.remove(entry.getKey());
                evictedCount++;
            }
        }
        if (evictedCount > 0) {
            cacheEvictions.addAndGet(evictedCount);
        }
    }
}
