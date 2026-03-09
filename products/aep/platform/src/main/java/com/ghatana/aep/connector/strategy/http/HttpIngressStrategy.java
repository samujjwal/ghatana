package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.strategy.QueueMessage;
import io.activej.promise.Promise;

import java.util.List;
import java.util.function.Consumer;

/**
 * HTTP ingress strategy for receiving events via HTTP webhooks and polling.
 * 
 * @doc.type interface
 * @doc.purpose HTTP event ingestion strategy
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public interface HttpIngressStrategy {
    
    /**
     * Start the HTTP ingress (server or polling).
     */
    Promise<Void> start();
    
    /**
     * Stop the HTTP ingress.
     */
    Promise<Void> stop();
    
    /**
     * Register a handler for incoming messages.
     */
    void onMessage(Consumer<QueueMessage> handler);
    
    /**
     * Get current status.
     */
    IngressStatus getStatus();
    
    /**
     * Authentication types supported by HTTP ingress.
     */
    enum AuthType {
        NONE,
        BASIC,
        BEARER,
        API_KEY,
        HMAC_SIGNATURE,
        OAUTH2
    }
    
    /**
     * Ingress status.
     */
    enum IngressStatus {
        CREATED,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        ERROR
    }
}
