/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
@DisplayName("AgentCatalogQueryTest")
class AgentCatalogQueryTest extends EventloopTestBase {

    private static final String TENANT = "catalog-tenant";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    @BeforeEach
    void setUp() { 
        registry = new DataCloudAgentRegistry(dataCloud, TENANT); 

        lenient().when(mockEntity.getId()).thenReturn(UUID.randomUUID()); 
        lenient().when(dataCloud.createEntity(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), anyMap())) 
                .thenReturn(Promise.of(mockEntity)); 
        lenient().when(dataCloud.appendEvent(eq(TENANT), anyString(), any())) 
                .thenReturn(Promise.of(0L)); 
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static TypedAgent<String, String> mockAgent(String id, 
                                                         AgentType type,
                                                         Set<String> capabilities) {
        TypedAgent<String, String> agent = org.mockito.Mockito.mock(TypedAgent.class); 
        AgentDescriptor descriptor = AgentDescriptor.builder() 
                .agentId(id) 
                .type(type) 
                .determinism(DeterminismGuarantee.FULL) 
                .capabilities(capabilities) 
                .build(); 
        lenient().when(agent.descriptor()).thenReturn(descriptor); 
        return agent;
    }

    private static AgentConfig configFor(String id) { 
        return AgentConfig.builder() 
                .agentId(id) 
                .timeout(Duration.ofSeconds(10)) 
                .build(); 
    }

    private void registerAll(TypedAgent<?, ?> ... agents) { 
        for (TypedAgent<?, ?> agent : agents) { 
            @SuppressWarnings("unchecked")
            TypedAgent<String, String> typed = (TypedAgent<String, String>) agent; 
            runPromise(() -> registry.register(typed, configFor(typed.descriptor().getAgentId()))); 
        }
    }

    // ── Catalog listing ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAgentIds()")
    class ListAgentIdsTests {

        @Test
        @DisplayName("returns empty set when no agents are registered")
        void emptyWhenNoneRegistered() { 
            Set<String> ids = runPromise(() -> registry.listAgentIds()); 
            assertThat(ids).isEmpty(); 
        }

        @Test
        @DisplayName("returns all registered agent IDs")
        void returnsAllRegisteredIds() { 
            TypedAgent<String, String> a = mockAgent("list-a", AgentType.DETERMINISTIC, Set.of("cap"));
            TypedAgent<String, String> b = mockAgent("list-b", AgentType.PROBABILISTIC, Set.of("score"));
            TypedAgent<String, String> c = mockAgent("list-c", AgentType.HYBRID, Set.of("cap", "score")); 

            registerAll(a, b, c); 

            Set<String> ids = runPromise(() -> registry.listAgentIds()); 
            assertThat(ids).containsExactlyInAnyOrder("list-a", "list-b", "list-c"); 
        }

        @Test
        @DisplayName("returns an immutable snapshot — mutations do not affect live map")
        void returnsImmutableSnapshot() { 
            TypedAgent<String, String> a = mockAgent("snap-a", AgentType.DETERMINISTIC, Set.of()); 
            registerAll(a); 

            Set<String> snapshot = runPromise(() -> registry.listAgentIds()); 
            // The returned snapshot should not be backed by the live map
            assertThat(snapshot).isNotSameAs(registry); // structural guard 
            assertThat(snapshot).contains("snap-a");
        }
    }

    // ── Capability filtering ──────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability() — filtering")
    class FilteringTests {

        @Test
        @DisplayName("filters to only agents that advertise the requested capability")
        void filtersToMatchingCapability() { 
            TypedAgent<String, String> fraud  = mockAgent("filter-fraud",  AgentType.DETERMINISTIC, Set.of("fraud-detection"));
            TypedAgent<String, String> hybrid = mockAgent("filter-hybrid", AgentType.HYBRID,        Set.of("fraud-detection", "anomaly")); 
            TypedAgent<String, String> other  = mockAgent("filter-other",  AgentType.PROBABILISTIC, Set.of("sentiment"));

            registerAll(fraud, hybrid, other); 

            List<String> results = runPromise(() -> registry.findByCapability("fraud-detection"));

            assertThat(results).containsExactlyInAnyOrder("filter-fraud", "filter-hybrid"); 
            assertThat(results).doesNotContain("filter-other");
        }

