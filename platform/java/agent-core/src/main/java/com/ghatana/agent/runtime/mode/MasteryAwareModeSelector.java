/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mastery-aware mode selector that uses mastery registry to determine execution mode.
 *
 * @doc.type class
 * @doc.purpose Mastery-aware mode selector
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class MasteryAwareModeSelector {

    private final MasteryRegistry masteryRegistry;
    private final TaskClassifier taskClassifier;
    private final ModeSelectionPolicy selectionPolicy;

    /**
     * Creates a mastery-aware mode selector.
     *
     * @param masteryRegistry mastery registry
     * @param taskClassifier task classifier
     * @param selectionPolicy mode selection policy
     */
    public MasteryAwareModeSelector(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull TaskClassifier taskClassifier,
            @NotNull ModeSelectionPolicy selectionPolicy
    ) {
        this.masteryRegistry = masteryRegistry;
        this.taskClassifier = taskClassifier;
        this.selectionPolicy = selectionPolicy;
    }

    /**
     * Selects the execution mode for a task based on mastery and task classification.
     *
     * @param skillId        skill identifier
     * @param agentId        agent identifier
     * @param tenantId       tenant identifier (must not be null or blank)
     * @param taskDescription task description
     * @param context        additional context
     * @param versionContext version context
     * @return promise of enriched mode selection result with trace metadata
     */
    @NotNull
    public Promise<EnrichedModeSelectionResult> selectMode(
            @NotNull String skillId,
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String taskDescription,
            @NotNull String context,
            @NotNull VersionContext versionContext
    ) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }

        // Generate trace ID for this mode selection
        String traceId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        // Convert versionContext dependencies map to string format for query
        String versionContextStr = formatVersionContext(versionContext);

        // Query mastery registry with explicit tenant and version context
        MasteryQuery query = MasteryQuery.bySkill(skillId)
                .withAgentId(agentId)
                .withTenantId(tenantId)
                .withVersionContext(versionContextStr);

        return masteryRegistry.decide(query)
                .then(masteryDecision -> {
                    // Classify task
                    return taskClassifier.classify(taskDescription, context)
                            .then(taskClassification ->
                                    // Apply selection policy
                                    selectionPolicy.selectMode(masteryDecision, taskClassification, versionContext)
                                            .then(modeSelectionResult -> {
                                                // Enrich result with trace metadata
                                                Instant endTime = Instant.now();
                                                Map<String, Object> traceMetadata = new HashMap<>();
                                                traceMetadata.put("traceId", traceId);
                                                traceMetadata.put("startTime", startTime.toString());
                                                traceMetadata.put("endTime", endTime.toString());
                                                traceMetadata.put("durationMs", endTime.toEpochMilli() - startTime.toEpochMilli());
                                                traceMetadata.put("skillId", skillId);
                                                traceMetadata.put("agentId", agentId);
                                                traceMetadata.put("tenantId", tenantId);
                                                traceMetadata.put("masteryItemId", masteryDecision.masteryItemId());
                                                traceMetadata.put("masteryState", masteryDecision.state().name());
                                                traceMetadata.put("versionApplicability", masteryDecision.versionApplicability().name());
                                                traceMetadata.put("executionScore", masteryDecision.executionScore());
                                                traceMetadata.put("stale", masteryDecision.stale());
                                                traceMetadata.put("terminal", masteryDecision.terminal());
                                                traceMetadata.put("taskRiskLevel", taskClassification.riskLevel().name());
                                                traceMetadata.put("taskNovelty", taskClassification.novelty().name());
                                                traceMetadata.put("versionContext", versionContextStr);
                                                traceMetadata.put("modeSelectionReason", modeSelectionResult.reasoning());

                                                return Promise.of(new EnrichedModeSelectionResult(
                                                        modeSelectionResult.strategy(),
                                                        modeSelectionResult.supervision(),
                                                        modeSelectionResult.reasoning(),
                                                        traceMetadata,
                                                        masteryDecision,
                                                        taskClassification,
                                                        versionContext
                                                ));
                                            }));
                });
    }

    /**
     * Selects the execution mode with custom query parameters.
     *
     * @param query              mastery query (must include tenantId)
     * @param taskClassification task classification
     * @param versionContext     version context
     * @return promise of enriched mode selection result with trace metadata
     */
    @NotNull
    public Promise<EnrichedModeSelectionResult> selectMode(
            @NotNull MasteryQuery query,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionContext versionContext
    ) {
        // Generate trace ID for this mode selection
        String traceId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        // Ensure version context is included in query if not already present
        String versionContextStr = formatVersionContext(versionContext);
        MasteryQuery enrichedQuery = query.versionContext() != null 
                ? query 
                : query.withVersionContext(versionContextStr);

        return masteryRegistry.decide(enrichedQuery)
                .then(masteryDecision ->
                        // Apply selection policy
                        selectionPolicy.selectMode(masteryDecision, taskClassification, versionContext)
                                .then(modeSelectionResult -> {
                                    // Enrich result with trace metadata
                                    Instant endTime = Instant.now();
                                    Map<String, Object> traceMetadata = new HashMap<>();
                                    traceMetadata.put("traceId", traceId);
                                    traceMetadata.put("startTime", startTime.toString());
                                    traceMetadata.put("endTime", endTime.toString());
                                    traceMetadata.put("durationMs", endTime.toEpochMilli() - startTime.toEpochMilli());
                                    traceMetadata.put("skillId", Objects.requireNonNullElse(query.skillId(), ""));
                                    traceMetadata.put("agentId", Objects.requireNonNullElse(query.agentId(), ""));
                                    traceMetadata.put("tenantId", Objects.requireNonNullElse(query.tenantId(), ""));
                                    traceMetadata.put("masteryItemId", Objects.requireNonNullElse(masteryDecision.masteryItemId(), ""));
                                    traceMetadata.put("masteryState", masteryDecision.state().name());
                                    traceMetadata.put("versionApplicability", masteryDecision.versionApplicability().name());
                                    traceMetadata.put("executionScore", masteryDecision.executionScore());
                                    traceMetadata.put("stale", masteryDecision.stale());
                                    traceMetadata.put("terminal", masteryDecision.terminal());
                                    traceMetadata.put("taskRiskLevel", taskClassification.riskLevel().name());
                                    traceMetadata.put("taskNovelty", taskClassification.novelty().name());
                                    traceMetadata.put("versionContext", versionContextStr);
                                    traceMetadata.put("modeSelectionReason", modeSelectionResult.reasoning());

                                    return Promise.of(new EnrichedModeSelectionResult(
                                            modeSelectionResult.strategy(),
                                            modeSelectionResult.supervision(),
                                            modeSelectionResult.reasoning(),
                                            traceMetadata,
                                            masteryDecision,
                                            taskClassification,
                                            versionContext
                                    ));
                                }));
    }

    /**
     * Formats version context dependencies map to string format.
     * Format: "component1=1.0.0,component2=2.0.0"
     *
     * @param versionContext version context
     * @return formatted string
     */
    @NotNull
    private static String formatVersionContext(@NotNull VersionContext versionContext) {
        Map<String, String> dependencies = versionContext.dependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return "";
        }
        return dependencies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Enriched mode selection result with trace metadata.
     */
    public record EnrichedModeSelectionResult(
            @NotNull ExecutionStrategy executionStrategy,
            @NotNull SupervisionMode supervisionMode,
            @NotNull String reason,
            @NotNull Map<String, Object> traceMetadata,
            @NotNull MasteryDecision masteryDecision,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionContext versionContext
    ) {
        public EnrichedModeSelectionResult {
            traceMetadata = Map.copyOf(traceMetadata);
        }

        /**
         * Returns the trace ID for this mode selection.
         *
         * @return trace ID
         */
        @NotNull
        public String traceId() {
            return (String) traceMetadata.get("traceId");
        }

        /**
         * Returns the duration of the mode selection in milliseconds.
         *
         * @return duration in milliseconds
         */
        public long durationMs() {
            Object duration = traceMetadata.get("durationMs");
            return duration instanceof Long ? (Long) duration : 0L;
        }

        /**
         * Alias for {@link #executionStrategy()} for caller compatibility.
         *
         * @return the execution strategy
         */
        @NotNull
        public ExecutionStrategy strategy() {
            return executionStrategy;
        }

        /**
         * Alias for {@link #supervisionMode()} for caller compatibility.
         *
         * @return the supervision mode
         */
        @NotNull
        public SupervisionMode supervision() {
            return supervisionMode;
        }

        /**
         * Returns {@code true} when this mode requires human approval before
         * the agent is allowed to execute (HUMAN_GATED supervision).
         *
         * @return true if approval is required
         */
        public boolean requiresApproval() {
            return supervisionMode == SupervisionMode.HUMAN_GATED;
        }

        /**
         * Returns {@code true} when this mode requires a verification pass
         * after execution proceeds (SUPERVISED supervision mode).
         *
         * @return true if verification is required
         */
        public boolean requiresVerification() {
            return supervisionMode == SupervisionMode.SUPERVISED;
        }
    }
}
