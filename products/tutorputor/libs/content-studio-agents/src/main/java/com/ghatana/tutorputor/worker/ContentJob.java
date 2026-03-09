package com.ghatana.tutorputor.worker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable content job representation.
 * 
 * <p>Represents a unit of work in the content generation pipeline.
 * Jobs are immutable and use a builder pattern for creation.
 *
 * @doc.type record
 * @doc.purpose Content generation job data
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ContentJob(
    @Nullable String id,
    @NotNull JobType type,
    @NotNull String tenantId,
    @NotNull String requesterId,
    int priority,
    @NotNull Map<String, Object> payload,
    int attempts,
    @Nullable String error,
    @Nullable Object result,
    @NotNull Instant createdAt,
    @Nullable Instant completedAt,
    @Nullable Map<String, String> metadata
) {
    
    /**
     * Job types for content generation.
     */
    public enum JobType {
        /** Generate claims for a topic */
        GENERATE_CLAIMS,
        /** Generate examples for a claim */
        GENERATE_EXAMPLES,
        /** Generate interactive simulation */
        GENERATE_SIMULATION,
        /** Validate generated content */
        VALIDATE_CONTENT,
        /** Enhance existing content */
        ENHANCE_CONTENT,
        /** Batch generation of multiple items */
        BATCH_GENERATION
    }

    /**
     * Creates a new job builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy with the given ID.
     *
     * @param id the new ID
     * @return a new job with the ID set
     */
    public ContentJob withId(String id) {
        return new ContentJob(id, type, tenantId, requesterId, priority, 
            payload, attempts, error, result, createdAt, completedAt, metadata);
    }

    /**
     * Returns a copy with the given attempts count.
     *
     * @param attempts the new attempts count
     * @return a new job with attempts updated
     */
    public ContentJob withAttempts(int attempts) {
        return new ContentJob(id, type, tenantId, requesterId, priority, 
            payload, attempts, error, result, createdAt, completedAt, metadata);
    }

    /**
     * Returns a copy with the given error message.
     *
     * @param error the error message
     * @return a new job with error set
     */
    public ContentJob withError(String error) {
        return new ContentJob(id, type, tenantId, requesterId, priority, 
            payload, attempts, error, result, createdAt, completedAt, metadata);
    }

    /**
     * Returns a copy with the given result.
     *
     * @param result the result
     * @return a new job with result set
     */
    public ContentJob withResult(Object result) {
        return new ContentJob(id, type, tenantId, requesterId, priority, 
            payload, attempts, error, result, createdAt, completedAt, metadata);
    }

    /**
     * Returns a copy with the given completion time.
     *
     * @param completedAt the completion time
     * @return a new job with completedAt set
     */
    public ContentJob withCompletedAt(Instant completedAt) {
        return new ContentJob(id, type, tenantId, requesterId, priority, 
            payload, attempts, error, result, createdAt, completedAt, metadata);
    }

    /**
     * Builder for ContentJob.
     */
    public static final class Builder {
        private String id;
        private JobType type;
        private String tenantId;
        private String requesterId;
        private int priority = 5; // Default medium priority
        private Map<String, Object> payload = Map.of();
        private int attempts = 0;
        private String error;
        private Object result;
        private Instant createdAt = Instant.now();
        private Instant completedAt;
        private Map<String, String> metadata = Map.of();

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(JobType type) {
            this.type = type;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder requesterId(String requesterId) {
            this.requesterId = requesterId;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets priority to high (10).
         *
         * @return this builder
         */
        public Builder highPriority() {
            this.priority = 10;
            return this;
        }

        /**
         * Sets priority to low (1).
         *
         * @return this builder
         */
        public Builder lowPriority() {
            this.priority = 1;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ContentJob build() {
            if (type == null) {
                throw new IllegalStateException("Job type is required");
            }
            if (tenantId == null) {
                throw new IllegalStateException("Tenant ID is required");
            }
            if (requesterId == null) {
                throw new IllegalStateException("Requester ID is required");
            }
            
            String finalId = id != null ? id : UUID.randomUUID().toString();
            
            return new ContentJob(finalId, type, tenantId, requesterId, priority,
                payload, attempts, error, result, createdAt, completedAt, metadata);
        }
    }
}
