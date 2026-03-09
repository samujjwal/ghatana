package com.ghatana.virtualorg.framework.integration;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.virtualorg.framework.task.Task;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-department orchestration tests for virtual-org framework.
 *
 * Tests validate multi-step workflows where multiple departments coordinate
 * tasks through event publishing and orchestration.
 *
 * @doc.type class
 * @doc.purpose Cross-department orchestration integration tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Virtual-Org Cross-Department Orchestration Tests")
class CrossDepartmentOrchestrationTest extends EventloopTestBase {

    private static final TenantId TENANT_ID = TenantId.random();
    private TestOrganization organization;
    private OrchestrationEventTracker eventTracker;
    private Map<DepartmentType, TestDepartment> departments;

    @BeforeEach
    void setUp() {
        eventTracker = new OrchestrationEventTracker();
        organization = new TestOrganization(eventloop(), "Acme Corp", "Test Organization", TENANT_ID, eventTracker);
        departments = new HashMap<>();

        registerDepartment(DepartmentType.ENGINEERING);
        registerDepartment(DepartmentType.QA);
        registerDepartment(DepartmentType.DEVOPS);
        registerDepartment(DepartmentType.SUPPORT);
        registerDepartment(DepartmentType.SALES);
        registerDepartment(DepartmentType.FINANCE);
        registerDepartment(DepartmentType.PRODUCT);
    }

    /**
     * Verifies complete Engineering→QA→DevOps→Production pipeline.
     */
    @Test
    @DisplayName("Should orchestrate Engineering→QA→DevOps→Production pipeline")
    void shouldOrchestrateProductionPipeline() {
        // GIVEN: All departments with agents
        setupAgentsForDepartment(DepartmentType.ENGINEERING, 2);
        setupAgentsForDepartment(DepartmentType.QA, 2);
        setupAgentsForDepartment(DepartmentType.DEVOPS, 1);

        // WHEN: Engineering publishes build completion
        eventTracker.publishBuildCompleted("build-001", "main");
        assertThat(eventTracker.getEventsByType("engineering.build.completed"))
                .as("Engineering build completion event should be published")
                .isNotEmpty();

        // AND: Simulate QA publishing test results
        eventTracker.publishTestingCompleted("build-001", "all-tests-passed");
        assertThat(eventTracker.getEventsByType("qa.testing.completed"))
                .as("QA testing completion event should be published")
                .isNotEmpty();

        // AND: Simulate DevOps deployment
        eventTracker.publishDeploymentStarted("build-001", "production");
        assertThat(eventTracker.getEventsByType("devops.deployment.started"))
                .as("DevOps deployment started event should be published")
                .isNotEmpty();

        // FINALLY: Verify event sequence
        List<String> eventSequence = eventTracker.getEventSequence();
        assertThat(eventSequence)
                .as("Events should be in orchestration sequence")
                .containsSequence(
                        "engineering.build.completed",
                        "qa.testing.completed",
                        "devops.deployment.started"
                );
    }

    /**
     * Verifies Sales→Finance deal processing flow.
     */
    @Test
    @DisplayName("Should orchestrate Sales→Finance deal flow")
    void shouldOrchestrateDealFlow() {
        // GIVEN: Sales and Finance departments with agents
        setupAgentsForDepartment(DepartmentType.SALES, 2);
        setupAgentsForDepartment(DepartmentType.FINANCE, 1);

        // WHEN: Sales publishes new deal
        eventTracker.publishDealCreated("deal-123", "Enterprise Customer", "100000");
        assertThat(eventTracker.getEventsByType("sales.deal.created"))
                .as("Sales deal created event should be published")
                .isNotEmpty();

        // AND: Finance approves deal
        eventTracker.publishDealApproved("deal-123", "approved");
        assertThat(eventTracker.getEventsByType("finance.deal.approved"))
                .as("Finance deal approved event should be published")
                .isNotEmpty();

        // FINALLY: Verify deal sequence
        List<String> sequence = eventTracker.getEventSequence();
        assertThat(sequence)
                .as("Deal should follow Sales→Finance sequence")
                .containsSequence("sales.deal.created", "finance.deal.approved");
    }

