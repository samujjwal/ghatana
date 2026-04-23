/*
 * Copyright (c) 2026 Ghatana // GH-90000
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class ProjectLifecycleE2ETest extends EventloopTestBase {

    private static YappcPlatform platform;
    private static String projectId;
    private static String tenantId = "e2e-test-tenant";

    @BeforeAll
    static void setUpPlatform() { // GH-90000
        platform = new MockYappcPlatform(); // GH-90000
    }

    @Test
    @Order(1) // GH-90000
    @DisplayName("E2E: Should create new project with intent")
    void testCreateProject() throws Exception { // GH-90000
        CreateProjectRequest request = CreateProjectRequest.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .projectName("E2E Test Microservice")
                .intent("Build a user authentication microservice with JWT")
                .build(); // GH-90000

        Promise<Project> promise = platform.createProject(request); // GH-90000
        Project project = runPromise(() -> promise); // GH-90000

        assertThat(project).isNotNull(); // GH-90000
        assertThat(project.id()).isNotNull(); // GH-90000
        assertThat(project.name()).isEqualTo("E2E Test Microservice");
        assertThat(project.status()).isEqualTo("CREATED");
        assertThat(project.currentPhase()).isEqualTo("PLANNING");

        projectId = project.id(); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("E2E: Should complete PLANNING phase")
    void testCompletePlanningPhase() throws Exception { // GH-90000
        PhaseExecutionRequest request = PhaseExecutionRequest.builder() // GH-90000
                .projectId(projectId) // GH-90000
                .phase("PLANNING")
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000

        Promise<PhaseResult> promise = platform.executePhase(request); // GH-90000
        PhaseResult result = runPromise(() -> promise); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.phase()).isEqualTo("PLANNING");
        assertThat(result.artifacts()).containsKey("requirements");
        assertThat(result.artifacts()).containsKey("architecture");
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("E2E: Should complete DESIGN phase")
    void testCompleteDesignPhase() throws Exception { // GH-90000
        PhaseExecutionRequest request = PhaseExecutionRequest.builder() // GH-90000
                .projectId(projectId) // GH-90000
                .phase("DESIGN")
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000

        Promise<PhaseResult> promise = platform.executePhase(request); // GH-90000
        PhaseResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.phase()).isEqualTo("DESIGN");
        assertThat(result.artifacts()).containsKey("apiSpec");
        assertThat(result.artifacts()).containsKey("dataModel");
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("E2E: Should complete IMPLEMENTATION phase")
    void testCompleteImplementationPhase() throws Exception { // GH-90000
        PhaseExecutionRequest request = PhaseExecutionRequest.builder() // GH-90000
                .projectId(projectId) // GH-90000
                .phase("IMPLEMENTATION")
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000

        Promise<PhaseResult> promise = platform.executePhase(request); // GH-90000
        PhaseResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.phase()).isEqualTo("IMPLEMENTATION");
        assertThat(result.artifacts()).containsKey("sourceCode");
        assertThat(result.artifacts()).containsKey("buildConfig");
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("E2E: Should complete TESTING phase")
    void testCompleteTestingPhase() throws Exception { // GH-90000
        PhaseExecutionRequest request = PhaseExecutionRequest.builder() // GH-90000
                .projectId(projectId) // GH-90000
                .phase("TESTING")
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000

        Promise<PhaseResult> promise = platform.executePhase(request); // GH-90000
        PhaseResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.phase()).isEqualTo("TESTING");
        assertThat(result.artifacts()).containsKey("testResults");
        assertThat(result.artifacts()).containsKey("coverageReport");
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("E2E: Should retrieve complete project with all phases")
    void testGetCompleteProject() throws Exception { // GH-90000
        Promise<Project> promise = platform.getProject(projectId, tenantId); // GH-90000
        Project project = runPromise(() -> promise); // GH-90000

        assertThat(project).isNotNull(); // GH-90000
        assertThat(project.id()).isEqualTo(projectId); // GH-90000
        assertThat(project.completedPhases()).hasSize(4); // GH-90000
        assertThat(project.completedPhases()).containsExactly( // GH-90000
            "PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING"
        );
        assertThat(project.status()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("E2E: Should track project metrics")
    void testProjectMetrics() throws Exception { // GH-90000
        Promise<ProjectMetrics> promise = platform.getProjectMetrics(projectId, tenantId); // GH-90000
        ProjectMetrics metrics = runPromise(() -> promise); // GH-90000

        assertThat(metrics).isNotNull(); // GH-90000
        assertThat(metrics.totalPhases()).isEqualTo(4); // GH-90000
        assertThat(metrics.completedPhases()).isEqualTo(4); // GH-90000
        assertThat(metrics.totalExecutionTimeMs()).isGreaterThan(0); // GH-90000
        assertThat(metrics.agentInvocations()).isGreaterThan(0); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("E2E: Should list projects for tenant")
    void testListProjects() throws Exception { // GH-90000
        Promise<Project[]> promise = platform.listProjects(tenantId); // GH-90000
        Project[] projects = runPromise(() -> promise); // GH-90000

        assertThat(projects).isNotEmpty(); // GH-90000
        assertThat(projects).anyMatch(p -> p.id().equals(projectId)); // GH-90000
    }

    // Mock implementations

    interface YappcPlatform {
        Promise<Project> createProject(CreateProjectRequest request); // GH-90000
        Promise<PhaseResult> executePhase(PhaseExecutionRequest request); // GH-90000
        Promise<Project> getProject(String projectId, String tenantId); // GH-90000
        Promise<ProjectMetrics> getProjectMetrics(String projectId, String tenantId); // GH-90000
        Promise<Project[]> listProjects(String tenantId); // GH-90000
    }

    record CreateProjectRequest(String tenantId, String projectName, String intent) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String tenantId, projectName, intent;
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            Builder projectName(String v) { projectName = v; return this; } // GH-90000
            Builder intent(String v) { intent = v; return this; } // GH-90000
            CreateProjectRequest build() { return new CreateProjectRequest(tenantId, projectName, intent); } // GH-90000
        }
    }

    record PhaseExecutionRequest(String projectId, String phase, String tenantId) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String projectId, phase, tenantId;
            Builder projectId(String v) { projectId = v; return this; } // GH-90000
            Builder phase(String v) { phase = v; return this; } // GH-90000
            Builder tenantId(String v) { tenantId = v; return this; } // GH-90000
            PhaseExecutionRequest build() { return new PhaseExecutionRequest(projectId, phase, tenantId); } // GH-90000
        }
    }

    record Project( // GH-90000
        String id,
        String name,
        String tenantId,
        String status,
        String currentPhase,
        java.util.List<String> completedPhases,
        Instant createdAt
    ) {}

    record PhaseResult( // GH-90000
        boolean success,
        String phase,
        Map<String, Object> artifacts,
        long executionTimeMs
    ) {
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private boolean success;
            private String phase;
            private Map<String, Object> artifacts;
            private long executionTimeMs;
            Builder success(boolean v) { success = v; return this; } // GH-90000
            Builder phase(String v) { phase = v; return this; } // GH-90000
            Builder artifacts(Map<String, Object> v) { artifacts = v; return this; } // GH-90000
            Builder executionTimeMs(long v) { executionTimeMs = v; return this; } // GH-90000
            PhaseResult build() { return new PhaseResult(success, phase, artifacts, executionTimeMs); } // GH-90000
        }
    }

    record ProjectMetrics( // GH-90000
        int totalPhases,
        int completedPhases,
        long totalExecutionTimeMs,
        int agentInvocations
    ) {}

    static class MockYappcPlatform implements YappcPlatform {
        private final Map<String, Project> projects = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, java.util.List<String>> projectPhases = new ConcurrentHashMap<>(); // GH-90000
        private final Map<String, ProjectMetrics> projectMetrics = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public Promise<Project> createProject(CreateProjectRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                String id = UUID.randomUUID().toString(); // GH-90000
                Project project = new Project( // GH-90000
                    id,
                    request.projectName(), // GH-90000
                    request.tenantId(), // GH-90000
                    "CREATED",
                    "PLANNING",
                    java.util.List.of(), // GH-90000
                    Instant.now() // GH-90000
                );
                projects.put(id, project); // GH-90000
                projectPhases.put(id, new java.util.ArrayList<>()); // GH-90000
                projectMetrics.put(id, new ProjectMetrics(4, 0, 0, 0)); // GH-90000
                return project;
            });
        }

        @Override
        public Promise<PhaseResult> executePhase(PhaseExecutionRequest request) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                long startTime = System.currentTimeMillis(); // GH-90000
                Thread.sleep(50); // Simulate execution time // GH-90000

                Map<String, Object> artifacts = switch (request.phase()) { // GH-90000
                    case "PLANNING" -> Map.of("requirements", "User stories", "architecture", "System design"); // GH-90000
                    case "DESIGN" -> Map.of("apiSpec", "OpenAPI spec", "dataModel", "Entity models"); // GH-90000
                    case "IMPLEMENTATION" -> Map.of("sourceCode", "Java/TS code", "buildConfig", "Gradle/npm"); // GH-90000
                    case "TESTING" -> Map.of("testResults", "All passed", "coverageReport", "85%"); // GH-90000
                    default -> Map.of(); // GH-90000
                };

                java.util.List<String> phases = projectPhases.get(request.projectId()); // GH-90000
                phases.add(request.phase()); // GH-90000

                // Update project status
                Project current = projects.get(request.projectId()); // GH-90000
                String newStatus = phases.size() == 4 ? "COMPLETED" : "IN_PROGRESS"; // GH-90000
                String nextPhase = phases.size() < 4 ?  // GH-90000
                    java.util.List.of("PLANNING", "DESIGN", "IMPLEMENTATION", "TESTING").get(phases.size()) :  // GH-90000
                    "COMPLETED";

                Project updated = new Project( // GH-90000
                    current.id(), // GH-90000
                    current.name(), // GH-90000
                    current.tenantId(), // GH-90000
                    newStatus,
                    nextPhase,
                    new java.util.ArrayList<>(phases), // GH-90000
                    current.createdAt() // GH-90000
                );
                projects.put(request.projectId(), updated); // GH-90000

                // Update metrics
                ProjectMetrics currentMetrics = projectMetrics.get(request.projectId()); // GH-90000
                long executionTime = System.currentTimeMillis() - startTime; // GH-90000
                projectMetrics.put(request.projectId(), new ProjectMetrics( // GH-90000
                    4,
                    phases.size(), // GH-90000
                    currentMetrics.totalExecutionTimeMs() + executionTime, // GH-90000
                    currentMetrics.agentInvocations() + 3 // GH-90000
                ));

                return PhaseResult.builder() // GH-90000
                    .success(true) // GH-90000
                    .phase(request.phase()) // GH-90000
                    .artifacts(artifacts) // GH-90000
                    .executionTimeMs(executionTime) // GH-90000
                    .build(); // GH-90000
            });
        }

        @Override
        public Promise<Project> getProject(String projectId, String tenantId) { // GH-90000
            return Promise.of(projects.get(projectId)); // GH-90000
        }

        @Override
        public Promise<ProjectMetrics> getProjectMetrics(String projectId, String tenantId) { // GH-90000
            return Promise.of(projectMetrics.get(projectId)); // GH-90000
        }

        @Override
        public Promise<Project[]> listProjects(String tenantId) { // GH-90000
            return Promise.of(projects.values().stream() // GH-90000
                .filter(p -> p.tenantId().equals(tenantId)) // GH-90000
                .toArray(Project[]::new)); // GH-90000
        }
    }
}
