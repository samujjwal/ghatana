/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.CompileContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.OperatorVersion;
import com.ghatana.aep.operator.contract.RuntimePlan;
import com.ghatana.aep.operator.contract.ValidationContext;
import com.ghatana.aep.operator.contract.ValidationResult;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.agent.AgentOperator;
import com.ghatana.core.operator.agent.AgentOperatorKind;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 6: Adapter that wraps a TypedAgent into the AgentOperator interface.
 *
 * <p>This adapter enables the migration from direct agent execution to the
 * AgentOperator pattern while maintaining backward compatibility with existing
 * agent implementations.
 *
 * @doc.type class
 * @doc.purpose Adapts TypedAgent to AgentOperator interface
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AgentOperatorAdapter implements AgentOperator {

    private final TypedAgent<Map<String, Object>, Map<String, Object>> agent;
    private final String agentRef;
    private final AgentOperatorKind kind;
    private final OperatorId operatorId;
    private final String operatorName;
    private final String operatorDescription;
    private final List<String> capabilities;
    private final Map<String, String> metadata;
    private volatile OperatorState state = OperatorState.CREATED;
    private volatile OperatorConfig config = OperatorConfig.empty();

    public AgentOperatorAdapter(
            @NotNull TypedAgent<Map<String, Object>, Map<String, Object>> agent,
            @NotNull String agentRef,
            @NotNull AgentOperatorKind kind) {
            this.agent = Objects.requireNonNull(agent, "agent");
            this.agentRef = Objects.requireNonNull(agentRef, "agentRef");
            this.kind = Objects.requireNonNull(kind, "kind");

            AgentDescriptor descriptor = agent.descriptor();
            String namespace = nonBlank(descriptor.getNamespace(), "default");
            String idSeed = nonBlank(descriptor.getAgentId(), agentRef);
            String version = nonBlank(descriptor.getVersion(), "1.0.0");

            this.operatorId = OperatorId.of(namespace, "agent", sanitize(idSeed), version);
            this.operatorName = nonBlank(descriptor.getName(), idSeed);
            this.operatorDescription = nonBlank(
                descriptor.getDescription(),
                "Adapter operator for typed agent " + this.agentRef);
            this.capabilities = List.copyOf(descriptor.getCapabilities());
            this.metadata = Map.of(
                "agentRef", this.agentRef,
                "agentOperatorKind", this.kind.name(),
                "adapterType", "typed-agent");
    }

    @Override
    @NotNull
            public Promise<EventOperatorResult<Map<String, Object>>> process(
                @NotNull EventContext<Map<String, Object>> input,
                @Nullable OperatorRuntimeContext ctx) {
            Map<String, Object> inputPayload = input.input().orElseGet(Map::of);
            AgentContext agentContext = createAgentContext(ctx, input.tenantId());
            return agent.process(agentContext, inputPayload)
                .map(result -> mapAgentResult(result, input));
    }

    @Override
    @NotNull
    public String agentRef() {
        return agentRef;
    }

    @Override
    @NotNull
    public AgentOperatorKind agentOperatorKind() {
        return kind;
    }

    @Override
    @NotNull
    public AgentSideEffectProfile sideEffectProfile() {
        return AgentSideEffectProfile.SIDE_EFFECTING;
    }

    @Override
    @NotNull
    public String inputSchema() {
        return "agent-input-default";
    }

    @Override
    @NotNull
    public String outputSchema() {
        return "agent-output-default";
    }

    @Override
    @NotNull
    public Map<String, Object> modelPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> toolPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> memoryPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> retrievalPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> guardrailPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> replayPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> uncertaintyPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> humanReviewPolicy() {
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, Object> observabilityPolicy() {
        return Map.of();
    }

    @Override
    public OperatorId id() {
        return operatorId;
    }

    @Override
    public OperatorKind kind() {
        return OperatorKind.valueOf(kind.name());
    }

    @Override
    public OperatorVersion version() {
        return new OperatorVersion(operatorId.getVersion());
    }

    @Override
    public ValidationResult validate(OperatorSpec spec, ValidationContext ctx) {
        if (spec.kind() != kind()) {
            return ValidationResult.invalid(List.of("AgentOperatorAdapter requires " + kind() + " spec"));
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
                "agent-operator-" + operatorId.getName(),
                List.of(operatorId.toString()),
                Map.of("agentRef", agentRef, "kind", kind.name()),
                Map.of("operatorType", "agent"));
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
        AgentContext agentContext = createAgentContext(null, event.getTenantId());
        return agent.process(agentContext, event.toPayloadMap())
                .map(result -> {
                    if (result.getStatus() == AgentResultStatus.FAILED
                            || result.getStatus() == AgentResultStatus.TIMEOUT) {
                        return OperatorResult.failed(nonBlank(result.getExplanation(), "Agent execution failed"));
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
        return null;
    }

    @Override
    public Map<String, Object> getMetrics() {
        return Map.of();
    }

    @Override
    public Map<String, Object> getInternalState() {
        return Map.of(
                "state", state.name(),
                "agentRef", agentRef,
                "kind", kind.name());
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
            EventContext<Map<String, Object>> input) {
        if (result.getStatus() == AgentResultStatus.FAILED
                || result.getStatus() == AgentResultStatus.TIMEOUT) {
            return EventOperatorResult.failure(List.of(nonBlank(result.getExplanation(), "Agent execution failed")));
        }
        return EventOperatorResult.success(
                result.getOutput() != null ? result.getOutput() : Map.of(),
                input.uncertainty());
    }

    private AgentContext createAgentContext(@Nullable OperatorRuntimeContext ctx, @NotNull String tenantId) {
        AgentContext.Builder builder = AgentContext.builder()
                .agentId(agentRef)
                .turnId(UUID.randomUUID().toString())
                .tenantId(nonBlank(tenantId, "default-tenant"))
                .memoryStore(MemoryStore.noOp());

        if (ctx != null) {
            builder.traceId(ctx.traceId().orElse(null));
            builder.config(ctx.policies());
            builder.metadata(Map.of(
                    "correlationId", ctx.correlationId().orElse(""),
                    "operatorId", operatorId.toString()));
        }

        return builder.build();
    }

    private static String nonBlank(@Nullable String value, @NotNull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String sanitize(@NotNull String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
    }
}
