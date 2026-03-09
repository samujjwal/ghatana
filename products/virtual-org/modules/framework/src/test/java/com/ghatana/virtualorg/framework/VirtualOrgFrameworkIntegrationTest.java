package com.ghatana.virtualorg.framework;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.virtualorg.framework.task.Task;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for virtual-org framework core functionality.
 *
 * Tests validate: - Organization creation and initialization - Department
 * registration with organizations - Agent assignment to departments - Event
 * publishing during lifecycle events - Multi-department coordination
 *
 * @see AbstractOrganization
 * @see Department
 * @see Agent
 */
@DisplayName("Virtual-Org Framework Integration Tests")
class VirtualOrgFrameworkIntegrationTest extends EventloopTestBase {

    private static final TenantId TENANT_ID = TenantId.of("tenant-test-123");
    private AbstractOrganization organization;
    private MockEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new MockEventPublisher();
        organization = new TestOrganization(eventloop(), TENANT_ID, "Test Org", "Test Organization", eventPublisher);
    }

    /**
     * Verifies that organization can be created and initialized.
     *
     * GIVEN: An organization instance WHEN: Organization is created THEN:
     * Organization properties are set correctly and initialized
     */
    @Test
    @DisplayName("Should create organization with correct properties")
    void shouldCreateOrganizationWithCorrectProperties() {
        assertThat(organization.getName())
                .as("Organization name should match constructor")
                .isEqualTo("Test Org");

        assertThat(organization.getDescription())
                .as("Organization description should match constructor")
                .isEqualTo("Test Organization");

        assertThat(organization.getTenantId())
                .as("Tenant ID should match constructor")
                .isEqualTo(TENANT_ID);
    }

    /**
     * Verifies that departments can be registered with an organization.
     *
     * GIVEN: An organization and department instances WHEN: Department is
     * registered THEN: Department is stored and organization reference is set
     */
    @Test
    @DisplayName("Should register department and emit event")
    void shouldRegisterDepartmentAndEmitEvent() {
        // GIVEN: An organization and a test department
        Department department = new TestDepartment("Engineering", DepartmentType.ENGINEERING);

        // WHEN: Department is registered
        organization.registerDepartment(department);

        // THEN: Department is registered and event is published
        assertThat(department.getOrganization())
                .as("Department organization reference should be set")
                .isEqualTo(organization);

        assertThat(eventPublisher.departmentRegisteredCalls.get())
                .as("DepartmentRegistered event should be published")
                .isEqualTo(1);

        assertThat(eventPublisher.lastDepartmentName)
                .as("Published department name should match")
                .isEqualTo("Engineering");
    }

    /**
     * Verifies that multiple departments can be registered in the same
     * organization.
     *
     * GIVEN: An organization WHEN: Multiple departments are registered THEN:
     * All departments are stored and accessible
     */
    @Test
    @DisplayName("Should register multiple departments")
    void shouldRegisterMultipleDepartments() {
        // GIVEN: An organization
        Department eng = new TestDepartment("Engineering", DepartmentType.ENGINEERING);
        Department qa = new TestDepartment("QA", DepartmentType.QA);
        Department devops = new TestDepartment("DevOps", DepartmentType.DEVOPS);

        // WHEN: Multiple departments are registered
        organization.registerDepartment(eng);
        organization.registerDepartment(qa);
        organization.registerDepartment(devops);

        // THEN: All departments should be registered
        assertThat(eventPublisher.departmentRegisteredCalls.get())
                .as("All departments should emit registration events")
                .isEqualTo(3);

        assertThat(eng.getOrganization())
                .as("All departments should have organization reference")
                .isEqualTo(organization);
        assertThat(qa.getOrganization())
                .isEqualTo(organization);
        assertThat(devops.getOrganization())
                .isEqualTo(organization);
    }

    /**
     * Verifies that agents can be registered with departments.
     *
     * GIVEN: An organization, department, and agents WHEN: Agents are
     * registered with department THEN: Agents are stored and accessible
     */
    @Test
    @DisplayName("Should register agents with departments")
    void shouldRegisterAgentsWithDepartments() {
        // GIVEN: An organization and department
        Department department = new TestDepartment("Engineering", DepartmentType.ENGINEERING);
        organization.registerDepartment(department);

        Agent agent1 = Agent.builder()
                .name("Agent-1")
                .department("engineering")
                .build();
        Agent agent2 = Agent.builder()
                .name("Agent-2")
                .department("engineering")
                .build();

        // WHEN: Agents are registered with department
        department.registerAgent(agent1);
        department.registerAgent(agent2);

        // THEN: Agents should be accessible from department
        assertThat(department.getAgents())
                .as("Department should have all registered agents")
                .hasSize(2)
                .contains(agent1, agent2);
    }

    /**
     * Verifies that organization can retrieve registered departments.
     *
     * GIVEN: An organization with registered departments WHEN: Departments are
     * queried THEN: All departments are retrievable
     */
    @Test
    @DisplayName("Should retrieve registered departments")
    void shouldRetrieveRegisteredDepartments() {
        // GIVEN: An organization with departments
        Department eng = new TestDepartment("Engineering", DepartmentType.ENGINEERING);
        Department qa = new TestDepartment("QA", DepartmentType.QA);
        organization.registerDepartment(eng);
        organization.registerDepartment(qa);

        // WHEN: Departments are retrieved
        int departmentCount = countDepartments(organization);

        // THEN: Department count should match registered departments
        assertThat(departmentCount)
                .as("Organization should have all registered departments")
                .isEqualTo(2);
    }

    /**
     * Verifies event publishing during department registration.
     *
     * GIVEN: An organization with event publisher WHEN: Department is
     * registered THEN: Correct events are published with correct data
     */
    @Test
    @DisplayName("Should publish correct event data during registration")
    void shouldPublishCorrectEventDataDuringRegistration() {
        // GIVEN: Organization and department
        Department department = new TestDepartment("Engineering", DepartmentType.ENGINEERING);

        // WHEN: Department is registered
        organization.registerDepartment(department);

        // THEN: Event should be published with correct department type
        assertThat(eventPublisher.lastDepartmentType)
                .as("Published event should include department type")
                .isEqualTo("ENGINEERING");
    }

    // ==================== Helper Classes ====================
    /**
     * Mock event publisher for testing.
     */
    private static class MockEventPublisher implements EventPublisher {

        AtomicInteger departmentRegisteredCalls = new AtomicInteger(0);
        String lastDepartmentName;
        String lastDepartmentType;

        @Override
        public void publish(String eventType, byte[] payload) {
            // Test stub
        }

        @Override
        public void publishOrganizationCreated(String name, String description) {
            // Test stub
        }

        @Override
        public void publishDepartmentRegistered(Identifier departmentId, String name, String type) {
            departmentRegisteredCalls.incrementAndGet();
            lastDepartmentName = name;
            lastDepartmentType = type;
        }

        @Override
        public void publishTaskDeclared(String taskId, String name, String description) {
            // Test stub
        }

        @Override
        public void publishTaskAssigned(String taskId, String agentId) {
            // Test stub
        }

        @Override
        public void publishTaskCompleted(String taskId, String result) {
            // Test stub
        }
    }

    /**
     * Test implementation of Organization.
     */
    private static class TestOrganization extends AbstractOrganization {

        public TestOrganization(Eventloop eventloop, TenantId tenantId, String name, String description, EventPublisher eventPublisher) {
            super(eventloop, tenantId, name, description, eventPublisher);
        }
    }

    /**
     * Test implementation of Department.
     */
    private static class TestDepartment extends Department {

        TestDepartment(String name, DepartmentType type) {
            super(name, type);
        }

        @Override
        protected io.activej.promise.Promise<Agent> assignTask(Task task) {
            return io.activej.promise.Promise.of(getAgents().stream()
                    .findFirst()
                    .orElse(null));
        }

        @Override
        public java.util.Map<String, Object> getKpis() {
            return java.util.Map.of();
        }
    }

    // ==================== Utility Methods ====================
    /**
     * Count departments in organization (uses reflection to access private
     * field).
     */
    private int countDepartments(AbstractOrganization org) {
        try {
            java.lang.reflect.Field field = AbstractOrganization.class.getDeclaredField("departments");
            field.setAccessible(true);
            java.util.Map<String, Department> depts = (java.util.Map<String, Department>) field.get(org);
            return depts.size();
        } catch (Exception e) {
            throw new RuntimeException("Failed to count departments", e);
        }
    }
}
