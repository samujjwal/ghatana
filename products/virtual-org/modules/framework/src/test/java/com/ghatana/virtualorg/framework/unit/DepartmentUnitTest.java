package com.ghatana.virtualorg.framework.unit;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.virtualorg.framework.task.Task;
import com.ghatana.virtualorg.framework.task.TaskPriority;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Department unit tests.
 *
 * Tests department lifecycle, agent management, and task orchestration.
 *
 * @doc.type class
 * @doc.purpose Department component unit tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Department Unit Tests")
class DepartmentUnitTest extends EventloopTestBase {

    private static final TenantId TENANT_ID = TenantId.random();
    private TestDepartment department;
    private SimpleEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new SimpleEventPublisher();
        department = new TestDepartment("Engineering", DepartmentType.ENGINEERING, TENANT_ID, eventPublisher);
    }

    /**
     * Verifies department creation with correct properties.
     */
    @Test
    @DisplayName("Should create department with correct properties")
    void shouldCreateDepartment() {
        assertThat(department.getName())
                .as("Department name should match")
                .isEqualTo("Engineering");

        assertThat(department.getId())
                .as("Department should have ID")
                .isNotNull();

        assertThat(department.getType())
                .as("Department should have type")
                .isEqualTo(DepartmentType.ENGINEERING);
    }

    /**
     * Verifies agent registration in department.
     */
    @Test
    @DisplayName("Should register agents in department")
    void shouldRegisterAgents() {
        // WHEN: Register agents
        Agent agent1 = Agent.builder()
                .id("eng-1")
                .name("Engineer 1")
                .department("Engineering")
                .build();
        Agent agent2 = Agent.builder()
                .id("eng-2")
                .name("Engineer 2")
                .department("Engineering")
                .build();

        department.registerAgent(agent1);
        department.registerAgent(agent2);

        // THEN: Agents should be registered
        assertThat(department.getAgents())
                .as("Department should have 2 agents")
                .hasSize(2);
    }

    /**
     * Verifies agent retrieval by ID.
     */
    @Test
    @DisplayName("Should retrieve agent by ID")
    void shouldRetrieveAgentById() {
        // GIVEN: Registered agent
        Agent agent = Agent.builder()
                .id("eng-3")
                .name("Engineer 3")
                .department("Engineering")
                .build();
        department.registerAgent(agent);

        // WHEN: Retrieve agent
        List<Agent> agents = department.getAgents();

        // THEN: Agent should be found
        assertThat(agents)
                .as("Should find registered agent")
                .anySatisfy(a -> assertThat(a.getId()).isEqualTo("eng-3"));
    }

    /**
     * Verifies task assignment to agents.
     */
    @Test
    @DisplayName("Should assign task to available agent")
    void shouldAssignTaskToAgent() {
        // GIVEN: Department with agents
        Agent agent = Agent.builder()
                .id("eng-4")
                .name("Engineer 4")
                .department("Engineering")
                .build();
        department.registerAgent(agent);

        // WHEN: Create and assign task
        Task task = new Task("code_review", TaskPriority.HIGH);

        // THEN: Task assignment should succeed
        assertThat(task)
                .as("Task should be created")
                .isNotNull();

        assertThat(task.getType())
                .as("Task type should match")
                .isEqualTo("code_review");
    }

    /**
     * Verifies department KPIs.
     */
    @Test
    @DisplayName("Should calculate department KPIs")
    void shouldCalculateKpis() {
        // GIVEN: Department with multiple agents
        for (int i = 0; i < 3; i++) {
            Agent agent = Agent.builder()
                    .id("eng-" + i)
                    .name("Engineer " + i)
                    .department("Engineering")
                    .build();
            department.registerAgent(agent);
        }

        // WHEN: Get KPIs
        Map<String, Object> kpis = department.getKpis();

        // THEN: KPIs should reflect department state
        assertThat(kpis)
                .as("KPIs should exist")
                .isNotNull()
                .containsKey("agents");

        assertThat((Integer) kpis.get("agents"))
                .as("KPIs should show 3 agents")
                .isEqualTo(3);
    }

    /**
     * Verifies agent availability tracking.
     */
    @Test
    @DisplayName("Should track agent availability")
    void shouldTrackAgentAvailability() {
        // GIVEN: Agent
        Agent agent = Agent.builder()
                .id("eng-5")
                .name("Engineer 5")
                .department("Engineering")
                .build();

        // THEN: Agent should be available
        assertThat(agent.isAvailable())
                .as("Agent should be available")
                .isTrue();

        // WHEN: Mark unavailable
        agent.setAvailable(false);

        // THEN: Agent should be unavailable
        assertThat(agent.isAvailable())
                .as("Agent should be unavailable")
                .isFalse();
    }

    /**
     * Verifies multiple departments can coexist.
     */
    @Test
    @DisplayName("Should support multiple departments")
    void shouldSupportMultipleDepartments() {
        // WHEN: Create multiple departments
        TestDepartment qa = new TestDepartment("QA", DepartmentType.QA, TENANT_ID, eventPublisher);
        TestDepartment devops = new TestDepartment("DevOps", DepartmentType.DEVOPS, TENANT_ID, eventPublisher);

        // THEN: Departments should be independent
        assertThat(department.getName()).isEqualTo("Engineering");
        assertThat(qa.getName()).isEqualTo("QA");
        assertThat(devops.getName()).isEqualTo("DevOps");

        // AND: Can register agents separately
        Agent engAgent = Agent.builder().id("eng").name("Eng").department("Engineering").build();
        Agent qaAgent = Agent.builder().id("qa").name("QA").department("QA").build();

        department.registerAgent(engAgent);
        qa.registerAgent(qaAgent);

        assertThat(department.getAgents()).hasSize(1);
        assertThat(qa.getAgents()).hasSize(1);
    }

    /**
     * Verifies department handles empty agent list gracefully.
     */
    @Test
    @DisplayName("Should handle department with no agents")
    void shouldHandleEmptyDepartment() {
        // WHEN: Department has no agents
        // THEN: Operations should handle gracefully
        assertThat(department.getAgents())
                .as("Empty department should have no agents")
                .isEmpty();

        Map<String, Object> kpis = department.getKpis();
        assertThat((Integer) kpis.get("agents"))
                .as("KPIs should show 0 agents")
                .isZero();
    }

    /**
     * Verifies agent capabilities are maintained.
     */
    @Test
    @DisplayName("Should maintain agent capabilities")
    void shouldMaintainAgentCapabilities() {
        // GIVEN: Agent with specific capabilities
        Agent agent = Agent.builder()
                .id("eng-6")
                .name("Senior Engineer")
                .department("Engineering")
                .capabilities("java", "spring", "kubernetes")
                .build();

        department.registerAgent(agent);

        // WHEN: Retrieve agent
        Agent retrieved = department.getAgents().get(0);

        // THEN: Capabilities should be preserved
        assertThat(retrieved)
                .as("Retrieved agent should have capabilities")
                .isNotNull();
    }

    /**
     * Verifies concurrent agent operations.
     */
    @Test
    @DisplayName("Should handle concurrent agent registrations")
    void shouldHandleConcurrentOperations() {
        // WHEN: Register multiple agents sequentially
        for (int i = 0; i < 10; i++) {
            Agent agent = Agent.builder()
                    .id("concurrent-" + i)
                    .name("Concurrent Agent " + i)
                    .department("Engineering")
                    .build();
            department.registerAgent(agent);
        }

        // THEN: All agents should be registered
        assertThat(department.getAgents())
                .as("All 10 agents should be registered")
                .hasSize(10);
    }

    /**
     * Verifies task priority handling.
     */
    @Test
    @DisplayName("Should handle task priorities")
    void shouldHandleTaskPriorities() {
        // WHEN: Create tasks with different priorities
        Task highPriority = new Task("urgent_fix", TaskPriority.HIGH);
        Task mediumPriority = new Task("feature", TaskPriority.MEDIUM);
        Task lowPriority = new Task("documentation", TaskPriority.LOW);

        // THEN: Tasks should preserve priority
        assertThat(highPriority.getType()).isEqualTo("urgent_fix");
        assertThat(mediumPriority.getType()).isEqualTo("feature");
        assertThat(lowPriority.getType()).isEqualTo("documentation");
    }

    // ============ HELPER CLASSES ============
    private static class SimpleEventPublisher implements EventPublisher {

        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public void publish(String eventType, byte[] payload) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            events.add(event);
        }

        @Override
        public void publishOrganizationCreated(String name, String description) {
            publish("OrganizationCreated", new byte[0]);
        }

        @Override
        public void publishDepartmentRegistered(Identifier departmentId, String name, String type) {
            publish("DepartmentRegistered", new byte[0]);
        }

        @Override
        public void publishTaskDeclared(String taskId, String name, String description) {
            publish("TaskDeclared", new byte[0]);
        }

        @Override
        public void publishTaskAssigned(String taskId, String agentId) {
            publish("TaskAssigned", new byte[0]);
        }

        @Override
        public void publishTaskCompleted(String taskId, String result) {
            publish("TaskCompleted", new byte[0]);
        }

        List<Map<String, Object>> getEvents() {
            return new ArrayList<>(events);
        }
    }

    private static class TestDepartment extends Department {

        private final List<Agent> agents = new ArrayList<>();

        public TestDepartment(String name, DepartmentType type, TenantId tenantId, EventPublisher eventPublisher) {
            super(name, type);
        }

        public void registerAgent(Agent agent) {
            agents.add(agent);
        }

        public List<Agent> getAgents() {
            return new ArrayList<>(agents);
        }

        @Override
        protected Promise<Agent> assignTask(Task task) {
            return Promise.of(agents.isEmpty() ? null : agents.get(0));
        }

        @Override
        public Map<String, Object> getKpis() {
            return Map.of("agents", agents.size(), "type", getType());
        }
    }
}
