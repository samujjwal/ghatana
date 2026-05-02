/*
 * Copyright (c) 2026 Ghatana Inc. 
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

@ExtendWith(MockitoExtension.class) 
@DisplayName("AepCentralRegistryService")
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
    void setUp() { 
        registry = CatalogRegistry.empty(); 
        registry.register(new TestCatalog("catalog-a", List.of( 
                CatalogAgentEntry.builder() 
                        .id("agent-1")
                        .name("Agent One")
                        .catalogId("catalog-a")
                        .capabilities(java.util.Set.of("search"))
                        .build(), 
                CatalogAgentEntry.builder() 
                        .id("agent-2")
                        .name("Agent Two")
                        .catalogId("catalog-a")
                        .capabilities(java.util.Set.of("plan", "search")) 
                        .build() 
        )));
        lenient().when(catalogService.getRegistry()).thenReturn(registry); 
        service = new AepCentralRegistryService(catalogService, materializer); 
    }

    @Test
    @DisplayName("listAgents exposes merged catalog definitions")
    void listAgentsExposesMergedDefinitions() { 
        List<CatalogAgentEntry> entries = runPromise(service::listAgents); 

        assertThat(entries).hasSize(2); 
        assertThat(entries).extracting(CatalogAgentEntry::getId) 
                .containsExactlyInAnyOrder("agent-1", "agent-2"); 
    }

    @Test
    @DisplayName("getAgent returns matching catalog definition")
    void getAgentReturnsMatchingDefinition() { 
        Optional<CatalogAgentEntry> found = runPromise(() -> service.getAgent("agent-1"));
        Optional<CatalogAgentEntry> missing = runPromise(() -> service.getAgent("missing"));

        assertThat(found).isPresent(); 
        assertThat(found.get().getName()).isEqualTo("Agent One");
        assertThat(missing).isEmpty(); 
    }

    @Test
    @DisplayName("findByCapability filters catalog definitions")
    void findByCapabilityFiltersDefinitions() { 
        List<CatalogAgentEntry> searchAgents = runPromise(() -> service.findByCapability("search"));
        List<CatalogAgentEntry> planAgents = runPromise(() -> service.findByCapability("plan"));

        assertThat(searchAgents).hasSize(2); 
        assertThat(planAgents).singleElement().extracting(CatalogAgentEntry::getId).isEqualTo("agent-2");
    }

    @Test
    @DisplayName("materializeAgent registers live agent in memory")
    void materializeAgentRegistersLiveAgent() { 
        AgentConfig config = AgentConfig.builder() 
                .agentId("agent-1")
                .type(AgentType.DETERMINISTIC) 
                .implementationRef("provider:test")
                .build(); 
        doReturn(typedAgent).when(materializer).materialize("provider:test", config); 

        TypedAgent<?, ?> result = runPromise(() -> service.materializeAgent("agent-1", "provider:test", config)); 
        Optional<TypedAgent<?, ?>> liveAgent = runPromise(() -> service.getLiveAgent("agent-1"));

        assertThat(result).isSameAs(typedAgent); 
        assertThat(liveAgent).containsSame(typedAgent); 
        assertThat(service.liveAgentCount()).isEqualTo(1); 
        assertThat(service.liveAgentIds()).containsExactly("agent-1");
    }

    @Test
    @DisplayName("shutdownAgent returns false when agent was never materialized")
    void shutdownAgentReturnsFalseWhenMissing() { 
        Boolean result = runPromise(() -> service.shutdownAgent("missing"));

        assertThat(result).isFalse(); 
    }

    @Test
    @DisplayName("shutdownAgent shuts down and removes live agent")
    void shutdownAgentRemovesLiveAgent() { 
        AgentConfig config = AgentConfig.builder() 
                .agentId("agent-1")
                .type(AgentType.DETERMINISTIC) 
                .implementationRef("provider:test")
                .build(); 
        doReturn(typedAgent).when(materializer).materialize("provider:test", config); 
        when(typedAgent.shutdown()).thenReturn(Promise.complete()); 

        runPromise(() -> service.materializeAgent("agent-1", "provider:test", config)); 
        Boolean result = runPromise(() -> service.shutdownAgent("agent-1"));

        assertThat(result).isTrue(); 
        assertThat(service.liveAgentCount()).isZero(); 
        assertThat(runPromise(() -> service.getLiveAgent("agent-1"))).isEmpty();
        verify(typedAgent).shutdown(); 
    }

    @Test
    @DisplayName("isAgentHealthy returns false for missing live agent")
    void isAgentHealthyReturnsFalseForMissingAgent() { 
        Boolean healthy = runPromise(() -> service.isAgentHealthy("missing"));

        assertThat(healthy).isFalse(); 
    }

    @Test
    @DisplayName("isAgentHealthy treats healthy and degraded as healthy")
    void isAgentHealthyTreatsHealthyAndDegradedAsHealthy() { 
        AgentConfig config = AgentConfig.builder() 
                .agentId("agent-1")
                .type(AgentType.DETERMINISTIC) 
                .implementationRef("provider:test")
                .build(); 
        doReturn(typedAgent).when(materializer).materialize("provider:test", config); 
        when(typedAgent.healthCheck()).thenReturn(Promise.of(HealthStatus.degraded("Agent is degraded")));

        runPromise(() -> service.materializeAgent("agent-1", "provider:test", config)); 
        Boolean healthy = runPromise(() -> service.isAgentHealthy("agent-1"));

        assertThat(healthy).isTrue(); 
    }

    @Test
    @DisplayName("canMaterialize delegates to materializer")
    void canMaterializeDelegates() { 
        when(materializer.canMaterialize("provider:test")).thenReturn(true);
        when(materializer.canMaterialize("missing:test")).thenReturn(false);

        assertThat(service.canMaterialize("provider:test")).isTrue();
        assertThat(service.canMaterialize("missing:test")).isFalse();
    }

    private static final class TestCatalog implements AgentCatalog {
        private final String catalogId;
        private final List<CatalogAgentEntry> definitions;

        private TestCatalog(String catalogId, List<CatalogAgentEntry> definitions) { 
            this.catalogId = catalogId;
            this.definitions = definitions;
        }

        @Override
        public String getCatalogId() { 
            return catalogId;
        }

        @Override
        public String getDisplayName() { 
            return catalogId;
        }

        @Override
        public int priority() { 
            return 100;
        }

        @Override
        public List<CatalogAgentEntry> getDefinitions() { 
            return definitions;
        }

        @Override
        public java.util.Optional<CatalogAgentEntry> findById(String agentId) { 
            return definitions.stream().filter(entry -> entry.getId().equals(agentId)).findFirst(); 
        }

        @Override
        public List<CatalogAgentEntry> findByCapability(String capability) { 
            return definitions.stream() 
                    .filter(entry -> entry.getCapabilities().contains(capability)) 
                    .toList(); 
        }

        @Override
        public List<CatalogAgentEntry> findByLevel(String level) { 
            return definitions.stream() 
                    .filter(entry -> level.equalsIgnoreCase(entry.getLevel())) 
                    .toList(); 
        }

        @Override
        public List<CatalogAgentEntry> findByDomain(String domain) { 
            return definitions.stream() 
                    .filter(entry -> domain.equalsIgnoreCase(entry.getDomain())) 
                    .toList(); 
        }

        @Override
        public java.util.Set<String> getAllCapabilities() { 
            return definitions.stream() 
                    .flatMap(entry -> entry.getCapabilities().stream()) 
                    .collect(java.util.stream.Collectors.toSet()); 
        }
    }
}
