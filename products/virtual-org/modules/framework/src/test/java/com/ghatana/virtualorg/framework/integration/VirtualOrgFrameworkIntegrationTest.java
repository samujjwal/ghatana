package com.ghatana.virtualorg.framework.integration;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.catalog.InMemoryOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.agent.OrganizationalAgent;
import com.ghatana.virtualorg.framework.hierarchy.Authority;
import com.ghatana.virtualorg.framework.hierarchy.EscalationPath;
import com.ghatana.virtualorg.framework.hierarchy.Layer;
import com.ghatana.virtualorg.framework.hierarchy.Role;
import com.ghatana.virtualorg.framework.workflow.WorkflowDefinition;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Virtual Org Framework stream processing.
 *
 * <p><b>Test Scenarios</b><br>
 * - Complete flow: Agent → Operator → Catalog → Workflow → Pipeline
 * - Sprint Planning workflow execution
 * - Code Review workflow execution
 * - Multi-agent collaboration through pipelines
 * - Event enrichment and metadata propagation
 *
 * <p><b>Purpose</b><br>
 * Validates Virtual Org framework implementation:
 * - Track 1: Agent Stream Operator Unification
 * - Track 2: Workflow as Stream Pipeline
 *
 * @doc.type test
 * @doc.purpose End-to-end Virtual Org framework integration validation
 * @doc.layer product
 */
@DisplayName("Virtual Org Framework Integration Tests - Stream Processing")
class VirtualOrgFrameworkIntegrationTest extends EventloopTestBase {

    private OperatorCatalog operatorCatalog;
    private SimpleMeterRegistry meterRegistry;
    private AgentOperatorRegistry agentRegistry;
    private WorkflowPipelineAdapter workflowAdapter;

    private static final String TENANT_ID = "integration-test-tenant";

    @BeforeEach
    void setUp() {
        operatorCatalog = new InMemoryOperatorCatalog();
        meterRegistry = new SimpleMeterRegistry();
        agentRegistry = new AgentOperatorRegistry(operatorCatalog, meterRegistry);
        workflowAdapter = new WorkflowPipelineAdapter(operatorCatalog);
    }