    /**
     * Verifies error handling in orchestration flows.
     */
    @Test
    @DisplayName("Should handle errors in orchestration flows")
    void shouldHandleErrorsInOrchestration() {
        // GIVEN: All departments ready
        setupAgentsForDepartment(DepartmentType.ENGINEERING, 1);
        setupAgentsForDepartment(DepartmentType.QA, 1);
        setupAgentsForDepartment(DepartmentType.DEVOPS, 1);
        setupAgentsForDepartment(DepartmentType.SUPPORT, 1);

        // WHEN: Engineering publishes build failure
        eventTracker.publishBuildFailed("build-bad", "compilation-error");
        assertThat(eventTracker.getEventsByType("engineering.build.failed"))
                .as("Build failure event should be published")
                .isNotEmpty();

        // AND: Error is escalated to support
        eventTracker.publishErrorEscalated("build-bad", "Support Team");
        assertThat(eventTracker.getEventsByType("support.error.escalated"))
                .as("Error should be escalated to support")
                .isNotEmpty();

        // FINALLY: Verify correct error sequence
        List<String> sequence = eventTracker.getEventSequence();
        assertThat(sequence)
                .as("Error should cascade: build failure → escalation")
                .containsSequence("engineering.build.failed", "support.error.escalated");
    }

    /**
     * Verifies multi-tenant isolation in cross-department flows.
     */
    @Test
    @DisplayName("Should maintain tenant isolation across orchestration")
    void shouldMaintainTenantIsolationAcrossDepartments() {
        // GIVEN: First organization
        setupAgentsForDepartment(DepartmentType.ENGINEERING, 1);

        // WHEN: First organization publishes build event
        eventTracker.publishBuildCompleted("build-org1", "main");

        // AND: Second organization with different tenant
        TenantId tenant2 = TenantId.random();
        OrchestrationEventTracker tracker2 = new OrchestrationEventTracker();
        TestOrganization org2 = new TestOrganization(eventloop(), "Beta Corp", "Other Organization", tenant2, tracker2);
        tracker2.publishBuildCompleted("build-org2", "dev");

        // THEN: Each tracker has only its own events
        assertThat(eventTracker.getEventsByType("engineering.build.completed"))
                .as("Org 1 should have exactly 1 build event")
                .hasSize(1);

        assertThat(tracker2.getEventsByType("engineering.build.completed"))
                .as("Org 2 should have exactly 1 build event")
                .hasSize(1);

        // AND: Org2 tracker should not have org1's events
        assertThat(tracker2.eventLog.stream()
                .filter(e -> e.eventData.contains("build-org1"))
                .collect(Collectors.toList()))
                .as("Org 2 should not contain org 1 events")
                .isEmpty();
    }

    /**
     * Verifies Product feedback loops.
     */
    @Test
    @DisplayName("Should handle Product→Engineering→QA feedback loops")
    void shouldHandleProductFeedbackLoops() {
        // GIVEN: Product chain departments
        setupAgentsForDepartment(DepartmentType.PRODUCT, 1);
        setupAgentsForDepartment(DepartmentType.ENGINEERING, 1);
        setupAgentsForDepartment(DepartmentType.QA, 1);

        // WHEN: Product requests feature
        eventTracker.publishFeatureRequested("feature-auth", "Two-factor authentication");
        assertThat(eventTracker.getEventsByType("product.feature.requested"))
                .as("Feature request should be published")
                .isNotEmpty();

        // AND: Engineering implements
        eventTracker.publishFeatureImplemented("feature-auth", "2FA implemented");
        assertThat(eventTracker.getEventsByType("engineering.feature.implemented"))
                .as("Implementation should be published")
                .isNotEmpty();

        // AND: QA tests feature
        eventTracker.publishFeatureTested("feature-auth", "passed");
        assertThat(eventTracker.getEventsByType("qa.feature.tested"))
                .as("Test result should be published")
                .isNotEmpty();

        // FINALLY: Verify sequence
        List<String> sequence = eventTracker.getEventSequence();
        assertThat(sequence)
                .as("Should follow feedback loop sequence")
                .containsSequence(
                        "product.feature.requested",
                        "engineering.feature.implemented",
                        "qa.feature.tested"
                );
    }

    // Helper methods
    private void registerDepartment(DepartmentType type) {
        TestDepartment dept = new TestDepartment(type.name(), type, TENANT_ID, eventTracker);
        organization.registerDepartment(dept);
        departments.put(type, dept);
    }

