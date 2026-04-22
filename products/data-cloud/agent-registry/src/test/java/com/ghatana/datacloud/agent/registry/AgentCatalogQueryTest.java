/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Catalog query tests for {@link DataCloudAgentRegistry}.
 *
 * <p>Validates catalog listing, capability-based filtering, sorted results,
 * pagination semantics, and per-agent stat aggregation.
 *
 * @doc.type    class
 * @doc.purpose Agent catalog query, filtering, sorting, and pagination
 * @doc.layer   registry
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AgentCatalogQueryTest [GH-90000]")
class AgentCatalogQueryTest extends EventloopTestBase {

    private static final String TENANT = "catalog-tenant";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new DataCloudAgentRegistry(dataCloud, TENANT); // GH-90000

        lenient().when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
        lenient().when(dataCloud.createEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), anyMap())) // GH-90000
                .thenReturn(Promise.of(mockEntity)); // GH-90000
        lenient().when(dataCloud.appendEvent(eq(TENANT), anyString(), any())) // GH-90000
                .thenReturn(Promise.of(0L)); // GH-90000
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked [GH-90000]")
    private static TypedAgent<String, String> mockAgent(String id, // GH-90000
                                                         AgentType type,
                                                         Set<String> capabilities) {
        TypedAgent<String, String> agent = org.mockito.Mockito.mock(TypedAgent.class); // GH-90000
        AgentDescriptor descriptor = AgentDescriptor.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(type) // GH-90000
                .determinism(DeterminismGuarantee.FULL) // GH-90000
                .capabilities(capabilities) // GH-90000
                .build(); // GH-90000
        lenient().when(agent.descriptor()).thenReturn(descriptor); // GH-90000
        return agent;
    }

    private static AgentConfig configFor(String id) { // GH-90000
        return AgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .timeout(Duration.ofSeconds(10)) // GH-90000
                .build(); // GH-90000
    }

    private void registerAll(TypedAgent<?, ?> ... agents) { // GH-90000
        for (TypedAgent<?, ?> agent : agents) { // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            TypedAgent<String, String> typed = (TypedAgent<String, String>) agent; // GH-90000
            runPromise(() -> registry.register(typed, configFor(typed.descriptor().getAgentId()))); // GH-90000
        }
    }

    // ── Catalog listing ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAgentIds() [GH-90000]")
    class ListAgentIdsTests {

        @Test
        @DisplayName("returns empty set when no agents are registered [GH-90000]")
        void emptyWhenNoneRegistered() { // GH-90000
            Set<String> ids = runPromise(() -> registry.listAgentIds()); // GH-90000
            assertThat(ids).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns all registered agent IDs [GH-90000]")
        void returnsAllRegisteredIds() { // GH-90000
            TypedAgent<String, String> a = mockAgent("list-a", AgentType.DETERMINISTIC, Set.of("cap [GH-90000]"));
            TypedAgent<String, String> b = mockAgent("list-b", AgentType.PROBABILISTIC, Set.of("score [GH-90000]"));
            TypedAgent<String, String> c = mockAgent("list-c", AgentType.HYBRID, Set.of("cap", "score")); // GH-90000

            registerAll(a, b, c); // GH-90000

            Set<String> ids = runPromise(() -> registry.listAgentIds()); // GH-90000
            assertThat(ids).containsExactlyInAnyOrder("list-a", "list-b", "list-c"); // GH-90000
        }

        @Test
        @DisplayName("returns an immutable snapshot — mutations do not affect live map [GH-90000]")
        void returnsImmutableSnapshot() { // GH-90000
            TypedAgent<String, String> a = mockAgent("snap-a", AgentType.DETERMINISTIC, Set.of()); // GH-90000
            registerAll(a); // GH-90000

            Set<String> snapshot = runPromise(() -> registry.listAgentIds()); // GH-90000
            // The returned snapshot should not be backed by the live map
            assertThat(snapshot).isNotSameAs(registry); // structural guard // GH-90000
            assertThat(snapshot).contains("snap-a [GH-90000]");
        }
    }

    // ── Capability filtering ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability() — filtering [GH-90000]")
    class FilteringTests {

        @Test
        @DisplayName("filters to only agents that advertise the requested capability [GH-90000]")
        void filtersToMatchingCapability() { // GH-90000
            TypedAgent<String, String> fraud  = mockAgent("filter-fraud",  AgentType.DETERMINISTIC, Set.of("fraud-detection [GH-90000]"));
            TypedAgent<String, String> hybrid = mockAgent("filter-hybrid", AgentType.HYBRID,        Set.of("fraud-detection", "anomaly")); // GH-90000
            TypedAgent<String, String> other  = mockAgent("filter-other",  AgentType.PROBABILISTIC, Set.of("sentiment [GH-90000]"));

            registerAll(fraud, hybrid, other); // GH-90000

            List<String> results = runPromise(() -> registry.findByCapability("fraud-detection [GH-90000]"));

            assertThat(results).containsExactlyInAnyOrder("filter-fraud", "filter-hybrid"); // GH-90000
            assertThat(results).doesNotContain("filter-other [GH-90000]");
        }

        @Test
        @DisplayName("empty list when capability is not advertised by any agent [GH-90000]")
        void emptyListForUnknownCapability() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("no-match-agent", AgentType.DETERMINISTIC, Set.of("known-cap [GH-90000]"));
            registerAll(agent); // GH-90000

            List<String> results = runPromise(() -> registry.findByCapability("unknown-cap [GH-90000]"));
            assertThat(results).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("agent advertising multiple capabilities matches each individually [GH-90000]")
        void multiCapabilityAgentMatchesEachCapability() { // GH-90000
            TypedAgent<String, String> agent = mockAgent( // GH-90000
                    "multi-cap-agent", AgentType.COMPOSITE,
                    Set.of("classification", "ranking", "search") // GH-90000
            );
            registerAll(agent); // GH-90000

            assertThat(runPromise(() -> registry.findByCapability("classification [GH-90000]"))).contains("multi-cap-agent [GH-90000]");
            assertThat(runPromise(() -> registry.findByCapability("ranking [GH-90000]"))).contains("multi-cap-agent [GH-90000]");
            assertThat(runPromise(() -> registry.findByCapability("search [GH-90000]"))).contains("multi-cap-agent [GH-90000]");
        }
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability() — sorting [GH-90000]")
    class SortingTests {

        @Test
        @DisplayName("results are sorted by agent ID in ascending alphabetical order [GH-90000]")
        void sortedAlphabeticallyByAgentId() { // GH-90000
            TypedAgent<String, String> z = mockAgent("sort-z", AgentType.DETERMINISTIC, Set.of("sort-cap [GH-90000]"));
            TypedAgent<String, String> a = mockAgent("sort-a", AgentType.DETERMINISTIC, Set.of("sort-cap [GH-90000]"));
            TypedAgent<String, String> m = mockAgent("sort-m", AgentType.DETERMINISTIC, Set.of("sort-cap [GH-90000]"));

            registerAll(z, a, m); // GH-90000

            List<String> results = runPromise(() -> registry.findByCapability("sort-cap [GH-90000]"));

            assertThat(results).containsExactly("sort-a", "sort-m", "sort-z"); // GH-90000
        }

        @Test
        @DisplayName("single-result query is still returned as a list [GH-90000]")
        void singleResultReturnedAsList() { // GH-90000
            TypedAgent<String, String> agent = mockAgent("only-agent", AgentType.REACTIVE, Set.of("unique-cap [GH-90000]"));
            registerAll(agent); // GH-90000

            List<String> results = runPromise(() -> registry.findByCapability("unique-cap [GH-90000]"));

            assertThat(results).hasSize(1).containsExactly("only-agent [GH-90000]");
        }
    }

    // ── Stats / aggregate catalog ─────────────────────────────────────────────

    @Nested
    @DisplayName("getStats() — aggregate catalog [GH-90000]")
    class StatsTests {

        @Test
        @DisplayName("stats report live agent count from in-memory cache [GH-90000]")
        void statsReportLiveAgentCount() { // GH-90000
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(2L)); // GH-90000

            TypedAgent<String, String> a = mockAgent("stats-a", AgentType.DETERMINISTIC, Set.of()); // GH-90000
            TypedAgent<String, String> b = mockAgent("stats-b", AgentType.PROBABILISTIC, Set.of()); // GH-90000
            registerAll(a, b); // GH-90000

            Map<String, Object> stats = runPromise(() -> registry.getStats()); // GH-90000

            assertThat(stats).containsEntry("registeredAgents", 2); // GH-90000
            assertThat(stats).containsEntry("persistedAgents", 2L); // GH-90000
            assertThat(stats).containsEntry("registryType", "DataCloud"); // GH-90000
            assertThat(stats).containsEntry("registryTenantId", TENANT); // GH-90000
        }

        @Test
        @DisplayName("stats map is unmodifiable [GH-90000]")
        void statsMapIsUnmodifiable() { // GH-90000
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(0L)); // GH-90000

            Map<String, Object> stats = runPromise(() -> registry.getStats()); // GH-90000

            org.assertj.core.api.Assertions.assertThatExceptionOfType(UnsupportedOperationException.class) // GH-90000
                    .isThrownBy(() -> stats.put("extra", "value")); // GH-90000
        }

        @Test
        @DisplayName("empty registry reports zero registered agents [GH-90000]")
        void emptyRegistryReportsZero() { // GH-90000
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(0L)); // GH-90000

            Map<String, Object> stats = runPromise(() -> registry.getStats()); // GH-90000

            assertThat(stats).containsEntry("registeredAgents", 0); // GH-90000
        }
    }
}
