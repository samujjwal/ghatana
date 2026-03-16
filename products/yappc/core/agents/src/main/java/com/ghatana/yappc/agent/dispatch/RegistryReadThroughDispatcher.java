/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.dispatch;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Read-through decorator over {@link CatalogAgentDispatcher} that falls back
 * to the persistent agent registry on a local cache miss.
 *
 * <h2>Read-Through Semantics</h2>
 * <pre>
 *   resolve(agentId, tenantId)
 *       │
 *       ├─ Local cache HIT  → return cached AgentRegistryRecord (fast path)
 *       │
 *       └─ Local cache MISS
 *               │
 *               └─ AgentRegistryLookup.findById(tenantId, agentId)  [JDBC]
 *                       │
 *                       ├─ Found → populate local cache → return record
 *                       │
 *                       └─ Not found → return Optional.empty()
 * </pre>
 *
 * <p>Cache population on the JDBC-found path means that a second call for the
 * same {@code agentId} will be served from the local cache without a database
 * round trip.
 *
 * <h2>ActiveJ Threading</h2>
 * All JDBC work is delegated to {@link AgentRegistryLookup}, whose contract
 * requires it to use {@code Promise.ofBlocking(executor, ...)} internally.
 * This dispatcher therefore remains non-blocking and safe to call from the
 * ActiveJ Eventloop.
 *
 * @doc.type class
 * @doc.purpose Read-through dispatcher: local cache first, JDBC fallback on miss
 * @doc.layer product
 * @doc.pattern Decorator, Cache
 */
public class RegistryReadThroughDispatcher extends CatalogAgentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RegistryReadThroughDispatcher.class);

    private final AgentRegistryLookup lookup;

    /**
     * Creates a read-through dispatcher backed by the given persistent lookup.
     *
     * @param lookup the JDBC-backed agent registry lookup (never {@code null})
     */
    public RegistryReadThroughDispatcher(@NotNull AgentRegistryLookup lookup) {
        super();
        this.lookup = Objects.requireNonNull(lookup, "lookup must not be null");
    }

    /**
     * Creates a read-through dispatcher with a custom cache TTL — intended for
     * tests that need deterministic eviction.
     *
     * @param lookup  the JDBC-backed agent registry lookup
     * @param maxSize maximum cache size
     * @param ttl     expiry duration
     * @param ttlUnit expiry time unit
     */
    public RegistryReadThroughDispatcher(
            @NotNull AgentRegistryLookup lookup,
            long maxSize,
            long ttl,
            TimeUnit ttlUnit) {
        super(maxSize, ttl, ttlUnit);
        this.lookup = Objects.requireNonNull(lookup, "lookup must not be null");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Async read-through resolve
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves an agent record, returning the local cache entry on a hit or
     * delegating to the persistent {@link AgentRegistryLookup} on a miss.
     *
     * <p>On a JDBC hit the resolved record is immediately populated into the
     * local cache so subsequent calls are served without a database round trip.
     *
     * @param agentId  the unique agent identifier
     * @param tenantId the tenant scope for security-aware lookup
     * @return a Promise of the resolved record or empty when not found anywhere
     */
    @NotNull
    public Promise<Optional<AgentRegistryRecord>> resolveAsync(
            @NotNull String agentId,
            @NotNull String tenantId) {
        Objects.requireNonNull(agentId,  "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        // Fast path — local cache hit
        Optional<AgentRegistryRecord> cached = resolve(agentId, tenantId);
        if (cached.isPresent()) {
            log.trace("Cache HIT for agent '{}' (tenant={})", agentId, tenantId);
            return Promise.of(cached);
        }

        // Slow path — JDBC read-through
        log.debug("Cache MISS for agent '{}' (tenant={}) — querying persistent registry", agentId, tenantId);
        return lookup.findById(tenantId, agentId)
                .map(optRecord -> {
                    optRecord.ifPresent(record -> {
                        cacheEntry(agentId, record);
                        log.debug("Cache populated for agent '{}' from persistent registry (tenant={})",
                                agentId, tenantId);
                    });
                    return optRecord;
                })
                .whenException(e ->
                        log.error("Persistent registry lookup failed for agent '{}' (tenant={}): {}",
                                agentId, tenantId, e.getMessage()));
    }
}
