package com.ghatana.aep.engine.registry;

import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AEP Central Registry Service Performance")
class AepCentralRegistryServicePerformanceTest extends EventloopTestBase {

    @Test
    @DisplayName("listAll returns 1000 agents within the latency budget")
    void listAllReturnsThousandAgentsWithinLatencyBudget() { 
        List<CatalogAgentEntry> entries = IntStream.range(0, 1_000) 
            .mapToObj(index -> CatalogAgentEntry.builder() 
                .id("agent-" + index) 
                .name("Agent " + index) 
                .catalogId("catalog-aep")
                .capabilities(Set.of("plan", "execute")) 
                .build()) 
            .toList(); 

        AgentRegistryContracts backend = new AgentRegistryContracts() { 
            @Override
            public Promise<List<CatalogAgentEntry>> listAgents() { 
                return Promise.of(entries); 
            }

            @Override
            public Promise<java.util.Optional<CatalogAgentEntry>> getAgent(String agentId) { 
                return Promise.of(java.util.Optional.empty()); 
            }

            @Override
            public Promise<List<CatalogAgentEntry>> findByCapability(String capability) { 
                return Promise.of(entries.stream() 
                    .filter(entry -> entry.getCapabilities().contains(capability)) 
                    .toList()); 
            }
        };

        AepCentralRegistryService service = new AepCentralRegistryService(backend); 

        List<AgentInfo> agents = runPromise(service::listAll); 
        long medianMillis = medianMillis(() -> runPromise(service::listAll), 5); 

        assertThat(agents).hasSize(1_000); 
        assertThat(medianMillis).isLessThan(100L); 
    }

    private long medianMillis(Supplier<?> operation, int iterations) { 
        operation.get(); 
        long[] timings = new long[iterations];
        for (int index = 0; index < iterations; index++) { 
            long startedAt = System.nanoTime(); 
            operation.get(); 
            timings[index] = (System.nanoTime() - startedAt) / 1_000_000L; 
        }
        java.util.Arrays.sort(timings); 
        return timings[iterations / 2];
    }
}