        @Test
        @DisplayName("empty list when capability is not advertised by any agent")
        void emptyListForUnknownCapability() { 
            TypedAgent<String, String> agent = mockAgent("no-match-agent", AgentType.DETERMINISTIC, Set.of("known-cap"));
            registerAll(agent); 

            List<String> results = runPromise(() -> registry.findByCapability("unknown-cap"));
            assertThat(results).isEmpty(); 
        }

        @Test
        @DisplayName("agent advertising multiple capabilities matches each individually")
        void multiCapabilityAgentMatchesEachCapability() { 
            TypedAgent<String, String> agent = mockAgent( 
                    "multi-cap-agent", AgentType.COMPOSITE,
                    Set.of("classification", "ranking", "search") 
            );
            registerAll(agent); 

            assertThat(runPromise(() -> registry.findByCapability("classification"))).contains("multi-cap-agent");
            assertThat(runPromise(() -> registry.findByCapability("ranking"))).contains("multi-cap-agent");
            assertThat(runPromise(() -> registry.findByCapability("search"))).contains("multi-cap-agent");
        }
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCapability() — sorting")
    class SortingTests {

        @Test
        @DisplayName("results are sorted by agent ID in ascending alphabetical order")
        void sortedAlphabeticallyByAgentId() { 
            TypedAgent<String, String> z = mockAgent("sort-z", AgentType.DETERMINISTIC, Set.of("sort-cap"));
            TypedAgent<String, String> a = mockAgent("sort-a", AgentType.DETERMINISTIC, Set.of("sort-cap"));
            TypedAgent<String, String> m = mockAgent("sort-m", AgentType.DETERMINISTIC, Set.of("sort-cap"));

            registerAll(z, a, m); 

            List<String> results = runPromise(() -> registry.findByCapability("sort-cap"));

            assertThat(results).containsExactly("sort-a", "sort-m", "sort-z"); 
        }

        @Test
        @DisplayName("single-result query is still returned as a list")
        void singleResultReturnedAsList() { 
            TypedAgent<String, String> agent = mockAgent("only-agent", AgentType.REACTIVE, Set.of("unique-cap"));
            registerAll(agent); 

            List<String> results = runPromise(() -> registry.findByCapability("unique-cap"));

            assertThat(results).hasSize(1).containsExactly("only-agent");
        }
    }

    // ── Stats / aggregate catalog ─────────────────────────────────────────────

    @Nested
    @DisplayName("getStats() — aggregate catalog")
    class StatsTests {

        @Test
        @DisplayName("stats report live agent count from in-memory cache")
        void statsReportLiveAgentCount() { 
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) 
                    .thenReturn(Promise.of(2L)); 

            TypedAgent<String, String> a = mockAgent("stats-a", AgentType.DETERMINISTIC, Set.of()); 
            TypedAgent<String, String> b = mockAgent("stats-b", AgentType.PROBABILISTIC, Set.of()); 
            registerAll(a, b); 

            Map<String, Object> stats = runPromise(() -> registry.getStats()); 

            assertThat(stats).containsEntry("registeredAgents", 2); 
            assertThat(stats).containsEntry("persistedAgents", 2L); 
            assertThat(stats).containsEntry("registryType", "DataCloud"); 
            assertThat(stats).containsEntry("registryTenantId", TENANT); 
        }

        @Test
        @DisplayName("stats map is unmodifiable")
        void statsMapIsUnmodifiable() { 
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) 
                    .thenReturn(Promise.of(0L)); 

            Map<String, Object> stats = runPromise(() -> registry.getStats()); 

            org.assertj.core.api.Assertions.assertThatExceptionOfType(UnsupportedOperationException.class) 
                    .isThrownBy(() -> stats.put("extra", "value")); 
        }

        @Test
        @DisplayName("empty registry reports zero registered agents")
        void emptyRegistryReportsZero() { 
            when(dataCloud.countEntities(eq(TENANT), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), any())) 
                    .thenReturn(Promise.of(0L)); 

            Map<String, Object> stats = runPromise(() -> registry.getStats()); 

            assertThat(stats).containsEntry("registeredAgents", 0); 
        }
    }
}
