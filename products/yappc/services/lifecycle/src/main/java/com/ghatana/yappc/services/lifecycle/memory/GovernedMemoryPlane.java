package com.ghatana.yappc.services.lifecycle.memory;

import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.security.MemoryRedactionFilter;
import com.ghatana.agent.memory.security.MemorySecurityManager;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Governance decorator for {@link MemoryPlane} that enforces data redaction before
 * persistence and tenant-based access control before returning results.
 *
 * <p>Applied unconditionally at the YAPPC lifecycle service boundary. All store
 * operations pass through {@link MemoryRedactionFilter} to strip PII and credentials
 * before handing off to the delegate {@code PersistentMemoryPlane}. All read / query
 * operations pass results through {@link MemorySecurityManager} to enforce tenant
 * isolation.
 *
 * <p><b>Design note</b>: This is a pure Decorator — it has no persistent state of its
 * own and delegates every method to the wrapped plane. The security checks are
 * synchronous and execute in the caller's thread before or after the async delegate call.
 *
 * @doc.type class
 * @doc.purpose Memory governance layer — redaction before write, isolation before read
 * @doc.layer product
 * @doc.pattern Decorator
 * @doc.gaa.memory episodic
 * @doc.gaa.lifecycle perceive
 */
public class GovernedMemoryPlane implements MemoryPlane {

    private static final Logger log = LoggerFactory.getLogger(GovernedMemoryPlane.class);

    private final MemoryPlane delegate;
    private final MemoryRedactionFilter redactionFilter;
    private final MemorySecurityManager securityManager;

    /**
     * @param delegate         the underlying persistent memory plane
     * @param redactionFilter  PII / credential redaction filter to apply before writes
     * @param securityManager  tenant-isolation guard applied after reads
     */
    public GovernedMemoryPlane(
            @NotNull MemoryPlane delegate,
            @NotNull MemoryRedactionFilter redactionFilter,
            @NotNull MemorySecurityManager securityManager) {
        this.delegate = delegate;
        this.redactionFilter = redactionFilter;
        this.securityManager = securityManager;
    }

    // =========================================================================
    // Episodic Memory — redact before write, filter after read
    // =========================================================================

