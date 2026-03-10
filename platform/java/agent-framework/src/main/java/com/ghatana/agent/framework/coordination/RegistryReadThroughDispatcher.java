/*
 * Copyright (c) 2025 Ghatana Technologies
 * Platform — Agent Framework — Coordination
 */
package com.ghatana.agent.framework.coordination;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Read-through dispatcher that resolves agents from a two-level cache hierarchy.
 *
 * <p>Resolution order for a given agent ID:
 * <ol>
 *   <li><b>Local cache</b> — an in-memory {@link ConcurrentHashMap} of recently resolved
 *       {@link AgentInfo} entries (TTL-based eviction).</li>
 *   <li><b>Primary registry</b> — a synchronous lookup function supplied at construction
 *       time (e.g., backed by {@code CatalogRegistry}).</li>
 *   <li><b>Secondary registry</b> — an optional asynchronous fallback (e.g., backed by
 *       a database or remote registry API).</li>
 * </ol>
 *
 * <p>On every cache miss the resolved entry is stored in the local cache.
 * Cache entries are invalidated after {@code cacheTtl} milliseconds.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RegistryReadThroughDispatcher dispatcher = RegistryReadThroughDispatcher
 *     .builder()
 *     .primaryRegistry(agentId ->
 *         Optional.ofNullable(catalogRegistry.findById(agentId))
 *                 .map(def -> toAgentInfo(def)))
 *     .secondaryRegistry(agentId -> remoteRegistryClient.lookup(agentId))
 *     .cacheTtl(Duration.ofMinutes(5))
 *     .build();
 *
 * dispatcher.dispatch("my-agent-id", request, context)
 *     .whenResult(result -> ...);
 * }</pre>
 *
 * @param <TInput>  input type forwarded to the resolved agent
 * @param <TOutput> output type returned by the resolved agent
 *
 * @doc.type class
 * @doc.purpose Read-through agent registry dispatcher with local cache
 * @doc.layer framework
 * @doc.pattern Proxy, Cache
 * @doc.gaa.lifecycle act
 */
public class RegistryReadThroughDispatcher<TInput, TOutput> {

    private static final Logger log = LoggerFactory.getLogger(RegistryReadThroughDispatcher.class);

    /** Default cache TTL: 5 minutes. */
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    // ─── Contracts ────────────────────────────────────────────────────────────

    /**
     * Synchronous lookup function for the primary (local) registry.
     * Returns {@link Optional#empty()} when the agent is not found.
     */
    @FunctionalInterface
    public interface PrimaryLookup {
        Optional<AgentInfo> lookup(String agentId);
    }

    /**
     * Asynchronous lookup function for the secondary (remote) registry.
     * Returns a Promise of {@link Optional#empty()} when the agent is not found.
     */
    @FunctionalInterface
    public interface SecondaryLookup {
        Promise<Optional<AgentInfo>> lookup(String agentId);
    }

    /**
     * Functional interface that creates a typed agent execution from an {@link AgentInfo}.
     * Implementations should instantiate or locate the live agent and invoke it.
     */
    @FunctionalInterface
    public interface AgentExecutor<TInput, TOutput> {
        Promise<TOutput> execute(AgentInfo info, TInput input, AgentContext context);
    }

    // ─── Implementation ───────────────────────────────────────────────────────

    private final PrimaryLookup primaryLookup;
    @Nullable
    private final SecondaryLookup secondaryLookup;
    private final AgentExecutor<TInput, TOutput> executor;
    private final long cacheTtlMs;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private RegistryReadThroughDispatcher(Builder<TInput, TOutput> b) {
        this.primaryLookup = b.primaryLookup;
        this.secondaryLookup = b.secondaryLookup;
        this.executor = b.executor;
        this.cacheTtlMs = b.cacheTtl.toMillis();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Resolves the agent with the given ID and dispatches the request to it.
     *
     * @param agentId the agent to resolve and invoke
     * @param input   the input to forward to the agent
     * @param context the execution context
     * @return Promise of the agent's output
     * @throws IllegalArgumentException if the agent cannot be resolved
     */
    @NotNull
    public Promise<TOutput> dispatch(
            @NotNull String agentId,
            @NotNull TInput input,
            @NotNull AgentContext context) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        return resolveAgent(agentId)
                .then(info -> executor.execute(info, input, context));
    }

