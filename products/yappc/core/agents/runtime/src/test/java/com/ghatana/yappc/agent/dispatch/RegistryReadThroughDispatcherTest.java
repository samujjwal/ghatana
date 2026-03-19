/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.dispatch;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RegistryReadThroughDispatcher}.
 *
 * <p>Verifies the core read-through contract:
 * <ol>
 *   <li>Cache miss → JDBC lookup → cache populated → record returned.</li>
 *   <li>Cache hit → no JDBC call → record returned from cache.</li>
 *   <li>Pre-seeded entries via {@link CatalogAgentDispatcher#registerFromRegistry} never hit JDBC.</li>
 *   <li>JDBC not-found → {@code Optional.empty()} returned, cache not polluted.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Unit tests for RegistryReadThroughDispatcher read-through caching behaviour
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RegistryReadThroughDispatcher Tests")
class RegistryReadThroughDispatcherTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-alpha";
    private static final String AGENT_ID  = "requirements-analyst-v2";

    @Mock
    private AgentRegistryLookup lookup;

    private RegistryReadThroughDispatcher dispatcher;

    /** Stub record returned by the mock lookup. */
    private AgentRegistryRecord stubRecord;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Very short TTL so eviction tests don't need to wait long
        dispatcher  = new RegistryReadThroughDispatcher(lookup, 128, 5, TimeUnit.MINUTES);
        stubRecord  = AgentRegistryRecord.of(
                AGENT_ID, "Requirements Analyst", "LLM",
                List.of("requirements", "analysis"),
                TENANT_ID, "2.0.0");
    }

    // =========================================================================
    // Read-through (cache miss → JDBC → populate cache)
    // =========================================================================

    @Nested
    @DisplayName("Cache Miss → JDBC Read-Through")
    class CacheMissReadThrough {

        @Test
        @DisplayName("miss: JDBC returns record → record returned and cached")
        void missJdbcHitPopulatesCache() {
            when(lookup.findById(TENANT_ID, AGENT_ID))
                    .thenReturn(Promise.of(Optional.of(stubRecord)));

            Optional<AgentRegistryRecord> first = runPromise(() ->
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID));

            assertThat(first).contains(stubRecord);
            // JDBC was called exactly once
            verify(lookup, times(1)).findById(TENANT_ID, AGENT_ID);
            // Cache is now warm
            assertThat(dispatcher.isCached(AGENT_ID)).isTrue();
        }

        @Test
        @DisplayName("miss then hit: second call served from cache, no second JDBC call")
        void secondCallServesFromCache() {
            when(lookup.findById(TENANT_ID, AGENT_ID))
                    .thenReturn(Promise.of(Optional.of(stubRecord)));

            // First call — cache miss → JDBC
            runPromise(() -> dispatcher.resolveAsync(AGENT_ID, TENANT_ID));

            // Second call — cache hit
            Optional<AgentRegistryRecord> second = runPromise(() ->
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID));

            assertThat(second).contains(stubRecord);
            // JDBC invoked only once total
            verify(lookup, times(1)).findById(eq(TENANT_ID), eq(AGENT_ID));
        }

        @Test
        @DisplayName("miss: JDBC returns empty → Optional.empty returned, cache not polluted")
        void missJdbcNotFoundLeavesNoCacheEntry() {
            when(lookup.findById(TENANT_ID, AGENT_ID))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<AgentRegistryRecord> result = runPromise(() ->
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID));

            assertThat(result).isEmpty();
            assertThat(dispatcher.isCached(AGENT_ID)).isFalse();
        }
    }

    // =========================================================================
    // Cache hit (pre-seeded via registerFromRegistry)
    // =========================================================================

    @Nested
    @DisplayName("Pre-Seeded Cache (registerFromRegistry)")
    class PreSeededCache {

        @Test
        @DisplayName("pre-seeded entry: resolveAsync returns record without touching JDBC")
        void preSeededEntryBypassesJdbc() {
            dispatcher.registerFromRegistry(stubRecord);

            Optional<AgentRegistryRecord> result = runPromise(() ->
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID));

            assertThat(result).contains(stubRecord);
            verifyNoInteractions(lookup);
        }

        @Test
        @DisplayName("sync resolve() returns pre-seeded entry")
        void syncResolveReturnsCachedEntry() {
            dispatcher.registerFromRegistry(stubRecord);

            Optional<AgentRegistryRecord> result = dispatcher.resolve(AGENT_ID, TENANT_ID);

            assertThat(result).contains(stubRecord);
        }

        @Test
        @DisplayName("registerFromRegistry replaces an existing entry")
        void registerFromRegistryOverwritesExistingEntry() {
            dispatcher.registerFromRegistry(stubRecord);

            AgentRegistryRecord updated = AgentRegistryRecord.of(
                    AGENT_ID, "Requirements Analyst (Updated)", "LLM",
                    List.of("requirements", "analysis", "review"),
                    TENANT_ID, "2.1.0");

            dispatcher.registerFromRegistry(updated);

            Optional<AgentRegistryRecord> result = dispatcher.resolve(AGENT_ID, TENANT_ID);
            assertThat(result).contains(updated);
            assertThat(result.get().version()).isEqualTo("2.1.0");
        }
    }

    // =========================================================================
    // Capability-based resolution
    // =========================================================================

    @Nested
    @DisplayName("Capability Resolution")
    class CapabilityResolution {

        @Test
        @DisplayName("resolveByCapability returns matching pre-seeded entries for tenant")
        void resolveByCapabilityFiltersCorrectly() {
            AgentRegistryRecord architect = AgentRegistryRecord.of(
                    "architect-v1", "Architect Agent", "LLM",
                    List.of("design", "architecture"),
                    TENANT_ID, "1.0.0");
            AgentRegistryRecord analyst = AgentRegistryRecord.of(
                    "analyst-v1", "Analyst Agent", "LLM",
                    List.of("requirements", "analysis"),
                    TENANT_ID, "1.0.0");
            AgentRegistryRecord otherTenant = AgentRegistryRecord.of(
                    "analyst-other", "Other Agent", "LLM",
                    List.of("requirements"),
                    "tenant-beta", "1.0.0");

            dispatcher.registerFromRegistry(architect);
            dispatcher.registerFromRegistry(analyst);
            dispatcher.registerFromRegistry(otherTenant);

            var matches = dispatcher.resolveByCapability("requirements", TENANT_ID);

            assertThat(matches).containsExactly(analyst);
        }
    }

    // =========================================================================
    // Cache statistics
    // =========================================================================

    @Nested
    @DisplayName("Cache Statistics")
    class CacheStatistics {

        @Test
        @DisplayName("cachedEntryCount reflects registered entries")
        void cachedEntryCountMatchesRegistrations() {
            assertThat(dispatcher.cachedEntryCount()).isZero();

            dispatcher.registerFromRegistry(stubRecord);
            assertThat(dispatcher.cachedEntryCount()).isEqualTo(1);

            dispatcher.evictTenant(TENANT_ID);
            assertThat(dispatcher.cachedEntryCount()).isZero();
        }
    }
}
