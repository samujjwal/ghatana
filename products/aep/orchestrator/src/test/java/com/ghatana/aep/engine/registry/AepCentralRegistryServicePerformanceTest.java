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
    void listAllReturnsThousandAgentsWithinLatencyBudget() { // GH-90000
        List<CatalogAgentEntry> entries = IntStream.range(0, 1_000) // GH-90000
            .mapToObj(index -> CatalogAgentEntry.builder() // GH-90000
                .id("agent-" + index) // GH-90000
                .name("Agent " + index) // GH-90000
                .catalogId("catalog-aep")
                .capabilities(Set.of("plan", "execute")) // GH-90000
                .build()) // GH-90000
            .toList(); // GH-90000

        AgentRegistryContracts backend = new AgentRegistryContracts() { // GH-90000
            @Override
            public Promise<List<CatalogAgentEntry>> listAgents() { // GH-90000
                return Promise.of(entries); // GH-90000
            }

            @Override
            public Promise<java.util.Optional<CatalogAgentEntry>> getAgent(String agentId) { // GH-90000
                return Promise.of(java.util.Optional.empty()); // GH-90000
            }

            @Override
            public Promise<List<CatalogAgentEntry>> findByCapability(String capability) { // GH-90000
                return Promise.of(entries.stream() // GH-90000
                    .filter(entry -> entry.getCapabilities().contains(capability)) // GH-90000
                    .toList()); // GH-90000
            }
        };

        AepCentralRegistryService service = new AepCentralRegistryService(backend); // GH-90000

        List<AgentInfo> agents = runPromise(service::listAll); // GH-90000
        long medianMillis = medianMillis(() -> runPromise(service::listAll), 5); // GH-90000

        assertThat(agents).hasSize(1_000); // GH-90000
        assertThat(medianMillis).isLessThan(100L); // GH-90000
    }

    private long medianMillis(Supplier<?> operation, int iterations) { // GH-90000
        operation.get(); // GH-90000
        long[] timings = new long[iterations];
        for (int index = 0; index < iterations; index++) { // GH-90000
            long startedAt = System.nanoTime(); // GH-90000
            operation.get(); // GH-90000
            timings[index] = (System.nanoTime() - startedAt) / 1_000_000L; // GH-90000
        }
        java.util.Arrays.sort(timings); // GH-90000
        return timings[iterations / 2];
    }
}