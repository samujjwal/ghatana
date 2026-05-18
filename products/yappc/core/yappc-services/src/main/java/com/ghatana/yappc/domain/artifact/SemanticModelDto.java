package com.ghatana.yappc.domain.artifact;

import java.time.Instant;
import java.util.HashMap;
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
 * P1: Enforces confidence range, uses provenance enum, and requires syntheticReason for synthetic provenance.
 * P1: Added adapter methods for proto-generated classes compatibility.
 */
public record SemanticModelDto(
    String id,
    String elementId,
    String elementType,
    String name,
    String qualifiedName,
    String filePath,
    SourceLocationDto sourceLocation,
    Map<String, Object> properties,
    List<String> dependencies,
    List<String> dependents,
    Double confidence,
    Boolean reviewRequired,
    String reviewReason,
    List<String> securityFlags,
    List<String> privacyFlags,
    List<String> graphNodeIds,
    List<String> residualIslandIds,
    String sourceRef,
    String symbolRef,
    String extractorId,
    String extractorVersion,
    String modelVersionId,
    String syntheticReason,
    Provenance provenance,
    Instant extractedAt,
    String snapshotId,
    String tenantId,
    String workspaceId,
    String projectId
) {
    /**
     * P1: Provenance enum for semantic model elements.
     */
    public enum Provenance {
        EXACT,
        INFERRED,
        SYNTHESIZED,
        MANUAL,
        ASSUMED
    }

    public SemanticModelDto {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(elementId, "elementId must not be null");
        Objects.requireNonNull(elementType, "elementType must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        Objects.requireNonNull(extractedAt, "extractedAt must not be null");
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        
        // P1: Validate confidence is in range [0.0, 1.0]
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be in range [0.0, 1.0], got: " + confidence);
        }
        
        // P1: Require syntheticReason when provenance is SYNTHESIZED
        if (provenance == Provenance.SYNTHESIZED && (syntheticReason == null || syntheticReason.isBlank())) {
            throw new IllegalArgumentException("syntheticReason is required when provenance is SYNTHESIZED");
        }
    }

    /**
     * P1: Source location for precise positioning within files.
     * Now using shared SourceLocationDto instead of nested record.
     */

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
        private SourceLocationDto sourceLocation;
        private Map<String, Object> properties;
        private List<String> dependencies;
        private List<String> dependents;
        private Double confidence;
        private Boolean reviewRequired;
        private String reviewReason;
        private List<String> securityFlags;
        private List<String> privacyFlags;
        private List<String> graphNodeIds;
        private List<String> residualIslandIds;
        private String sourceRef;
        private String symbolRef;
        private String extractorId;
        private String extractorVersion;
        private String modelVersionId;
        private String syntheticReason;
        private Provenance provenance;
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

        public Builder sourceLocation(SourceLocationDto sourceLocation) {
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

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reviewRequired(Boolean reviewRequired) {
            this.reviewRequired = reviewRequired;
            return this;
        }

        public Builder reviewReason(String reviewReason) {
            this.reviewReason = reviewReason;
            return this;
        }

        public Builder securityFlags(List<String> securityFlags) {
            this.securityFlags = securityFlags;
            return this;
        }

        public Builder privacyFlags(List<String> privacyFlags) {
            this.privacyFlags = privacyFlags;
            return this;
        }

        public Builder graphNodeIds(List<String> graphNodeIds) {
            this.graphNodeIds = graphNodeIds;
            return this;
        }

        public Builder residualIslandIds(List<String> residualIslandIds) {
            this.residualIslandIds = residualIslandIds;
            return this;
        }

        public Builder sourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
            return this;
        }

        public Builder symbolRef(String symbolRef) {
            this.symbolRef = symbolRef;
            return this;
        }

        public Builder extractorId(String extractorId) {
            this.extractorId = extractorId;
            return this;
        }

        public Builder extractorVersion(String extractorVersion) {
            this.extractorVersion = extractorVersion;
            return this;
        }

        public Builder modelVersionId(String modelVersionId) {
            this.modelVersionId = modelVersionId;
            return this;
        }

        public Builder syntheticReason(String syntheticReason) {
            this.syntheticReason = syntheticReason;
            return this;
        }

        public Builder provenance(Provenance provenance) {
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
                confidence,
                reviewRequired != null ? reviewRequired : false,
                reviewReason,
                securityFlags != null ? securityFlags : List.of(),
                privacyFlags != null ? privacyFlags : List.of(),
                graphNodeIds != null ? graphNodeIds : List.of(),
                residualIslandIds != null ? residualIslandIds : List.of(),
                sourceRef,
                symbolRef,
                extractorId,
                extractorVersion,
                modelVersionId,
                syntheticReason,
                provenance,
                extractedAt != null ? extractedAt : Instant.now(),
                snapshotId,
                tenantId,
                workspaceId,
                projectId
            );
        }
    }

    /**
     * P1: Adapter method to convert from proto-generated SemanticModel to domain DTO.
     * Provides compatibility layer between proto contract and validated domain model.
     */
    public static SemanticModelDto fromProto(com.ghatana.yappc.artifact.grpc.SemanticModel proto) {
        return new SemanticModelDto(
            proto.getId(),
            proto.getElementId(),
            proto.getElementType(),
            proto.getName(),
            proto.getQualifiedName(),
            proto.getFilePath(),
            proto.hasSourceLocation() ? SourceLocationDto.fromProto(proto.getSourceLocation()) : null,
            Map.copyOf(proto.getPropertiesMap()),
            List.copyOf(proto.getDependenciesList()),
            List.copyOf(proto.getDependentsList()),
            proto.getConfidence(),
            proto.getReviewRequired(),
            proto.getReviewReason(),
            List.copyOf(proto.getSecurityFlagsList()),
            List.copyOf(proto.getPrivacyFlagsList()),
            List.copyOf(proto.getGraphNodeIdsList()),
            List.copyOf(proto.getResidualIslandIdsList()),
            proto.getSourceRef(),
            proto.getSymbolRef(),
            proto.getExtractorId(),
            proto.getExtractorVersion(),
            proto.getModelVersionId(),
            proto.getSyntheticReason(),
            fromProtoProvenance(proto.getProvenance()),
            Instant.parse(proto.getExtractedAt()),
            proto.getSnapshotId(),
            proto.getTenantId(),
            proto.getWorkspaceId(),
            proto.getProjectId()
        );
    }

    /**
     * P1: Adapter method to convert domain DTO to proto-generated SemanticModel.
     * Provides compatibility layer between validated domain model and proto contract.
     */
    public com.ghatana.yappc.artifact.grpc.SemanticModel toProto() {
        com.ghatana.yappc.artifact.grpc.SemanticModel.Builder builder = com.ghatana.yappc.artifact.grpc.SemanticModel.newBuilder()
            .setId(id)
            .setElementId(elementId)
            .setElementType(elementType)
            .setName(name)
            .setQualifiedName(qualifiedName)
            .setFilePath(filePath)
            .putAllProperties(convertPropertiesToStringMap(properties))
            .addAllDependencies(dependencies != null ? dependencies : List.of())
            .addAllDependents(dependents != null ? dependents : List.of())
            .setConfidence(confidence != null ? confidence : 0.0)
            .setReviewRequired(reviewRequired != null ? reviewRequired : false)
            .setReviewReason(reviewReason)
            .addAllSecurityFlags(securityFlags != null ? securityFlags : List.of())
            .addAllPrivacyFlags(privacyFlags != null ? privacyFlags : List.of())
            .addAllGraphNodeIds(graphNodeIds != null ? graphNodeIds : List.of())
            .addAllResidualIslandIds(residualIslandIds != null ? residualIslandIds : List.of())
            .setSourceRef(sourceRef)
            .setSymbolRef(symbolRef)
            .setExtractorId(extractorId)
            .setExtractorVersion(extractorVersion)
            .setModelVersionId(modelVersionId)
            .setSyntheticReason(syntheticReason)
            .setProvenance(toProtoProvenance(provenance))
            .setExtractedAt(extractedAt.toString())
            .setSnapshotId(snapshotId)
            .setTenantId(tenantId)
            .setWorkspaceId(workspaceId)
            .setProjectId(projectId);

        if (sourceLocation != null) {
            builder.setSourceLocation(sourceLocation.toProto());
        }

        return builder.build();
    }

    /**
     * P1: Helper method to convert proto Provenance enum to domain Provenance enum.
     */
    private static Provenance fromProtoProvenance(com.ghatana.yappc.artifact.grpc.Provenance protoProvenance) {
        return switch (protoProvenance) {
            case EXACT -> Provenance.EXACT;
            case INFERRED -> Provenance.INFERRED;
            case SYNTHESIZED -> Provenance.SYNTHESIZED;
            case MANUAL -> Provenance.MANUAL;
            case ASSUMED -> Provenance.ASSUMED;
            case UNRECOGNIZED, PROVENANCE_UNSPECIFIED -> Provenance.ASSUMED;
        };
    }

    /**
     * P1: Helper method to convert domain Provenance enum to proto Provenance enum.
     */
    private static com.ghatana.yappc.artifact.grpc.Provenance toProtoProvenance(Provenance provenance) {
        return switch (provenance) {
            case EXACT -> com.ghatana.yappc.artifact.grpc.Provenance.EXACT;
            case INFERRED -> com.ghatana.yappc.artifact.grpc.Provenance.INFERRED;
            case SYNTHESIZED -> com.ghatana.yappc.artifact.grpc.Provenance.SYNTHESIZED;
            case MANUAL -> com.ghatana.yappc.artifact.grpc.Provenance.MANUAL;
            case ASSUMED -> com.ghatana.yappc.artifact.grpc.Provenance.ASSUMED;
        };
    }

    /**
     * P1: Helper method to convert Map<String,Object> to Map<String,String> for proto compatibility.
     */
    private static Map<String, String> convertPropertiesToStringMap(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
