package com.ghatana.virtualorg.framework.unit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.agent.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent unit tests.
 *
 * Tests agent lifecycle, capabilities, and availability.
 *
 * @doc.type class
 * @doc.purpose Agent component unit tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Agent Unit Tests")
class AgentUnitTest extends EventloopTestBase {

    private Agent agent;

    @BeforeEach
    void setUp() {
        agent = Agent.builder()
                .id("agent-001")
                .name("Senior Engineer")
                .department("Engineering")
                .build();
    }

    /**
     * Verifies agent creation with correct properties.
     */
    @Test
    @DisplayName("Should create agent with correct properties")
    void shouldCreateAgent() {
        assertThat(agent.getId())
                .as("Agent ID should match")
                .isEqualTo("agent-001");

        assertThat(agent.getName())
                .as("Agent name should match")
                .isEqualTo("Senior Engineer");

        assertThat(agent.getDepartment())
                .as("Agent department should match")
                .isEqualTo("Engineering");
    }

    /**
     * Verifies agent availability state.
     */
    @Test
    @DisplayName("Should track agent availability state")
    void shouldTrackAvailability() {
        // GIVEN: Agent created (default available)
        // WHEN: Check availability
        boolean available = agent.isAvailable();

        // THEN: Should be available by default
        assertThat(available)
                .as("Agent should be available by default")
                .isTrue();
    }

    /**
     * Verifies agent availability can be changed.
     */
    @Test
    @DisplayName("Should update agent availability")
    void shouldUpdateAvailability() {
        // WHEN: Set unavailable
        agent.setAvailable(false);

        // THEN: Should be unavailable
        assertThat(agent.isAvailable())
                .as("Agent should be unavailable after setAvailable(false)")
                .isFalse();

        // WHEN: Set available again
        agent.setAvailable(true);

        // THEN: Should be available
        assertThat(agent.isAvailable())
                .as("Agent should be available after setAvailable(true)")
                .isTrue();
    }

    /**
     * Verifies agent with capabilities.
     */
    @Test
    @DisplayName("Should support agent capabilities")
    void shouldSupportCapabilities() {
        // WHEN: Create agent with capabilities
        Agent capableAgent = Agent.builder()
                .id("agent-002")
                .name("Full Stack Engineer")
                .department("Engineering")
                .capabilities("java", "javascript", "kubernetes")
                .build();

        // THEN: Agent should be created
        assertThat(capableAgent)
                .as("Agent with capabilities should be created")
                .isNotNull();

        assertThat(capableAgent.getId())
                .as("Agent should have correct ID")
                .isEqualTo("agent-002");
    }

    /**
     * Verifies multiple agents can coexist.
     */
    @Test
    @DisplayName("Should support multiple agents")
    void shouldSupportMultipleAgents() {
        // WHEN: Create multiple agents
        Agent agent1 = Agent.builder()
                .id("multi-1")
                .name("Agent 1")
                .department("Engineering")
                .build();

        Agent agent2 = Agent.builder()
                .id("multi-2")
                .name("Agent 2")
                .department("Engineering")
                .build();

        Agent agent3 = Agent.builder()
                .id("multi-3")
                .name("Agent 3")
                .department("QA")
                .build();

        // THEN: All agents should be independent
        assertThat(agent1.getId()).isEqualTo("multi-1");
        assertThat(agent2.getId()).isEqualTo("multi-2");
        assertThat(agent3.getId()).isEqualTo("multi-3");

        // AND: Department assignments should be independent
        assertThat(agent1.getDepartment()).isEqualTo("Engineering");
        assertThat(agent3.getDepartment()).isEqualTo("QA");
    }

    /**
     * Verifies agent department assignment.
     */
    @Test
    @DisplayName("Should support different departments")
    void shouldSupportDifferentDepartments() {
        // WHEN: Create agents in different departments
        Agent engAgent = Agent.builder()
                .id("eng")
                .name("Engineer")
                .department("Engineering")
                .build();

        Agent qaAgent = Agent.builder()
                .id("qa")
                .name("QA")
                .department("QA")
                .build();

        Agent opsAgent = Agent.builder()
                .id("ops")
                .name("DevOps")
                .department("DevOps")
                .build();

        // THEN: Department assignments should be correct
        assertThat(engAgent.getDepartment()).isEqualTo("Engineering");
        assertThat(qaAgent.getDepartment()).isEqualTo("QA");
        assertThat(opsAgent.getDepartment()).isEqualTo("DevOps");
    }

    /**
     * Verifies agent builder pattern.
     */
    @Test
    @DisplayName("Should support builder pattern for agent creation")
    void shouldSupportBuilderPattern() {
        // WHEN: Create agent using builder
        Agent builtAgent = Agent.builder()
                .id("builder-test")
                .name("Built Agent")
                .department("Engineering")
                .capabilities("testing", "automation")
                .build();

        // THEN: All properties should be set
        assertThat(builtAgent.getId()).isEqualTo("builder-test");
        assertThat(builtAgent.getName()).isEqualTo("Built Agent");
        assertThat(builtAgent.getDepartment()).isEqualTo("Engineering");
        assertThat(builtAgent.isAvailable()).isTrue();
    }

    /**
     * Verifies agent identity.
     */
    @Test
    @DisplayName("Should maintain agent identity")
    void shouldMaintainIdentity() {
        // WHEN: Create multiple agents
        Agent a1 = Agent.builder().id("identity-1").name("Agent 1").department("Eng").build();
        Agent a2 = Agent.builder().id("identity-1").name("Agent 1").department("Eng").build();
        Agent a3 = Agent.builder().id("identity-2").name("Agent 2").department("Eng").build();

        // THEN: Same ID should create distinct instances but same identity
        assertThat(a1.getId()).isEqualTo(a2.getId());
        assertThat(a1.getId()).isNotEqualTo(a3.getId());
    }

    /**
     * Verifies agent can work in multiple states.
     */
    @Test
    @DisplayName("Should handle agent state transitions")
    void shouldHandleStateTransitions() {
        // GIVEN: Agent available
        assertThat(agent.isAvailable()).isTrue();

        // WHEN: Make unavailable (e.g., on vacation)
        agent.setAvailable(false);

        // THEN: Should reflect new state
        assertThat(agent.isAvailable()).isFalse();

        // WHEN: Make available again
        agent.setAvailable(true);

        // THEN: Should reflect new state
        assertThat(agent.isAvailable()).isTrue();
    }

    /**
     * Verifies agent name persistence.
     */
    @Test
    @DisplayName("Should preserve agent name")
    void shouldPreserveName() {
        assertThat(agent.getName())
                .as("Agent name should be preserved")
                .isEqualTo("Senior Engineer");
    }

    /**
     * Verifies agents can be disabled without losing information.
     */
    @Test
    @DisplayName("Should allow agent disabling without data loss")
    void shouldAllowDisabling() {
        // WHEN: Disable agent
        agent.setAvailable(false);

        // THEN: All other properties should be preserved
        assertThat(agent.getId()).isEqualTo("agent-001");
        assertThat(agent.getName()).isEqualTo("Senior Engineer");
        assertThat(agent.getDepartment()).isEqualTo("Engineering");
        assertThat(agent.isAvailable()).isFalse();
    }
}
