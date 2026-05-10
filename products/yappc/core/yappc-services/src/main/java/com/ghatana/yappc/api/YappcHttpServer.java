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
            RouteAuthorizationFilter routeAuthorizationFilter,
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
            PreviewSessionApiController previewSessionApiController,
            PhasePacketController phasePacketController) {

        ApiVersionPolicy versionPolicy = new ApiVersionPolicy();

        RoutingServlet routing = RoutingServlet.builder(eventloop)
                // Health check
                .with(HttpMethod.GET, "/health", request ->
                    Promise.of(HttpResponse.ok200().withPlainText("OK").build()))

                // Intent endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/intent/capture", secureVersioned(authFilter, routeAuthorizationFilter, intentController::captureIntent, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/intent/analyze", secureVersioned(authFilter, routeAuthorizationFilter, intentController::analyzeIntent, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/intent/:id", secureVersioned(authFilter, routeAuthorizationFilter, intentController::getIntent, versionPolicy))

                // Shape endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/shape/derive", secureVersioned(authFilter, routeAuthorizationFilter, shapeController::deriveShape, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/shape/model", secureVersioned(authFilter, routeAuthorizationFilter, shapeController::generateSystemModel, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/shape/:id", secureVersioned(authFilter, routeAuthorizationFilter, shapeController::getShape, versionPolicy))

                // Validation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/validate", secureVersioned(authFilter, routeAuthorizationFilter, validationController::validate, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-config", secureVersioned(authFilter, routeAuthorizationFilter, validationController::validateWithConfig, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-policy", secureVersioned(authFilter, routeAuthorizationFilter, validationController::validateWithPolicy, versionPolicy))

                // Generation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/generate", secureVersioned(authFilter, routeAuthorizationFilter, generationController::generateArtifacts, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/diff", secureVersioned(authFilter, routeAuthorizationFilter, generationController::regenerateWithDiff, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/apply", secureVersioned(authFilter, routeAuthorizationFilter, generationController::applyReviewDecision, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/reject", secureVersioned(authFilter, routeAuthorizationFilter, generationController::rejectReviewDecision, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/rollback", secureVersioned(authFilter, routeAuthorizationFilter, generationController::rollbackReviewDecision, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/generate/artifacts/:id", secureVersioned(authFilter, routeAuthorizationFilter, generationController::getArtifacts, versionPolicy))

                // Run endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/run", secureVersioned(authFilter, routeAuthorizationFilter, runController::executeRun, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/with-observation", secureVersioned(authFilter, routeAuthorizationFilter, runController::executeRunWithObservation, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/rollback", secureVersioned(authFilter, routeAuthorizationFilter, runController::rollback, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/promote", secureVersioned(authFilter, routeAuthorizationFilter, runController::promote, versionPolicy))

                // Observe endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/observe", secureVersioned(authFilter, routeAuthorizationFilter, observeController::collectObservation, versionPolicy))

                // Learn endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/learn", secureVersioned(authFilter, routeAuthorizationFilter, learnController::analyze, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/learn/with-context", secureVersioned(authFilter, routeAuthorizationFilter, learnController::analyzeWithContext, versionPolicy))

                // Evolve endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/evolve", secureVersioned(authFilter, routeAuthorizationFilter, evolveController::propose, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/evolve/with-constraints", secureVersioned(authFilter, routeAuthorizationFilter, evolveController::proposeWithConstraints, versionPolicy))

                // Full lifecycle orchestration endpoint
                .with(HttpMethod.POST, "/api/v1/yappc/lifecycle/execute", secureVersioned(authFilter, routeAuthorizationFilter, lifecycleController::executeFullLifecycle, versionPolicy))

                // Artifact Compiler endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/ingest", secureVersioned(authFilter, routeAuthorizationFilter, artifactGraphController::ingest, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/analyze", secureVersioned(authFilter, routeAuthorizationFilter, artifactGraphController::analyze, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/merge", secureVersioned(authFilter, routeAuthorizationFilter, artifactGraphController::merge, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/query", secureVersioned(authFilter, routeAuthorizationFilter, artifactGraphController::query, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/residual/analyze", secureVersioned(authFilter, routeAuthorizationFilter, artifactGraphController::analyzeResidual, versionPolicy))

                // Preview session endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/preview/session/create", secureVersioned(authFilter, routeAuthorizationFilter, previewSessionApiController::createSession, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/preview/session/validate", secureVersioned(authFilter, routeAuthorizationFilter, previewSessionApiController::validateSession, versionPolicy))

                // Phase cockpit packet endpoint
                .with(HttpMethod.POST, "/api/v1/yappc/phase/packet", secureVersioned(authFilter, routeAuthorizationFilter, phasePacketController::getPhasePacket, versionPolicy))

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

                // Page Artifact routes (use route authorization filter)
                .with(HttpMethod.PUT, "/api/v1/page-artifacts/:artifactId/document", request ->
                    routeAuthorizationFilter.apply(request, authFilter.secure(pageArtifactController::saveDocument)))
                .with(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/document", request ->
                    routeAuthorizationFilter.apply(request, authFilter.secure(pageArtifactController::loadDocument)))
                .with(HttpMethod.POST, "/api/v1/page-artifacts/:artifactId/review-decisions", request ->
                    routeAuthorizationFilter.apply(request, authFilter.secure(pageArtifactController::recordReviewDecision)))
                .with(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/operation-log/export", request ->
                    routeAuthorizationFilter.apply(request, authFilter.secure(pageArtifactController::exportOperationLog)))

                .build();

        return routing;
    }

    private AsyncServlet secureVersioned(
            YappcApiAuthFilter authFilter,
            RouteAuthorizationFilter routeAuthorizationFilter,
            AsyncServlet delegate,
            ApiVersionPolicy versionPolicy) {
        AsyncServlet authorized = authFilter.secure(request -> routeAuthorizationFilter.apply(request, delegate));
        return versionPolicy.apply(authorized);
    }

    @Provides
    YappcApiAuthFilter yappcApiAuthFilter() {
        return YappcApiAuthFilter.fromEnvironment();
    }

    @Provides
    RouteAuthorizationFilter routeAuthorizationFilter(RouteAuthorizationRegistry routeAuthorizationRegistry) {
        return new RouteAuthorizationFilter(routeAuthorizationRegistry);
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
