package com.ghatana.yappc.domain.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose DTO for semantic model elements extracted from source code with full metadata for compile-back operations
 * @doc.layer domain
 * @doc.pattern DTO
 *
 * P3: Semantic model DTO for persisting high-level semantic understanding of code artifacts.
 * Used in compile-back operations to provide context for change planning and patch generation.
 */
public record SemanticModelDto(
    String id,
    String elementId,
    String elementType,
    String name,
    String qualifiedName,
    String filePath,
    SourceLocation sourceLocation,
    Map<String, Object> properties,
    List<String> dependencies,
    List<String> dependents,
    String provenance,
    Instant extractedAt,
    String snapshotId,
    String tenantId,
    String workspaceId,
    String projectId
) {
    public SemanticModelDto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(elementId, "elementId must not be null");
        Objects.requireNonNull(elementType, "elementType must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        Objects.requireNonNull(extractedAt, "extractedAt must not be null");
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
    }

    /**
     * Source location for precise positioning within files.
     */
    public record SourceLocation(
        String filePath,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn
    ) {
        public SourceLocation {
            Objects.requireNonNull(filePath, "filePath must not be null");
            if (startLine < 0) throw new IllegalArgumentException("startLine must be non-negative");
            if (startColumn < 0) throw new IllegalArgumentException("startColumn must be non-negative");
            if (endLine < 0) throw new IllegalArgumentException("endLine must be non-negative");
            if (endColumn < 0) throw new IllegalArgumentException("endColumn must be non-negative");
        }
    }

    /**
     * Builder for SemanticModelDto.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String elementId;
        private String elementType;
        private String name;
        private String qualifiedName;
        private String filePath;
        private SourceLocation sourceLocation;
        private Map<String, Object> properties;
        private List<String> dependencies;
        private List<String> dependents;
        private String provenance;
        private Instant extractedAt;
        private String snapshotId;
        private String tenantId;
        private String workspaceId;
        private String projectId;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder elementId(String elementId) {
            this.elementId = elementId;
            return this;
        }

        public Builder elementType(String elementType) {
            this.elementType = elementType;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder qualifiedName(String qualifiedName) {
            this.qualifiedName = qualifiedName;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public Builder dependents(List<String> dependents) {
            this.dependents = dependents;
            return this;
        }

        public Builder provenance(String provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder extractedAt(Instant extractedAt) {
            this.extractedAt = extractedAt;
            return this;
        }

        public Builder snapshotId(String snapshotId) {
            this.snapshotId = snapshotId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public SemanticModelDto build() {
            return new SemanticModelDto(
                id != null ? id : java.util.UUID.randomUUID().toString(),
                elementId,
                elementType,
                name,
                qualifiedName,
                filePath,
                sourceLocation,
                properties != null ? properties : Map.of(),
                dependencies != null ? dependencies : List.of(),
                dependents != null ? dependents : List.of(),
                provenance,
                extractedAt != null ? extractedAt : Instant.now(),
                snapshotId,
                tenantId,
                workspaceId,
                projectId
            );
        }
    }
}
