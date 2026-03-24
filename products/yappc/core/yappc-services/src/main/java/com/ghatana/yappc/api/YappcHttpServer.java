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
            IntentApiController intentController,
            ShapeApiController shapeController,
            ValidationApiController validationController,
            GenerationApiController generationController) {
        
        return RoutingServlet.builder(eventloop)
                // Health check
                .with(HttpMethod.GET, "/health", request -> 
                    Promise.of(HttpResponse.ok200().withPlainText("OK").build()))
                
                // Intent endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/intent/capture", intentController::captureIntent)
                .with(HttpMethod.POST, "/api/v1/yappc/intent/analyze", intentController::analyzeIntent)
                .with(HttpMethod.GET, "/api/v1/yappc/intent/:id", intentController::getIntent)
                
                // Shape endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/shape/derive", shapeController::deriveShape)
                .with(HttpMethod.POST, "/api/v1/yappc/shape/model", shapeController::generateSystemModel)
                .with(HttpMethod.GET, "/api/v1/yappc/shape/:id", shapeController::getShape)
                
                // Validation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/validate", validationController::validate)
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-config", validationController::validateWithConfig)
                .with(HttpMethod.POST, "/api/v1/yappc/validate/with-policy", validationController::validateWithPolicy)
                
                // Generation endpoints
                .with(HttpMethod.POST, "/api/v1/yappc/generate", generationController::generateArtifacts)
                .with(HttpMethod.POST, "/api/v1/yappc/generate/diff", generationController::regenerateWithDiff)
                .with(HttpMethod.GET, "/api/v1/yappc/generate/artifacts/:id", generationController::getArtifacts)
                
                // API info
                .with(HttpMethod.GET, "/api/v1/yappc/info", request ->
                    Promise.of(HttpResponse.ok200().withJson("""
                        {
                          "name": "YAPPC Lifecycle API",
                          "version": "1.0.0",
                          "phases": ["intent", "shape", "validate", "generate", "run", "observe", "learn", "evolve"]
                        }
                        """).build()))
                .build();
    }
    
    public static void main(String[] args) throws Exception {
        log.info("Starting YAPPC HTTP Server...");
        Launcher launcher = new YappcHttpServer();
        launcher.launch(args);
    }
}
