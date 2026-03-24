package com.ghatana.yappc.client.impl;

import com.ghatana.yappc.client.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Remote implementation of YAPPCClient that connects to a YAPPC server via HTTP/gRPC.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles remote yappc client operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class RemoteYAPPCClient implements YAPPCClient {
    
    private static final Logger logger = LoggerFactory.getLogger(RemoteYAPPCClient.class);
    
    private final String serverUrl;
    private final YAPPCConfig config;
    private final ClientOptions options;
    private final HttpClient httpClient;
    private volatile boolean started = false;
    
    public RemoteYAPPCClient(String serverUrl, YAPPCConfig config, ClientOptions options) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.config = config;
        this.options = options;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(options.getTimeout()))
            .build();
    }
    
    @Override
    public Promise<Void> start() {
        return Promise.ofCallback(cb -> {
            if (started) {
                cb.set(null);
                return;
            }
            
            logger.info("Starting remote YAPPC client for server: {}", serverUrl);
            
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/health"))
                    .timeout(Duration.ofMillis(options.getTimeout()))
                    .GET()
                    .build();
                
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            started = true;
                            logger.info("Remote YAPPC client connected successfully");
                            cb.set(null);
                        } else {
                            cb.setException(new RuntimeException(
                                "Server health check failed: " + response.statusCode()));
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Failed to connect to YAPPC server", e);
                        cb.setException(e instanceof Exception ex ? ex : new RuntimeException(e));
                        return null;
                    });
            } catch (Exception e) {
                logger.error("Failed to start remote YAPPC client", e);
                cb.setException(e);
            }
        });
    }
    
    @Override
    public Promise<Void> stop() {
        return Promise.ofCallback(cb -> {
            if (!started) {
                cb.set(null);
                return;
            }
            
            logger.info("Stopping remote YAPPC client");
            started = false;
            cb.set(null);
        });
    }
    
    @Override
    public Promise<TaskRegistrationResult> registerTask(TaskDefinition task) {
        return executeRequest(
            "/api/tasks/register",
            task,
            TaskRegistrationResult.class
        );
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <R> Promise<TaskResult<R>> executeTask(String taskId, Object request, TaskContext context) {
        return (Promise) executeRequest(
            "/api/tasks/" + taskId + "/execute",
            Map.of("request", request, "context", context),
            TaskResult.class
        );
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Promise<List<TaskDefinition>> listTasks() {
        return (Promise) executeRequest(
            "/api/tasks",
            null,
            List.class
        );
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <I, O> Promise<StepResult<O>> invokeAgent(String phase, String stepName, I input, StepContext context) {
        return (Promise) executeRequest(
            "/api/agents/" + phase + "/" + stepName,
            Map.of("input", input, "context", context),
            StepResult.class
        );
    }
    
    @Override
    public Promise<CanvasResult> createCanvas(CreateCanvasRequest request) {
        return executeRequest(
            "/api/canvas/create",
            request,
            CanvasResult.class
        );
    }
    
    @Override
    public Promise<ValidationReport> validateCanvas(String canvasId, ValidationContext context) {
        return executeRequest(
            "/api/canvas/" + canvasId + "/validate",
            context,
            ValidationReport.class
        );
    }
    
    @Override
    public Promise<GenerationResult> generateFromCanvas(String canvasId, GenerationOptions options) {
        return executeRequest(
            "/api/canvas/" + canvasId + "/generate",
            options,
            GenerationResult.class
        );
    }
    
    @Override
    public Promise<SearchResults> searchKnowledge(KnowledgeQuery query) {
        return executeRequest(
            "/api/knowledge/search",
            query,
            SearchResults.class
        );
    }
    
    @Override
    public Promise<Void> ingestKnowledge(KnowledgeDocument document) {
        return executeRequest(
            "/api/knowledge/ingest",
            document,
            Void.class
        );
    }
    
    @Override
    public Promise<LifecycleState> getLifecycleState(String projectId) {
        return executeRequest(
            "/api/lifecycle/" + projectId,
            null,
            LifecycleState.class
        );
    }
    
    @Override
    public Promise<PhaseResult> advancePhase(String projectId, AdvancePhaseRequest request) {
        return executeRequest(
            "/api/lifecycle/" + projectId + "/advance",
            request,
            PhaseResult.class
        );
    }
    
    @Override
    public Promise<HealthStatus> checkHealth() {
        return executeRequest(
            "/health",
            null,
            HealthStatus.class
        );
    }
    
    @Override
    public Promise<YAPPCConfig> getConfiguration() {
        return Promise.of(config);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Promise<Map<String, Object>> getMetrics() {
        return (Promise) executeRequest(
            "/metrics",
            null,
            Map.class
        );
    }
    
    @Override
    public void close() {
        if (started) {
            logger.info("Stopping remote YAPPC client");
            started = false;
        }
    }
    
    private <T> Promise<T> executeRequest(String path, Object body, Class<T> responseType) {
        return Promise.ofCallback(cb -> {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .timeout(Duration.ofMillis(options.getTimeout()));
                
                if (body != null) {
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                        serializeToJson(body)));
                    requestBuilder.header("Content-Type", "application/json");
                } else {
                    requestBuilder.GET();
                }
                
                HttpRequest request = requestBuilder.build();
                
                executeWithRetry(request, 0)
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            T result = deserializeFromJson(response.body(), responseType);
                            cb.set(result);
                        } else {
                            cb.setException(new RuntimeException(
                                "Request failed with status: " + response.statusCode()));
                        }
                    })
                    .exceptionally(e -> {
                        logger.error("Request failed: {}", path, e);
                        cb.setException(e instanceof Exception ex ? ex : new RuntimeException(e));
                        return null;
                    });
            } catch (Exception e) {
                logger.error("Failed to execute request: {}", path, e);
                cb.setException(e);
            }
        });
    }
    
    private java.util.concurrent.CompletableFuture<HttpResponse<String>> executeWithRetry(
            HttpRequest request, int attemptNumber) {
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> {
                if (response.statusCode() >= 500 && attemptNumber < options.getMaxRetries()) {
                    logger.warn("Request failed with status {}, retrying (attempt {}/{})",
                        response.statusCode(), attemptNumber + 1, options.getMaxRetries());
                    
                    return java.util.concurrent.CompletableFuture
                        .supplyAsync(() -> (Void) null,
                            java.util.concurrent.CompletableFuture.delayedExecutor(
                                1, java.util.concurrent.TimeUnit.SECONDS))
                        .thenCompose(v -> executeWithRetry(request, attemptNumber + 1));
                }
                return java.util.concurrent.CompletableFuture.completedFuture(response);
            });
    }
    
    private String serializeToJson(Object obj) {
        return "{}";
    }
    
    @SuppressWarnings("unchecked")
    private <T> T deserializeFromJson(String json, Class<T> type) {
        if (type == Void.class) {
            return null;
        }
        return (T) new Object();
    }
}
