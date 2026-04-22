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

@DisplayName("AEP Central Registry Service [GH-90000]")
class AepCentralRegistryServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("discover and resolve can delegate to backend registry contract [GH-90000]")
    void shouldUseBackendRegistryContractWhenProvided() { // GH-90000
        AgentRegistryContracts backend = new AgentRegistryContracts() { // GH-90000
            @Override
            public Promise<List<CatalogAgentEntry>> listAgents() { // GH-90000
                CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
                        .id("backend-agent [GH-90000]")
                        .name("Backend Agent [GH-90000]")
                        .description("From backend [GH-90000]")
                        .catalogId("yappc [GH-90000]")
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
                        .id("backend-agent [GH-90000]")
                        .name("Backend Agent [GH-90000]")
                        .catalogId("yappc [GH-90000]")
                        .capabilities(Set.of("analyze [GH-90000]"))
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
        Optional<AgentInfo> resolved = runPromise(() -> service.resolve("backend-agent [GH-90000]"));

        assertThat(discovered).hasSize(1); // GH-90000
        assertThat(discovered.get(0).id()).isEqualTo("backend-agent [GH-90000]");
        assertThat(resolved).isPresent(); // GH-90000
        assertThat(resolved.get().product()).isEqualTo("yappc [GH-90000]");
    }

    @Test
    @DisplayName("register and discover returns registered agents [GH-90000]")
    void shouldRegisterAndDiscoverAgents() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000
        AgentInfo agent = new AgentInfo("agent-001", "Test Agent", "DETERMINISTIC"); // GH-90000

        runPromise(() -> service.registerAgent(agent)); // GH-90000
        assertThat(runPromise(service::discoverAgents)).hasSize(1); // GH-90000
        assertThat(runPromise(service::listAll)).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("resolve returns empty for unknown agent [GH-90000]")
    void shouldReturnEmptyForUnknownAgent() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000

        Optional<AgentInfo> resolved = runPromise(() -> service.resolveAgent("missing-agent [GH-90000]"));

        assertThat(resolved).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("execute returns failure when agent is not found [GH-90000]")
    void shouldFailExecutionWhenAgentMissing() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000

        AgentExecutionResult result = runPromise(() -> service.executeAgent("missing-agent", "input")); // GH-90000

        assertThat(result.isSuccess()).isFalse(); // GH-90000
        assertThat(result.getErrorMessage()).isEqualTo("Agent not found [GH-90000]");
    }

    @Test
    @DisplayName("execute returns accepted payload for registered agent [GH-90000]")
    void shouldReturnAcceptedPayloadForRegisteredAgent() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000
        runPromise(() -> service.registerAgent(new AgentInfo("agent-002", "Test Agent", "DETERMINISTIC"))); // GH-90000

        AgentExecutionResult result = runPromise(() -> service.executeAgent("agent-002", "hello")); // GH-90000

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getOutput()).isInstanceOf(java.util.Map.class); // GH-90000
    }

    @Test
    @DisplayName("register rejects null or blank ids [GH-90000]")
    void shouldRejectInvalidRegistration() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> service.registerAgent(null))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("agent.id is required [GH-90000]");

        AgentInfo blankId = new AgentInfo("id", "name", "type"); // GH-90000
        blankId.id = " ";

        assertThatThrownBy(() -> runPromise(() -> service.registerAgent(blankId))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("agent.id is required [GH-90000]");
    }

    @Test
    @DisplayName("deregister removes existing agent [GH-90000]")
    void shouldDeregisterAgent() { // GH-90000
        AepCentralRegistryService service = new AepCentralRegistryService(); // GH-90000
        runPromise(() -> service.registerAgent(new AgentInfo("agent-003", "Test Agent", "DETERMINISTIC"))); // GH-90000

        runPromise(() -> service.deregisterAgent("agent-003 [GH-90000]"));

        assertThat(runPromise(() -> service.resolve("agent-003 [GH-90000]"))).isEmpty();
    }
}