    /**
     * Explicitly removes the given agent from the local cache.
     * Useful when the registry is updated and stale entries must be cleared.
     *
     * @param agentId the agent ID to evict
     */
    public void invalidate(@NotNull String agentId) {
        cache.remove(agentId);
        log.debug("RegistryReadThroughDispatcher: invalidated cache for '{}'", agentId);
    }

    /** Clears the entire local cache. */
    public void invalidateAll() {
        int size = cache.size();
        cache.clear();
        log.info("RegistryReadThroughDispatcher: cleared {} cached entries", size);
    }

    /** Returns the number of currently cached agent info entries. */
    public int cacheSize() {
        return cache.size();
    }

    // ─── Resolution chain ─────────────────────────────────────────────────────

    private Promise<AgentInfo> resolveAgent(String agentId) {
        // 1. Check local cache
        CacheEntry cached = cache.get(agentId);
        if (cached != null && !cached.isExpired(cacheTtlMs)) {
            log.debug("RegistryReadThroughDispatcher: cache hit for '{}'", agentId);
            return Promise.of(cached.info);
        }

        // 2. Primary lookup
        Optional<AgentInfo> primary = primaryLookup.lookup(agentId);
        if (primary.isPresent()) {
            log.debug("RegistryReadThroughDispatcher: primary hit for '{}'", agentId);
            cache.put(agentId, new CacheEntry(primary.get()));
            return Promise.of(primary.get());
        }

        // 3. Secondary lookup (async, optional)
        if (secondaryLookup == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Agent '" + agentId + "' not found in any registry"));
        }

        log.debug("RegistryReadThroughDispatcher: primary miss for '{}', falling back to secondary", agentId);
        return secondaryLookup.lookup(agentId)
                .map(opt -> {
                    if (opt.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Agent '" + agentId + "' not found in primary or secondary registry");
                    }
                    AgentInfo info = opt.get();
                    cache.put(agentId, new CacheEntry(info));
                    log.debug("RegistryReadThroughDispatcher: secondary hit for '{}', cached", agentId);
                    return info;
                });
    }

    // ─── Cache entry ──────────────────────────────────────────────────────────

    private static final class CacheEntry {
        final AgentInfo info;
        final Instant cachedAt;

        CacheEntry(AgentInfo info) {
            this.info = info;
            this.cachedAt = Instant.now();
        }

        boolean isExpired(long ttlMs) {
            return Duration.between(cachedAt, Instant.now()).toMillis() > ttlMs;
        }
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Creates a new builder.
     *
     * @param <I> input type
     * @param <O> output type
     */
    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    /**
     * Fluent builder for {@link RegistryReadThroughDispatcher}.
     */
    public static final class Builder<I, O> {
        private PrimaryLookup primaryLookup;
        private SecondaryLookup secondaryLookup;
        private AgentExecutor<I, O> executor;
        private Duration cacheTtl = DEFAULT_CACHE_TTL;

        private Builder() {}

        /**
         * Sets the synchronous primary (local) registry lookup.
         * Required.
         */
        public Builder<I, O> primaryRegistry(@NotNull PrimaryLookup lookup) {
            this.primaryLookup = Objects.requireNonNull(lookup);
            return this;
        }

        /**
         * Sets the optional asynchronous secondary (remote) registry lookup.
         */
        public Builder<I, O> secondaryRegistry(@Nullable SecondaryLookup lookup) {
            this.secondaryLookup = lookup;
            return this;
        }

        /**
         * Sets the executor that invokes a resolved agent.
         * Required.
         */
        public Builder<I, O> executor(@NotNull AgentExecutor<I, O> executor) {
            this.executor = Objects.requireNonNull(executor);
            return this;
        }

        /**
         * Sets the local cache TTL (default: {@link #DEFAULT_CACHE_TTL}).
         */
        public Builder<I, O> cacheTtl(@NotNull Duration ttl) {
            this.cacheTtl = Objects.requireNonNull(ttl);
            return this;
        }

        /**
         * Creates the dispatcher.
         *
         * @throws IllegalStateException if required fields are missing
         */
        @NotNull
        public RegistryReadThroughDispatcher<I, O> build() {
            Objects.requireNonNull(primaryLookup, "primaryRegistry is required");
            Objects.requireNonNull(executor, "executor is required");
            return new RegistryReadThroughDispatcher<>(this);
        }
    }
}
