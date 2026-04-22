/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
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
 *   <li>JDBC not-found → {@code Optional.empty()} returned, cache not polluted.</li> // GH-90000
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Unit tests for RegistryReadThroughDispatcher read-through caching behaviour
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RegistryReadThroughDispatcher Tests [GH-90000]")
class RegistryReadThroughDispatcherTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-alpha";
    private static final String AGENT_ID  = "requirements-analyst-v2";

    @Mock
    private AgentRegistryLookup lookup;

    private RegistryReadThroughDispatcher dispatcher;

    /** Stub record returned by the mock lookup. */
    private AgentRegistryRecord stubRecord;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        // Very short TTL so eviction tests don't need to wait long
        dispatcher  = new RegistryReadThroughDispatcher(lookup, 128, 5, TimeUnit.MINUTES); // GH-90000
        stubRecord  = AgentRegistryRecord.of( // GH-90000
                AGENT_ID, "Requirements Analyst", "LLM",
                List.of("requirements", "analysis"), // GH-90000
                TENANT_ID, "2.0.0");
    }

    // =========================================================================
    // Read-through (cache miss → JDBC → populate cache) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Cache Miss → JDBC Read-Through [GH-90000]")
    class CacheMissReadThrough {

        @Test
        @DisplayName("miss: JDBC returns record → record returned and cached [GH-90000]")
        void missJdbcHitPopulatesCache() { // GH-90000
            when(lookup.findById(TENANT_ID, AGENT_ID)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(stubRecord))); // GH-90000

            Optional<AgentRegistryRecord> first = runPromise(() -> // GH-90000
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID)); // GH-90000

            assertThat(first).contains(stubRecord); // GH-90000
            // JDBC was called exactly once
            verify(lookup, times(1)).findById(TENANT_ID, AGENT_ID); // GH-90000
            // Cache is now warm
            assertThat(dispatcher.isCached(AGENT_ID)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("miss then hit: second call served from cache, no second JDBC call [GH-90000]")
        void secondCallServesFromCache() { // GH-90000
            when(lookup.findById(TENANT_ID, AGENT_ID)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(stubRecord))); // GH-90000

            // First call — cache miss → JDBC
            runPromise(() -> dispatcher.resolveAsync(AGENT_ID, TENANT_ID)); // GH-90000

            // Second call — cache hit
            Optional<AgentRegistryRecord> second = runPromise(() -> // GH-90000
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID)); // GH-90000

            assertThat(second).contains(stubRecord); // GH-90000
            // JDBC invoked only once total
            verify(lookup, times(1)).findById(eq(TENANT_ID), eq(AGENT_ID)); // GH-90000
        }

        @Test
        @DisplayName("miss: JDBC returns empty → Optional.empty returned, cache not polluted [GH-90000]")
        void missJdbcNotFoundLeavesNoCacheEntry() { // GH-90000
            when(lookup.findById(TENANT_ID, AGENT_ID)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<AgentRegistryRecord> result = runPromise(() -> // GH-90000
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
            assertThat(dispatcher.isCached(AGENT_ID)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Cache hit (pre-seeded via registerFromRegistry) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Pre-Seeded Cache (registerFromRegistry) [GH-90000]")
    class PreSeededCache {

        @Test
        @DisplayName("pre-seeded entry: resolveAsync returns record without touching JDBC [GH-90000]")
        void preSeededEntryBypassesJdbc() { // GH-90000
            dispatcher.registerFromRegistry(stubRecord); // GH-90000

            Optional<AgentRegistryRecord> result = runPromise(() -> // GH-90000
                    dispatcher.resolveAsync(AGENT_ID, TENANT_ID)); // GH-90000

            assertThat(result).contains(stubRecord); // GH-90000
            verifyNoInteractions(lookup); // GH-90000
        }

        @Test
        @DisplayName("sync resolve() returns pre-seeded entry [GH-90000]")
        void syncResolveReturnsCachedEntry() { // GH-90000
            dispatcher.registerFromRegistry(stubRecord); // GH-90000

            Optional<AgentRegistryRecord> result = dispatcher.resolve(AGENT_ID, TENANT_ID); // GH-90000

            assertThat(result).contains(stubRecord); // GH-90000
        }

        @Test
        @DisplayName("registerFromRegistry replaces an existing entry [GH-90000]")
        void registerFromRegistryOverwritesExistingEntry() { // GH-90000
            dispatcher.registerFromRegistry(stubRecord); // GH-90000

            AgentRegistryRecord updated = AgentRegistryRecord.of( // GH-90000
                    AGENT_ID, "Requirements Analyst (Updated)", "LLM", // GH-90000
                    List.of("requirements", "analysis", "review"), // GH-90000
                    TENANT_ID, "2.1.0");

            dispatcher.registerFromRegistry(updated); // GH-90000

            Optional<AgentRegistryRecord> result = dispatcher.resolve(AGENT_ID, TENANT_ID); // GH-90000
            assertThat(result).contains(updated); // GH-90000
            assertThat(result.get().version()).isEqualTo("2.1.0 [GH-90000]");
        }
    }

    // =========================================================================
    // Capability-based resolution
    // =========================================================================

    @Nested
    @DisplayName("Capability Resolution [GH-90000]")
    class CapabilityResolution {

        @Test
        @DisplayName("resolveByCapability returns matching pre-seeded entries for tenant [GH-90000]")
        void resolveByCapabilityFiltersCorrectly() { // GH-90000
            AgentRegistryRecord architect = AgentRegistryRecord.of( // GH-90000
                    "architect-v1", "Architect Agent", "LLM",
                    List.of("design", "architecture"), // GH-90000
                    TENANT_ID, "1.0.0");
            AgentRegistryRecord analyst = AgentRegistryRecord.of( // GH-90000
                    "analyst-v1", "Analyst Agent", "LLM",
                    List.of("requirements", "analysis"), // GH-90000
                    TENANT_ID, "1.0.0");
            AgentRegistryRecord otherTenant = AgentRegistryRecord.of( // GH-90000
                    "analyst-other", "Other Agent", "LLM",
                    List.of("requirements [GH-90000]"),
                    "tenant-beta", "1.0.0");

            dispatcher.registerFromRegistry(architect); // GH-90000
            dispatcher.registerFromRegistry(analyst); // GH-90000
            dispatcher.registerFromRegistry(otherTenant); // GH-90000

            var matches = dispatcher.resolveByCapability("requirements", TENANT_ID); // GH-90000

            assertThat(matches).containsExactly(analyst); // GH-90000
        }
    }

    // =========================================================================
    // Cache statistics
    // =========================================================================

    @Nested
    @DisplayName("Cache Statistics [GH-90000]")
    class CacheStatistics {

        @Test
        @DisplayName("cachedEntryCount reflects registered entries [GH-90000]")
        void cachedEntryCountMatchesRegistrations() { // GH-90000
            assertThat(dispatcher.cachedEntryCount()).isZero(); // GH-90000

            dispatcher.registerFromRegistry(stubRecord); // GH-90000
            assertThat(dispatcher.cachedEntryCount()).isEqualTo(1); // GH-90000

            dispatcher.evictTenant(TENANT_ID); // GH-90000
            assertThat(dispatcher.cachedEntryCount()).isZero(); // GH-90000
        }
    }
}
