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

package com.ghatana.datacloud.client;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context object passed through the AI aspect pipeline.
 *
 * <p>Carries request metadata, tenant information, and shared state
 * between aspects. Aspects can read and write attributes to share
 * computed values.
 *
 * <h2>Thread Safety</h2>
 * <p>Context is designed to be thread-safe. Multiple aspects can
 * read/write attributes concurrently.
 *
 * @see AIAspect
 * @see AIAspectPipeline
 * @doc.type class
 * @doc.purpose AI aspect execution context
 * @doc.layer core
 * @doc.pattern Context Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class AIAspectContext {

    private final String requestId;
    private final String tenantId;
    private final AIAspect.OperationType operationType;
    private final Instant startTime;
    private final Map<String, Object> attributes;
    private final Map<String, AIAspectResult> aspectResults;

    private AIAspectContext(Builder builder) {
        this.requestId = builder.requestId;
        this.tenantId = builder.tenantId;
        this.operationType = builder.operationType;
        this.startTime = Instant.now();
        this.attributes = new ConcurrentHashMap<>(builder.attributes);
        this.aspectResults = new ConcurrentHashMap<>();
    }

    /**
     * Returns the unique request ID.
     *
     * @return request ID
     */
    public String requestId() {
        return requestId;
    }

    /**
     * Returns the tenant ID for multi-tenancy.
     *
     * @return tenant ID
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Returns the operation type.
     *
     * @return operation type
     */
    public AIAspect.OperationType operationType() {
        return operationType;
    }

    /**
     * Returns the request start time.
     *
     * @return start timestamp
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * Gets an attribute by key.
     *
     * @param key attribute key
     * @param <T> expected type
     * @return optional containing the value if present
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(String key) {
        return Optional.ofNullable((T) attributes.get(key));
    }

    /**
     * Gets an attribute with a default value.
     *
     * @param key attribute key
     * @param defaultValue default if not present
     * @param <T> expected type
     * @return the value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Sets an attribute.
     *
     * @param key attribute key
     * @param value attribute value
     * @return this context for chaining
     */
    public AIAspectContext setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /**
     * Removes an attribute.
     *
     * @param key attribute key
     * @return this context for chaining
     */
    public AIAspectContext removeAttribute(String key) {
        attributes.remove(key);
        return this;
    }

    /**
     * Records the result of an aspect execution.
     *
     * @param aspectName aspect name
     * @param result aspect result
     */
    public void recordResult(String aspectName, AIAspectResult result) {
        aspectResults.put(aspectName, result);
    }

    /**
     * Gets the result of a previously executed aspect.
     *
     * @param aspectName aspect name
     * @return optional containing the result
     */
    public Optional<AIAspectResult> getResult(String aspectName) {
        return Optional.ofNullable(aspectResults.get(aspectName));
    }

    /**
     * Returns all aspect results.
     *
     * @return unmodifiable map of results
     */
    public Map<String, AIAspectResult> allResults() {
        return Map.copyOf(aspectResults);
    }

    /**
     * Returns elapsed time since start in milliseconds.
     *
     * @return elapsed milliseconds
     */
    public long elapsedMillis() {
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AIAspectContext.
     */
    public static final class Builder {
        private String requestId;
        private String tenantId;
        private AIAspect.OperationType operationType = AIAspect.OperationType.READ;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder operationType(AIAspect.OperationType operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public AIAspectContext build() {
            if (requestId == null) {
                requestId = java.util.UUID.randomUUID().toString();
            }
            if (tenantId == null) {
                tenantId = "default";
            }
            return new AIAspectContext(this);
        }
    }

    /**
     * Result of an aspect execution.
     *
     * @param aspectName name of the aspect
     * @param success whether execution succeeded
     * @param output the output value (if any)
     * @param error error message (if failed)
     * @param durationMs execution duration in milliseconds
     */
    public record AIAspectResult(
            String aspectName,
            boolean success,
            Object output,
            String error,
            long durationMs
    ) {
        /**
         * Creates a successful result.
         *
         * @param aspectName aspect name
         * @param output output value
         * @param durationMs duration
         * @return success result
         */
        public static AIAspectResult success(String aspectName, Object output, long durationMs) {
            return new AIAspectResult(aspectName, true, output, null, durationMs);
        }

        /**
         * Creates a failure result.
         *
         * @param aspectName aspect name
         * @param error error message
         * @param durationMs duration
         * @return failure result
         */
        public static AIAspectResult failure(String aspectName, String error, long durationMs) {
            return new AIAspectResult(aspectName, false, null, error, durationMs);
        }
    }
}
