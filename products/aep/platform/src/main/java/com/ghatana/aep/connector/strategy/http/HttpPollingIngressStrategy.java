package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.promise.Promise;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * HTTP polling ingress strategy implementation.
 * Polls an HTTP endpoint at regular intervals to retrieve events.
 * 
 * @doc.type class
 * @doc.purpose HTTP polling event ingestion
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class HttpPollingIngressStrategy implements HttpIngressStrategy {
    
    private final HttpIngressConfig config;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Duration pollInterval;
    private final ResponseParser responseParser;
    
    private final AtomicReference<HttpClient> httpClientRef = new AtomicReference<>();
    private final AtomicReference<IngressStatus> statusRef = new AtomicReference<>(IngressStatus.CREATED);
    private final List<Consumer<QueueMessage>> handlers = new CopyOnWriteArrayList<>();
    private volatile ScheduledFuture<?> pollTask;
    
    public HttpPollingIngressStrategy(
            HttpIngressConfig config, 
            Duration pollInterval,
            ResponseParser responseParser,
            Executor executor,
            ScheduledExecutorService scheduler) {
        this.config = config;
        this.pollInterval = pollInterval;
        this.responseParser = responseParser;
        this.executor = executor;
        this.scheduler = scheduler;
    }
    
    /**
     * Interface for parsing HTTP responses into QueueMessages.
     */
    @FunctionalInterface
    public interface ResponseParser {
        List<QueueMessage> parse(String responseBody, Map<String, String> headers);
    }
    
    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(executor, () -> {
            statusRef.set(IngressStatus.STARTING);
            
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .executor(executor);
            
            httpClientRef.set(clientBuilder.build());
            
            // Start polling
            pollTask = scheduler.scheduleWithFixedDelay(
                this::poll,
                0,
                pollInterval.toMillis(),
                TimeUnit.MILLISECONDS
            );
            
            statusRef.set(IngressStatus.RUNNING);
            return null;
        });
    }
    
    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(executor, () -> {
            statusRef.set(IngressStatus.STOPPING);
            
            if (pollTask != null) {
                pollTask.cancel(false);
            }
            
            // HttpClient doesn't have a close method in Java 11+
            httpClientRef.set(null);
            
            statusRef.set(IngressStatus.STOPPED);
            return null;
        });
    }
    
    @Override
    public void onMessage(Consumer<QueueMessage> handler) {
        handlers.add(handler);
    }
    
    @Override
    public IngressStatus getStatus() {
        return statusRef.get();
    }
    
    private void poll() {
        HttpClient client = httpClientRef.get();
        if (client == null || statusRef.get() != IngressStatus.RUNNING) {
            return;
        }
        
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount <= config.getMaxRetries()) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpoint()))
                    .timeout(config.getTimeout())
                    .GET();
                
                // Add auth headers
                config.getAuthHeaders().forEach(requestBuilder::header);
                requestBuilder.header("Accept", config.getContentType());
                
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    // Extract headers
                    Map<String, String> responseHeaders = new HashMap<>();
                    response.headers().map().forEach((k, v) -> {
                        if (!v.isEmpty()) {
                            responseHeaders.put(k, v.get(0));
                        }
                    });
                    
                    // Parse response into messages
                    List<QueueMessage> messages = responseParser.parse(response.body(), responseHeaders);
                    
                    // Dispatch to handlers
                    for (QueueMessage message : messages) {
                        for (Consumer<QueueMessage> handler : handlers) {
                            try {
                                handler.accept(message);
                            } catch (Exception e) {
                                // Log and continue
                            }
                        }
                    }
                    return; // Success, exit retry loop
                    
                } else if (response.statusCode() >= 500) {
                    // Server error, retry
                    lastException = new RuntimeException("HTTP " + response.statusCode());
                } else {
                    // Client error, don't retry
                    statusRef.set(IngressStatus.ERROR);
                    return;
                }
                
            } catch (Exception e) {
                lastException = e;
            }
            
            retryCount++;
            if (retryCount <= config.getMaxRetries()) {
                try {
                    Thread.sleep(config.getRetryBackoff().toMillis() * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        // All retries exhausted
        // In production, log lastException
    }
    
    /**
     * Factory method for JSON array parser (each element is a message).
     */
    public static ResponseParser jsonArrayParser() {
        return (body, headers) -> {
            List<QueueMessage> messages = new ArrayList<>();
            // Simple JSON array parsing - in production use Jackson/Gson
            if (body != null && body.trim().startsWith("[")) {
                // For simplicity, treat entire body as single message
                // In production, properly parse JSON array
                Map<String, String> metadata = new HashMap<>();
                metadata.put("timestamp", Instant.now().toString());
                metadata.put("source", "http-polling");
                messages.add(new QueueMessage(
                    UUID.randomUUID().toString(),
                    body,
                    metadata
                ));
            }
            return messages;
        };
    }
    
    /**
     * Factory method for single JSON object parser.
     */
    public static ResponseParser jsonObjectParser() {
        return (body, headers) -> {
            List<QueueMessage> messages = new ArrayList<>();
            if (body != null && !body.trim().isEmpty()) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("timestamp", Instant.now().toString());
                metadata.put("source", "http-polling");
                headers.forEach((k, v) -> {
                    if (k.toLowerCase().startsWith("x-")) {
                        metadata.put(k, v);
                    }
                });
                messages.add(new QueueMessage(
                    UUID.randomUUID().toString(),
                    body,
                    metadata
                ));
            }
            return messages;
        };
    }
}