    @Override
    public @NotNull Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        String tenantId = TenantContext.getCurrentTenantId();
        String agentId = episode.getAgentId();
        if (!securityManager.canWrite(episode, tenantId, agentId)) {
            return Promise.ofException(new SecurityException(
                    "Memory write denied: tenant mismatch for episode " + episode.getId()));
        }
        return delegate.storeEpisode(redactEpisode(episode));
    }

    @Override
    public @NotNull Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        String tenantId = TenantContext.getCurrentTenantId();
        return delegate.queryEpisodes(query)
                .map(episodes -> episodes.stream()
                        .filter(ep -> securityManager.canRead(ep, tenantId, resolveAgentId(query)))
                        .collect(Collectors.toList()));
    }

    // =========================================================================
    // Semantic Memory (Facts) — redact before write, filter after read
    // =========================================================================

    @Override
    public @NotNull Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        String tenantId = TenantContext.getCurrentTenantId();
        String agentId = fact.getAgentId() != null ? fact.getAgentId() : "system";
        if (!securityManager.canWrite(fact, tenantId, agentId)) {
            return Promise.ofException(new SecurityException(
                    "Memory write denied: tenant mismatch for fact " + fact.getId()));
        }
        return delegate.storeFact(redactFact(fact));
    }

    @Override
    public @NotNull Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        String tenantId = TenantContext.getCurrentTenantId();
        return delegate.queryFacts(query)
                .map(facts -> facts.stream()
                        .filter(f -> securityManager.canRead(f, tenantId, resolveAgentId(query)))
                        .collect(Collectors.toList()));
    }

    // =========================================================================
    // Procedural Memory — redact before write, filter after read
    // =========================================================================

    @Override
    public @NotNull Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        String tenantId = TenantContext.getCurrentTenantId();
        String agentId = procedure.getAgentId() != null ? procedure.getAgentId() : "system";
        if (!securityManager.canWrite(procedure, tenantId, agentId)) {
            return Promise.ofException(new SecurityException(
                    "Memory write denied: tenant mismatch for procedure " + procedure.getId()));
        }
        return delegate.storeProcedure(redactProcedure(procedure));
    }

    @Override
    public @NotNull Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        String tenantId = TenantContext.getCurrentTenantId();
        return delegate.queryProcedures(query)
                .map(procs -> procs.stream()
                        .filter(p -> securityManager.canRead(p, tenantId, resolveAgentId(query)))
                        .collect(Collectors.toList()));
    }

    @Override
    public @NotNull Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return delegate.getProcedure(procedureId)
                .map(proc -> {
                    if (proc == null) return null;
                    String agentId = proc.getAgentId() != null ? proc.getAgentId() : "system";
                    if (!securityManager.canRead(proc, tenantId, agentId)) {
                        log.warn("Procedure '{}' access denied for tenant '{}'", procedureId, tenantId);
                        return null;
                    }
                    return proc;
                });
    }

    // =========================================================================
    // Typed Artifacts — delegate directly (binary content, not redacted)
    // =========================================================================

    @Override
    public @NotNull Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        return delegate.writeArtifact(artifact);
    }

    // =========================================================================
    // Cross-tier Query + Semantic Search
    // =========================================================================

    @Override
    public @NotNull Promise<MemoryItem> store(@NotNull MemoryItem item) {
        // Dispatch to typed method for redaction; fall back to delegate for unknown types
        return switch (item.getType()) {
            case EPISODE -> storeEpisode((EnhancedEpisode) item).map(ep -> ep);
            case FACT -> storeFact((EnhancedFact) item).map(f -> f);
            case PROCEDURE -> storeProcedure((EnhancedProcedure) item).map(p -> p);
            default -> delegate.store(item);
        };
    }

    @Override
    public @NotNull Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        String tenantId = TenantContext.getCurrentTenantId();
        return delegate.query(query)
                .map(items -> items.stream()
                        .filter(item -> securityManager.canRead(item, tenantId, resolveAgentId(query)))
                        .collect(Collectors.toList()));
    }

    @Override
    public @NotNull Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        String tenantId = TenantContext.getCurrentTenantId();
        return delegate.readItems(query)
                .map(items -> items.stream()
                        .filter(item -> securityManager.canRead(item, tenantId, resolveAgentId(query)))
                        .collect(Collectors.toList()));
    }

    @Override
    public @NotNull Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (!securityManager.canSearch(tenantId, "system")) {
            return Promise.ofException(new SecurityException(
                    "Semantic search denied for tenant: " + tenantId));
        }
        return delegate.searchSemantic(query, itemTypes, k, startTime, endTime)
                .map(results -> results.stream()
                        .filter(scored -> securityManager.canRead(scored.getItem(), tenantId, "system"))
                        .collect(Collectors.toList()));
    }

    // =========================================================================
    // Working Memory + Task State — pure delegation (tenant scoped at construction)
    // =========================================================================

    @Override
    public @NotNull WorkingMemory getWorkingMemory() {
        return delegate.getWorkingMemory();
    }

    @Override
    public @NotNull TaskStateStore getTaskStateStore() {
        return delegate.getTaskStateStore();
    }

    // =========================================================================
    // Lifecycle — pure delegation
    // =========================================================================

    @Override
    public @NotNull Promise<String> checkpoint(@NotNull String taskId) {
        return delegate.checkpoint(taskId);
    }

    @Override
    public @NotNull Promise<MemoryPlaneStats> getStats() {
        return delegate.getStats();
    }

    // =========================================================================
    // Redaction helpers — rebuild immutable value objects with redacted fields
    // =========================================================================

    /**
     * Returns a copy of {@code episode} with {@code input}, {@code output}, and
     * {@code action} fields run through the {@link MemoryRedactionFilter}.
     */
    private EnhancedEpisode redactEpisode(EnhancedEpisode ep) {
        return EnhancedEpisode.builder()
                .id(ep.getId())
                .type(ep.getType())
                .createdAt(ep.getCreatedAt())
                .updatedAt(ep.getUpdatedAt())
                .expiresAt(ep.getExpiresAt())
                .provenance(ep.getProvenance())
                .embedding(ep.getEmbedding())
                .validity(ep.getValidity())
                .links(ep.getLinks())
                .labels(ep.getLabels())
                .tenantId(ep.getTenantId())
                .sphereId(ep.getSphereId())
                .classification(ep.getClassification())
                .agentId(ep.getAgentId())
                .turnId(ep.getTurnId())
                .timestamp(ep.getTimestamp())
                .input(redactionFilter.redact(ep.getInput()))
                .output(redactionFilter.redact(ep.getOutput()))
                .action(ep.getAction() != null ? redactionFilter.redact(ep.getAction()) : null)
                .context(ep.getContext())
                .tags(ep.getTags())
                .reward(ep.getReward())
                .toolExecutions(ep.getToolExecutions())
                .cost(ep.getCost())
                .latencyMs(ep.getLatencyMs())
                .redactionLevel("APPLIED")
                .build();
    }

    /**
     * Returns a copy of {@code fact} with {@code subject}, {@code predicate},
     * and {@code object} fields run through the {@link MemoryRedactionFilter}.
     */
    private EnhancedFact redactFact(EnhancedFact fact) {
        return EnhancedFact.builder()
                .id(fact.getId())
                .type(fact.getType())
                .createdAt(fact.getCreatedAt())
                .updatedAt(fact.getUpdatedAt())
                .expiresAt(fact.getExpiresAt())
                .provenance(fact.getProvenance())
                .embedding(fact.getEmbedding())
                .validity(fact.getValidity())
                .links(fact.getLinks())
                .labels(fact.getLabels())
                .tenantId(fact.getTenantId())
                .sphereId(fact.getSphereId())
                .classification(fact.getClassification())
                .agentId(fact.getAgentId())
                .confidence(fact.getConfidence())
                .subject(redactionFilter.redact(fact.getSubject()))
                .predicate(redactionFilter.redact(fact.getPredicate()))
                .object(redactionFilter.redact(fact.getObject()))
                .source(fact.getSource())
                .learnedAt(fact.getLearnedAt())
                .version(fact.getVersion())
                .versionHistory(fact.getVersionHistory())
                .build();
    }

    /**
     * Returns a copy of {@code procedure} with {@code situation} and {@code action}
     * fields run through the {@link MemoryRedactionFilter}.
     */
    private EnhancedProcedure redactProcedure(EnhancedProcedure proc) {
        return EnhancedProcedure.builder()
                .id(proc.getId())
                .type(proc.getType())
                .createdAt(proc.getCreatedAt())
                .updatedAt(proc.getUpdatedAt())
                .expiresAt(proc.getExpiresAt())
                .provenance(proc.getProvenance())
                .embedding(proc.getEmbedding())
                .validity(proc.getValidity())
                .links(proc.getLinks())
                .labels(proc.getLabels())
                .tenantId(proc.getTenantId())
                .sphereId(proc.getSphereId())
                .classification(proc.getClassification())
                .agentId(proc.getAgentId())
                .confidence(proc.getConfidence())
                .tags(proc.getTags())
                .lastUsedAt(proc.getLastUsedAt())
                .situation(redactionFilter.redact(proc.getSituation()))
                .action(redactionFilter.redact(proc.getAction()))
                .useCount(proc.getUseCount())
                .learnedFromEpisodes(proc.getLearnedFromEpisodes())
                .version(proc.getVersion())
                .steps(proc.getSteps())
                .successRate(proc.getSuccessRate())
                .prerequisites(proc.getPrerequisites())
                .environmentConstraints(proc.getEnvironmentConstraints())
                .versionHistory(proc.getVersionHistory())
                .build();
    }

    /** Resolves an agent ID from a query for use in security checks. Falls back to "system". */
    private String resolveAgentId(@NotNull MemoryQuery query) {
        return query.getAgentId() != null ? query.getAgentId() : "system";
    }
}
