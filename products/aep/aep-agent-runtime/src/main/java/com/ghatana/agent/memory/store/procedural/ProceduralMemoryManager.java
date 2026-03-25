package com.ghatana.agent.memory.store.procedural;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.procedure.ProcedureStep;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Manages the procedural memory tier — procedures (action sequences) learned
 * from successful episodes.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Procedure storage with step ordering</li>
 *   <li>Situation-based selection via semantic search</li>
 *   <li>Confidence advancement from successful executions</li>
 *   <li>Version management for evolving procedures</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Procedural memory management
 * @doc.layer agent-memory
 */
public class ProceduralMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(ProceduralMemoryManager.class);

    private final MemoryPlane memoryPlane;
    private final ProcedureSelector selector;

    public ProceduralMemoryManager(
            @NotNull MemoryPlane memoryPlane,
            @NotNull ProcedureSelector selector) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    /**
     * Stores or updates a procedure. If a matching procedure exists
     * (similar situation), merges steps and bumps confidence.
     */
    @NotNull
    public Promise<EnhancedProcedure> storeOrMerge(@NotNull EnhancedProcedure procedure) {
        return memoryPlane.queryProcedures(MemoryQuery.builder().limit(20).build())
                .then(existingProcs -> {
                    Optional<EnhancedProcedure> match = existingProcs.stream()
                            .filter(p -> situationMatches(p, procedure))
                            .findFirst();

                    if (match.isPresent()) {
                        EnhancedProcedure existing = match.get();
                        log.debug("Merging with existing procedure: {}", existing.getId());
                        EnhancedProcedure merged = mergeProcedures(existing, procedure);
                        return memoryPlane.storeProcedure(merged);
                    }
                    return memoryPlane.storeProcedure(procedure);
                });
    }

    /**
     * Selects the best procedure for a given situation description.
     */
    @NotNull
    public Promise<Optional<EnhancedProcedure>> selectForSituation(@NotNull String situation) {
        return selector.selectBest(situation);
    }

    /**
     * Records a successful execution, boosting the procedure's confidence.
     */
    @NotNull
    public Promise<EnhancedProcedure> recordSuccess(@NotNull String procedureId) {
        return memoryPlane.getProcedure(procedureId)
                .then(proc -> {
                    if (proc == null) {
                        return Promise.ofException(
                                new IllegalArgumentException("Procedure not found: " + procedureId));
                    }
                    EnhancedProcedure boosted = EnhancedProcedure.builder()
                            .id(proc.getId())
                            .agentId(proc.getAgentId())
                            .situation(proc.getSituation())
                            .action(proc.getAction())
                            .steps(proc.getSteps())
                            .confidence(Math.min(1.0, proc.getConfidence() + 0.03))
                            .useCount(proc.getUseCount() + 1)
                            .lastUsedAt(Instant.now())
                            .tags(proc.getTags())
                            .labels(proc.getLabels())
                            .provenance(proc.getProvenance())
                            .validity(proc.getValidity())
                            .versionHistory(proc.getVersionHistory())
                            .createdAt(proc.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();
                    return memoryPlane.storeProcedure(boosted);
                });
    }

    /**
     * Records a failed execution, decreasing the procedure's confidence.
     */
    @NotNull
    public Promise<EnhancedProcedure> recordFailure(@NotNull String procedureId, @NotNull String reason) {
        return memoryPlane.getProcedure(procedureId)
                .then(proc -> {
                    if (proc == null) {
                        return Promise.ofException(
                                new IllegalArgumentException("Procedure not found: " + procedureId));
                    }
                    log.info("Recording failure for procedure {}: {}", procedureId, reason);
                    EnhancedProcedure degraded = EnhancedProcedure.builder()
                            .id(proc.getId())
                            .agentId(proc.getAgentId())
                            .situation(proc.getSituation())
                            .action(proc.getAction())
                            .steps(proc.getSteps())
                            .confidence(Math.max(0.0, proc.getConfidence() - 0.1))
                            .useCount(proc.getUseCount() + 1)
                            .lastUsedAt(Instant.now())
                            .tags(proc.getTags())
                            .labels(proc.getLabels())
                            .provenance(proc.getProvenance())
                            .validity(proc.getValidity())
                            .versionHistory(proc.getVersionHistory())
                            .createdAt(proc.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();
                    return memoryPlane.storeProcedure(degraded);
                });
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private boolean situationMatches(EnhancedProcedure a, EnhancedProcedure b) {
        // Simple string comparison; in production, use embedding similarity
        return a.getSituation().equalsIgnoreCase(b.getSituation());
    }

    private EnhancedProcedure mergeProcedures(EnhancedProcedure existing, EnhancedProcedure incoming) {
        // Take the steps of the newer procedure (higher confidence wins)
        List<ProcedureStep> steps = incoming.getConfidence() >= existing.getConfidence()
                ? incoming.getSteps()
                : existing.getSteps();

        return EnhancedProcedure.builder()
                .id(existing.getId())
                .agentId(existing.getAgentId())
                .situation(existing.getSituation())
                .action(incoming.getAction() != null ? incoming.getAction() : existing.getAction())
                .steps(steps)
                .confidence(Math.max(existing.getConfidence(), incoming.getConfidence()))
                .useCount(existing.getUseCount() + incoming.getUseCount())
                .lastUsedAt(Instant.now())
                .tags(existing.getTags())
                .labels(existing.getLabels())
                .provenance(incoming.getProvenance())
                .validity(existing.getValidity())
                .versionHistory(existing.getVersionHistory())
                .createdAt(existing.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
    }
}
