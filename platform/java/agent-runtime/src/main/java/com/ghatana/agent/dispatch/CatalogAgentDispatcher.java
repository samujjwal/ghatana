/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.dispatch.tier.LlmExecutionPlan;
import com.ghatana.agent.dispatch.tier.ServiceOrchestrationPlan;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link AgentDispatcher} implementation that resolves agent IDs
 * against the {@link CatalogRegistry} and dispatches to the appropriate
 * execution tier.
 *
 * <h2>Resolution Order</h2>
 * <ol>
 *   <li>Check registered Java {@link TypedAgent} beans (Tier-J)</li>
 *   <li>If not found, load {@link CatalogAgentEntry} from {@link CatalogRegistry}</li>
 *   <li>If generator type is PIPELINE with delegation → Tier-S</li>
 *   <li>If generator has LLM step → Tier-L</li>
 *   <li>Otherwise → UNRESOLVABLE</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Default three-tier agent dispatcher
 * @doc.layer framework
 * @doc.pattern Dispatcher, Strategy
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public class CatalogAgentDispatcher implements AgentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CatalogAgentDispatcher.class);

    private final CatalogRegistry catalogRegistry;
    private final LlmExecutionPlan llmPlan;
    private final ServiceOrchestrationPlan servicePlan;

    // Tier-J registry: agentId → TypedAgent bean
    private final Map<String, TypedAgent<?, ?>> javaAgents = new ConcurrentHashMap<>();

    public CatalogAgentDispatcher(
            CatalogRegistry catalogRegistry,
            LlmExecutionPlan llmPlan,
            ServiceOrchestrationPlan servicePlan) {
        this.catalogRegistry = Objects.requireNonNull(catalogRegistry, "catalogRegistry");
        this.llmPlan = Objects.requireNonNull(llmPlan, "llmPlan");
        this.servicePlan = Objects.requireNonNull(servicePlan, "servicePlan");
    }

    /**
     * Registers a Java-implemented agent (Tier-J).
     * Tier-J agents take precedence over catalog-defined execution.
     *
     * @param agentId the agent ID matching the catalog entry
     * @param agent   the TypedAgent implementation
     */
    public void registerJavaAgent(String agentId, TypedAgent<?, ?> agent) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(agent, "agent");
        javaAgents.put(agentId, agent);
        log.info("Registered Tier-J agent: {}", agentId);
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <I, O> Promise<AgentResult<O>> dispatch(
            @NotNull String agentId,
            @NotNull I input,
            @NotNull AgentContext ctx) {

        Instant start = Instant.now();
        DispatchResult resolution = resolveInternal(agentId);

        return switch (resolution.getTier()) {
            case JAVA_IMPLEMENTED -> {
                TypedAgent<I, O> agent = (TypedAgent<I, O>) javaAgents.get(agentId);
                log.debug("Dispatching {} via Tier-J (Java)", agentId);
                yield agent.process(ctx, input);
            }

            case SERVICE_ORCHESTRATED -> {
                log.debug("Dispatching {} via Tier-S (Service Orchestration)", agentId);
                yield (Promise<AgentResult<O>>) (Promise<?>) servicePlan.execute(
                        resolution.getCatalogEntry(), input, ctx, this);
            }

            case LLM_EXECUTED -> {
                log.debug("Dispatching {} via Tier-L (LLM)", agentId);
                yield (Promise<AgentResult<O>>) (Promise<?>) llmPlan.execute(
                        resolution.getCatalogEntry(), input, ctx);
            }

            case UNRESOLVABLE -> {
                Duration elapsed = Duration.between(start, Instant.now());
                log.warn("Agent '{}' is unresolvable — not found in registry or catalog", agentId);
                yield Promise.of(AgentResult.<O>builder()
                        .status(AgentResultStatus.FAILED)
                        .confidence(0.0)
                        .agentId(agentId)
                        .explanation("Agent '" + agentId + "' not found in any catalog or runtime registry")
                        .processingTime(elapsed)
                        .build());
            }
        };
    }

    @Override
    @NotNull
    public ExecutionTier resolve(@NotNull String agentId) {
        return resolveInternal(agentId).getTier();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal resolution
    // ═══════════════════════════════════════════════════════════════════════════

    private DispatchResult resolveInternal(String agentId) {
        // 1. Check Tier-J: registered Java TypedAgent beans
        if (javaAgents.containsKey(agentId)) {
            return DispatchResult.builder()
                    .tier(ExecutionTier.JAVA_IMPLEMENTED)
                    .catalogEntry(catalogRegistry.findById(agentId).orElse(null))
                    .hasJavaImplementation(true)
                    .build();
        }

        // 2. Check catalog for definition
        Optional<CatalogAgentEntry> entry = catalogRegistry.findById(agentId);
        if (entry.isEmpty()) {
            return DispatchResult.builder()
                    .tier(ExecutionTier.UNRESOLVABLE)
                    .hasJavaImplementation(false)
                    .build();
        }

        CatalogAgentEntry catalogEntry = entry.get();
        Map<String, Object> generator = catalogEntry.getGenerator();

        // 3. Determine tier from generator definition
        ExecutionTier tier = classifyTier(generator, catalogEntry);

        return DispatchResult.builder()
                .tier(tier)
                .catalogEntry(catalogEntry)
                .hasJavaImplementation(false)
                .build();
    }

    private ExecutionTier classifyTier(Map<String, Object> generator, CatalogAgentEntry entry) {
        if (generator == null || generator.isEmpty()) {
            return ExecutionTier.UNRESOLVABLE;
        }

        String type = String.valueOf(generator.getOrDefault("type", ""));

        if ("PIPELINE".equalsIgnoreCase(type)) {
            // Check if it has delegation (Tier-S) or only LLM steps (Tier-L)
            Map<String, Object> delegation = entry.getDelegation();
            Object delegateTo = delegation != null ? delegation.get("can_delegate_to") : null;

            if (delegateTo instanceof java.util.List<?> list && !list.isEmpty()) {
                return ExecutionTier.SERVICE_ORCHESTRATED;
            }

            // PIPELINE with no delegation but has LLM steps → Tier-L
            if (hasLlmStep(generator)) {
                return ExecutionTier.LLM_EXECUTED;
            }

            return ExecutionTier.SERVICE_ORCHESTRATED;
        }

        if ("LLM".equalsIgnoreCase(type)) {
            return ExecutionTier.LLM_EXECUTED;
        }

        if ("RULE_BASED".equalsIgnoreCase(type) || "SERVICE_CALL".equalsIgnoreCase(type)) {
            return ExecutionTier.SERVICE_ORCHESTRATED;
        }

        return ExecutionTier.UNRESOLVABLE;
    }

    @SuppressWarnings("unchecked")
    private boolean hasLlmStep(Map<String, Object> generator) {
        Object steps = generator.get("steps");
        if (steps instanceof java.util.List<?> stepList) {
            return stepList.stream()
                    .filter(Map.class::isInstance)
                    .map(s -> (Map<String, Object>) s)
                    .anyMatch(step -> "LLM".equalsIgnoreCase(String.valueOf(step.getOrDefault("type", ""))));
        }
        return false;
    }
}
