/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.learning;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureStep;
import com.ghatana.yappc.api.domain.LearnedPolicy;
import com.ghatana.yappc.api.repository.LearnedPolicyRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts generalizable action patterns from high-confidence agent executions and
 * persists them as {@link LearnedPolicy} records in {@link LearnedPolicyRepository}.
 *
 * <h2>Learning Contract (plan 9.5.3)</h2>
 * After each agent turn, if the result confidence ≥ {@link #DEFAULT_CONFIDENCE_THRESHOLD},
 * the turn episode is converted to an {@link EnhancedProcedure}-compatible JSON payload
 * and stored as a learned policy.
 *
 * <p>Learning is fire-and-forget at the REFLECT phase — failures are logged but do NOT
 * surface to the user request.
 *
 * @doc.type class
 * @doc.purpose Extracts high-confidence patterns from agent turns and persists as learned policies
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 * @doc.gaa.memory procedural
 */
public class PolicyLearningService {

    private static final Logger log = LoggerFactory.getLogger(PolicyLearningService.class);

    /** Confidence threshold above which a procedure is eligible for learning. */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.9;

    private final LearnedPolicyRepository policyRepository;
    private final double                   confidenceThreshold;

    /**
     * Creates a {@link PolicyLearningService} with the default confidence threshold
     * of {@value #DEFAULT_CONFIDENCE_THRESHOLD}.
     *
     * @param policyRepository backing store for learned policies
     */
    public PolicyLearningService(@NotNull LearnedPolicyRepository policyRepository) {
        this(policyRepository, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * Creates a {@link PolicyLearningService} with a custom confidence threshold.
     *
     * @param policyRepository   backing store
     * @param confidenceThreshold minimum confidence for a procedure to be learned (0.0–1.0)
     */
    public PolicyLearningService(@NotNull LearnedPolicyRepository policyRepository,
                                  double confidenceThreshold) {
        this.policyRepository    = Objects.requireNonNull(policyRepository, "policyRepository");
        this.confidenceThreshold = confidenceThreshold;
    }

    // ─── Core learning pipeline ───────────────────────────────────────────────

    /**
     * Evaluates a list of {@link EnhancedProcedure}s extracted from episodic memory
     * and persists those above the confidence threshold as {@link LearnedPolicy} entries.
     *
     * <p>Called at the REFLECT phase of each agent turn (fire-and-forget).
     *
     * @param procedures the candidate procedures to evaluate
     * @param tenantId   the owning tenant
     * @return promise that resolves to the number of policies saved
     */
    @NotNull
    public Promise<Integer> persistHighConfidence(
            @NotNull List<EnhancedProcedure> procedures, @NotNull String tenantId) {
        if (procedures.isEmpty()) {
            return Promise.of(0);
        }

        List<EnhancedProcedure> eligible = procedures.stream()
                .filter(p -> p.getConfidence() >= confidenceThreshold)
                .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            log.debug("PolicyLearningService: no procedures above threshold {} for tenant '{}'",
                    confidenceThreshold, tenantId);
            return Promise.of(0);
        }

        log.info("PolicyLearningService: persisting {} high-confidence procedures for tenant '{}'",
                eligible.size(), tenantId);

        // Chain saves — fire-and-forget errors are logged but don't propagate
        Promise<Integer> chain = Promise.of(0);
        for (EnhancedProcedure proc : eligible) {
            LearnedPolicy policy = toLearnedPolicy(proc, tenantId);
            chain = chain.then(count ->
                    policyRepository.save(policy)
                            .map(saved -> count + 1)
                            .mapException(ex -> {
                                log.warn("PolicyLearningService: failed to save policy '{}': {}",
                                        policy.getId(), ex.getMessage());
                                return count;   // don't fail — this is fire-and-forget
                            }));
        }
        return chain;
    }

    /**
     * Retrieves the learned policies for a specific agent, usable at REASON phase.
     *
     * @param tenantId the owning tenant
     * @param agentId  the agent
     * @return promise of learned policies, newest-first
     */
    @NotNull
    public Promise<List<LearnedPolicy>> getPoliciesForAgent(
            @NotNull String tenantId, @NotNull String agentId) {
        return policyRepository.findByAgent(tenantId, agentId);
    }

    /**
     * Retrieves learned policies with confidence ≥ {@code minConfidence}.
     *
     * @param tenantId      the owning tenant
     * @param minConfidence minimum confidence (0.0–1.0)
     * @return high-confidence policies sorted by confidence descending
     */
    @NotNull
    public Promise<List<LearnedPolicy>> getHighConfidencePolicies(
            @NotNull String tenantId, double minConfidence) {
        return policyRepository.findAboveConfidence(tenantId, minConfidence);
    }

    // ─── Conversion ───────────────────────────────────────────────────────────

    /**
     * Converts an {@link EnhancedProcedure} to a {@link LearnedPolicy} for storage.
     *
     * <p>The {@link LearnedPolicy#getProcedure()} field is serialized as a simple
     * JSON representation of the procedure steps (no external serialization library
     * needed — the field is opaque string storage).
     */
    private static LearnedPolicy toLearnedPolicy(EnhancedProcedure proc, String tenantId) {
        String procedureJson = serializeSteps(proc.getSteps(), proc.getSituation(), proc.getAction());
        String source = proc.getProvenance() != null
                ? proc.getProvenance().getSource() : "agent_reflection";

        return LearnedPolicy.builder()
                .id(generateId(proc))
                .agentId(proc.getAgentId() != null ? proc.getAgentId() : "unknown")
                .name("Learned: " + abbreviate(proc.getSituation(), 60))
                .description(proc.getSituation())
                .procedure(procedureJson)
                .confidence(proc.getConfidence())
                .source(source)
                .version(proc.getVersion())
                .tenantId(tenantId)
                .createdAt(proc.getCreatedAt() != null ? proc.getCreatedAt() : Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static String generateId(EnhancedProcedure proc) {
        // Stable ID: prefer existing id; otherwise derive from agentId + situation hash
        if (proc.getId() != null && !proc.getId().isBlank()) {
            return proc.getId();
        }
        String base = Optional.ofNullable(proc.getAgentId()).orElse("anon")
                + "-" + Integer.toHexString(
                        Optional.ofNullable(proc.getSituation()).orElse("").hashCode());
        return base;
    }

    private static String serializeSteps(List<ProcedureStep> steps, String situation, String action) {
        if (steps == null || steps.isEmpty()) {
            // Fall back to simple action representation
            return "{\"situation\":\"" + escape(situation) + "\","
                    + "\"action\":\"" + escape(action) + "\","
                    + "\"steps\":[]}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"situation\":\"").append(escape(situation)).append("\",");
        sb.append("\"action\":\"").append(escape(action)).append("\",");
        sb.append("\"steps\":[");
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) sb.append(",");
            ProcedureStep s = steps.get(i);
            sb.append("{\"order\":").append(i + 1).append(",");
            sb.append("\"action\":\"").append(escape(s.getAction())).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen - 3) + "...";
    }
}
