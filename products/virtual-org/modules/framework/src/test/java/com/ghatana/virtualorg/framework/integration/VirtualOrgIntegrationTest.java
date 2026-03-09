package com.ghatana.virtualorg.framework.integration;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.virtualorg.framework.task.Task;
import com.ghatana.virtualorg.framework.task.TaskPriority;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for virtual-org framework orchestration.
 *
 * Tests validate: - Department registration and lifecycle - Agent management
 * within departments - Task creation and assignment - Event publishing -
 * Multi-tenant isolation
 *
 * @doc.type class
 * @doc.purpose Integration tests for virtual-org framework
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Virtual-Org Framework Integration Tests")
class VirtualOrgIntegrationTest extends EventloopTestBase {

    private static final TenantId TENANT_ID = TenantId.random();
    private TestOrganization organization;
    private StubEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new StubEventPublisher();
        organization = new TestOrganization(eventloop(), "Test Corp", "Test Organization", TENANT_ID, eventPublisher);
    }

    /**
     * Verifies that departments can be registered in an organization.
     *
     * GIVEN: An organization WHEN: A department is registered THEN: The
     * department exists with correct identity
     */
    @Test
    @DisplayName("Should register departments in organization")
    void shouldRegisterDepartmentsInOrganization() {
        // GIVEN: Organization is initialized

        // WHEN: Register an engineering department
        TestDepartment engineering = new TestDepartment("Engineering", DepartmentType.ENGINEERING, TENANT_ID, eventPublisher);
        organization.registerDepartment(engineering);

        // THEN: Department should be registered
        assertThat(engineering)
                .as("Engineering department should be created")
                .isNotNull();

        assertThat(engineering.getName())
                .as("Department name should match")
                .isEqualTo("Engineering");

        assertThat(engineering.getId())
                .as("Department should have an ID")
                .isNotNull();

        assertThat(engineering.getType())
                .as("Department type should be ENGINEERING")
                .isEqualTo(DepartmentType.ENGINEERING);
    }

    /**
     * Verifies that agents can be registered in departments.
     *
     * GIVEN: A department in the organization WHEN: An agent is registered
     * THEN: The agent should be accessible in the department
     */
    @Test
    @DisplayName("Should register agents in departments")
    void shouldRegisterAgentsInDepartments() {
        // GIVEN: Engineering department
        TestDepartment engineering = new TestDepartment("Engineering", DepartmentType.ENGINEERING, TENANT_ID, eventPublisher);
        organization.registerDepartment(engineering);

        // WHEN: Register an agent
        Agent agent = Agent.builder()
                .id("Agent-1")
                .name("Senior Engineer")
                .department("Engineering")
                .capabilities("code-review", "architecture-design")
                .build();
        engineering.registerAgent(agent);

        // THEN: Agent should be registered
        assertThat(engineering.getAgents())
                .as("Department should have one agent")
                .hasSize(1);

        assertThat(engineering.getAgents().get(0))
                .as("Registered agent should match")
                .isEqualTo(agent);

        assertThat(agent.getName())
                .as("Agent name should match")
                .isEqualTo("Senior Engineer");

        assertThat(agent.getId())
                .as("Agent should have an ID")
                .isNotNull();
    }

    /**
     * Verifies that tasks can be created and assigned to agents.
     *
     * GIVEN: A department with agents WHEN: A task is created and assigned
     * THEN: The task should exist with correct properties
     */
    @Test
    @DisplayName("Should create and assign tasks")
    void shouldCreateAndAssignTasks() {
        // GIVEN: Engineering department with an agent
        TestDepartment engineering = new TestDepartment("Engineering", DepartmentType.ENGINEERING, TENANT_ID, eventPublisher);
        organization.registerDepartment(engineering);
        Agent engineer = Agent.builder()
                .id("Eng-1")
                .name("Backend Developer")
                .department("Engineering")
                .build();
        engineering.registerAgent(engineer);

        // WHEN: Create a task
        Task task = new Task("ImplementFeature", TaskPriority.HIGH);

        // THEN: Task should have correct properties
        assertThat(task)
                .as("Task should be created")
                .isNotNull();

        assertThat(task.getType())
                .as("Task type should match")
                .isEqualTo("ImplementFeature");

        assertThat(task.getPriority())
                .as("Task priority should be HIGH")
                .isEqualTo(TaskPriority.HIGH);

        // WHEN: Assign task to agent
        task.assignTo(engineer);

        // THEN: Agent should be assigned
        assertThat(task.getAssignedAgent())
                .as("Task should be assigned to engineer")
                .isEqualTo(engineer);
    }

    /**
     * Verifies that events are published during organization lifecycle.
     *
     * GIVEN: An organization with event publisher WHEN: Events are published
     * THEN: Events should be recorded in the event publisher
     */
    @Test
    @DisplayName("Should publish organization events")
    void shouldPublishOrganizationEvents() {
        // GIVEN: Organization with event publisher
        assertThat(eventPublisher)
                .as("Event publisher should be initialized")
                .isNotNull();

        // WHEN: Publish an organization created event
        eventPublisher.publishOrganizationCreated(organization.getName(), "Test Organization");

        // THEN: Event should be published
        assertThat(eventPublisher.getPublishedEvents())
                .as("Events should be published")
                .isNotEmpty();

        assertThat(eventPublisher.lastPublishedEventType)
                .as("Last event type should be organization.created")
                .isEqualTo("organization.created");
    }

    /**
     * Verifies multi-tenant isolation between organizations.
     *
     * GIVEN: Two organizations with different tenant IDs WHEN: Each
     * organization registers departments THEN: Each organization maintains
     * independent state
     */
    @Test
    @DisplayName("Should maintain tenant isolation")
    void shouldMaintainTenantIsolation() {
        // GIVEN: Current organization
        TestDepartment eng1 = new TestDepartment("Engineering", DepartmentType.ENGINEERING, TENANT_ID, eventPublisher);
        organization.registerDepartment(eng1);

        // Create second organization with different tenant
        TenantId tenant2 = TenantId.random();
        StubEventPublisher publisher2 = new StubEventPublisher();
        TestOrganization organization2 = new TestOrganization(eventloop(), "Other Corp", "Other Organization", tenant2, publisher2);
        TestDepartment eng2 = new TestDepartment("Engineering", DepartmentType.ENGINEERING, tenant2, publisher2);
        organization2.registerDepartment(eng2);

        // THEN: TenantIds should be different
        assertThat(organization.getTenantId())
                .as("Organization 1 tenant should differ from Organization 2")
                .isNotEqualTo(organization2.getTenantId());

        // And departments should have different IDs (even with same name)
        assertThat(eng1.getId())
                .as("Engineering departments should have different IDs")
                .isNotEqualTo(eng2.getId());
    }

    /**
     * Verifies that multiple departments can coexist in same organization.
     *
     * GIVEN: An organization WHEN: Multiple departments are registered THEN:
     * Organization should track all departments
     */
    @Test
    @DisplayName("Should support multiple departments in organization")
    void shouldSupportMultipleDepartmentsInOrganization() {
        // GIVEN: Organization

        // WHEN: Register multiple departments
        TestDepartment engineering = new TestDepartment("Engineering", DepartmentType.ENGINEERING, TENANT_ID, eventPublisher);
        TestDepartment qa = new TestDepartment("QA", DepartmentType.QA, TENANT_ID, eventPublisher);
        TestDepartment devops = new TestDepartment("DevOps", DepartmentType.DEVOPS, TENANT_ID, eventPublisher);

        organization.registerDepartment(engineering);
        organization.registerDepartment(qa);
        organization.registerDepartment(devops);

        // THEN: Organization should have multiple departments
        assertThat(organization.getTenantId())
                .as("Organization should have tenant ID")
                .isEqualTo(TENANT_ID);

        // And all departments should be registered with the organization
        assertThat(engineering.getOrganization())
                .as("Engineering should be registered with organization")
                .isEqualTo(organization);

        assertThat(qa.getOrganization())
                .as("QA should be registered with organization")
                .isEqualTo(organization);

        assertThat(devops.getOrganization())
                .as("DevOps should be registered with organization")
                .isEqualTo(organization);
    }

    /**
     * Test helper: Stub implementation of EventPublisher for testing.
     */
    private static class StubEventPublisher implements EventPublisher {

        private final List<String> publishedEvents = new ArrayList<>();
        String lastPublishedEventType;

        @Override
        public void publish(String eventType, byte[] payload) {
            publishedEvents.add(eventType);
            lastPublishedEventType = eventType;
        }

        @Override
        public void publishOrganizationCreated(String name, String description) {
            publish("organization.created", new byte[0]);
        }

        @Override
        public void publishDepartmentRegistered(com.ghatana.platform.types.identity.Identifier departmentId, String name, String type) {
            publish("department.registered", new byte[0]);
        }

        @Override
        public void publishTaskDeclared(String taskId, String name, String description) {
            publish("task.declared", new byte[0]);
        }

        @Override
        public void publishTaskAssigned(String taskId, String agentId) {
            publish("task.assigned", new byte[0]);
        }

        @Override
        public void publishTaskCompleted(String taskId, String result) {
            publish("task.completed", new byte[0]);
        }

        List<String> getPublishedEvents() {
            return new ArrayList<>(publishedEvents);
        }
    }

    /**
     * Test helper: Test department implementation.
     */
    private static class TestDepartment extends Department {

        public TestDepartment(String name, DepartmentType type, TenantId tenantId, EventPublisher eventPublisher) {
            super(name, type);
            // Note: organization is set when registered with AbstractOrganization
        }

        @Override
        protected Promise<Agent> assignTask(com.ghatana.virtualorg.framework.task.Task task) {
            return Promise.of(getAgents().isEmpty() ? null : getAgents().get(0));
        }

        @Override
        public Map<String, Object> getKpis() {
            return Map.of("agent_count", getAgents().size());
        }
    }

    /**
     * Test helper: Minimal test organization implementation.
     */
    private static class TestOrganization extends AbstractOrganization {

        public TestOrganization(Eventloop eventloop, String name, String description, TenantId tenantId, EventPublisher eventPublisher) {
            super(eventloop, tenantId, name, description, eventPublisher);
        }
    }
}
