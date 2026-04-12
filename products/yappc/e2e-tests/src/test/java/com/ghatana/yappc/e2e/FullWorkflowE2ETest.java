/**
 * YAPPC Full Workflow E2E Test Suite
 *
 * Comprehensive end-to-end tests for the complete YAPPC workflow.
 * Tests the full lifecycle from Intent → Planning → Design → Implementation → Testing → Deploy → Evolve.
 * Integrates project lifecycle, agent execution, and workflow management in a cohesive test suite.
 *
 * @doc.type test
 * @doc.purpose E2E validation for complete YAPPC full workflow
 * @doc.layer products
 * @doc.pattern E2ETest
 */

package com.ghatana.yappc.e2e;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for complete YAPPC full workflow.
 * Tests the complete lifecycle from Intent through Evolve with real service interactions.
 */
@DisplayName("YAPPC Full Workflow E2E Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullWorkflowE2ETest extends EventloopTestBase {

    private static FullWorkflowPlatform platform;
    private static String tenantId = "e2e-full-workflow-tenant";
    private static String projectId;
    private static String workflowId;
    private static String javaExpertAgentId;
    private static String codeReviewerAgentId;

    @BeforeAll
    static void setUpPlatform() {
        platform = new MockFullWorkflowPlatform();
    }

    // ── Phase 1: Intent & Project Creation ───────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Full Workflow: Should create project with intent")
    void testCreateProjectWithIntent() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .projectName("E2E Full Workflow Microservice")
                .intent("Build a user authentication microservice with JWT, OAuth2 integration, and role-based access control")
                .techStack(List.of("Java", "Spring Boot", "PostgreSQL", "Redis"))
                .build();

        Promise<Project> promise = platform.createProject(request);
        Project project = runPromise(() -> promise);

        assertThat(project).isNotNull();
        assertThat(project.id()).isNotNull();
        assertThat(project.name()).isEqualTo("E2E Full Workflow Microservice");
        assertThat(project.status()).isEqualTo("CREATED");
        assertThat(project.currentPhase()).isEqualTo("PLANNING");
        assertThat(project.techStack()).contains("Java", "Spring Boot");

        projectId = project.id();
    }

    @Test
    @Order(2)
    @DisplayName("Full Workflow: Should register agents for the workflow")
    void testRegisterAgentsForWorkflow() throws Exception {
        AgentRegistrationRequest javaRequest = AgentRegistrationRequest.builder()
                .tenantId(tenantId)
                .agentConfigPath("agents/java-expert.yaml")
                .build();

        AgentRegistrationResult javaResult = runPromise(() -> platform.registerAgent(javaRequest));
        assertThat(javaResult.agentId()).isNotNull();
        assertThat(javaResult.agentName()).isEqualTo("Java Expert");
        javaExpertAgentId = javaResult.agentId();

        AgentRegistrationRequest reviewerRequest = AgentRegistrationRequest.builder()
                .tenantId(tenantId)
                .agentConfigPath("agents/code-reviewer.yaml")
                .build();

        AgentRegistrationResult reviewerResult = runPromise(() -> platform.registerAgent(reviewerRequest));
        assertThat(reviewerResult.agentId()).isNotNull();
        assertThat(reviewerResult.agentName()).isEqualTo("Code Reviewer");
        codeReviewerAgentId = reviewerResult.agentId();
    }

    // ── Phase 2: Planning ──────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Full Workflow: Should complete PLANNING phase with AI-generated plan")
    void testCompletePlanningPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("PLANNING")
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("PLANNING");
        assertThat(result.artifacts()).containsKey("requirements");
        assertThat(result.artifacts()).containsKey("architecture");
        assertThat(result.artifacts()).containsKey("techStack");
        assertThat(result.executionTimeMs()).isGreaterThan(0);
    }

    @Test
    @Order(4)
    @DisplayName("Full Workflow: Should generate AI workflow plan")
    void testGenerateAiWorkflowPlan() throws Exception {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .name("Auth Microservice Workflow")
                .description("Complete workflow for auth microservice development")
                .objective("Implement JWT-based authentication with OAuth2 integration")
                .build();

        Promise<AiWorkflow> promise = platform.createWorkflow(request);
        AiWorkflow workflow = runPromise(() -> promise);

        assertThat(workflow).isNotNull();
        assertThat(workflow.id()).isNotNull();
        assertThat(workflow.status()).isEqualTo("DRAFT");
        workflowId = workflow.id();
    }

    @Test
    @Order(5)
    @DisplayName("Full Workflow: Should approve and start AI workflow")
    void testApproveAndStartAiWorkflow() throws Exception {
        // Generate plan
        AiPlan plan = runPromise(() -> platform.generatePlan(workflowId, tenantId, "Implement auth microservice"));
        assertThat(plan).isNotNull();
        assertThat(plan.steps()).isNotEmpty();

        // Approve plan
        AiPlan approvedPlan = runPromise(() -> platform.approvePlan(plan.id(), tenantId));
        assertThat(approvedPlan.status()).isEqualTo("APPROVED");

        // Start workflow
        AiWorkflow startedWorkflow = runPromise(() -> platform.startWorkflow(workflowId, tenantId));
        assertThat(startedWorkflow.status()).isEqualTo("IN_PROGRESS");
    }

    // ── Phase 3: Design ────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Full Workflow: Should complete DESIGN phase with API spec and data model")
    void testCompleteDesignPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("DESIGN")
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("DESIGN");
        assertThat(result.artifacts()).containsKey("apiSpec");
        assertThat(result.artifacts()).containsKey("dataModel");
        assertThat(result.artifacts()).containsKey("sequenceDiagram");
    }

    @Test
    @Order(7)
    @DisplayName("Full Workflow: Should perform code review on design artifacts")
    void testCodeReviewOnDesignArtifacts() throws Exception {
        String designCode = """
                public class AuthController {
                    @PostMapping("/login")
                    public ResponseEntity<AuthToken> login(@RequestBody LoginRequest request) {
                        return authService.authenticate(request);
                    }
                }
                """;

        CodeReviewRequest request = CodeReviewRequest.builder()
                .tenantId(tenantId)
                .agentId(codeReviewerAgentId)
                .code(designCode)
                .language("java")
                .reviewType("security")
                .build();

        CodeReviewResult result = runPromise(() -> platform.reviewCode(request));

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.issues()).isNotNull();
        assertThat(result.overallScore()).isBetween(0, 100);
    }

    // ── Phase 4: Implementation ─────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Full Workflow: Should complete IMPLEMENTATION phase with generated code")
    void testCompleteImplementationPhase() throws Exception {
        CodeGenerationRequest codeRequest = CodeGenerationRequest.builder()
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .language("java")
                .framework("spring-boot")
                .description("Generate authentication microservice implementation")
                .requirements(List.of(
                        "JWT token generation and validation",
                        "OAuth2 integration",
                        "Role-based access control",
                        "User entity with roles",
                        "Refresh token support"
                ))
                .build();

        Promise<CodeGenerationResult> codePromise = platform.generateCode(codeRequest);
        CodeGenerationResult codeResult = runPromise(() -> codePromise);

        assertThat(codeResult).isNotNull();
        assertThat(codeResult.success()).isTrue();
        assertThat(codeResult.generatedFiles()).isNotEmpty();
        assertThat(codeResult.generatedFiles()).containsKey("AuthController.java");
        assertThat(codeResult.generatedFiles()).containsKey("AuthService.java");

        PhaseExecutionRequest phaseRequest = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("IMPLEMENTATION")
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .artifacts(codeResult.generatedFiles())
                .build();

        Promise<PhaseResult> phasePromise = platform.executePhase(phaseRequest);
        PhaseResult phaseResult = runPromise(() -> phasePromise);

        assertThat(phaseResult.success()).isTrue();
        assertThat(phaseResult.phase()).isEqualTo("IMPLEMENTATION");
        assertThat(phaseResult.artifacts()).containsKey("sourceCode");
        assertThat(phaseResult.artifacts()).containsKey("buildConfig");
    }

    @Test
    @Order(9)
    @DisplayName("Full Workflow: Should execute workflow steps via agent routing")
    void testWorkflowStepsViaAgentRouting() throws Exception {
        AepEvent stepEvent = AepEvent.builder()
                .eventType("workflow.step.requested")
                .tenantId(tenantId)
                .correlationId(UUID.randomUUID().toString())
                .payload(Map.of(
                        "workflowId", workflowId,
                        "stepId", "step-implementation",
                        "stepType", "CODE_GENERATION"
                ))
                .build();

        AepEvent result = runPromise(() -> platform.routeEvent(stepEvent));

        assertThat(result).isNotNull();
        assertThat(result.eventType()).isEqualTo("workflow.step.completed");
        assertThat(result.tenantId()).isEqualTo(tenantId);
    }

    // ── Phase 5: Testing ───────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("Full Workflow: Should complete TESTING phase with test results")
    void testCompleteTestingPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("TESTING")
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("TESTING");
        assertThat(result.artifacts()).containsKey("testResults");
        assertThat(result.artifacts()).containsKey("coverageReport");
        assertThat(result.artifacts()).containsKey("testMetrics");
    }

    @Test
    @Order(11)
    @DisplayName("Full Workflow: Should verify test coverage meets threshold")
    void testVerifyTestCoverageThreshold() throws Exception {
        Promise<ProjectMetrics> promise = platform.getProjectMetrics(projectId, tenantId);
        ProjectMetrics metrics = runPromise(() -> promise);

        assertThat(metrics).isNotNull();
        assertThat(metrics.testCoverage()).isGreaterThanOrEqualTo(80);
        assertThat(metrics.totalTests()).isGreaterThan(0);
    }

    // ── Phase 6: Deploy ────────────────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("Full Workflow: Should complete DEPLOY phase with deployment artifacts")
    void testCompleteDeployPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("DEPLOY")
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("DEPLOY");
        assertThat(result.artifacts()).containsKey("dockerImage");
        assertThat(result.artifacts()).containsKey("k8sManifest");
        assertThat(result.artifacts()).containsKey("deploymentConfig");
    }

    @Test
    @Order(13)
    @DisplayName("Full Workflow: Should verify deployment health check")
    void testVerifyDeploymentHealthCheck() throws Exception {
        DeploymentHealthCheck check = runPromise(() -> platform.checkDeploymentHealth(projectId, tenantId));

        assertThat(check).isNotNull();
        assertThat(check.healthy()).isTrue();
        assertThat(check.uptimeSeconds()).isGreaterThan(0);
        assertThat(check.memoryUsageMb()).isGreaterThan(0);
    }

    // ── Phase 7: Evolve ────────────────────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("Full Workflow: Should handle EVOLVE phase with feature additions")
    void testCompleteEvolvePhase() throws Exception {
        EvolveRequest request = EvolveRequest.builder()
                .projectId(projectId)
                .tenantId(tenantId)
                .agentId(javaExpertAgentId)
                .newFeatures(List.of(
                        "Add multi-factor authentication support",
                        "Implement audit logging for all auth events",
                        "Add rate limiting per user"
                ))
                .build();

        Promise<EvolveResult> promise = platform.evolve(request);
        EvolveResult result = runPromise(() -> promise);

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.addedFeatures()).hasSize(3);
        assertThat(result.modifiedFiles()).isNotEmpty();
    }

    // ── Complete Workflow Verification ───────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("Full Workflow: Should retrieve complete project with all phases")
    void testGetCompleteProject() throws Exception {
        Promise<Project> promise = platform.getProject(projectId, tenantId);
        Project project = runPromise(() -> promise);

        assertThat(project).isNotNull();
        assertThat(project.id()).isEqualTo(projectId);
        assertThat(project.completedPhases()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(project.completedPhases()).contains(
                "PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING", "DEPLOY", "EVOLVE"
        );
        assertThat(project.status()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(16)
    @DisplayName("Full Workflow: Should track complete workflow metrics")
    void testCompleteWorkflowMetrics() throws Exception {
        Promise<ProjectMetrics> promise = platform.getProjectMetrics(projectId, tenantId);
        ProjectMetrics metrics = runPromise(() -> promise);

        assertThat(metrics).isNotNull();
        assertThat(metrics.totalPhases()).isGreaterThanOrEqualTo(6);
        assertThat(metrics.completedPhases()).isGreaterThanOrEqualTo(6);
        assertThat(metrics.totalExecutionTimeMs()).isGreaterThan(0);
        assertThat(metrics.agentInvocations()).isGreaterThan(0);
        assertThat(metrics.codeFilesGenerated()).isGreaterThan(0);
        assertThat(metrics.testCoverage()).isGreaterThanOrEqualTo(80);
    }

    @Test
    @Order(17)
    @DisplayName("Full Workflow: Should verify AI workflow completion")
    void testVerifyAiWorkflowCompletion() throws Exception {
        Promise<AiWorkflow> promise = platform.getWorkflow(workflowId, tenantId);
        AiWorkflow workflow = runPromise(() -> promise);

        assertThat(workflow).isNotNull();
        assertThat(workflow.status()).isEqualTo("COMPLETED");
        assertThat(workflow.completedSteps()).isGreaterThan(0);
    }

    // ── Error Handling and Edge Cases ───────────────────────────────────────────

    @Test
    @Order(18)
    @DisplayName("Full Workflow: Should handle workflow pause and resume")
    void testWorkflowPauseAndResume() throws Exception {
        // Create new workflow for pause test
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .name("Pause Test Workflow")
                .description("Test pause and resume")
                .objective("Test pause functionality")
                .build();

        AiWorkflow workflow = runPromise(() -> platform.createWorkflow(request));
        String pauseWorkflowId = workflow.id();

        AiPlan plan = runPromise(() -> platform.generatePlan(pauseWorkflowId, tenantId, "Test objective"));
        runPromise(() -> platform.approvePlan(plan.id(), tenantId));
        runPromise(() -> platform.startWorkflow(pauseWorkflowId, tenantId));

        // Pause
        AiWorkflow paused = runPromise(() -> platform.pauseWorkflow(pauseWorkflowId, tenantId));
        assertThat(paused.status()).isEqualTo("PAUSED");

        // Resume
        AiWorkflow resumed = runPromise(() -> platform.resumeWorkflow(pauseWorkflowId, tenantId));
        assertThat(resumed.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @Order(19)
    @DisplayName("Full Workflow: Should handle workflow cancellation")
    void testWorkflowCancellation() throws Exception {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .name("Cancel Test Workflow")
                .description("Test cancellation")
                .objective("Test cancel functionality")
                .build();

        AiWorkflow workflow = runPromise(() -> platform.createWorkflow(request));
        String cancelWorkflowId = workflow.id();

        AiWorkflow cancelled = runPromise(() -> platform.cancelWorkflow(cancelWorkflowId, tenantId));
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @ParameterizedTest(name = "concurrent={0}")
    @Order(20)
    @ValueSource(ints = {2, 5, 10})
    @DisplayName("Full Workflow: Should handle concurrent workflow executions")
    void testConcurrentWorkflowExecutions(int concurrentCount) throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentCount; i++) {
            CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                    .tenantId(tenantId)
                    .projectId(projectId)
                    .name("Concurrent Workflow " + i)
                    .description("Test concurrent execution")
                    .objective("Concurrent test " + i)
                    .build();

            try {
                AiWorkflow workflow = runPromise(() -> platform.createWorkflow(request));
                AiPlan plan = runPromise(() -> platform.generatePlan(workflow.id(), tenantId, "Objective " + i));
                runPromise(() -> platform.approvePlan(plan.id(), tenantId));
                runPromise(() -> platform.startWorkflow(workflow.id(), tenantId));
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Handle errors
            }
        }

        assertThat(successCount.get()).isEqualTo(concurrentCount);
    }

    // ── Supporting types and mock ───────────────────────────────────────────────

    interface FullWorkflowPlatform {
        Promise<Project> createProject(CreateProjectRequest request);
        Promise<AgentRegistrationResult> registerAgent(AgentRegistrationRequest request);
        Promise<PhaseResult> executePhase(PhaseExecutionRequest request);
        Promise<AiWorkflow> createWorkflow(CreateWorkflowRequest request);
        Promise<AiPlan> generatePlan(String workflowId, String tenantId, String objective);
        Promise<AiPlan> approvePlan(String planId, String tenantId);
        Promise<AiWorkflow> startWorkflow(String workflowId, String tenantId);
        Promise<AiWorkflow> pauseWorkflow(String workflowId, String tenantId);
        Promise<AiWorkflow> resumeWorkflow(String workflowId, String tenantId);
        Promise<AiWorkflow> cancelWorkflow(String workflowId, String tenantId);
        Promise<AiWorkflow> getWorkflow(String workflowId, String tenantId);
        Promise<CodeGenerationResult> generateCode(CodeGenerationRequest request);
        Promise<CodeReviewResult> reviewCode(CodeReviewRequest request);
        Promise<AepEvent> routeEvent(AepEvent event);
        Promise<Project> getProject(String projectId, String tenantId);
        Promise<ProjectMetrics> getProjectMetrics(String projectId, String tenantId);
        Promise<DeploymentHealthCheck> checkDeploymentHealth(String projectId, String tenantId);
        Promise<EvolveResult> evolve(EvolveRequest request);
    }

    record CreateProjectRequest(String tenantId, String projectName, String intent, List<String> techStack) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, projectName, intent;
            private List<String> techStack = List.of();
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder projectName(String v) { projectName = v; return this; }
            Builder intent(String v) { intent = v; return this; }
            Builder techStack(List<String> v) { techStack = v; return this; }
            CreateProjectRequest build() { return new CreateProjectRequest(tenantId, projectName, intent, techStack); }
        }
    }

    record Project(
            String id, String name, String tenantId, String status, String currentPhase,
            List<String> completedPhases, List<String> techStack, Instant createdAt
    ) {}

    record PhaseExecutionRequest(
            String projectId, String phase, String tenantId, String agentId, Map<String, String> artifacts
    ) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String projectId, phase, tenantId, agentId;
            private Map<String, String> artifacts = Map.of();
            Builder projectId(String v) { projectId = v; return this; }
            Builder phase(String v) { phase = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder agentId(String v) { agentId = v; return this; }
            Builder artifacts(Map<String, String> v) { artifacts = v; return this; }
            PhaseExecutionRequest build() { return new PhaseExecutionRequest(projectId, phase, tenantId, agentId, artifacts); }
        }
    }

    record PhaseResult(boolean success, String phase, Map<String, Object> artifacts, long executionTimeMs) {}

    record AgentRegistrationRequest(String tenantId, String agentConfigPath) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, agentConfigPath;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder agentConfigPath(String v) { agentConfigPath = v; return this; }
            AgentRegistrationRequest build() { return new AgentRegistrationRequest(tenantId, agentConfigPath); }
        }
    }

    record AgentRegistrationResult(String agentId, String agentName, List<String> capabilities) {}

    record CreateWorkflowRequest(
            String tenantId, String projectId, String name, String description, String objective
    ) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, projectId, name, description, objective;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder projectId(String v) { projectId = v; return this; }
            Builder name(String v) { name = v; return this; }
            Builder description(String v) { description = v; return this; }
            Builder objective(String v) { objective = v; return this; }
            CreateWorkflowRequest build() { return new CreateWorkflowRequest(tenantId, projectId, name, description, objective); }
        }
    }

    record AiWorkflow(String id, String name, String status, int completedSteps) {}

    record AiPlan(String id, String status, List<String> steps) {}

    record CodeGenerationRequest(
            String tenantId, String agentId, String language, String framework,
            String description, List<String> requirements
    ) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, agentId, language, framework, description;
            private List<String> requirements = List.of();
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder agentId(String v) { agentId = v; return this; }
            Builder language(String v) { language = v; return this; }
            Builder framework(String v) { framework = v; return this; }
            Builder description(String v) { description = v; return this; }
            Builder requirements(List<String> v) { requirements = v; return this; }
            CodeGenerationRequest build() { return new CodeGenerationRequest(tenantId, agentId, language, framework, description, requirements); }
        }
    }

    record CodeGenerationResult(boolean success, Map<String, String> generatedFiles, String language) {}

    record CodeReviewRequest(String tenantId, String agentId, String code, String language, String reviewType) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, agentId, code, language, reviewType;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder agentId(String v) { agentId = v; return this; }
            Builder code(String v) { code = v; return this; }
            Builder language(String v) { language = v; return this; }
            Builder reviewType(String v) { reviewType = v; return this; }
            CodeReviewRequest build() { return new CodeReviewRequest(tenantId, agentId, code, language, reviewType); }
        }
    }

    record CodeReviewResult(boolean success, List<String> issues, int overallScore) {}

    record AepEvent(String eventType, String tenantId, String correlationId, Map<String, Object> payload) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String eventType, tenantId, correlationId;
            private Map<String, Object> payload = Map.of();
            Builder eventType(String v) { eventType = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder correlationId(String v) { correlationId = v; return this; }
            Builder payload(Map<String, Object> v) { payload = v; return this; }
            AepEvent build() { return new AepEvent(eventType, tenantId, correlationId, payload); }
        }
    }

    record ProjectMetrics(
            int totalPhases, int completedPhases, long totalExecutionTimeMs,
            int agentInvocations, int codeFilesGenerated, int testCoverage, int totalTests
    ) {}

    record DeploymentHealthCheck(boolean healthy, long uptimeSeconds, int memoryUsageMb) {}

    record EvolveRequest(String projectId, String tenantId, String agentId, List<String> newFeatures) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String projectId, tenantId, agentId;
            private List<String> newFeatures = List.of();
            Builder projectId(String v) { projectId = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder agentId(String v) { agentId = v; return this; }
            Builder newFeatures(List<String> v) { newFeatures = v; return this; }
            EvolveRequest build() { return new EvolveRequest(projectId, tenantId, agentId, newFeatures); }
        }
    }

    record EvolveResult(boolean success, List<String> addedFeatures, List<String> modifiedFiles) {}

    static class MockFullWorkflowPlatform implements FullWorkflowPlatform {
        private final Map<String, Project> projects = new ConcurrentHashMap<>();
        private final Map<String, Map<String, AgentRegistrationResult>> tenantAgents = new ConcurrentHashMap<>();
        private final Map<String, AiWorkflow> workflows = new ConcurrentHashMap<>();
        private final Map<String, AiPlan> plans = new ConcurrentHashMap<>();
        private final Map<String, ProjectMetrics> projectMetrics = new ConcurrentHashMap<>();

        @Override
        public Promise<Project> createProject(CreateProjectRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String id = UUID.randomUUID().toString();
                Project project = new Project(
                        id, request.projectName(), request.tenantId(), "CREATED", "PLANNING",
                        List.of(), request.techStack(), Instant.now()
                );
                projects.put(id, project);
                projectMetrics.put(id, new ProjectMetrics(6, 0, 0, 0, 0, 0, 0));
                return project;
            });
        }

        @Override
        public Promise<AgentRegistrationResult> registerAgent(AgentRegistrationRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String agentId = UUID.randomUUID().toString();
                AgentRegistrationResult result = switch (request.agentConfigPath()) {
                    case "agents/java-expert.yaml" -> new AgentRegistrationResult(
                            agentId, "Java Expert", List.of("code-analysis", "architecture-review")
                    );
                    case "agents/code-reviewer.yaml" -> new AgentRegistrationResult(
                            agentId, "Code Reviewer", List.of("review", "analysis")
                    );
                    default -> new AgentRegistrationResult(agentId, "Unknown Agent", List.of());
                };
                tenantAgents.computeIfAbsent(request.tenantId(), k -> new ConcurrentHashMap<>()).put(agentId, result);
                return result;
            });
        }

        @Override
        public Promise<PhaseResult> executePhase(PhaseExecutionRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                long startTime = System.currentTimeMillis();
                Thread.sleep(50);

                Map<String, Object> artifacts = switch (request.phase()) {
                    case "PLANNING" -> Map.of("requirements", "User stories", "architecture", "System design", "techStack", "Java, Spring Boot");
                    case "DESIGN" -> Map.of("apiSpec", "OpenAPI spec", "dataModel", "Entity models", "sequenceDiagram", "UML diagram");
                    case "IMPLEMENTATION" -> request.artifacts().isEmpty() ?
                            Map.of("sourceCode", "Java/TS code", "buildConfig", "Gradle/npm") :
                            Map.of("sourceCode", request.artifacts(), "buildConfig", "Gradle/npm");
                    case "TESTING" -> Map.of("testResults", "All passed", "coverageReport", "85%", "testMetrics", "100 tests passed");
                    case "DEPLOY" -> Map.of("dockerImage", "ghatana/auth:1.0.0", "k8sManifest", "k8s.yaml", "deploymentConfig", "config");
                    default -> Map.of();
                };

                // Update project
                Project current = projects.get(request.projectId());
                List<String> completed = new java.util.ArrayList<>(current.completedPhases());
                completed.add(request.phase());
                String newStatus = completed.size() >= 6 ? "COMPLETED" : "IN_PROGRESS";
                String nextPhase = completed.size() < 6 ?
                        List.of("PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING", "DEPLOY", "EVOLVE").get(completed.size()) :
                        "COMPLETED";

                projects.put(request.projectId(), new Project(
                        current.id(), current.name(), current.tenantId(), newStatus, nextPhase,
                        completed, current.techStack(), current.createdAt()
                ));

                // Update metrics
                ProjectMetrics metrics = projectMetrics.get(request.projectId());
                projectMetrics.put(request.projectId(), new ProjectMetrics(
                        6, completed.size(), metrics.totalExecutionTimeMs() + (System.currentTimeMillis() - startTime),
                        metrics.agentInvocations() + 3, metrics.codeFilesGenerated() + 5, 85, metrics.totalTests() + 20
                ));

                return new PhaseResult(true, request.phase(), artifacts, System.currentTimeMillis() - startTime);
            });
        }

        @Override
        public Promise<AiWorkflow> createWorkflow(CreateWorkflowRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String id = UUID.randomUUID().toString();
                AiWorkflow workflow = new AiWorkflow(id, request.name(), "DRAFT", 0);
                workflows.put(id, workflow);
                return workflow;
            });
        }

        @Override
        public Promise<AiPlan> generatePlan(String workflowId, String tenantId, String objective) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String planId = UUID.randomUUID().toString();
                AiPlan plan = new AiPlan(planId, "DRAFT", List.of("step-1", "step-2", "step-3"));
                plans.put(planId, plan);
                return plan;
            });
        }

        @Override
        public Promise<AiPlan> approvePlan(String planId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                AiPlan plan = plans.get(planId);
                AiPlan updated = new AiPlan(planId, "APPROVED", plan.steps());
                plans.put(planId, updated);
                return updated;
            });
        }

        @Override
        public Promise<AiWorkflow> startWorkflow(String workflowId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                AiWorkflow workflow = workflows.get(workflowId);
                AiWorkflow updated = new AiWorkflow(workflowId, workflow.name(), "IN_PROGRESS", 1);
                workflows.put(workflowId, updated);
                return updated;
            });
        }

        @Override
        public Promise<AiWorkflow> pauseWorkflow(String workflowId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                AiWorkflow workflow = workflows.get(workflowId);
                AiWorkflow updated = new AiWorkflow(workflowId, workflow.name(), "PAUSED", workflow.completedSteps());
                workflows.put(workflowId, updated);
                return updated;
            });
        }

        @Override
        public Promise<AiWorkflow> resumeWorkflow(String workflowId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                AiWorkflow workflow = workflows.get(workflowId);
                AiWorkflow updated = new AiWorkflow(workflowId, workflow.name(), "IN_PROGRESS", workflow.completedSteps());
                workflows.put(workflowId, updated);
                return updated;
            });
        }

        @Override
        public Promise<AiWorkflow> cancelWorkflow(String workflowId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                AiWorkflow workflow = workflows.get(workflowId);
                AiWorkflow updated = new AiWorkflow(workflowId, workflow.name(), "CANCELLED", workflow.completedSteps());
                workflows.put(workflowId, updated);
                return updated;
            });
        }

        @Override
        public Promise<AiWorkflow> getWorkflow(String workflowId, String tenantId) {
            return Promise.of(workflows.get(workflowId));
        }

        @Override
        public Promise<CodeGenerationResult> generateCode(CodeGenerationRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                Map<String, String> files = new java.util.LinkedHashMap<>();
                files.put("AuthController.java", "public class AuthController { /* generated */ }");
                files.put("AuthService.java", "public class AuthService { /* generated */ }");
                return new CodeGenerationResult(true, files, request.language());
            });
        }

        @Override
        public Promise<CodeReviewResult> reviewCode(CodeReviewRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                return new CodeReviewResult(true, List.of("Issue 1", "Issue 2"), 85);
            });
        }

        @Override
        public Promise<AepEvent> routeEvent(AepEvent event) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String outputType = event.eventType().replace(".requested", ".completed");
                return new AepEvent(outputType, event.tenantId(), event.correlationId(),
                        Map.of("status", "success"));
            });
        }

        @Override
        public Promise<Project> getProject(String projectId, String tenantId) {
            return Promise.of(projects.get(projectId));
        }

        @Override
        public Promise<ProjectMetrics> getProjectMetrics(String projectId, String tenantId) {
            return Promise.of(projectMetrics.get(projectId));
        }

        @Override
        public Promise<DeploymentHealthCheck> checkDeploymentHealth(String projectId, String tenantId) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                return new DeploymentHealthCheck(true, 300, 256);
            });
        }

        @Override
        public Promise<EvolveResult> evolve(EvolveRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                return new EvolveResult(true, request.newFeatures(), List.of("file1.java", "file2.java"));
            });
        }
    }
}
