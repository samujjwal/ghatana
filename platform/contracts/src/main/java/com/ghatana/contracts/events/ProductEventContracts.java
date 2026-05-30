package com.ghatana.contracts.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Generic event contracts for products in the Ghatana platform.
 *
 * <p>These typed records define the wire format for events published by products
 * and consumed by Kernel, Data Cloud, and other products. They provide a generic
 * foundation that can be extended by specific products while maintaining platform
 * reusability.</p>
 *
 * <p>Consumers must tolerate unknown fields (use Jackson's {@code @JsonIgnoreProperties(ignoreUnknown = true)}).
 * Publishers must not remove or rename fields without a deprecation cycle.</p>
 *
 * <p>Schema version: {@code v1}</p>
 *
 * @doc.type class
 * @doc.purpose Generic typed event contracts for products
 * @doc.layer platform
 * @doc.pattern EventContract
 * @since 1.0.0
 */
public final class ProductEventContracts {

    private ProductEventContracts() {}

    // =========================================================================
    // Generic Product Lifecycle Events
    // =========================================================================

    /**
     * Published by products when transitioning between lifecycle phases.
     *
     * <p>This event enables cross-product coordination and evidence collection.
     * Kernel consumes this for lifecycle tracking and Data Cloud stores it as durable evidence.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product identifier (e.g., "phr", "data-cloud", "yappc")
     * @param phase           lifecycle phase (validate, test, build, package, deploy, verify)
     * @param status          phase status (started, completed, failed)
     * @param runId           lifecycle run identifier
     * @param environment     deployment environment (local, dev, staging, prod)
     * @param tenantId        tenant ID (optional for system-level events)
     * @param timestamp       event timestamp (UTC)
     * @param metadata        additional product-specific metadata
     */
    public record ProductLifecycleEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("phase") String phase,
            @JsonProperty("status") String status,
            @JsonProperty("runId") String runId,
            @JsonProperty("environment") String environment,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        public ProductLifecycleEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(metadata, "metadata");
        }

        /**
         * Creates a new product lifecycle event with the given parameters.
         */
        public static ProductLifecycleEvent create(
                String eventId,
                String correlationId,
                String productId,
                String phase,
                String status,
                String runId,
                String environment,
                String tenantId,
                Map<String, Object> metadata) {
            return new ProductLifecycleEvent(
                    "v1",
                    eventId,
                    correlationId,
                    productId,
                    phase,
                    status,
                    runId,
                    environment,
                    tenantId,
                    Instant.now(),
                    metadata != null ? metadata : Map.of()
            );
        }
    }

    /**
     * Published by products when significant state changes occur.
     *
     * <p>This event provides a generic mechanism for products to publish
     * state changes that need to be tracked across the platform.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product identifier
     * @param eventType       type of state change (e.g., "user_created", "config_updated")
     * @param entityId        affected entity ID
     * @param entityType      type of affected entity
     * @param previousState   previous state (optional)
     * @param newState        new state
     * @param tenantId        tenant ID (optional for system-level events)
     * @param timestamp       event timestamp (UTC)
     * @param metadata        additional product-specific metadata
     */
    public record ProductStateChangeEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("entityId") String entityId,
            @JsonProperty("entityType") String entityType,
            @JsonProperty("previousState") Map<String, Object> previousState,
            @JsonProperty("newState") Map<String, Object> newState,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        public ProductStateChangeEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(entityType, "entityType");
            Objects.requireNonNull(newState, "newState");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(metadata, "metadata");
        }

        /**
         * Creates a new product state change event with the given parameters.
         */
        public static ProductStateChangeEvent create(
                String eventId,
                String correlationId,
                String productId,
                String eventType,
                String entityId,
                String entityType,
                Map<String, Object> previousState,
                Map<String, Object> newState,
                String tenantId,
                Map<String, Object> metadata) {
            return new ProductStateChangeEvent(
                    "v1",
                    eventId,
                    correlationId,
                    productId,
                    eventType,
                    entityId,
                    entityType,
                    previousState,
                    newState,
                    tenantId,
                    Instant.now(),
                    metadata != null ? metadata : Map.of()
            );
        }
    }

    /**
     * Published by products when errors or failures occur.
     *
     * <p>This event provides a standardized way for products to report
     * errors that need to be tracked and potentially escalated.</p>
     *
     * @param schemaVersion   always {@code "v1"}
     * @param eventId         unique event ID (UUID)
     * @param correlationId   request-scoped correlation ID for trace linkage
     * @param productId       the product identifier
     * @param errorType       type of error (e.g., "validation", "system", "business")
     * @param errorCode       error code for programmatic handling
     * @param errorMessage    human-readable error message
     * @param severity        error severity (low, medium, high, critical)
     * @param entityId        affected entity ID (optional)
     * @param entityType      type of affected entity (optional)
     * @param stackTrace      error stack trace (optional, for debugging)
     * @param tenantId        tenant ID (optional for system-level events)
     * @param timestamp       event timestamp (UTC)
     * @param metadata        additional product-specific metadata
     */
    public record ProductErrorEvent(
            @JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("productId") String productId,
            @JsonProperty("errorType") String errorType,
            @JsonProperty("errorCode") String errorCode,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("severity") String severity,
            @JsonProperty("entityId") String entityId,
            @JsonProperty("entityType") String entityType,
            @JsonProperty("stackTrace") String stackTrace,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        public ProductErrorEvent {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(errorType, "errorType");
            Objects.requireNonNull(errorCode, "errorCode");
            Objects.requireNonNull(errorMessage, "errorMessage");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(metadata, "metadata");
        }

        /**
         * Creates a new product error event with the given parameters.
         */
        public static ProductErrorEvent create(
                String eventId,
                String correlationId,
                String productId,
                String errorType,
                String errorCode,
                String errorMessage,
                String severity,
                String entityId,
                String entityType,
                String stackTrace,
                String tenantId,
                Map<String, Object> metadata) {
            return new ProductErrorEvent(
                    "v1",
                    eventId,
                    correlationId,
                    productId,
                    errorType,
                    errorCode,
                    errorMessage,
                    severity,
                    entityId,
                    entityType,
                    stackTrace,
                    tenantId,
                    Instant.now(),
                    metadata != null ? metadata : Map.of()
            );
        }
    }
}
