package com.ghatana.tutorputor.explorer;

import com.ghatana.platform.http.HttpServer;
import com.ghatana.platform.http.AsyncServlet;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.platform.http.HttpResponse;
import com.ghatana.platform.http.HttpRequest;
import com.ghatana.platform.http.HttpStatus;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.ai.integration.AIService;
import com.ghatana.core.database.DatabaseService;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import io.activej.launcher.Launcher;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * @doc.type class
 * @doc.purpose Main Content Explorer launcher using ActiveJ framework
 * @doc.layer product
 * @doc.pattern Launcher
 */
public class ContentExplorerLauncher extends Launcher {
    private static final Logger logger = LoggerFactory.getLogger(ContentExplorerLauncher.class);
    
    @Provides
    Eventloop eventloop() {
        return Eventloop.create();
    }
    
    @Provides
    ContentExplorerService explorerService(DatabaseService dbService, AIService aiService, MetricsCollector metrics) {
        return new ContentExplorerService(dbService, aiService, metrics);
    }
    
    @Provides
    AsyncServlet mainServlet(ContentExplorerService explorerService) {
        return RoutingServlet.create()
            .map("/", request -> handleRoot(request, explorerService))
            .map("/health", request -> handleHealth(request, explorerService))
            .map("/api/explorer/status", request -> handleStatus(request, explorerService))
            .map("/api/explorer/config", request -> handleConfig(request, explorerService))
            .map("/api/explorer/discovery", request -> handleDiscovery(request, explorerService))
            .map("/api/explorer/generation", request -> handleGeneration(request, explorerService));
    }
    
    @Provides
    HttpServer httpServer(Eventloop eventloop, AsyncServlet servlet) {
        return HttpServer.create(eventloop, servlet)
            .withListenPort(8080);
    }
    
    @Override
    protected Module getModule() {
        return ModuleBuilder.create()
            .bind(ContentExplorerLauncher.class)
            .build();
    }
    
    @Override
    protected void run() throws Exception {
        logger.info("Starting Content Explorer with ActiveJ framework");
        logger.info("AEP Integration Mode: library");
        logger.info("Port: 8080");
        
        HttpServer server = getInstance(HttpServer.class);
        server.listen();
        
        logger.info("Content Explorer started successfully!");
        logger.info("Available endpoints:");
        logger.info("  Health: http://localhost:8080/health");
        logger.info("  Status: http://localhost:8080/api/explorer/status");
        logger.info("  Config: http://localhost:8080/api/explorer/config");
    }
    
    private Promise<HttpResponse> handleRoot(HttpRequest request, ContentExplorerService service) {
        Map<String, Object> response = Map.of(
            "service", "Content Explorer",
            "framework", "ActiveJ",
            "status", "running",
            "timestamp", Instant.now().toString(),
            "aepMode", "library",
            "version", "1.0.0"
        );
        
        return Promise.of(HttpResponse.of(HttpStatus.OK)
            .withJson(toJson(response)));
    }
    
    private Promise<HttpResponse> handleHealth(HttpRequest request, ContentExplorerService service) {
        return service.healthCheck()
            .map(health -> HttpResponse.of(HttpStatus.OK)
                .withJson(toJson(health)));
    }
    
    private Promise<HttpResponse> handleStatus(HttpRequest request, ContentExplorerService service) {
        return service.getStatus()
            .map(status -> HttpResponse.of(HttpStatus.OK)
                .withJson(toJson(status)));
    }
    
    private Promise<HttpResponse> handleConfig(HttpRequest request, ContentExplorerService service) {
        return service.getConfig()
            .map(config -> HttpResponse.of(HttpStatus.OK)
                .withJson(toJson(config)));
    }
    
    private Promise<HttpResponse> handleDiscovery(HttpRequest request, ContentExplorerService service) {
        return service.getDiscoveryStatus()
            .map(discovery -> HttpResponse.of(HttpStatus.OK)
                .withJson(toJson(discovery)));
    }
    
    private Promise<HttpResponse> handleGeneration(HttpRequest request, ContentExplorerService service) {
        return service.getGenerationStatus()
            .map(generation -> HttpResponse.of(HttpStatus.OK)
                .withJson(toJson(generation)));
    }
    
    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":");
            json.append(toJsonValue(entry.getValue()));
        }
        json.append("}");
        return json.toString();
    }
    
    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        return "\"" + value.toString().replace("\"", "\\\"") + "\"";
    }
    
    public static void main(String[] args) throws Exception {
        new ContentExplorerLauncher().run(args);
    }
}
