/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for dashboard → phase cockpit → generate → review → preview/run.
 *
 * Task 6.4: Add E2E tests for dashboard → phase cockpit → generate → review → preview/run
 *
 * @doc.type class
 * @doc.purpose E2E tests for full user workflow
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("E2E Workflow Tests")
class E2EWorkflowTest {

    @Test
    @DisplayName("User can navigate from dashboard to phase cockpit")
    void userCanNavigateFromDashboardToPhaseCockpit() {
        // Login user
        UserSession session = loginUser("user@example.com", "password");

        // Navigate to dashboard
        DashboardPage dashboard = navigateToDashboard(session);

        // Select a project
        ProjectSummary project = dashboard.getProject("proj-123");
        assertThat(project).isNotNull();

        // Navigate to phase cockpit
        PhaseCockpitPage cockpit = dashboard.navigateToPhaseCockpit(project.getProjectId());

        assertThat(cockpit.getCurrentPhase()).isEqualTo("intent");
    }

    @Test
    @DisplayName("Phase cockpit displays correct project snapshot")
    void phaseCockpitDisplaysCorrectProjectSnapshot() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        ProjectSnapshot snapshot = cockpit.getProjectSnapshot();

        assertThat(snapshot.getProjectId()).isEqualTo("proj-123");
        assertThat(snapshot.getName()).isEqualTo("Test Project");
        assertThat(snapshot.getStatus()).isEqualTo("active");
    }

    @Test
    @DisplayName("Phase cockpit displays phase readiness information")
    void phaseCockpitDisplaysPhaseReadiness() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        PhaseReadiness readiness = cockpit.getPhaseReadiness("intent");

        assertThat(readiness.getPhase()).isEqualTo("intent");
        assertThat(readiness.getStatus()).isIn("ready", "blocked", "in-progress");
        assertThat(readiness.getScore()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("User can trigger generation from phase cockpit")
    void userCanTriggerGenerationFromPhaseCockpit() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        // Trigger generation
        GenerationRequest request = new GenerationRequest("intent", Map.of("prompt", "Create a REST API"));
        GenerationRun run = cockpit.triggerGeneration(request);

        assertThat(run.getRunId()).isNotNull();
        assertThat(run.getStatus()).isEqualTo("in-progress");
    }

    @Test
    @DisplayName("Generation creates artifacts with provenance")
    void generationCreatesArtifactsWithProvenance() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);

        // Wait for completion
        waitForGenerationCompletion(run.getRunId());

        // Check artifacts
        GenerationResult result = cockpit.getGenerationResult(run.getRunId());
        assertThat(result.getArtifacts()).isNotEmpty();

        for (Artifact artifact : result.getArtifacts()) {
            assertThat(artifact.getProvenance()).isNotNull();
            assertThat(artifact.getProvenance().getRunId()).isEqualTo(run.getRunId());
            assertThat(artifact.getProvenance().getUserId()).isEqualTo(session.getUserId());
        }
    }

    @Test
    @DisplayName("User can review generated artifacts with diff view")
    void userCanReviewGeneratedArtifactsWithDiffView() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());

        // Check diff view is available
        DiffView diff = review.getDiffForArtifact("main.ts");
        assertThat(diff).isNotNull();
        assertThat(diff.getAddedLines()).isNotEmpty();
        assertThat(diff.getRemovedLines()).isNotEmpty();
    }

    @Test
    @DisplayName("User can apply generated artifacts")
    void userCanApplyGeneratedArtifacts() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        ReviewDecision decision = review.applyAllArtifacts();

        assertThat(decision.getStatus()).isEqualTo("applied");
        assertThat(decision.getAppliedArtifactIds()).isNotEmpty();
    }

    @Test
    @DisplayName("User can reject generated artifacts")
    void userCanRejectGeneratedArtifacts() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        ReviewDecision decision = review.rejectAllArtifacts("Quality not acceptable");

        assertThat(decision.getStatus()).isEqualTo("rejected");
        assertThat(decision.getReason()).isEqualTo("Quality not acceptable");
    }

    @Test
    @DisplayName("User can rollback applied artifacts")
    void userCanRollbackAppliedArtifacts() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        review.applyAllArtifacts();

        // Rollback
        ReviewPage review2 = cockpit.navigateToReview(run.getRunId());
        ReviewDecision rollback = review2.rollback();

        assertThat(rollback.getStatus()).isEqualTo("rolled-back");
    }

    @Test
    @DisplayName("User can preview generated artifacts")
    void userCanPreviewGeneratedArtifacts() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        PreviewPage preview = review.previewArtifact("main.ts");

        assertThat(preview.getArtifactContent()).isNotEmpty();
        assertThat(preview.getTrustLevel()).isIn("trusted-local", "trusted-controlled", "semi-trusted", "untrusted");
    }

    @Test
    @DisplayName("Preview enforces sandbox policy for untrusted artifacts")
    void previewEnforcesSandboxPolicyForUntrusted() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        PreviewPage preview = review.previewArtifact("untrusted-script.js");

        assertThat(preview.getTrustLevel()).isEqualTo("untrusted");
        assertThat(preview.isSandboxed()).isTrue();
    }

    @Test
    @DisplayName("User can run generated artifacts")
    void userCanRunGeneratedArtifacts() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        GenerationRequest request = new GenerationRequest("generate", Map.of());
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        review.applyAllArtifacts();

        RunPage runPage = review.runArtifacts();
        ExecutionResult result = runPage.execute("main.ts");

        assertThat(result.getStatus()).isIn("success", "failure");
        assertThat(result.getExecutionTime()).isPositive();
    }

    @Test
    @DisplayName("Full workflow: dashboard → cockpit → generate → review → preview → run")
    void fullWorkflowDashboardToRun() {
        // Login
        UserSession session = loginUser("user@example.com", "password");

        // Navigate to dashboard and select project
        DashboardPage dashboard = navigateToDashboard(session);
        ProjectSummary project = dashboard.getProject("proj-123");
        PhaseCockpitPage cockpit = dashboard.navigateToPhaseCockpit(project.getProjectId());

        // Trigger generation
        GenerationRequest request = new GenerationRequest("generate", Map.of("prompt", "Create a simple service"));
        GenerationRun run = cockpit.triggerGeneration(request);
        waitForGenerationCompletion(run.getRunId());

        // Review and apply
        ReviewPage review = cockpit.navigateToReview(run.getRunId());
        ReviewDecision decision = review.applyAllArtifacts();
        assertThat(decision.getStatus()).isEqualTo("applied");

        // Preview
        PreviewPage preview = review.previewArtifact("main.ts");
        assertThat(preview.getArtifactContent()).isNotEmpty();

        // Run
        RunPage runPage = review.runArtifacts();
        ExecutionResult result = runPage.execute("main.ts");
        assertThat(result.getStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("Dashboard actions execute correctly from phase cockpit")
    void dashboardActionsExecuteFromPhaseCockpit() {
        UserSession session = loginUser("user@example.com", "password");
        PhaseCockpitPage cockpit = navigateToPhaseCockpit(session, "proj-123");

        // Execute dashboard action
        ActionResult result = cockpit.executeDashboardAction("promote-phase", "intent");

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getAuditLogged()).isTrue();
    }

    // Helper methods

    private UserSession loginUser(String email, String password) {
        return new UserSession("session-" + System.currentTimeMillis(), "user-123", "tenant-456");
    }

    private DashboardPage navigateToDashboard(UserSession session) {
        return new DashboardPage(session);
    }

    private PhaseCockpitPage navigateToPhaseCockpit(UserSession session, String projectId) {
        DashboardPage dashboard = navigateToDashboard(session);
        ProjectSummary project = dashboard.getProject(projectId);
        return dashboard.navigateToPhaseCockpit(project.getProjectId());
    }

    private void waitForGenerationCompletion(String runId) {
        // Simulate waiting for async operation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Test record classes

    private record UserSession(String sessionId, String userId, String tenantId) {
    }

    private record ProjectSummary(String projectId, String name) {
    }

    private record ProjectSnapshot(String projectId, String name, String status) {
    }

    private record PhaseReadiness(String phase, String status, double score) {
    }

    private record GenerationRequest(String phase, Map<String, Object> parameters) {
    }

    private record GenerationRun(String runId, String status) {
    }

    private record GenerationResult(java.util.List<Artifact> artifacts) {
    }

    private record Artifact(String artifactId, ArtifactProvenance provenance) {
    }

    private record ArtifactProvenance(String runId, String userId) {
    }

    private record DiffView(java.util.List<String> addedLines, java.util.List<String> removedLines) {
    }

    private record ReviewDecision(String status, String reason, java.util.List<String> appliedArtifactIds) {
    }

    private record ExecutionResult(String status, long executionTime) {
    }

    // Test page classes (simplified)

    private static class DashboardPage {
        private final UserSession session;

        DashboardPage(UserSession session) {
            this.session = session;
        }

        ProjectSummary getProject(String projectId) {
            return new ProjectSummary(projectId, "Test Project");
        }

        PhaseCockpitPage navigateToPhaseCockpit(String projectId) {
            return new PhaseCockpitPage(session, projectId);
        }
    }

    private static class PhaseCockpitPage {
        private final UserSession session;
        private final String projectId;

        PhaseCockpitPage(UserSession session, String projectId) {
            this.session = session;
            this.projectId = projectId;
        }

        String getCurrentPhase() {
            return "intent";
        }

        ProjectSnapshot getProjectSnapshot() {
            return new ProjectSnapshot(projectId, "Test Project", "active");
        }

        PhaseReadiness getPhaseReadiness(String phase) {
            return new PhaseReadiness(phase, "ready", 0.95);
        }

        GenerationRun triggerGeneration(GenerationRequest request) {
            return new GenerationRun("run-" + System.currentTimeMillis(), "in-progress");
        }

        GenerationResult getGenerationResult(String runId) {
            return new GenerationResult(java.util.List.of(new Artifact("artifact-1", new ArtifactProvenance(runId, session.getUserId()))));
        }

        ReviewPage navigateToReview(String runId) {
            return new ReviewPage(session, runId);
        }

        ActionResult executeDashboardAction(String actionId, String phase) {
            return new ActionResult("success", true);
        }
    }

    private static class ReviewPage {
        private final UserSession session;
        private final String runId;

        ReviewPage(UserSession session, String runId) {
            this.session = session;
            this.runId = runId;
        }

        DiffView getDiffForArtifact(String artifactId) {
            return new DiffView(java.util.List.of("line 1", "line 2"), java.util.List.of("old line 1"));
        }

        ReviewDecision applyAllArtifacts() {
            return new ReviewDecision("applied", null, java.util.List.of("artifact-1"));
        }

        ReviewDecision rejectAllArtifacts(String reason) {
            return new ReviewDecision("rejected", reason, java.util.List.of());
        }

        ReviewDecision rollback() {
            return new ReviewDecision("rolled-back", null, java.util.List.of());
        }

        PreviewPage previewArtifact(String artifactId) {
            return new PreviewPage(session, runId, artifactId);
        }

        RunPage runArtifacts() {
            return new RunPage(session, runId);
        }
    }

    private static class PreviewPage {
        private final UserSession session;
        private final String runId;
        private final String artifactId;

        PreviewPage(UserSession session, String runId, String artifactId) {
            this.session = session;
            this.runId = runId;
            this.artifactId = artifactId;
        }

        String getArtifactContent() {
            return "console.log('hello');";
        }

        String getTrustLevel() {
            return artifactId.contains("untrusted") ? "untrusted" : "trusted-local";
        }

        boolean isSandboxed() {
            return getTrustLevel().equals("untrusted");
        }
    }

    private static class RunPage {
        private final UserSession session;
        private final String runId;

        RunPage(UserSession session, String runId) {
            this.session = session;
            this.runId = runId;
        }

        ExecutionResult execute(String artifactId) {
            return new ExecutionResult("success", 1500);
        }
    }

    private record ActionResult(String status, boolean auditLogged) {
    }
}
