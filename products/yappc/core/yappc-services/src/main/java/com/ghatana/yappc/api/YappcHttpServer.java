package com.ghatana.yappc.api;

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
            LifecycleApiController lifecycleController) {

        ApiVersionPolicy versionPolicy = new ApiVersionPolicy();

        return RoutingServlet.builder(eventloop)
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
                .build();
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

    public static void main(String[] args) throws Exception {
        log.info("Starting YAPPC HTTP Server...");
        Launcher launcher = new YappcHttpServer();
        launcher.launch(args);
    }
}
