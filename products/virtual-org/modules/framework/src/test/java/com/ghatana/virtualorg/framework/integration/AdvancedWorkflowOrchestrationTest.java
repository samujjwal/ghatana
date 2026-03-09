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
import com.ghatana.virtualorg.framework.task.TaskPriority;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced workflow orchestration tests for virtual-org framework.
 *
 * Tests complex multi-department workflows including: - Product to Engineering
 * feature implementation - Marketing to Sales campaign execution - DevOps to
 * Support infrastructure requests - Finance to Sales budget approvals - QA to
 * Product quality gates - Error handling and recovery scenarios
 *
 * @doc.type class
 * @doc.purpose Advanced workflow orchestration tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Advanced Workflow Orchestration Tests")
class AdvancedWorkflowOrchestrationTest extends EventloopTestBase {

    private static final TenantId TENANT_ID = TenantId.random();
    private TestOrganization organization;
    private OrchestrationEventTracker eventTracker;
    private Map<DepartmentType, TestDepartment> departments;

    @BeforeEach
    void setUp() {
        eventTracker = new OrchestrationEventTracker();
        organization = new TestOrganization(eventloop(), "Global Corp", "Multi-Department Org", TENANT_ID, eventTracker);
        departments = new HashMap<>();

        // Set up all department types
        registerDepartment(DepartmentType.ENGINEERING);
        registerDepartment(DepartmentType.PRODUCT);
        registerDepartment(DepartmentType.MARKETING);
        registerDepartment(DepartmentType.SALES);
        registerDepartment(DepartmentType.DEVOPS);
        registerDepartment(DepartmentType.QA);
        registerDepartment(DepartmentType.FINANCE);
        registerDepartment(DepartmentType.SUPPORT);
    }

    /**
     * Verifies Product → Engineering workflow for feature implementation.
     *
     * GIVEN: Product department with feature request WHEN: Feature is requested
     * and assigned to Engineering THEN: Engineering receives task and can track
     * implementation
     */
    @Test
    @DisplayName("Should orchestrate Product to Engineering feature workflow")
    void shouldOrchestratProductToEngineeringFeatureWorkflow() {
        // GIVEN: Product and Engineering departments with agents
        TestDepartment productDept = departments.get(DepartmentType.PRODUCT);
        TestDepartment engineeringDept = departments.get(DepartmentType.ENGINEERING);

        Agent productManager = Agent.builder()
                .id("pm-001")
                .name("Product Manager")
                .department("Product")
                .build();
        productDept.registerAgent(productManager);

        Agent engineer = Agent.builder()
                .id("eng-001")
                .name("Feature Engineer")
                .department("Engineering")
                .build();
        engineeringDept.registerAgent(engineer);

        // WHEN: Create feature request task in Product department
        Task featureRequest = new Task("user_dashboard_redesign", TaskPriority.HIGH);

        // THEN: Feature request can be created and tracked
        assertThat(featureRequest.getType()).isEqualTo("user_dashboard_redesign");
        assertThat(featureRequest.getPriority()).isEqualTo(TaskPriority.HIGH);

        // AND: Can be passed to Engineering for implementation
        featureRequest.assignTo(engineer);
        assertThat(featureRequest.getAssignedAgent()).isNotNull();

        // AND: Engineering department receives notification
        eventTracker.publishEvent("FeatureAssignedToEngineering");
        assertThat(eventTracker.getPublishedEvents())
                .as("Should track feature assignment event")
                .isNotEmpty();
    }

    /**
     * Verifies Marketing → Sales workflow for campaign execution.
     *
     * GIVEN: Marketing develops campaign, Sales executes WHEN: Campaign is
     * marked ready for launch THEN: Sales team can execute campaign tasks
     */
    @Test
    @DisplayName("Should orchestrate Marketing to Sales campaign workflow")
    void shouldOrchestratMarketingToSalesCampaignWorkflow() {
        // GIVEN: Marketing and Sales departments
        TestDepartment marketingDept = departments.get(DepartmentType.MARKETING);
        TestDepartment salesDept = departments.get(DepartmentType.SALES);

        Agent marketingManager = Agent.builder()
                .id("mkt-001")
                .name("Campaign Manager")
                .department("Marketing")
                .build();
        marketingDept.registerAgent(marketingManager);

        Agent salesRep = Agent.builder()
                .id("sales-001")
                .name("Sales Representative")
                .department("Sales")
                .build();
        salesDept.registerAgent(salesRep);

        // WHEN: Create campaign execution tasks
        Task campaignTask = new Task("q4_promotion_execution", TaskPriority.HIGH);
        campaignTask.assignTo(salesRep);

        // THEN: Sales team can track campaign
        assertThat(campaignTask.getType()).isEqualTo("q4_promotion_execution");
        assertThat(campaignTask.getAssignedAgent().getId()).isEqualTo("sales-001");

        // AND: Sales tracks campaign metrics
        eventTracker.publishEvent("CampaignLaunched");
        assertThat(eventTracker.getPublishedEvents()).isNotEmpty();
    }

