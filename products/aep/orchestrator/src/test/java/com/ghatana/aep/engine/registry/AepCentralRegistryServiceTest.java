package com.ghatana.aep.engine.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.aep.registry.AgentRegistryContracts;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AEP Central Registry Service")
class AepCentralRegistryServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("discover and resolve can delegate to backend registry contract")
    void shouldUseBackendRegistryContractWhenProvided() { // GH-90000
        AgentRegistryContracts backend = new AgentRegistryContracts() { // GH-90000
            @Override
            public Promise<List<CatalogAgentEntry>> listAgents() { // GH-90000
                CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                        .id("backend-agent")
                        .name("Backend Agent")
                        .description("From backend")
                        .catalogId("yappc")
                        .capabilities(Set.of("analyze", "execute")) // GH-90000
                        .build(); // GH-90000
                return Promise.of(List.of(entry)); // GH-90000
            }

            @Override
            public Promise<Optional<CatalogAgentEntry>> getAgent(String agentId) { // GH-90000
                if (!"backend-agent".equals(agentId)) { // GH-90000
                    return Promise.of(Optional.empty()); // GH-90000
                }
                CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                        .id("backend-agent")
                        .name("Backend Agent")
                        .catalogId("yappc")
                        .capabilities(Set.of("analyze"))
                        .build(); // GH-90000
                return Promise.of(Optional.of(entry)); // GH-90000
            }

            @Override
            public Promise<List<CatalogAgentEntry>> findByCapability(String capability) { // GH-90000
                return Promise.of(List.of()); // GH-90000
            }
        };

        AepCentralRegistryService service = new AepCentralRegistryService(backend); // GH-90000

        List<AgentInfo> discovered = runPromise(service::discoverAgents); // GH-90000
        Optional<AgentInfo> resolved = runPromise(() -> service.resolve("backend-agent"));

        assertThat(discovered).hasSize(1); // GH-90000
        assertThat(discovered.get(0).id()).isEqualTo("backend-agent");
        assertThat(resolved).isPresent(); // GH-90000
        assertThat(resolved.get().product()).isEqualTo("yappc");
    }

    @Test
    @DisplayName("register and discover returns registered agents")
    void shouldRegisterAndDiscoverAgents() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000
        AgentInfo agent = new AgentInfo("agent-001", "Test Agent", "DETERMINISTIC"); // GH-90000

        runPromise(() -> service.registerAgent(agent)); // GH-90000
        assertThat(runPromise(service::discoverAgents)).hasSize(1); // GH-90000
        assertThat(runPromise(service::listAll)).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("resolve returns empty for unknown agent")
    void shouldReturnEmptyForUnknownAgent() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000

        Optional<AgentInfo> resolved = runPromise(() -> service.resolveAgent("missing-agent"));

        assertThat(resolved).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("execute returns failure when agent is not found")
    void shouldFailExecutionWhenAgentMissing() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000

        AgentExecutionResult result = runPromise(() -> service.executeAgent("missing-agent", "input")); // GH-90000

        assertThat(result.isSuccess()).isFalse(); // GH-90000
        assertThat(result.getErrorMessage()).isEqualTo("Agent not found");
    }

    @Test
    @DisplayName("execute returns accepted payload for registered agent")
    void shouldReturnAcceptedPayloadForRegisteredAgent() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000
        runPromise(() -> service.registerAgent(new AgentInfo("agent-002", "Test Agent", "DETERMINISTIC"))); // GH-90000

        AgentExecutionResult result = runPromise(() -> service.executeAgent("agent-002", "hello")); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getOutput()).isInstanceOf(java.util.Map.class); // GH-90000
    }

    @Test
    @DisplayName("register rejects null or blank ids")
    void shouldRejectInvalidRegistration() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> service.registerAgent(null))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("agent.id is required");

        AgentInfo blankId = new AgentInfo("id", "name", "type"); // GH-90000
        blankId.id = " ";

        assertThatThrownBy(() -> runPromise(() -> service.registerAgent(blankId))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("agent.id is required");
    }

    @Test
    @DisplayName("deregister removes existing agent")
    void shouldDeregisterAgent() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000
        runPromise(() -> service.registerAgent(new AgentInfo("agent-003", "Test Agent", "DETERMINISTIC"))); // GH-90000

        runPromise(() -> service.deregisterAgent("agent-003"));

        assertThat(runPromise(() -> service.resolve("agent-003"))).isEmpty();
    }
}
