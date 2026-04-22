/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.runtime;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.aep.catalog.AepCentralCatalogService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepCentralRegistryService [GH-90000]")
class AepCentralRegistryServiceTest extends EventloopTestBase {

    @Mock
    private AepCentralCatalogService catalogService;

    @Mock
    private AgentMaterializer materializer;

    @Mock
    private TypedAgent<Object, Object> typedAgent;

    private CatalogRegistry registry;
    private AepCentralRegistryService service;

    @BeforeEach
    void setUp() { // GH-90000
        registry = CatalogRegistry.empty(); // GH-90000
        registry.register(new TestCatalog("catalog-a", List.of( // GH-90000
                CatalogAgentEntry.builder() // GH-90000
                        .id("agent-1 [GH-90000]")
                        .name("Agent One [GH-90000]")
                        .catalogId("catalog-a [GH-90000]")
                        .capabilities(java.util.Set.of("search [GH-90000]"))
                        .build(), // GH-90000
                CatalogAgentEntry.builder() // GH-90000
                        .id("agent-2 [GH-90000]")
                        .name("Agent Two [GH-90000]")
                        .catalogId("catalog-a [GH-90000]")
                        .capabilities(java.util.Set.of("plan", "search")) // GH-90000
                        .build() // GH-90000
        )));
        lenient().when(catalogService.getRegistry()).thenReturn(registry); // GH-90000
        service = new AepCentralRegistryService(catalogService, materializer); // GH-90000
    }

    @Test
    @DisplayName("listAgents exposes merged catalog definitions [GH-90000]")
    void listAgentsExposesMergedDefinitions() { // GH-90000
        List<CatalogAgentEntry> entries = runPromise(service::listAgents); // GH-90000

        assertThat(entries).hasSize(2); // GH-90000
        assertThat(entries).extracting(CatalogAgentEntry::getId) // GH-90000
                .containsExactlyInAnyOrder("agent-1", "agent-2"); // GH-90000
    }