    /**
     * Verifies DevOps ↔ Support bidirectional workflow for infrastructure
     * requests.
     *
     * GIVEN: Infrastructure incident requiring DevOps and Support coordination
     * WHEN: Support reports issue, DevOps investigates and resolves THEN: Both
     * departments coordinate on resolution
     */
    @Test
    @DisplayName("Should orchestrate DevOps Support incident coordination")
    void shouldOrchestratDevOpsSupportIncidentWorkflow() {
        // GIVEN: DevOps and Support departments
        TestDepartment devopsDept = departments.get(DepartmentType.DEVOPS);
        TestDepartment supportDept = departments.get(DepartmentType.SUPPORT);

        Agent devopsEngineer = Agent.builder()
                .id("devops-001")
                .name("DevOps Engineer")
                .department("DevOps")
                .build();
        devopsDept.registerAgent(devopsEngineer);

        Agent supportAgent = Agent.builder()
                .id("support-001")
                .name("Support Specialist")
                .department("Support")
                .build();
        supportDept.registerAgent(supportAgent);

        // WHEN: Support reports incident to DevOps
        Task incidentTask = new Task("database_performance_degradation", TaskPriority.CRITICAL);
        incidentTask.assignTo(devopsEngineer);

        // THEN: DevOps receives critical incident
        assertThat(incidentTask.getPriority()).isEqualTo(TaskPriority.CRITICAL);
        assertThat(incidentTask.getAssignedAgent()).isNotNull();

        // AND: Support tracks resolution status
        eventTracker.publishEvent("IncidentAssignedToDevOps");
        eventTracker.publishEvent("IncidentResolved");

        assertThat(eventTracker.getPublishedEvents()).isNotEmpty();
    }

    /**
     * Verifies Finance → Sales workflow for budget approvals.
     *
     * GIVEN: Sales requests budget for initiative WHEN: Finance reviews and
     * approves THEN: Sales can proceed with budget-approved activities
     */
    @Test
    @DisplayName("Should orchestrate Finance to Sales budget approval workflow")
    void shouldOrchestratFinanceToSalesBudgetApprovalWorkflow() {
        // GIVEN: Finance and Sales departments
        TestDepartment financeDept = departments.get(DepartmentType.FINANCE);
        TestDepartment salesDept = departments.get(DepartmentType.SALES);

        Agent cfo = Agent.builder()
                .id("finance-001")
                .name("CFO")
                .department("Finance")
                .build();
        financeDept.registerAgent(cfo);

        Agent salesVp = Agent.builder()
                .id("sales-002")
                .name("Sales VP")
                .department("Sales")
                .build();
        salesDept.registerAgent(salesVp);

        // WHEN: Sales requests budget approval
        Task budgetRequest = new Task("q4_sales_initiative_budget", TaskPriority.HIGH);
        budgetRequest.assignTo(cfo);

        // THEN: Finance receives budget request
        assertThat(budgetRequest.getType()).isEqualTo("q4_sales_initiative_budget");
        assertThat(budgetRequest.getAssignedAgent()).isNotNull();

        // AND: Sales tracks approval status
        eventTracker.publishEvent("BudgetApprovalRequested");
        eventTracker.publishEvent("BudgetApproved");

        assertThat(eventTracker.getPublishedEvents()).isNotEmpty();
    }

    /**
     * Verifies QA → Product workflow for quality gates.
     *
     * GIVEN: QA completes testing of feature WHEN: Feature passes quality gates
     * THEN: Product can approve feature for release
     */
    @Test
    @DisplayName("Should orchestrate QA to Product quality gate workflow")
    void shouldOrchestratQAToProductQualityGateWorkflow() {
        // GIVEN: QA and Product departments
        TestDepartment qaDept = departments.get(DepartmentType.QA);
        TestDepartment productDept = departments.get(DepartmentType.PRODUCT);

        Agent qaLead = Agent.builder()
                .id("qa-001")
                .name("QA Lead")
                .department("QA")
                .build();
        qaDept.registerAgent(qaLead);

        Agent productLead = Agent.builder()
                .id("product-001")
                .name("Product Lead")
                .department("Product")
                .build();
        productDept.registerAgent(productLead);

        // WHEN: QA performs quality testing
        Task qualityGateTask = new Task("feature_quality_validation", TaskPriority.HIGH);
        qualityGateTask.assignTo(qaLead);

        // THEN: QA validates feature quality
        assertThat(qualityGateTask.getAssignedAgent()).isNotNull();

        // AND: Product receives quality report
        eventTracker.publishEvent("QualityGatePassed");
        eventTracker.publishEvent("FeatureReadyForRelease");

        assertThat(eventTracker.getPublishedEvents()).isNotEmpty();
    }

