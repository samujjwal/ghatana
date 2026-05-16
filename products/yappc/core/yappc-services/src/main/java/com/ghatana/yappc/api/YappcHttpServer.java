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
            YappcAuthenticationFilter authenticationFilter,
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
            ImportController importController,
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
                .with(HttpMethod.POST, "/api/v1/yappc/intent/capture", secureVersioned(authenticationFilter, routeAuthorizationFilter, intentController::captureIntent, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/intent/analyze", secureVersioned(authenticationFilter, routeAuthorizationFilter, intentController::analyzeIntent, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/intent/:id", secureVersioned(authenticationFilter, routeAuthorizationFilter, intentController::getIntent, versionPolicy))

                // Shape endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/shape/derive", secureVersioned(authenticationFilter, routeAuthorizationFilter, shapeController::deriveShape, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/shape/model", secureVersioned(authenticationFilter, routeAuthorizationFilter, shapeController::generateSystemModel, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/shape/:id", secureVersioned(authenticationFilter, routeAuthorizationFilter, shapeController::getShape, versionPolicy))

                // Validation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/validate", secureVersioned(authenticationFilter, routeAuthorizationFilter, validationController::validate, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-config", secureVersioned(authenticationFilter, routeAuthorizationFilter, validationController::validateWithConfig, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-policy", secureVersioned(authenticationFilter, routeAuthorizationFilter, validationController::validateWithPolicy, versionPolicy))

                // Generation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/generate", secureVersioned(authenticationFilter, routeAuthorizationFilter, generationController::generateArtifacts, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/diff", secureVersioned(authenticationFilter, routeAuthorizationFilter, generationController::regenerateWithDiff, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/apply", secureVersioned(authenticationFilter, routeAuthorizationFilter, generationController::applyReviewDecision, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/reject", secureVersioned(authenticationFilter, routeAuthorizationFilter, generationController::rejectReviewDecision, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/generate/runs/:runId/rollback", secureVersioned(authenticationFilter, routeAuthorizationFilter, generationController::rollbackReviewDecision, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/generate/artifacts/:id", secureVersioned(authenticationFilter, routeAuthorizationFilter, generationController::getArtifacts, versionPolicy))

                // Run endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/run", secureVersioned(authenticationFilter, routeAuthorizationFilter, runController::executeRun, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/with-observation", secureVersioned(authenticationFilter, routeAuthorizationFilter, runController::executeRunWithObservation, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/rollback", secureVersioned(authenticationFilter, routeAuthorizationFilter, runController::rollback, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/run/promote", secureVersioned(authenticationFilter, routeAuthorizationFilter, runController::promote, versionPolicy))

                // Observe endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/observe", secureVersioned(authenticationFilter, routeAuthorizationFilter, observeController::collectObservation, versionPolicy))

                // Learn endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/learn", secureVersioned(authenticationFilter, routeAuthorizationFilter, learnController::analyze, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/learn/with-context", secureVersioned(authenticationFilter, routeAuthorizationFilter, learnController::analyzeWithContext, versionPolicy))

                // Evolve endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/evolve", secureVersioned(authenticationFilter, routeAuthorizationFilter, evolveController::propose, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/evolve/with-constraints", secureVersioned(authenticationFilter, routeAuthorizationFilter, evolveController::proposeWithConstraints, versionPolicy))

                // Full lifecycle orchestration endpoint
                .with(HttpMethod.POST, "/api/v1/yappc/lifecycle/execute", secureVersioned(authenticationFilter, routeAuthorizationFilter, lifecycleController::executeFullLifecycle, versionPolicy))

                // Artifact Compiler endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/import-source", secureVersioned(authenticationFilter, routeAuthorizationFilter, importController::createImportJob, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/yappc/artifact/import-source/:jobId", secureVersioned(authenticationFilter, routeAuthorizationFilter, importController::getImportJobStatus, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/ingest", secureVersioned(authenticationFilter, routeAuthorizationFilter, artifactGraphController::ingest, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/analyze", secureVersioned(authenticationFilter, routeAuthorizationFilter, artifactGraphController::analyze, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/merge", secureVersioned(authenticationFilter, routeAuthorizationFilter, artifactGraphController::merge, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/graph/query", secureVersioned(authenticationFilter, routeAuthorizationFilter, artifactGraphController::query, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/yappc/artifact/residual/analyze", secureVersioned(authenticationFilter, routeAuthorizationFilter, artifactGraphController::analyzeResidual, versionPolicy))

                // Preview session endpoints (canonical paths per route-manifest.yaml)
                .with(HttpMethod.POST, "/api/v1/preview/session/create", secureVersioned(authenticationFilter, routeAuthorizationFilter, previewSessionApiController::createSession, versionPolicy))
                .with(HttpMethod.POST, "/api/v1/preview/session/validate", secureVersioned(authenticationFilter, routeAuthorizationFilter, previewSessionApiController::validateSession, versionPolicy))

                // Phase cockpit packet endpoint (canonical path per route-manifest.yaml)
                .with(HttpMethod.POST, "/api/v1/phase/packet", secureVersioned(authenticationFilter, routeAuthorizationFilter, phasePacketController::getPhasePacket, versionPolicy))
                .with(HttpMethod.GET, "/api/v1/phase/packet", secureVersioned(authenticationFilter, routeAuthorizationFilter, phasePacketController::getPhasePacketWithQuery, versionPolicy))

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
                    routeAuthorizationFilter.apply(request, authenticationFilter.secure(pageArtifactController::saveDocument)))
                .with(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/document", request ->
                    routeAuthorizationFilter.apply(request, authenticationFilter.secure(pageArtifactController::loadDocument)))
                .with(HttpMethod.POST, "/api/v1/page-artifacts/:artifactId/review-decisions", request ->
                    routeAuthorizationFilter.apply(request, authenticationFilter.secure(pageArtifactController::recordReviewDecision)))
                .with(HttpMethod.GET, "/api/v1/page-artifacts/:artifactId/operation-log/export", request ->
                    routeAuthorizationFilter.apply(request, authenticationFilter.secure(pageArtifactController::exportOperationLog)))

                .build();

        return routing;
    }

    private AsyncServlet secureVersioned(
            YappcAuthenticationFilter authenticationFilter,
            RouteAuthorizationFilter routeAuthorizationFilter,
            AsyncServlet delegate,
            ApiVersionPolicy versionPolicy) {
        AsyncServlet authorized = authenticationFilter.secure(request -> routeAuthorizationFilter.apply(request, delegate));
        return versionPolicy.apply(authorized);
    }

    @Provides
    YappcAuthenticationFilter yappcAuthenticationFilter(RouteAuthorizationRegistry routeAuthorizationRegistry) {
        return YappcAuthenticationFilter.fromEnvironment(null, routeAuthorizationRegistry);
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
