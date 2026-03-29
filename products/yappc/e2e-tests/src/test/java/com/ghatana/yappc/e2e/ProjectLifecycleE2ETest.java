/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.e2e;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for complete project lifecycle.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectLifecycleE2ETest extends EventloopTestBase {

    private static YappcPlatform platform;
    private static String projectId;
    private static String tenantId = "e2e-test-tenant";

    @BeforeAll
    static void setUpPlatform() {
        platform = new MockYappcPlatform();
    }

    @Test
    @Order(1)
    @DisplayName("E2E: Should create new project with intent")
    void testCreateProject() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .projectName("E2E Test Microservice")
                .intent("Build a user authentication microservice with JWT")
                .build();

        Promise<Project> promise = platform.createProject(request);
        Project project = runPromise(() -> promise);

        assertThat(project).isNotNull();
        assertThat(project.id()).isNotNull();
        assertThat(project.name()).isEqualTo("E2E Test Microservice");
        assertThat(project.status()).isEqualTo("CREATED");
        assertThat(project.currentPhase()).isEqualTo("PLANNING");

        projectId = project.id();
    }

    @Test
    @Order(2)
    @DisplayName("E2E: Should complete PLANNING phase")
    void testCompletePlanningPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("PLANNING")
                .tenantId(tenantId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("PLANNING");
        assertThat(result.artifacts()).containsKey("requirements");
        assertThat(result.artifacts()).containsKey("architecture");
    }

    @Test
    @Order(3)
    @DisplayName("E2E: Should complete DESIGN phase")
    void testCompleteDesignPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("DESIGN")
                .tenantId(tenantId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("DESIGN");
        assertThat(result.artifacts()).containsKey("apiSpec");
        assertThat(result.artifacts()).containsKey("dataModel");
    }

    @Test
    @Order(4)
    @DisplayName("E2E: Should complete IMPLEMENTATION phase")
    void testCompleteImplementationPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("IMPLEMENTATION")
                .tenantId(tenantId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("IMPLEMENTATION");
        assertThat(result.artifacts()).containsKey("sourceCode");
        assertThat(result.artifacts()).containsKey("buildConfig");
    }

    @Test
    @Order(5)
    @DisplayName("E2E: Should complete TESTING phase")
    void testCompleteTestingPhase() throws Exception {
        PhaseExecutionRequest request = PhaseExecutionRequest.builder()
                .projectId(projectId)
                .phase("TESTING")
                .tenantId(tenantId)
                .build();

        Promise<PhaseResult> promise = platform.executePhase(request);
        PhaseResult result = runPromise(() -> promise);

        assertThat(result.success()).isTrue();
        assertThat(result.phase()).isEqualTo("TESTING");
        assertThat(result.artifacts()).containsKey("testResults");
        assertThat(result.artifacts()).containsKey("coverageReport");
    }

    @Test
    @Order(6)
    @DisplayName("E2E: Should retrieve complete project with all phases")
    void testGetCompleteProject() throws Exception {
        Promise<Project> promise = platform.getProject(projectId, tenantId);
        Project project = runPromise(() -> promise);

        assertThat(project).isNotNull();
        assertThat(project.id()).isEqualTo(projectId);
        assertThat(project.completedPhases()).hasSize(4);
        assertThat(project.completedPhases()).containsExactly(
            "PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING"
        );
        assertThat(project.status()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(7)
    @DisplayName("E2E: Should track project metrics")
    void testProjectMetrics() throws Exception {
        Promise<ProjectMetrics> promise = platform.getProjectMetrics(projectId, tenantId);
        ProjectMetrics metrics = runPromise(() -> promise);

        assertThat(metrics).isNotNull();
        assertThat(metrics.totalPhases()).isEqualTo(4);
        assertThat(metrics.completedPhases()).isEqualTo(4);
        assertThat(metrics.totalExecutionTimeMs()).isGreaterThan(0);
        assertThat(metrics.agentInvocations()).isGreaterThan(0);
    }

    @Test
    @Order(8)
    @DisplayName("E2E: Should list projects for tenant")
    void testListProjects() throws Exception {
        Promise<Project[]> promise = platform.listProjects(tenantId);
        Project[] projects = runPromise(() -> promise);

        assertThat(projects).isNotEmpty();
        assertThat(projects).anyMatch(p -> p.id().equals(projectId));
    }

    // Mock implementations

    interface YappcPlatform {
        Promise<Project> createProject(CreateProjectRequest request);
        Promise<PhaseResult> executePhase(PhaseExecutionRequest request);
        Promise<Project> getProject(String projectId, String tenantId);
        Promise<ProjectMetrics> getProjectMetrics(String projectId, String tenantId);
        Promise<Project[]> listProjects(String tenantId);
    }

    record CreateProjectRequest(String tenantId, String projectName, String intent) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String tenantId, projectName, intent;
            Builder tenantId(String v) { tenantId = v; return this; }
            Builder projectName(String v) { projectName = v; return this; }
            Builder intent(String v) { intent = v; return this; }
            CreateProjectRequest build() { return new CreateProjectRequest(tenantId, projectName, intent); }
        }
    }

    record PhaseExecutionRequest(String projectId, String phase, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String projectId, phase, tenantId;
            Builder projectId(String v) { projectId = v; return this; }
            Builder phase(String v) { phase = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            PhaseExecutionRequest build() { return new PhaseExecutionRequest(projectId, phase, tenantId); }
        }
    }

    record Project(
        String id,
        String name,
        String tenantId,
        String status,
        String currentPhase,
        java.util.List<String> completedPhases,
        Instant createdAt
    ) {}

    record PhaseResult(
        boolean success,
        String phase,
        Map<String, Object> artifacts,
        long executionTimeMs
    ) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private boolean success;
            private String phase;
            private Map<String, Object> artifacts;
            private long executionTimeMs;
            Builder success(boolean v) { success = v; return this; }
            Builder phase(String v) { phase = v; return this; }
            Builder artifacts(Map<String, Object> v) { artifacts = v; return this; }
            Builder executionTimeMs(long v) { executionTimeMs = v; return this; }
            PhaseResult build() { return new PhaseResult(success, phase, artifacts, executionTimeMs); }
        }
    }

    record ProjectMetrics(
        int totalPhases,
        int completedPhases,
        long totalExecutionTimeMs,
        int agentInvocations
    ) {}

    static class MockYappcPlatform implements YappcPlatform {
        private final Map<String, Project> projects = new ConcurrentHashMap<>();
        private final Map<String, java.util.List<String>> projectPhases = new ConcurrentHashMap<>();
        private final Map<String, ProjectMetrics> projectMetrics = new ConcurrentHashMap<>();

        @Override
        public Promise<Project> createProject(CreateProjectRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                String id = UUID.randomUUID().toString();
                Project project = new Project(
                    id,
                    request.projectName(),
                    request.tenantId(),
                    "CREATED",
                    "PLANNING",
                    java.util.List.of(),
                    Instant.now()
                );
                projects.put(id, project);
                projectPhases.put(id, new java.util.ArrayList<>());
                projectMetrics.put(id, new ProjectMetrics(4, 0, 0, 0));
                return project;
            });
        }

        @Override
        public Promise<PhaseResult> executePhase(PhaseExecutionRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                long startTime = System.currentTimeMillis();
                Thread.sleep(50); // Simulate execution time

                Map<String, Object> artifacts = switch (request.phase()) {
                    case "PLANNING" -> Map.of("requirements", "User stories", "architecture", "System design");
                    case "DESIGN" -> Map.of("apiSpec", "OpenAPI spec", "dataModel", "Entity models");
                    case "IMPLEMENTATION" -> Map.of("sourceCode", "Java/TS code", "buildConfig", "Gradle/npm");
                    case "TESTING" -> Map.of("testResults", "All passed", "coverageReport", "85%");
                    default -> Map.of();
                };

                java.util.List<String> phases = projectPhases.get(request.projectId());
                phases.add(request.phase());

                // Update project status
                Project current = projects.get(request.projectId());
                String newStatus = phases.size() == 4 ? "COMPLETED" : "IN_PROGRESS";
                String nextPhase = phases.size() < 4 ? 
                    java.util.List.of("PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING").get(phases.size()) : 
                    "COMPLETED";

                Project updated = new Project(
                    current.id(),
                    current.name(),
                    current.tenantId(),
                    newStatus,
                    nextPhase,
                    new java.util.ArrayList<>(phases),
                    current.createdAt()
                );
                projects.put(request.projectId(), updated);

                // Update metrics
                ProjectMetrics currentMetrics = projectMetrics.get(request.projectId());
                long executionTime = System.currentTimeMillis() - startTime;
                projectMetrics.put(request.projectId(), new ProjectMetrics(
                    4,
                    phases.size(),
                    currentMetrics.totalExecutionTimeMs() + executionTime,
                    currentMetrics.agentInvocations() + 3
                ));

                return PhaseResult.builder()
                    .success(true)
                    .phase(request.phase())
                    .artifacts(artifacts)
                    .executionTimeMs(executionTime)
                    .build();
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
        public Promise<Project[]> listProjects(String tenantId) {
            return Promise.of(projects.values().stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .toArray(Project[]::new));
        }
    }
}
