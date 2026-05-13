/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Negative knowledge representing known failure modes and anti-patterns to avoid.
 * Negative knowledge is used to prevent the agent from repeating mistakes.
 *
 * @doc.type record
 * @doc.purpose Negative knowledge for failure mode avoidance
 * @doc.layer framework
 * @doc.pattern Record
 * @doc.gaa.memory negative
 */
public record NegativeKnowledge(
        @NotNull String id,
        @NotNull String skillId,
        @NotNull String failureMode,
        @NotNull String description,
        @NotNull Instant timestamp,
        @NotNull String tenantId,
        @NotNull Map<String, String> metadata
) {
    public NegativeKnowledge {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");
        Objects.requireNonNull(failureMode, "failureMode must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a new negative knowledge item with the current timestamp.
     *
     * @param id unique identifier
     * @param skillId skill identifier
     * @param failureMode failure mode identifier
     * @param description description of the failure mode
     * @param tenantId tenant identifier
     * @param metadata additional metadata
     * @return new negative knowledge item
     */
    @NotNull
    public static NegativeKnowledge of(
            @NotNull String id,
            @NotNull String skillId,
            @NotNull String failureMode,
            @NotNull String description,
            @NotNull String tenantId,
            @NotNull Map<String, String> metadata) {
        return new NegativeKnowledge(id, skillId, failureMode, description, Instant.now(), tenantId, metadata);
    }

    /**
     * Creates a builder for constructing negative knowledge.
     *
     * @return new builder
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for NegativeKnowledge.
     */
    public static class Builder {
        private String id;
        private String skillId;
        private String failureMode;
        private String description;
        private Instant timestamp;
        private String tenantId;
        private Map<String, String> metadata = Map.of();

        public Builder id(@NotNull String id) {
            this.id = id;
            return this;
        }

        public Builder skillId(@NotNull String skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder failureMode(@NotNull String failureMode) {
            this.failureMode = failureMode;
            return this;
        }

        public Builder description(@NotNull String description) {
            this.description = description;
            return this;
        }

        public Builder timestamp(@NotNull Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder tenantId(@NotNull String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder metadata(@NotNull Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        @NotNull
        public NegativeKnowledge build() {
            return new NegativeKnowledge(
                    Objects.requireNonNull(id, "id must not be null"),
                    Objects.requireNonNull(skillId, "skillId must not be null"),
                    Objects.requireNonNull(failureMode, "failureMode must not be null"),
                    Objects.requireNonNull(description, "description must not be null"),
                    timestamp != null ? timestamp : Instant.now(),
                    Objects.requireNonNull(tenantId, "tenantId must not be null"),
                    metadata != null ? metadata : Map.of()
            );
        }
    }
}
