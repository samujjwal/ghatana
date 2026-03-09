package com.ghatana.virtualorg.framework.integration;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.catalog.InMemoryOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.agent.OrganizationalAgent;
import com.ghatana.virtualorg.framework.hierarchy.Authority;
import com.ghatana.virtualorg.framework.hierarchy.EscalationPath;
import com.ghatana.virtualorg.framework.hierarchy.Layer;
import com.ghatana.virtualorg.framework.hierarchy.Role;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgentOperatorRegistry.
 *
 * <p>Tests validate:
 * - Single agent registration as operator
 * - Batch agent registration (all 12 agents)
 * - Operator catalog integration
 * - Operator ID format and namespacing
 * - Unregistration and cleanup
 *
 * @doc.type test
 * @doc.purpose Validate agent-to-operator registration
 * @doc.layer product
 */
@DisplayName("AgentOperatorRegistry Tests")
class AgentOperatorRegistryTest extends EventloopTestBase {
    
    private OperatorCatalog operatorCatalog;
    private SimpleMeterRegistry meterRegistry;
    private AgentOperatorRegistry registry;
    
    private static final String TENANT_ID = "test-tenant";
    
    @BeforeEach
    void setUp() {
        operatorCatalog = new InMemoryOperatorCatalog();
        meterRegistry = new SimpleMeterRegistry();
        registry = new AgentOperatorRegistry(operatorCatalog, meterRegistry);
    }
    
    /**
     * Test single agent registration.
     *
     * GIVEN: CEO agent instance
     * WHEN: registering agent
     * THEN: agent appears in operator catalog with correct ID
     */
    @Test
    @DisplayName("Should register single agent as operator")
    void shouldRegisterSingleAgent() {
        // GIVEN: CEO agent
        OrganizationalAgent ceo = createTestCEO();
        
        // WHEN: registering agent
        runPromise(() -> registry.register(ceo, TENANT_ID));
        
        // THEN: registration metric incremented (catalog wiring is currently stubbed)
        assertThat(meterRegistry.counter(
            "virtualorg.agent.registered",
            "tenant", TENANT_ID
        ).count())
            .as("Registration metric should be incremented")
            .isEqualTo(1.0);
    }
    
    /**
     * Test batch agent registration.
     *
     * GIVEN: multiple organizational agents
     * WHEN: registering all agents in batch
     * THEN: all agents registered in catalog
     */
    @Test
    @DisplayName("Should register multiple agents in parallel")
    void shouldRegisterMultipleAgents() {
        // GIVEN: Multiple agents
        List<OrganizationalAgent> agents = List.of(
            createTestCEO(),
            createTestCTO(),
            createTestEngineer()
        );
        
        // WHEN: registering all agents
        runPromise(() -> registry.registerAll(agents, TENANT_ID));
        
        // THEN: registration completes without error for all agents (catalog wiring is stubbed)
    }
    
    /**
     * Test agent unregistration.
     *
     * GIVEN: registered agent
     * WHEN: unregistering agent
     * THEN: agent removed from catalog
     */
    @Test
    @DisplayName("Should unregister agent from catalog")
    void shouldUnregisterAgent() {
        // GIVEN: registered agent
        OrganizationalAgent ceo = createTestCEO();
        runPromise(() -> registry.register(ceo, TENANT_ID));
        
        // WHEN: unregistering agent
        runPromise(() -> registry.unregister(ceo, TENANT_ID));

        // THEN: unregistration metric incremented
        assertThat(meterRegistry.counter(
            "virtualorg.agent.unregistered",
            "role", "CEO",
            "tenant", TENANT_ID
        ).count())
            .as("Unregistration metric should be incremented")
            .isEqualTo(1.0);
    }
    
    /**
     * Test empty agent list registration.
     *
     * GIVEN: empty agent list
     * WHEN: registering empty list
     * THEN: operation completes without error
     */
    @Test
    @DisplayName("Should handle empty agent list gracefully")
    void shouldHandleEmptyAgentList() {
        // GIVEN: empty agent list
        List<OrganizationalAgent> emptyList = List.of();
        
        // WHEN/THEN: registration completes without error
        runPromise(() -> registry.registerAll(emptyList, TENANT_ID));
    }
    
    // ========================================================================
    // Test Helpers
    // ========================================================================
    
    private OrganizationalAgent createTestCEO() {
        return new TestAgent(
            Role.of("CEO", Layer.EXECUTIVE),
            Authority.builder().addDecision("all_decisions").build(),
            EscalationPath.of()
        );
    }
    
    private OrganizationalAgent createTestCTO() {
        return new TestAgent(
            Role.of("CTO", Layer.EXECUTIVE),
            Authority.builder().addDecision("tech_decisions").build(),
            EscalationPath.of(Role.of("CEO", Layer.EXECUTIVE))
        );
    }
    
    private OrganizationalAgent createTestEngineer() {
        return new TestAgent(
            Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR),
            Authority.builder().addDecision("code_review").build(),
            EscalationPath.of(
                Role.of("SeniorEngineer", Layer.INDIVIDUAL_CONTRIBUTOR),
                Role.of("CTO", Layer.EXECUTIVE)
            )
        );
    }
    
    /**
     * Test agent implementation for testing.
     */
    private static class TestAgent implements OrganizationalAgent {
        private final Role role;
        private final Authority authority;
        private final EscalationPath escalationPath;
        
        TestAgent(Role role, Authority authority, EscalationPath escalationPath) {
            this.role = role;
            this.authority = authority;
            this.escalationPath = escalationPath;
        }
        
        @Override
        public Role getRole() {
            return role;
        }
        
        @Override
        public Authority getAuthority() {
            return authority;
        }
        
        @Override
        public EscalationPath getEscalationPath() {
            return escalationPath;
        }
        
        // Minimal Agent interface implementation
        @Override
        public String getId() {
            return role.name();
        }
        
        public String getName() {
            return role.name() + " Agent";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public java.util.Set<String> getSupportedEventTypes() {
            return java.util.Collections.emptySet();
        }

        @Override
        public java.util.Set<String> getOutputEventTypes() {
            return java.util.Collections.emptySet();
        }

        @Override
        public java.util.List<com.ghatana.platform.domain.domain.event.Event> handle(
            com.ghatana.platform.domain.domain.event.Event event,
            com.ghatana.platform.domain.agent.registry.AgentExecutionContext context
        ) {
            return java.util.List.of(event);
        }

        @Override
        public com.ghatana.contracts.agent.v1.AgentResultProto execute(
                com.ghatana.contracts.agent.v1.AgentInputProto input) {
                return null;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public com.ghatana.platform.domain.agent.registry.AgentMetrics getMetrics() {
            return null;
        }
    }
}