    private void setupAgentsForDepartment(DepartmentType type, int count) {
        TestDepartment dept = departments.get(type);
        for (int i = 1; i <= count; i++) {
            Agent agent = Agent.builder()
                    .id(type.name().toLowerCase() + "-agent-" + i)
                    .name("Agent " + i + " (" + type.name() + ")")
                    .department(type.name().toLowerCase())
                    .build();
            dept.registerAgent(agent);
        }
    }

    private static class OrchestrationEventTracker implements EventPublisher {

        final List<OrchestrationEvent> eventLog = new CopyOnWriteArrayList<>();

        @Override
        public void publish(String eventType, byte[] payload) {
            eventLog.add(new OrchestrationEvent(eventType, new String(payload)));
        }

        @Override
        public void publishOrganizationCreated(String name, String description) {
            publish("organization.created", (name + "|" + description).getBytes());
        }

        @Override
        public void publishDepartmentRegistered(Identifier departmentId, String name, String type) {
            publish("department.registered", (departmentId + "|" + name + "|" + type).getBytes());
        }

        @Override
        public void publishTaskDeclared(String taskId, String name, String description) {
            publish("task.declared", (taskId + "|" + name).getBytes());
        }

        @Override
        public void publishTaskAssigned(String taskId, String agentId) {
            publish("task.assigned", (taskId + "|" + agentId).getBytes());
        }

        @Override
        public void publishTaskCompleted(String taskId, String result) {
            publish("task.completed", (taskId + "|" + result).getBytes());
        }

        void publishBuildCompleted(String buildId, String branch) {
            publish("engineering.build.completed", (buildId + "|" + branch).getBytes());
        }

        void publishBuildFailed(String buildId, String reason) {
            publish("engineering.build.failed", (buildId + "|" + reason).getBytes());
        }

        void publishTestingCompleted(String buildId, String result) {
            publish("qa.testing.completed", (buildId + "|" + result).getBytes());
        }

        void publishDeploymentStarted(String buildId, String environment) {
            publish("devops.deployment.started", (buildId + "|" + environment).getBytes());
        }

        void publishDealCreated(String dealId, String customer, String amount) {
            publish("sales.deal.created", (dealId + "|" + customer + "|" + amount).getBytes());
        }

        void publishDealApproved(String dealId, String status) {
            publish("finance.deal.approved", (dealId + "|" + status).getBytes());
        }

        void publishErrorEscalated(String buildId, String team) {
            publish("support.error.escalated", (buildId + "|" + team).getBytes());
        }

        void publishFeatureRequested(String featureId, String description) {
            publish("product.feature.requested", (featureId + "|" + description).getBytes());
        }

        void publishFeatureImplemented(String featureId, String notes) {
            publish("engineering.feature.implemented", (featureId + "|" + notes).getBytes());
        }

        void publishFeatureTested(String featureId, String result) {
            publish("qa.feature.tested", (featureId + "|" + result).getBytes());
        }

        List<OrchestrationEvent> getEventsByType(String type) {
            return eventLog.stream()
                    .filter(e -> e.eventType.equals(type))
                    .collect(Collectors.toList());
        }

        List<String> getEventSequence() {
            return eventLog.stream()
                    .map(e -> e.eventType)
                    .collect(Collectors.toList());
        }

        static class OrchestrationEvent {

            String eventType;
            String eventData;

            OrchestrationEvent(String eventType, String eventData) {
                this.eventType = eventType;
                this.eventData = eventData;
            }
        }
    }

    private static class TestDepartment extends Department {

        public TestDepartment(String name, DepartmentType type, TenantId tenantId, EventPublisher eventPublisher) {
            super(name, type);
        }

        @Override
        protected Promise<Agent> assignTask(Task task) {
            return Promise.of(getAgents().isEmpty() ? null : getAgents().get(0));
        }

        @Override
        public Map<String, Object> getKpis() {
            return Map.of("agents", getAgents().size(), "status", "operational");
        }
    }

    private static class TestOrganization extends AbstractOrganization {

        public TestOrganization(Eventloop eventloop, String name, String description, TenantId tenantId, EventPublisher eventPublisher) {
            super(eventloop, tenantId, name, description, eventPublisher);
        }
    }
}