    @Test
    @DisplayName("getAgent returns matching catalog definition [GH-90000]")
    void getAgentReturnsMatchingDefinition() { // GH-90000
        Optional<CatalogAgentEntry> found = runPromise(() -> service.getAgent("agent-1 [GH-90000]"));
        Optional<CatalogAgentEntry> missing = runPromise(() -> service.getAgent("missing [GH-90000]"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().getName()).isEqualTo("Agent One [GH-90000]");
        assertThat(missing).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("findByCapability filters catalog definitions [GH-90000]")
    void findByCapabilityFiltersDefinitions() { // GH-90000
        List<CatalogAgentEntry> searchAgents = runPromise(() -> service.findByCapability("search [GH-90000]"));
        List<CatalogAgentEntry> planAgents = runPromise(() -> service.findByCapability("plan [GH-90000]"));

        assertThat(searchAgents).hasSize(2); // GH-90000
        assertThat(planAgents).singleElement().extracting(CatalogAgentEntry::getId).isEqualTo("agent-2 [GH-90000]");
    }

    @Test
    @DisplayName("materializeAgent registers live agent in memory [GH-90000]")
    void materializeAgentRegistersLiveAgent() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("provider:test [GH-90000]")
                .build(); // GH-90000
        doReturn(typedAgent).when(materializer).materialize("provider:test", config); // GH-90000

        TypedAgent<?, ?> result = runPromise(() -> service.materializeAgent("agent-1", "provider:test", config)); // GH-90000
        Optional<TypedAgent<?, ?>> liveAgent = runPromise(() -> service.getLiveAgent("agent-1 [GH-90000]"));

        assertThat(result).isSameAs(typedAgent); // GH-90000
        assertThat(liveAgent).containsSame(typedAgent); // GH-90000
        assertThat(service.liveAgentCount()).isEqualTo(1); // GH-90000
        assertThat(service.liveAgentIds()).containsExactly("agent-1 [GH-90000]");
    }

    @Test
    @DisplayName("shutdownAgent returns false when agent was never materialized [GH-90000]")
    void shutdownAgentReturnsFalseWhenMissing() { // GH-90000
        Boolean result = runPromise(() -> service.shutdownAgent("missing [GH-90000]"));

        assertThat(result).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("shutdownAgent shuts down and removes live agent [GH-90000]")
    void shutdownAgentRemovesLiveAgent() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("provider:test [GH-90000]")
                .build(); // GH-90000
        doReturn(typedAgent).when(materializer).materialize("provider:test", config); // GH-90000
        when(typedAgent.shutdown()).thenReturn(Promise.complete()); // GH-90000

        runPromise(() -> service.materializeAgent("agent-1", "provider:test", config)); // GH-90000
        Boolean result = runPromise(() -> service.shutdownAgent("agent-1 [GH-90000]"));

        assertThat(result).isTrue(); // GH-90000
        assertThat(service.liveAgentCount()).isZero(); // GH-90000
        assertThat(runPromise(() -> service.getLiveAgent("agent-1 [GH-90000]"))).isEmpty();
        verify(typedAgent).shutdown(); // GH-90000
    }

    @Test
    @DisplayName("isAgentHealthy returns false for missing live agent [GH-90000]")
    void isAgentHealthyReturnsFalseForMissingAgent() { // GH-90000
        Boolean healthy = runPromise(() -> service.isAgentHealthy("missing [GH-90000]"));

        assertThat(healthy).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("isAgentHealthy treats healthy and degraded as healthy [GH-90000]")
    void isAgentHealthyTreatsHealthyAndDegradedAsHealthy() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("provider:test [GH-90000]")
                .build(); // GH-90000
        doReturn(typedAgent).when(materializer).materialize("provider:test", config); // GH-90000
        when(typedAgent.healthCheck()).thenReturn(Promise.of(HealthStatus.degraded("Agent is degraded [GH-90000]")));

        runPromise(() -> service.materializeAgent("agent-1", "provider:test", config)); // GH-90000
        Boolean healthy = runPromise(() -> service.isAgentHealthy("agent-1 [GH-90000]"));

        assertThat(healthy).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("canMaterialize delegates to materializer [GH-90000]")
    void canMaterializeDelegates() { // GH-90000
        when(materializer.canMaterialize("provider:test [GH-90000]")).thenReturn(true);
        when(materializer.canMaterialize("missing:test [GH-90000]")).thenReturn(false);

        assertThat(service.canMaterialize("provider:test [GH-90000]")).isTrue();
        assertThat(service.canMaterialize("missing:test [GH-90000]")).isFalse();
    }

    private static final class TestCatalog implements AgentCatalog {
        private final String catalogId;
        private final List<CatalogAgentEntry> definitions;

        private TestCatalog(String catalogId, List<CatalogAgentEntry> definitions) { // GH-90000
            this.catalogId = catalogId;
            this.definitions = definitions;
        }

        @Override
        public String getCatalogId() { // GH-90000
            return catalogId;
        }

        @Override
        public String getDisplayName() { // GH-90000
            return catalogId;
        }

        @Override
        public int priority() { // GH-90000
            return 100;
        }

        @Override
        public List<CatalogAgentEntry> getDefinitions() { // GH-90000
            return definitions;
        }

        @Override
        public java.util.Optional<CatalogAgentEntry> findById(String agentId) { // GH-90000
            return definitions.stream().filter(entry -> entry.getId().equals(agentId)).findFirst(); // GH-90000
        }

        @Override
        public List<CatalogAgentEntry> findByCapability(String capability) { // GH-90000
            return definitions.stream() // GH-90000
                    .filter(entry -> entry.getCapabilities().contains(capability)) // GH-90000
                    .toList(); // GH-90000
        }

        @Override
        public List<CatalogAgentEntry> findByLevel(String level) { // GH-90000
            return definitions.stream() // GH-90000
                    .filter(entry -> level.equalsIgnoreCase(entry.getLevel())) // GH-90000
                    .toList(); // GH-90000
        }

        @Override
        public List<CatalogAgentEntry> findByDomain(String domain) { // GH-90000
            return definitions.stream() // GH-90000
                    .filter(entry -> domain.equalsIgnoreCase(entry.getDomain())) // GH-90000
                    .toList(); // GH-90000
        }

        @Override
        public java.util.Set<String> getAllCapabilities() { // GH-90000
            return definitions.stream() // GH-90000
                    .flatMap(entry -> entry.getCapabilities().stream()) // GH-90000
                    .collect(java.util.stream.Collectors.toSet()); // GH-90000
        }
    }
}
