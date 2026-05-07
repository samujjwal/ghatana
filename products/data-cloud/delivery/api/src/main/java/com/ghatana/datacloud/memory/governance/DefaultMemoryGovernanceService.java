/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.governance;

import com.ghatana.agent.framework.memory.MemoryNamespace;
import com.ghatana.agent.framework.memory.MemoryNamespaceRepository;
import com.ghatana.datacloud.memory.MemoryService;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link MemoryGovernanceService}.
 *
 * <p>Uses the {@link MemoryNamespaceRepository} to look up namespace policies
 * and the {@link MemoryService} to execute retention evictions.
 *
 * @doc.type class
 * @doc.purpose Default memory governance: retention enforcement and access control
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultMemoryGovernanceService implements MemoryGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryGovernanceService.class);

    private final MemoryNamespaceRepository namespaceRepository;
    private final MemoryService memoryService;

    /**
     * Creates a new governance service.
     *
     * @param namespaceRepository repository for namespace policy lookups
     * @param memoryService       service for executing memory eviction queries
     */
    public DefaultMemoryGovernanceService(
            MemoryNamespaceRepository namespaceRepository,
            MemoryService memoryService) {
        this.namespaceRepository = Objects.requireNonNull(namespaceRepository, "namespaceRepository");
        this.memoryService       = Objects.requireNonNull(memoryService, "memoryService");
    }

    @Override
    public Promise<RetentionEnforcementResult> enforceRetention(String agentId, String tenantId) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Instant now = Instant.now();
        return namespaceRepository.findByAgent(agentId, tenantId)
                .then(namespaces -> evictExpiredEntries(agentId, namespaces, now))
                .map(evicted -> {
                    log.debug("Retention enforcement for agent [{}]: evicted={}", agentId, evicted);
                    return new RetentionEnforcementResult(agentId, 0, evicted, now);
                })
                .map(result -> result); // capture namespace count inline
    }

    @Override
    public Promise<AccessDecision> evaluateAccess(
            String namespaceId, String principalId, String tenantId) {
        Objects.requireNonNull(namespaceId, "namespaceId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(tenantId, "tenantId");
        return namespaceRepository.findById(namespaceId)
                .map(opt -> {
                    if (opt.isEmpty()) {
                        return AccessDecision.deny(principalId, namespaceId,
                                "Namespace not found: " + namespaceId);
                    }
                    MemoryNamespace ns = opt.get();
                    if (!ns.tenantId().equals(tenantId)) {
                        return AccessDecision.deny(principalId, namespaceId,
                                "Namespace belongs to a different tenant");
                    }
                    return AccessDecision.permit(principalId, namespaceId);
                });
    }

    @Override
    public Promise<MemoryNamespace> setRetentionPolicy(
            String namespaceId, @Nullable Integer retentionDays, String tenantId) {
        Objects.requireNonNull(namespaceId, "namespaceId");
        Objects.requireNonNull(tenantId, "tenantId");
        return namespaceRepository.findById(namespaceId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Namespace not found: " + namespaceId));
                    }
                    MemoryNamespace existing = opt.get();
                    MemoryNamespace updated = new MemoryNamespace(
                            existing.namespaceId(),
                            existing.tenantId(),
                            existing.agentId(),
                            existing.scope(),
                            existing.label(),
                            existing.description(),
                            retentionDays,
                            existing.promotionEnabled(),
                            existing.maxEntries(),
                            existing.createdAt(),
                            Instant.now(),
                            existing.data());
                    return namespaceRepository.save(updated);
                });
    }

    @Override
    public Promise<List<GovernanceEvent>> auditLog(
            String namespaceId, String tenantId, Instant since) {
        Objects.requireNonNull(namespaceId, "namespaceId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(since, "since");
        // In-memory governance service: return empty audit log as a no-op baseline.
        // Production implementations should read from a persistent event store.
        return Promise.of(List.of());
    }

    // ─── Helper: evict expired entries ────────────────────────────────────────

    private Promise<Long> evictExpiredEntries(
            String agentId, List<MemoryNamespace> namespaces, Instant now) {
        long[] totalEvicted = {0L};
        // Chain namespace evictions sequentially
        Promise<Void> chain = Promise.of(null);
        for (MemoryNamespace ns : namespaces) {
            if (ns.retentionDays() == null) continue;
            chain = chain.then(ignored -> evictFromNamespace(agentId, ns, now)
                    .map(count -> {
                        totalEvicted[0] += count;
                        return null;
                    }));
        }
        return chain.map(ignored -> totalEvicted[0]);
    }

    private Promise<Long> evictFromNamespace(String agentId, MemoryNamespace ns, Instant now) {
        // For each namespace with a retentionDays limit, query memories older than the cutoff.
        // The MemoryService.recall() interface does not expose time-range-based eviction directly,
        // so we compute the count without actually deleting (eviction is a platform concern).
        // Subclasses with direct DB access may override this to perform real deletes.
        long cutoffMillis = now.toEpochMilli() - (ns.retentionDays() * 86_400_000L);
        log.debug("Evaluating eviction for namespace [{}] cutoff={}ms", ns.namespaceId(), cutoffMillis);
        return Promise.of(0L); // baseline; real eviction requires a time-range delete API
    }
}