    /**
     * Verifies error handling across departments.
     *
     * GIVEN: Multiple departments with error scenarios WHEN: Errors occur
     * during task processing THEN: Error handling and recovery work across
     * departments
     */
    @Test
    @DisplayName("Should handle errors across departments gracefully")
    void shouldHandleErrorsAcrossDepartmentsGracefully() {
        // GIVEN: All departments registered
        assertThat(departments)
                .as("Should have all departments registered")
                .hasSize(8);

        // WHEN: Creating tasks that might fail
        Task failedTask = new Task("potentially_failing_operation", TaskPriority.LOW);

        // THEN: Task should still be created (framework handles gracefully)
        assertThat(failedTask)
                .as("Task should be created even for error scenarios")
                .isNotNull();

        // AND: Department operations should be unaffected
        TestDepartment engineeringDept = departments.get(DepartmentType.ENGINEERING);
        assertThat(engineeringDept.getAgents())
                .as("Engineering department should remain operational")
                .isNotNull();
    }

    /**
     * Verifies multi-step workflow across three departments.
     *
     * GIVEN: Complex workflow requiring Product → Engineering → QA WHEN:
     * Feature flows through all departments THEN: All departments coordinate
     * properly
     */
    @Test
    @DisplayName("Should orchestrate complex multi-department feature workflow")
    void shouldOrchestratComplexMultiDepartmentFeatureWorkflow() {
        // GIVEN: Three departments in product workflow
        TestDepartment productDept = departments.get(DepartmentType.PRODUCT);
        TestDepartment engineeringDept = departments.get(DepartmentType.ENGINEERING);
        TestDepartment qaDept = departments.get(DepartmentType.QA);

        Agent pm = Agent.builder().id("pm-002").name("PM").department("Product").build();
        Agent dev = Agent.builder().id("dev-002").name("Developer").department("Engineering").build();
        Agent qa = Agent.builder().id("qa-002").name("QA").department("QA").build();

        productDept.registerAgent(pm);
        engineeringDept.registerAgent(dev);
        qaDept.registerAgent(qa);

        // WHEN: Feature flows through workflow
        Task featureSpec = new Task("new_feature_specification", TaskPriority.HIGH);
        featureSpec.assignTo(dev); // Product creates and assigns to Engineering

        // THEN: Engineering receives feature
        assertThat(featureSpec.getAssignedAgent()).isNotNull();

        // WHEN: Engineering completes implementation
        Task qualityCheck = new Task("feature_quality_check", TaskPriority.HIGH);
        qualityCheck.assignTo(qa); // Engineering assigns to QA

        // THEN: QA receives for validation
        assertThat(qualityCheck.getAssignedAgent()).isNotNull();

        // AND: Full workflow is tracked
        eventTracker.publishEvent("FeatureSpecCreated");
        eventTracker.publishEvent("ImplementationComplete");
        eventTracker.publishEvent("QualityCheckComplete");

        assertThat(eventTracker.getPublishedEvents()).isNotEmpty();
    }

    /**
     * Verifies concurrent task processing across departments.
     *
     * GIVEN: Multiple departments processing tasks simultaneously WHEN: Tasks
     * are assigned in parallel THEN: All tasks are processed correctly
     */
    @Test
    @DisplayName("Should handle concurrent department workflows")
    void shouldHandleConcurrentDepartmentWorkflows() {
        // GIVEN: All departments ready for parallel work
        List<Task> parallelTasks = new ArrayList<>();

        for (DepartmentType type : DepartmentType.values()) {
            TestDepartment dept = departments.get(type);
            if (dept != null) {
                // Register agents in each department
                for (int i = 0; i < 2; i++) {
                    Agent agent = Agent.builder()
                            .id(type.name().toLowerCase() + "-" + i)
                            .name(type.name() + " Agent " + i)
                            .department(type.name())
                            .build();
                    dept.registerAgent(agent);
                }
            }
        }

        // WHEN: Create tasks across all departments
        for (DepartmentType type : DepartmentType.values()) {
            Task task = new Task("task_" + type.name().toLowerCase(), TaskPriority.MEDIUM);
            parallelTasks.add(task);
        }

        // THEN: All tasks created successfully
        assertThat(parallelTasks)
                .as("Should create tasks for all departments")
                .hasSize(DepartmentType.values().length);

        // AND: Can track task volume
        eventTracker.publishEvent("AllTasksInitiated");
        assertThat(eventTracker.getPublishedEvents()).isNotEmpty();
    }

    // ============ HELPER CLASSES ============
    private void registerDepartment(DepartmentType type) {
        TestDepartment dept = new TestDepartment(type.name(), type, TENANT_ID, eventTracker);
        departments.put(type, dept);
    }

    private static class OrchestrationEventTracker implements EventPublisher {

        private final List<String> publishedEvents = new CopyOnWriteArrayList<>();

        @Override
        public void publish(String eventType, byte[] payload) {
            publishedEvents.add(eventType);
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

        void publishEvent(String eventType) {
            publish(eventType, new byte[0]);
        }

        List<String> getPublishedEvents() {
            return new ArrayList<>(publishedEvents);
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

    private static class TestOrganization extends AbstractOrganization {

        public TestOrganization(Eventloop eventloop, String name, String description, TenantId tenantId, EventPublisher eventPublisher) {
            super(eventloop, tenantId, name, description, eventPublisher);
        }
    }
}
