/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.brain;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Context for brain processing operations.
 *
 * <p>BrainContext carries request-scoped information through the
 * processing pipeline, including tenant information, operation
 * settings, and tracking data.
 *
 * @doc.type record
 * @doc.purpose Processing context
 * @doc.layer core
 * @doc.pattern Context Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class BrainContext {

    /**
     * Unique identifier for this processing context.
     */
    String contextId;

    /**
     * The tenant ID.
     */
    String tenantId;

    /**
     * The user ID (if authenticated).
     */
    String userId;

    /**
     * Session ID for grouping related operations.
     */
    String sessionId;

    /**
     * Correlation ID for distributed tracing.
     */
    String correlationId;

    /**
     * When this context was created.
     */
    @Builder.Default
    Instant createdAt = Instant.now();

    /**
     * Request deadline (if any).
     */
    Instant deadline;

    /**
     * Processing priority (1 = highest, 10 = lowest).
     */
    @Builder.Default
    int priority = 5;

    /**
     * Source of the request.
     */
    @Builder.Default
    String source = "api";

    /**
     * Operation being performed.
     */
    String operation;

    /**
     * Processing mode.
     */
    @Builder.Default
    ProcessingMode mode = ProcessingMode.NORMAL;

    /**
     * Whether to enable learning in this context.
     */
    @Builder.Default
    boolean learningEnabled = true;

    /**
     * Whether to enable reflexes in this context.
     */
    @Builder.Default
    boolean reflexesEnabled = true;

    /**
     * Whether to return detailed results.
     */
    @Builder.Default
    boolean detailedResults = false;

    /**
     * Whether this is a dry run (no side effects).
     */
    @Builder.Default
    boolean dryRun = false;

    /**
     * Additional context attributes.
     */
    @Builder.Default
    Map<String, Object> attributes = Map.of();

    /**
     * Processing modes.
     */
    public enum ProcessingMode {
        /**
         * Normal processing with all features.
         */
        NORMAL,

        /**
         * Fast path with reflexes only.
         */
        FAST,

        /**
         * Deliberate processing with full analysis.
         */
        DELIBERATE,

        /**
         * Batch processing optimized for throughput.
         */
        BATCH,

        /**
         * Debug mode with extra logging.
         */
        DEBUG
    }

    /**
     * Creates a default context for a tenant.
     *
     * @param tenantId the tenant ID
     * @return new context
     */
    public static BrainContext forTenant(String tenantId) {
        return BrainContext.builder()
                .contextId("ctx-" + System.nanoTime())
                .tenantId(tenantId)
                .build();
    }

    /**
     * Creates a context for batch processing.
     *
     * @param tenantId the tenant ID
     * @param batchId batch identifier
     * @return batch context
     */
    public static BrainContext forBatch(String tenantId, String batchId) {
        return BrainContext.builder()
                .contextId("ctx-batch-" + batchId)
                .tenantId(tenantId)
                .sessionId(batchId)
                .mode(ProcessingMode.BATCH)
                .build();
    }

    /**
     * Creates a context for debugging.
     *
     * @param tenantId the tenant ID
     * @return debug context
     */
    public static BrainContext debug(String tenantId) {
        return BrainContext.builder()
                .contextId("ctx-debug-" + System.nanoTime())
                .tenantId(tenantId)
                .mode(ProcessingMode.DEBUG)
                .detailedResults(true)
                .build();
    }

    /**
     * Checks if the context has exceeded its deadline.
     *
     * @return true if deadline exceeded
     */
    public boolean isExpired() {
        return deadline != null && Instant.now().isAfter(deadline);
    }

    /**
     * Gets an attribute value.
     *
     * @param key the attribute key
     * @param <T> the expected type
     * @return the attribute value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Gets an attribute with default value.
     *
     * @param key the attribute key
     * @param defaultValue the default value
     * @param <T> the expected type
     * @return the attribute value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Creates a child context with the same tenant and correlation.
     *
     * @param operation the child operation
     * @return child context
     */
    public BrainContext child(String operation) {
        return this.toBuilder()
                .contextId("ctx-" + System.nanoTime())
                .operation(operation)
                .createdAt(Instant.now())
                .build();
    }
}