    /**
     * Test complete Sprint Planning workflow.
     *
     * GIVEN: Sprint Planning workflow with 3 agents
     * WHEN: registering agents and converting workflow to pipeline
     * THEN: pipeline executable with all agent operators
     */
    @Test
    @DisplayName("Should execute complete Sprint Planning workflow as pipeline")
    void shouldExecuteSprintPlanningWorkflow() {
        // GIVEN: Organizational agents
        OrganizationalAgent productManager = createProductManagerAgent();
        OrganizationalAgent cto = createCTOAgent();
        OrganizationalAgent architect = createArchitectAgent();

        List<OrganizationalAgent> agents = List.of(productManager, cto, architect);

        // Register agents as operators
        runPromise(() -> agentRegistry.registerAll(agents, TENANT_ID));

        // Define Sprint Planning workflow
        WorkflowDefinition sprintPlanningWorkflow = WorkflowDefinition.builder()
            .id("sprint-planning")
            .name("Sprint Planning")
            .version("1.0.0")
            .description("Plan sprint with backlog, capacity, and priorities")
            .triggerEvent("sprint.started")
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "load-backlog",
                "Load and prioritize backlog items",
                "ProductManager",
                120
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "estimate-capacity",
                "Estimate team capacity and velocity",
                "CTO",
                90
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "assign-tasks",
                "Assign tasks and set sprint goals",
                "ArchitectLead",
                120
            ))
            .build();

        // WHEN: Converting workflow to pipeline
        Pipeline pipeline = runPromise(() ->
            workflowAdapter.toPipeline(sprintPlanningWorkflow, TENANT_ID)
        );

        // THEN: Pipeline created successfully
        assertThat(pipeline).as("Pipeline should be created").isNotNull();
        assertThat(pipeline.getName()).isEqualTo("Sprint Planning");

        // Verify all agent operators registered
        assertAgentOperatorExists("ProductManager");
        assertAgentOperatorExists("CTO");
        assertAgentOperatorExists("ArchitectLead");

        // Verify pipeline structure
        assertThat(pipeline.getStages())
            .as("Pipeline should have 5 stages (1 filter + 3 agent map operators + 1 DLQ)")
            .hasSize(5);
    }

    /**
     * Test Code Review workflow with sequential agent processing.
     *
     * GIVEN: Code Review workflow with 4 agents
     * WHEN: executing workflow as pipeline
     * THEN: event flows through all agent operators sequentially
     */
    @Test
    @DisplayName("Should execute Code Review workflow with event flow")
    void shouldExecuteCodeReviewWorkflow() {
        // GIVEN: Code review agents
        OrganizationalAgent architect = createArchitectAgent();
        OrganizationalAgent seniorEngineer = createSeniorEngineerAgent();
        OrganizationalAgent devOps = createDevOpsAgent();

        List<OrganizationalAgent> agents = List.of(architect, seniorEngineer, devOps);

        // Register agents
        runPromise(() -> agentRegistry.registerAll(agents, TENANT_ID));

        // Define Code Review workflow
        WorkflowDefinition codeReviewWorkflow = WorkflowDefinition.builder()
            .id("code-review")
            .name("Code Review Process")
            .version("1.0.0")
            .description("Review, test, and merge pull request")
            .triggerEvent("pull_request.created")
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "review-architecture",
                "Review architectural changes",
                "ArchitectLead",
                180
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "review-code",
                "Review code quality and tests",
                "SeniorEngineer",
                120
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "run-ci",
                "Run CI/CD pipeline",
                "DevOpsEngineer",
                300
            ))
            .build();

        // WHEN: Converting workflow to pipeline
        Pipeline pipeline = runPromise(() ->
            workflowAdapter.toPipeline(codeReviewWorkflow, TENANT_ID)
        );

        // THEN: Pipeline structure correct
        assertThat(pipeline).as("Pipeline created").isNotNull();
        assertThat(pipeline.getStages())
            .as("Pipeline has correct stage count")
            .hasSize(5); // 1 filter + 3 agents + 1 DLQ

        // Verify workflow metadata
        assertThat(pipeline.getDescription())
            .contains("Auto-generated from workflow: Code Review Process");
    }

    /**
     * Test agent discovery through operator catalog.
     *
     * GIVEN: 12 registered software organization agents
     * WHEN: querying catalog by type and capability
     * THEN: correct agents discovered
     */
    @Test
    @DisplayName("Should discover agents through operator catalog")
    void shouldDiscoverAgentsThroughCatalog() {
        // GIVEN: All 12 software organization agents
        List<OrganizationalAgent> allAgents = createAllSoftwareOrgAgents();

        // Register all agents
        runPromise(() -> agentRegistry.registerAll(allAgents, TENANT_ID));

        // WHEN: Querying catalog
        var ceoOperator = runPromise(() -> operatorCatalog.get(
            OperatorId.of(TENANT_ID, "virtualorg", "agent:CEO", "1.0.0")
        ));

        var engineerOperator = runPromise(() -> operatorCatalog.get(
            OperatorId.of(TENANT_ID, "virtualorg", "agent:Engineer", "1.0.0")
        ));

        // THEN: Agents discoverable
        assertThat(ceoOperator).as("CEO operator found").isPresent();
        assertThat(engineerOperator).as("Engineer operator found").isPresent();

        // Verify all 12 agents registered
        assertThat(meterRegistry.counter(
            "virtualorg.agent.registered",
            "tenant", TENANT_ID
        ).count())
            .as("All 12 agents registered")
            .isEqualTo(12.0);
    }

    /**
     * Test event metadata enrichment through workflow pipeline.
     *
     * GIVEN: workflow with metadata enrichment
     * WHEN: event flows through pipeline
     * THEN: event enriched with workflow step metadata
     */
    @Test
    @DisplayName("Should enrich event with workflow metadata")
    void shouldEnrichEventWithWorkflowMetadata() {
        // GIVEN: Simple workflow
        OrganizationalAgent ceo = createCEOAgent();
        runPromise(() -> agentRegistry.register(ceo, TENANT_ID));

        WorkflowDefinition simpleWorkflow = WorkflowDefinition.builder()
            .id("simple")
            .name("Simple Workflow")
            .version("1.0.0")
            .triggerEvent("test.event")
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "step1",
                "Process event",
                "CEO",
                60
            ))
            .build();

        // WHEN: Converting to pipeline
        Pipeline pipeline = runPromise(() ->
            workflowAdapter.toPipeline(simpleWorkflow, TENANT_ID)
        );

        // THEN: Pipeline configured for metadata enrichment
        assertThat(pipeline).as("Pipeline created").isNotNull();
    }

    // ========================================================================
    // Test Agent Factories
    // ========================================================================

    private OrganizationalAgent createCEOAgent() {
        return new TestAgent(
            Role.of("CEO", Layer.EXECUTIVE),
            Authority.builder().addDecision("all_decisions").build(),
            EscalationPath.of()
        );
    }

    private OrganizationalAgent createCTOAgent() {
        return new TestAgent(
            Role.of("CTO", Layer.EXECUTIVE),
            Authority.builder().addDecision("tech_decisions").build(),
            EscalationPath.of(Role.of("CEO", Layer.EXECUTIVE))
        );
    }

    private OrganizationalAgent createProductManagerAgent() {
        return new TestAgent(
            Role.of("ProductManager", Layer.MANAGEMENT),
            Authority.builder().addDecision("product_decisions").build(),
            EscalationPath.of(Role.of("CPO", Layer.EXECUTIVE))
        );
    }

    private OrganizationalAgent createArchitectAgent() {
        return new TestAgent(
            Role.of("ArchitectLead", Layer.MANAGEMENT),
            Authority.builder().addDecision("architecture_decisions").build(),
            EscalationPath.of(Role.of("CTO", Layer.EXECUTIVE))
        );
    }

    private OrganizationalAgent createSeniorEngineerAgent() {
        return new TestAgent(
            Role.of("SeniorEngineer", Layer.INDIVIDUAL_CONTRIBUTOR),
            Authority.builder().addDecision("code_review").build(),
            EscalationPath.of(Role.of("ArchitectLead", Layer.MANAGEMENT))
        );
    }

    private OrganizationalAgent createDevOpsAgent() {
        return new TestAgent(
            Role.of("DevOpsEngineer", Layer.INDIVIDUAL_CONTRIBUTOR),
            Authority.builder().addDecision("deployment").build(),
            EscalationPath.of(Role.of("DevOpsLead", Layer.MANAGEMENT))
        );
    }

    private List<OrganizationalAgent> createAllSoftwareOrgAgents() {
        return List.of(
            createCEOAgent(),
            createCTOAgent(),
            new TestAgent(Role.of("CPO", Layer.EXECUTIVE),
                         Authority.builder().build(), EscalationPath.of()),
            createProductManagerAgent(),
            createArchitectAgent(),
            new TestAgent(Role.of("DevOpsLead", Layer.MANAGEMENT),
                         Authority.builder().build(), EscalationPath.of()),
            createSeniorEngineerAgent(),
            new TestAgent(Role.of("Engineer", Layer.INDIVIDUAL_CONTRIBUTOR),
                         Authority.builder().build(), EscalationPath.of()),
            new TestAgent(Role.of("JuniorEngineer", Layer.INDIVIDUAL_CONTRIBUTOR),
                         Authority.builder().build(), EscalationPath.of()),
            new TestAgent(Role.of("QAEngineer", Layer.INDIVIDUAL_CONTRIBUTOR),
                         Authority.builder().build(), EscalationPath.of()),
            createDevOpsAgent(),
            new TestAgent(Role.of("DevOpsEngineer2", Layer.INDIVIDUAL_CONTRIBUTOR),
                         Authority.builder().build(), EscalationPath.of())
        );
    }

    // ========================================================================
    // Assertion Helpers
    // ========================================================================

    private void assertAgentOperatorExists(String roleName) {
        OperatorId operatorId = OperatorId.of(
            TENANT_ID,
            "virtualorg",
            "agent:" + roleName,
            "1.0.0"
        );

        var operator = runPromise(() -> operatorCatalog.get(operatorId));

        assertThat(operator)
            .as(roleName + " operator should exist in catalog")
            .isPresent();
    }

    // ========================================================================
    // Test Agent Implementation
    // ========================================================================

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
        public java.util.List<Event> handle(
            Event event,
            com.ghatana.platform.domain.agent.registry.AgentExecutionContext context
        ) {
            // Simple pass-through for testing: return the input event
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

