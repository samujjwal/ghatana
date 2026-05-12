/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured user edits schema for generation review.
 * Stores user modifications to generated content with provenance and rollback metadata.
 *
 * This class uses Jackson ObjectMapper for serialization/deserialization of user edits.
 * All edits are stored as structured JSON for queryability and validation.
 *
 * @doc.type class
 * @doc.purpose Structured user edits schema for generation review with JSON serialization
 * @doc.layer product
 * @doc.pattern DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UserEdits {

    private static final Logger log = LoggerFactory.getLogger(UserEdits.class);
    private static final ObjectMapper objectMapper = createObjectMapper();

    private final String editsId;
    private final String generationRunId;
    private final String actorId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<FileEdit> fileEdits;
    private final EditMetadata metadata;

    private UserEdits(Builder builder) {
        this.editsId = builder.editsId;
        this.generationRunId = builder.generationRunId;
        this.actorId = builder.actorId;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.fileEdits = List.copyOf(builder.fileEdits);
        this.metadata = builder.metadata;
    }

    public String editsId() {
        return editsId;
    }

    public String generationRunId() {
        return generationRunId;
    }

    public String actorId() {
        return actorId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<FileEdit> fileEdits() {
        return fileEdits;
    }

    public EditMetadata metadata() {
        return metadata;
    }

    /**
     * Serializes user edits to JSON string using Jackson ObjectMapper.
     *
     * @return JSON string representation of user edits
     * @throws JsonProcessingException if serialization fails
     */
    public String toJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }

    /**
     * Deserializes user edits from JSON string using Jackson ObjectMapper.
     *
     * @param json JSON string to deserialize
     * @return UserEdits instance
     * @throws JsonProcessingException if deserialization fails
     */
    public static UserEdits fromJson(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, UserEdits.class);
    }

    /**
     * Creates and configures Jackson ObjectMapper for user edits serialization.
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        return mapper;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String editsId = UUID.randomUUID().toString();
        private String generationRunId;
        private String actorId;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private final List<FileEdit> fileEdits = new ArrayList<>();
        private EditMetadata metadata;

        public Builder editsId(String editsId) {
            this.editsId = editsId;
            return this;
        }

        public Builder generationRunId(String generationRunId) {
            this.generationRunId = generationRunId;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder addFileEdit(FileEdit fileEdit) {
            this.fileEdits.add(fileEdit);
            return this;
        }

        public Builder fileEdits(List<FileEdit> fileEdits) {
            this.fileEdits.clear();
            this.fileEdits.addAll(fileEdits);
            return this;
        }

        public Builder metadata(EditMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public UserEdits build() {
            if (generationRunId == null || generationRunId.isBlank()) {
                throw new IllegalArgumentException("generationRunId is required");
            }
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("actorId is required");
            }
            if (metadata == null) {
                metadata = new EditMetadata(null, Map.of());
            }
            return new UserEdits(this);
        }
    }

    /**
     * Represents edits to a single file.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileEdit(
            @JsonProperty("fileId") String fileId,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("edits") List<EditRegion> edits,
            @JsonProperty("originalContentHash") String originalContentHash,
            @JsonProperty("editedContentHash") String editedContentHash
    ) {
        public FileEdit {
            if (fileId == null || fileId.isBlank()) {
                throw new IllegalArgumentException("fileId is required");
            }
            if (filePath == null || filePath.isBlank()) {
                throw new IllegalArgumentException("filePath is required");
            }
            if (edits == null || edits.isEmpty()) {
                throw new IllegalArgumentException("edits cannot be null or empty");
            }
        }
    }

    /**
     * Represents a specific edit region within a file.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EditRegion(
            @JsonProperty("regionId") String regionId,
            @JsonProperty("startLine") int startLine,
            @JsonProperty("endLine") int endLine,
            @JsonProperty("originalContent") String originalContent,
            @JsonProperty("editedContent") String editedContent,
            @JsonProperty("editType") EditType editType,
            @JsonProperty("ownership") Ownership ownership,
            @JsonProperty("provenance") RegionProvenance provenance
    ) {
        public EditRegion {
            if (regionId == null || regionId.isBlank()) {
                throw new IllegalArgumentException("regionId is required");
            }
            if (startLine < 0) {
                throw new IllegalArgumentException("startLine must be >= 0");
            }
            if (endLine < startLine) {
                throw new IllegalArgumentException("endLine must be >= startLine");
            }
        }
    }

    /**
     * Type of edit performed.
     */
    public enum EditType {
        MODIFICATION,
        DELETION,
        INSERTION,
        REPLACEMENT
    }

    /**
     * Ownership of the edited content.
     */
    public enum Ownership {
        AI_GENERATED,
        USER_EDITED,
        SYSTEM_GENERATED,
        HYBRID
    }

    /**
     * Provenance information for an edit region.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegionProvenance(
            @JsonProperty("generatorVersion") String generatorVersion,
            @JsonProperty("sourcePromptHash") String sourcePromptHash,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("correlationId") String correlationId
    ) {}

    /**
     * Metadata for the entire user edits set.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EditMetadata(
            @JsonProperty("reviewDecisionId") String reviewDecisionId,
            @JsonProperty("additionalMetadata") Map<String, String> additionalMetadata
    ) {}
}
