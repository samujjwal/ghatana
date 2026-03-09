package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.promise.Promise;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * HTTP webhook egress strategy implementation.
 * Sends events to configured HTTP endpoints via POST/PUT.
 * 
 * @doc.type class
 * @doc.purpose HTTP webhook event delivery
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class HttpWebhookEgressStrategy {
    
    private final HttpIngressConfig config;
    private final Executor executor;
    private final String httpMethod;
    
    private final AtomicReference<HttpClient> httpClientRef = new AtomicReference<>();
    private final AtomicReference<EgressStatus> statusRef = new AtomicReference<>(EgressStatus.CREATED);
    
    public HttpWebhookEgressStrategy(HttpIngressConfig config, Executor executor) {
        this(config, executor, "POST");
    }
    
    public HttpWebhookEgressStrategy(HttpIngressConfig config, Executor executor, String httpMethod) {
        this.config = config;
        this.executor = executor;
        this.httpMethod = httpMethod;
    }
    
    public enum EgressStatus {
        CREATED,
        RUNNING,
        STOPPED,
        ERROR
    }
    
    public Promise<Void> start() {
        return Promise.ofBlocking(executor, () -> {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .executor(executor)
                .build();
            
            httpClientRef.set(client);
            statusRef.set(EgressStatus.RUNNING);
            return null;
        });
    }
    
    public Promise<Void> stop() {
        return Promise.ofBlocking(executor, () -> {
            httpClientRef.set(null);
            statusRef.set(EgressStatus.STOPPED);
            return null;
        });
    }
    
    public Promise<DeliveryResult> send(QueueMessage message) {
        return Promise.ofBlocking(executor, () -> {
            HttpClient client = httpClientRef.get();
            if (client == null) {
                throw new IllegalStateException("Egress strategy not started");
            }
            
            return sendWithRetry(client, message);
        });
    }
    
    public Promise<List<DeliveryResult>> sendBatch(List<QueueMessage> messages) {
        return Promise.ofBlocking(executor, () -> {
            HttpClient client = httpClientRef.get();
            if (client == null) {
                throw new IllegalStateException("Egress strategy not started");
            }
            
            return messages.stream()
                .map(msg -> sendWithRetry(client, msg))
                .toList();
        });
    }
    
    private DeliveryResult sendWithRetry(HttpClient client, QueueMessage message) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount <= config.getMaxRetries()) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpoint()))
                    .timeout(config.getTimeout());
                
                // Add auth headers
                config.getAuthHeaders().forEach(requestBuilder::header);
                requestBuilder.header("Content-Type", config.getContentType());
                
                // Add message metadata as headers
                if (message.metadata() != null) {
                    message.metadata().forEach((k, v) -> {
                        if (!k.equalsIgnoreCase("content-type") && 
                            !k.equalsIgnoreCase("authorization")) {
                            requestBuilder.header("X-Message-" + k, v);
                        }
                    });
                }
                
                // Add message ID header
                if (message.messageId() != null) {
                    requestBuilder.header("X-Message-Id", message.messageId());
                }
                
                // Set HTTP method
                HttpRequest.BodyPublisher bodyPublisher = 
                    HttpRequest.BodyPublishers.ofString(message.payload());
                
                if ("PUT".equalsIgnoreCase(httpMethod)) {
                    requestBuilder.PUT(bodyPublisher);
                } else {
                    requestBuilder.POST(bodyPublisher);
                }
                
                HttpRequest request = requestBuilder.build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return new DeliveryResult(
                        message.messageId(),
                        true,
                        response.statusCode(),
                        null
                    );
                } else if (response.statusCode() >= 500) {
                    // Server error, retry
                    lastException = new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                } else {
                    // Client error, don't retry
                    return new DeliveryResult(
                        message.messageId(),
                        false,
                        response.statusCode(),
                        "Client error: " + response.body()
                    );
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
                    break;
                }
            }
        }
        
        return new DeliveryResult(
            message.messageId(),
            false,
            -1,
            lastException != null ? lastException.getMessage() : "Max retries exceeded"
        );
    }
    
    public EgressStatus getStatus() {
        return statusRef.get();
    }
    
    /**
     * Result of a delivery attempt.
     */
    public record DeliveryResult(
        String messageId,
        boolean success,
        int statusCode,
        String errorMessage
    ) {}
}
