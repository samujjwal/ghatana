/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.CapabilityInvocation;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.CapabilityResult;
import com.ghatana.aep.agent.capability.EventOperatorCapability;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.CompileContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.OperatorVersion;
import com.ghatana.aep.operator.contract.RuntimePlan;
import com.ghatana.aep.operator.contract.ValidationContext;
import com.ghatana.aep.operator.contract.ValidationResult;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapts a TypedAgent event capability to AEP event-operator execution.
 *
 * @doc.type class
 * @doc.purpose Adapts a TypedAgent capability to EventOperatorCapability
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AgentEventOperatorCapabilityAdapter
        implements EventOperatorCapability<Map<String, Object>, Map<String, Object>>, UnifiedOperator {

    private final TypedAgent<Map<String, Object>, Map<String, Object>> agent;
    private final String agentRef;
    private final AgentCapabilityRole operatorKind;
    private final CapabilityId capabilityId;
    private final CapabilityDescriptor descriptor;
    private final OperatorId operatorId;
    private final String operatorName;
    private final String operatorDescription;
    private final List<String> capabilities;
    private final Map<String, String> metadata;
    private final Map<String, Object> modelPolicy;
    private final Map<String, Object> toolPolicy;
    private final Map<String, Object> memoryPolicy;
    private final Map<String, Object> retrievalPolicy;
    private final Map<String, Object> guardrailPolicy;
    private final Map<String, Object> replayPolicy;
    private final Map<String, Object> uncertaintyPolicy;
    private final Map<String, Object> humanReviewPolicy;
    private final Map<String, Object> observabilityPolicy;
    private final MemoryStore memoryStore;
    private final AtomicLong invocationCount = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private final AtomicLong deniedCount = new AtomicLong();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private volatile OperatorState state = OperatorState.CREATED;
    private volatile OperatorConfig config = OperatorConfig.empty();
    private volatile String lastOutcome = "not_invoked";

    public AgentEventOperatorCapabilityAdapter(
            @NotNull TypedAgent<Map<String, Object>, Map<String, Object>> agent,
            @NotNull String agentRef,
            @NotNull AgentCapabilityRole operatorKind,
            @NotNull String inputSchema,
            @NotNull String outputSchema,
            @NotNull AgentSideEffectProfile sideEffectProfile,
            @NotNull Map<String, Object> modelPolicy,
            @NotNull Map<String, Object> toolPolicy,
            @NotNull Map<String, Object> memoryPolicy,
            @NotNull Map<String, Object> retrievalPolicy,
            @NotNull Map<String, Object> guardrailPolicy,
            @NotNull Map<String, Object> replayPolicy,
            @NotNull Map<String, Object> uncertaintyPolicy,
            @NotNull Map<String, Object> humanReviewPolicy,
            @NotNull Map<String, Object> observabilityPolicy,
            @NotNull MemoryStore memoryStore) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.agentRef = requireNonBlank(agentRef, "agentRef");
        this.operatorKind = Objects.requireNonNull(operatorKind, "operatorKind");
        this.modelPolicy = requirePolicy(modelPolicy, "modelPolicy");
        this.toolPolicy = requirePolicy(toolPolicy, "toolPolicy");
        this.memoryPolicy = requirePolicy(memoryPolicy, "memoryPolicy");
        this.retrievalPolicy = requirePolicy(retrievalPolicy, "retrievalPolicy");
        this.guardrailPolicy = requirePolicy(guardrailPolicy, "guardrailPolicy");
        this.replayPolicy = requirePolicy(replayPolicy, "replayPolicy");
        this.uncertaintyPolicy = requirePolicy(uncertaintyPolicy, "uncertaintyPolicy");
        this.humanReviewPolicy = requirePolicy(humanReviewPolicy, "humanReviewPolicy");
        this.observabilityPolicy = requirePolicy(observabilityPolicy, "observabilityPolicy");
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore");
        requireSideEffectControls(sideEffectProfile, this.toolPolicy, this.humanReviewPolicy, this.observabilityPolicy, this.replayPolicy);

        AgentDescriptor agentDescriptor = agent.descriptor();
        String namespace = nonBlank(agentDescriptor.getNamespace(), "default");
        String idSeed = nonBlank(agentDescriptor.getAgentId(), this.agentRef);
        String version = nonBlank(agentDescriptor.getVersion(), "1.0.0");

        this.operatorId = OperatorId.of(namespace, "agent-capability", sanitize(idSeed), version);
        this.capabilityId = CapabilityId.of(this.agentRef + "/capabilities/" + this.operatorKind.name().toLowerCase(java.util.Locale.ROOT));
        this.operatorName = nonBlank(agentDescriptor.getName(), idSeed);
        this.operatorDescription = nonBlank(
            agentDescriptor.getDescription(),
            "Event operator capability for typed agent " + this.agentRef);
        this.capabilities = List.copyOf(agentDescriptor.getCapabilities());
        this.metadata = Map.of(
            "agentRef", this.agentRef,
            "capabilityId", this.capabilityId.value(),
            "agentCapabilityRole", this.operatorKind.name(),
            "adapterType", "typed-agent-event-operator-capability");
        this.descriptor = new CapabilityDescriptor(
            this.capabilityId,
            CapabilityKind.EVENT_OPERATOR,
            this.agentRef,
            Optional.of(agentDescriptor),
            requireNonBlank(inputSchema, "inputSchema"),
            requireNonBlank(outputSchema, "outputSchema"),
            sideEffectProfile,
            this.capabilities,
            Map.of(
                "modelPolicy", this.modelPolicy,
                "toolPolicy", this.toolPolicy,
                "memoryPolicy", this.memoryPolicy,
                "retrievalPolicy", this.retrievalPolicy,
                "guardrailPolicy", this.guardrailPolicy,
                "replayPolicy", this.replayPolicy,
                "uncertaintyPolicy", this.uncertaintyPolicy,
                "humanReviewPolicy", this.humanReviewPolicy,
                "observabilityPolicy", this.observabilityPolicy),
            this.metadata);
    }

    @Override
    public CapabilityId capabilityId() {
        return capabilityId;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Promise<CapabilityResult<EventOperatorResult<Map<String, Object>>>> invoke(
            CapabilityInvocation<EventContext<Map<String, Object>>> invocation) {
        OperatorRuntimeContext runtimeContext = runtimeContextFromInvocation(invocation);
        long start = System.nanoTime();
        return process(invocation.input(), runtimeContext)
            .map(result -> CapabilityResult.success(
                result,
                result.uncertainty().modelConfidence(),
                Duration.ofNanos(System.nanoTime() - start),
                result.evidence()));
    }

    @Override
    public Promise<EventOperatorResult<Map<String, Object>>> process(
            @NotNull EventContext<Map<String, Object>> input,
            @NotNull OperatorRuntimeContext ctx) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(ctx, "ctx");
        long start = System.nanoTime();
        invocationCount.incrementAndGet();
        AgentContext agentContext = createAgentContext(ctx, input.tenantId());
        Map<String, Object> inputPayload = input.input().orElseGet(Map::of);
        return agent.process(agentContext, inputPayload)
            .map(result -> {
                long latency = System.nanoTime() - start;
                totalLatencyNanos.addAndGet(latency);
                recordOutcome(result);
                return mapAgentResult(result, input, ctx, latency);
            });
    }

    @Override
    public OperatorId id() {
        return operatorId;
    }

    @Override
    public OperatorKind kind() {
        return OperatorKind.valueOf(operatorKind.name());
    }

    @Override
    public OperatorVersion version() {
        return new OperatorVersion(operatorId.getVersion());
    }

    @Override
    public ValidationResult validate(OperatorSpec spec, ValidationContext ctx) {
        if (spec.kind() != kind()) {
            return ValidationResult.invalid(List.of("AgentEventOperatorCapabilityAdapter requires " + kind() + " spec"));
        }
        return ValidationResult.ok();
    }

    @Override
    public RuntimePlan compile(OperatorSpec spec, CompileContext ctx) {
        ValidationResult validation = validate(spec, null);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        return new RuntimePlan(
            "agent-capability-" + operatorId.getName(),
            List.of(operatorId.toString()),
            Map.of("agentRef", agentRef, "capabilityId", capabilityId.value()),
            Map.of("operatorType", "agent-capability"));
    }

    @Override
    public OperatorId getId() {
        return operatorId;
    }

    @Override
    public String getName() {
        return operatorName;
    }

    @Override
    public OperatorType getType() {
        return OperatorType.AGENT;
    }

    @Override
    public String getVersion() {
        return operatorId.getVersion();
    }

    @Override
    public String getDescription() {
        return operatorDescription;
    }

    @Override
    public List<String> getCapabilities() {
        return capabilities;
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        EventContext<Map<String, Object>> eventContext = eventContextFromEvent(event);
        OperatorRuntimeContext runtimeContext = runtimeContextFromEvent(event);
        return process(eventContext, runtimeContext)
            .map(result -> {
                if (!result.success()) {
                    return OperatorResult.failed(String.join("; ", result.errors()));
                }
                return OperatorResult.empty();
            });
    }

    @Override
    public Promise<Void> initialize(OperatorConfig config) {
        this.config = config != null ? config : OperatorConfig.empty();
        this.state = OperatorState.INITIALIZED;
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = OperatorState.RUNNING;
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = OperatorState.STOPPED;
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return state != OperatorState.FAILED;
    }

    @Override
    public OperatorState getState() {
        return state;
    }

    @Override
    public Event toEvent() {
        return Event.builder()
            .typeTenantVersion("system", "agent.capability.registered", "v1")
            .headers(Map.of("correlationId", "capability-" + operatorId.getName()))
            .payload(Map.of(
                "operatorId", operatorId.toString(),
                "capabilityId", capabilityId.value(),
                "agentRef", agentRef,
                "operatorKind", operatorKind.name(),
                "inputSchema", descriptor.inputSchema(),
                "outputSchema", descriptor.outputSchema()))
            .build();
    }

    @Override
    public Map<String, Object> getMetrics() {
        long invocations = invocationCount.get();
        long totalNanos = totalLatencyNanos.get();
        return Map.ofEntries(
            Map.entry("invocations", invocations),
            Map.entry("success", successCount.get()),
            Map.entry("failure", failureCount.get()),
            Map.entry("timeout", timeoutCount.get()),
            Map.entry("denied", deniedCount.get()),
            Map.entry("latencyNanosTotal", totalNanos),
            Map.entry("latencyNanosAverage", invocations == 0 ? 0 : totalNanos / invocations),
            Map.entry("lastOutcome", lastOutcome),
            Map.entry("tenantScoped", true),
            Map.entry("capabilityId", capabilityId.value()),
            Map.entry("sideEffectProfile", descriptor.sideEffectProfile().name()));
    }

    @Override
    public Map<String, Object> getInternalState() {
        return Map.of(
            "state", state.name(),
            "agentRef", agentRef,
            "capabilityId", capabilityId.value(),
            "kind", operatorKind.name());
    }

    @Override
    public OperatorConfig getConfig() {
        return config;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    private EventOperatorResult<Map<String, Object>> mapAgentResult(
            AgentResult<Map<String, Object>> result,
            EventContext<Map<String, Object>> input,
            OperatorRuntimeContext ctx,
            long latencyNanos) {
        if (result.getStatus() == AgentResultStatus.FAILED
                || result.getStatus() == AgentResultStatus.TIMEOUT) {
            return EventOperatorResult.failure(List.of(nonBlank(result.getExplanation(), "Agent execution failed")));
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("agentRef", agentRef);
        evidence.put("capabilityId", capabilityId.value());
        evidence.put("operatorId", operatorId.toString());
        evidence.put("tenantId", ctx.tenantId());
        evidence.put("traceId", ctx.traceId().orElseThrow());
        evidence.put("correlationId", ctx.correlationId().orElseThrow());
        evidence.put("latencyNanos", latencyNanos);
        evidence.put("agentConfidence", result.getConfidence());
        evidence.put("agentEvidence", result.getEvidence());
        return new EventOperatorResult<>(
            true,
            Optional.of(result.getOutput() != null ? result.getOutput() : Map.of()),
            List.of(),
            mapUncertainty(input.uncertainty(), result),
            evidence,
            List.of());
    }

    private UncertaintyContext mapUncertainty(UncertaintyContext input, AgentResult<Map<String, Object>> result) {
        double retrievalConfidence = confidence(result.getMetrics(), "retrievalConfidence", input.retrievalConfidence());
        double calibrationScore = confidence(result.getMetrics(), "calibrationScore", input.calibrationScore());
        return new UncertaintyContext(
            input.eventDetectionConfidence(),
            input.attributeConfidence(),
            input.temporalConfidence(),
            input.sourceReliability(),
            input.patternConfidence(),
            clamp(result.getConfidence()),
            retrievalConfidence,
            input.inputCompleteness(),
            calibrationScore,
            Map.of(
                "agentRef", agentRef,
                "capabilityId", capabilityId.value(),
                "agentEvidence", result.getEvidence(),
                "modelMetadata", result.getMetrics()));
    }

    private AgentContext createAgentContext(OperatorRuntimeContext ctx, String tenantId) {
        String resolvedTenant = requireNonBlank(tenantId, "tenantId");
        if (!resolvedTenant.equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Event tenantId must match runtime tenantId");
        }
        String traceId = ctx.traceId().filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("traceId is required for agent capability execution"));
        String correlationId = ctx.correlationId().filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("correlationId is required for agent capability execution"));
        return AgentContext.builder()
            .agentId(agentRef)
            .turnId(UUID.randomUUID().toString())
            .tenantId(resolvedTenant)
            .traceId(traceId)
            .memoryStore(memoryStore)
            .config(ctx.policies())
            .metadata(Map.of(
                "correlationId", correlationId,
                "operatorId", operatorId.toString(),
                "capabilityId", capabilityId.value(),
                "replayMode", "runtime-context-required"))
            .build();
    }

    private void recordOutcome(AgentResult<Map<String, Object>> result) {
        if (result.getStatus() == AgentResultStatus.TIMEOUT) {
            timeoutCount.incrementAndGet();
            failureCount.incrementAndGet();
            lastOutcome = "timeout";
            return;
        }
        if (result.getStatus() == AgentResultStatus.FAILED) {
            failureCount.incrementAndGet();
            lastOutcome = "failure";
            return;
        }
        if (result.getStatus() == AgentResultStatus.PENDING_APPROVAL) {
            deniedCount.incrementAndGet();
            lastOutcome = "pending_approval";
            return;
        }
        successCount.incrementAndGet();
        lastOutcome = "success";
    }

    private static OperatorRuntimeContext runtimeContextFromInvocation(
            CapabilityInvocation<EventContext<Map<String, Object>>> invocation) {
        Object runtimeContext = invocation.attributes().get("operatorRuntimeContext");
        if (runtimeContext instanceof OperatorRuntimeContext ctx) {
            return ctx;
        }
        throw new IllegalArgumentException("operatorRuntimeContext attribute is required");
    }

    private static OperatorRuntimeContext runtimeContextFromEvent(Event event) {
        String traceId = event.getHeader("traceId");
        String correlationId = event.getCorrelationId();
        if (isBlank(traceId) || isBlank(correlationId)) {
            throw new IllegalArgumentException("Event process entrypoint requires traceId and correlationId headers");
        }
        return new OperatorRuntimeContext(
            requireNonBlank(event.getTenantId(), "tenantId"),
            Optional.of(traceId),
            Optional.of(correlationId),
            Map.of(),
            Map.of());
    }

    private static EventContext<Map<String, Object>> eventContextFromEvent(Event event) {
        return new EventContext<>(
            requireNonBlank(event.getTenantId(), "tenantId"),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            new EventTimeContext(
                EventTimeContext.TimeMode.EVENT_TIME,
                Optional.empty(),
                Duration.ZERO,
                EventTimeContext.LateEventBehavior.INCORPORATE,
                Optional.empty()),
            UncertaintyContext.certain(),
            new ReplayContext(
                ReplayContext.ReplayMode.LIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()),
            Optional.of(event.toPayloadMap()));
    }

    private static void requireSideEffectControls(
            AgentSideEffectProfile sideEffectProfile,
            Map<String, Object> toolPolicy,
            Map<String, Object> humanReviewPolicy,
            Map<String, Object> observabilityPolicy,
            Map<String, Object> replayPolicy) {
        if (sideEffectProfile != AgentSideEffectProfile.SIDE_EFFECTING) {
            return;
        }
        if (!hasPolicy(toolPolicy, "allowedTools")) {
            throw new IllegalArgumentException("SIDE_EFFECTING capability requires toolPolicy.allowedTools");
        }
        if (!hasPolicy(humanReviewPolicy, "approvalPolicy") && !hasPolicy(humanReviewPolicy, "mode")) {
            throw new IllegalArgumentException("SIDE_EFFECTING capability requires humanReviewPolicy approval");
        }
        if (!hasPolicy(observabilityPolicy, "auditPolicy") && !Boolean.TRUE.equals(observabilityPolicy.get("audit"))) {
            throw new IllegalArgumentException("SIDE_EFFECTING capability requires observability audit policy");
        }
        if (!Boolean.TRUE.equals(replayPolicy.get("idempotencyRequired"))) {
            throw new IllegalArgumentException("SIDE_EFFECTING capability requires replayPolicy.idempotencyRequired");
        }
    }

    private static boolean hasPolicy(Map<String, Object> policy, String key) {
        Object value = policy.get(key);
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return !isBlank(value);
    }

    private static double confidence(Map<String, Object> values, String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return clamp(number.doubleValue());
        }
        return fallback;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String nonBlank(@Nullable String value, @NotNull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String requireNonBlank(@Nullable String value, @NotNull String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static Map<String, Object> requirePolicy(
            @Nullable Map<String, Object> policy,
            @NotNull String fieldName) {
        if (policy == null || policy.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return Map.copyOf(policy);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static String sanitize(@NotNull String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
    }
}
