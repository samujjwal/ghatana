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
    void shouldUseBackendRegistryContractWhenProvided() {
        AgentRegistryContracts backend = new AgentRegistryContracts() {
            @Override
            public Promise<List<CatalogAgentEntry>> listAgents() {
                CatalogAgentEntry entry = CatalogAgentEntry.builder()
                        .id("backend-agent")
                        .name("Backend Agent")
                        .description("From backend")
                        .catalogId("yappc")
                        .capabilities(Set.of("analyze", "execute"))
                        .build();
                return Promise.of(List.of(entry));
            }

            @Override
            public Promise<Optional<CatalogAgentEntry>> getAgent(String agentId) {
                if (!"backend-agent".equals(agentId)) {
                    return Promise.of(Optional.empty());
                }
                CatalogAgentEntry entry = CatalogAgentEntry.builder()
                        .id("backend-agent")
                        .name("Backend Agent")
                        .catalogId("yappc")
                        .capabilities(Set.of("analyze"))
                        .build();
                return Promise.of(Optional.of(entry));
            }

            @Override
            public Promise<List<CatalogAgentEntry>> findByCapability(String capability) {
                return Promise.of(List.of());
            }
        };

        AepCentralRegistryService service = new AepCentralRegistryService(backend);

        List<AgentInfo> discovered = runPromise(service::discoverAgents);
        Optional<AgentInfo> resolved = runPromise(() -> service.resolve("backend-agent"));

        assertThat(discovered).hasSize(1);
        assertThat(discovered.get(0).id()).isEqualTo("backend-agent");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().product()).isEqualTo("yappc");
    }

    @Test
    @DisplayName("register and discover returns registered agents")
    void shouldRegisterAndDiscoverAgents() {
        AepCentralRegistryService service = new AepCentralRegistryService();
        AgentInfo agent = new AgentInfo("agent-001", "Test Agent", "DETERMINISTIC");

        runPromise(() -> service.registerAgent(agent));
        assertThat(runPromise(service::discoverAgents)).hasSize(1);
        assertThat(runPromise(service::listAll)).hasSize(1);
    }

    @Test
    @DisplayName("resolve returns empty for unknown agent")
    void shouldReturnEmptyForUnknownAgent() {
        AepCentralRegistryService service = new AepCentralRegistryService();

        Optional<AgentInfo> resolved = runPromise(() -> service.resolveAgent("missing-agent"));

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("execute returns failure when agent is not found")
    void shouldFailExecutionWhenAgentMissing() {
        AepCentralRegistryService service = new AepCentralRegistryService();

        AgentExecutionResult result = runPromise(() -> service.executeAgent("missing-agent", "input"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Agent not found");
    }

    @Test
    @DisplayName("execute returns accepted payload for registered agent")
    void shouldReturnAcceptedPayloadForRegisteredAgent() {
        AepCentralRegistryService service = new AepCentralRegistryService();
        runPromise(() -> service.registerAgent(new AgentInfo("agent-002", "Test Agent", "DETERMINISTIC")));

        AgentExecutionResult result = runPromise(() -> service.executeAgent("agent-002", "hello"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isInstanceOf(java.util.Map.class);
    }

    @Test
    @DisplayName("register rejects null or blank ids")
    void shouldRejectInvalidRegistration() {
        AepCentralRegistryService service = new AepCentralRegistryService();

        assertThatThrownBy(() -> runPromise(() -> service.registerAgent(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent.id is required");

        AgentInfo blankId = new AgentInfo("id", "name", "type");
        blankId.id = " ";

        assertThatThrownBy(() -> runPromise(() -> service.registerAgent(blankId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent.id is required");
    }

    @Test
    @DisplayName("deregister removes existing agent")
    void shouldDeregisterAgent() {
        AepCentralRegistryService service = new AepCentralRegistryService();
        runPromise(() -> service.registerAgent(new AgentInfo("agent-003", "Test Agent", "DETERMINISTIC")));

        runPromise(() -> service.deregisterAgent("agent-003"));

        assertThat(runPromise(() -> service.resolve("agent-003"))).isEmpty();
    }
}
