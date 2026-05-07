package com.ghatana.yappc.api;

import com.ghatana.yappc.domain.pageartifact.http.PageArtifactController;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Provides;
import io.activej.launcher.Launcher;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose ActiveJ HTTP server for YAPPC API
 * @doc.layer api
 * @doc.pattern Server
 */
public class YappcHttpServer extends HttpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(YappcHttpServer.class);

    @Provides
    AsyncServlet servlet(
            Eventloop eventloop,
            YappcApiAuthFilter authFilter,
            IntentApiController intentController,
            ShapeApiController shapeController,
            ValidationApiController validationController,
            GenerationApiController generationController,
            RunApiController runController,
            ObserveApiController observeController,
            LearnApiController learnController,
            EvolveApiController evolveController,
            LifecycleApiController lifecycleController,
            ArtifactGraphController artifactGraphController,
            PageArtifactController pageArtifactController,
            PreviewSessionApiController previewSessionApiController) {

        ApiVersionPolicy versionPolicy = new ApiVersionPolicy();

        RoutingServlet routing = RoutingServlet.builder(eventloop)
                // Health check
                .with(HttpMethod.GET, "/health", request ->
                    Promise.of(HttpResponse.ok200().withPlainText("OK").build()))

                // Intent endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/intent/capture", secureVersioned(authFilter, intentController::captureIntent, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/intent/analyze", secureVersioned(authFilter, intentController::analyzeIntent, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/intent/:id", secureVersioned(authFilter, intentController::getIntent, versionPolicy))

                // Shape endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/shape/derive", secureVersioned(authFilter, shapeController::deriveShape, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/shape/model", secureVersioned(authFilter, shapeController::generateSystemModel, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/shape/:id", secureVersioned(authFilter, shapeController::getShape, versionPolicy))

                // Validation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/validate", secureVersioned(authFilter, validationController::validate, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-config", secureVersioned(authFilter, validationController::validateWithConfig, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-policy", secureVersioned(authFilter, validationController::validateWithPolicy, versionPolicy))

                // Generation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/generate", secureVersioned(authFilter, generationController::generateArtifacts, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/diff", secureVersioned(authFilter, generationController::regenerateWithDiff, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/apply", secureVersioned(authFilter, generationController::applyReviewDecision, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/reject", secureVersioned(authFilter, generationController::rejectReviewDecision, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/rollback", secureVersioned(authFilter, generationController::rollbackReviewDecision, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/generate/artifacts/:id", secureVersioned(authFilter, generationController::getArtifacts, versionPolicy))

                // Run endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/run", secureVersioned(authFilter, runController::executeRun, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/with-observation", secureVersioned(authFilter, runController::executeRunWithObservation, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/rollback", secureVersioned(authFilter, runController::rollback, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/promote", secureVersioned(authFilter, runController::promote, versionPolicy))

                // Observe endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/observe", secureVersioned(authFilter, observeController::collectObservation, versionPolicy))

                // Learn endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/learn", secureVersioned(authFilter, learnController::analyze, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/learn/with-context", secureVersioned(authFilter, learnController::analyzeWithContext, versionPolicy))

                // Evolve endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/evolve", secureVersioned(authFilter, evolveController::propose, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/evolve/with-constraints", secureVersioned(authFilter, evolveController::proposeWithConstraints, versionPolicy))

                // Full lifecycle orchestration endpoint
                .with(HttpMethod.POST, "/api/v1/yappc/lifecycle/execute", secureVersioned(authFilter, lifecycleController::executeFullLifecycle, versionPolicy))

                // Artifact Compiler endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/ingest", secureVersioned(authFilter, artifactGraphController::ingest, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/analyze", secureVersioned(authFilter, artifactGraphController::analyze, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/merge", secureVersioned(authFilter, artifactGraphController::merge, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/query", secureVersioned(authFilter, artifactGraphController::query, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/residual/analyze", secureVersioned(authFilter, artifactGraphController::analyzeResidual, versionPolicy))

                // Preview session endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/preview/sessions", secureVersioned(authFilter, previewSessionApiController::createSession, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/preview/sessions/validate", secureVersioned(authFilter, previewSessionApiController::validateSession, versionPolicy))

                // API info
                .with(HttpMethod.GET, "/api/v1/yappc/info", versionPolicy.apply(request ->
                    Promise.of(HttpResponse.ok200().withJson("""
                        {
                          "name": "YAPPC Lifecycle API",
                          "version": "1.0.0",
                          "apiVersion": "v1",
                          "phases": ["intent", "shape", "validate", "generate", "run", "observe", "learn", "evolve"]
                        }
                        """).build())))

                // Page Artifact routes
                .with(HttpMethod.PUT, "/api/v1/page-artifacts/:artifactId/document", request ->
                    authFilter.secure(pageArtifactController::saveDocument).serve(request))
                .with(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/document", request ->
                    authFilter.secure(pageArtifactController::loadDocument).serve(request))
                .with(HttpMethod.POST, "/api/v1/page-artifacts/:artifactId/review-decisions", request ->
                    authFilter.secure(pageArtifactController::recordReviewDecision).serve(request))
                .with(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/operation-log/export", request ->
                    authFilter.secure(pageArtifactController::exportOperationLog).serve(request))

                .build();

        return routing;
    }

    private AsyncServlet secureVersioned(
            YappcApiAuthFilter authFilter,
            AsyncServlet delegate,
            ApiVersionPolicy versionPolicy) {
        return versionPolicy.apply(authFilter.secure(delegate));
    }

    @Provides
    YappcApiAuthFilter yappcApiAuthFilter() {
        return YappcApiAuthFilter.fromEnvironment();
    }

    @Provides
    PreviewSessionApiController previewSessionApiController() {
        return new PreviewSessionApiController(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                System.getenv("YAPPC_PREVIEW_SESSION_SECRET")
        );
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting YAPPC HTTP Server...");
        
        // Production startup guard: fail fast if task execution is enabled but no real CI/CD adapter is configured
        boolean enableTaskExecution = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_TASK_EXECUTION", "false"));
        if (enableTaskExecution) {
            String githubToken = System.getenv("GITHUB_TOKEN");
            String githubRepo = System.getenv("GITHUB_REPO");
            
            if (githubToken == null || githubToken.isBlank() || githubRepo == null || githubRepo.isBlank()) {
                log.error("PRODUCTION STARTUP GUARD FAILED: ENABLE_TASK_EXECUTION=true but no real CI/CD adapter is configured.");
                log.error("Required environment variables:");
                log.error("  - GITHUB_TOKEN: GitHub personal access token");
                log.error("  - GITHUB_REPO: Repository in format 'owner/repo'");
                log.error("Either configure a real CI/CD adapter or set ENABLE_TASK_EXECUTION=false");
                System.exit(1);
            }
            
            log.info("Production startup guard passed: ENABLE_TASK_EXECUTION=true with real CI/CD adapter configured");
        } else {
            log.info("Task execution disabled (ENABLE_TASK_EXECUTION=false) - using NoOpCiCdAdapter");
        }
        
        Launcher launcher = new YappcHttpServer();
        launcher.launch(args);
    }
}
