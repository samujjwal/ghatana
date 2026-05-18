package com.ghatana.yappc.domain.artifact;

import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Shared source location DTO for precise positioning within source files
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 *
 * P1: Canonical source location representation used across all artifact DTOs.
 * Replaces nested SourceLocation records and map-based source locations.
 * Matches the SourceLocation message in artifact_compiler.proto.
 * P1: Added adapter methods for proto-generated classes compatibility.
 */
public record SourceLocationDto(
    String filePath,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn
) {
    public SourceLocationDto {
        Objects.requireNonNull(filePath, "filePath must not be null");
        if (startLine < 0) throw new IllegalArgumentException("startLine must be non-negative");
        if (startColumn < 0) throw new IllegalArgumentException("startColumn must be non-negative");
        if (endLine < 0) throw new IllegalArgumentException("endLine must be non-negative");
        if (endColumn < 0) throw new IllegalArgumentException("endColumn must be non-negative");
    }

    /**
     * P1: Create a SourceLocationDto from a map representation (for backward compatibility with ArtifactNodeDto).
     */
    public static SourceLocationDto fromMap(java.util.Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new SourceLocationDto(
            (String) map.get("filePath"),
            ((Number) map.getOrDefault("startLine", 0)).intValue(),
            ((Number) map.getOrDefault("startColumn", 0)).intValue(),
            ((Number) map.getOrDefault("endLine", 0)).intValue(),
            ((Number) map.getOrDefault("endColumn", 0)).intValue()
        );
    }

    /**
     * P1: Convert to map representation (for backward compatibility with ArtifactNodeDto).
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("filePath", filePath);
        map.put("startLine", startLine);
        map.put("startColumn", startColumn);
        map.put("endLine", endLine);
        map.put("endColumn", endColumn);
        return map;
    }

    /**
     * P1: Adapter method to convert from proto-generated SourceLocation to domain DTO.
     * Provides compatibility layer between proto contract and validated domain model.
     */
    public static SourceLocationDto fromProto(com.ghatana.yappc.artifact.grpc.SourceLocation proto) {
        return new SourceLocationDto(
            proto.getFilePath(),
            proto.getStartLine(),
            proto.getStartColumn(),
            proto.getEndLine(),
            proto.getEndColumn()
        );
    }

    /**
     * P1: Adapter method to convert domain DTO to proto-generated SourceLocation.
     * Provides compatibility layer between validated domain model and proto contract.
     */
    public com.ghatana.yappc.artifact.grpc.SourceLocation toProto() {
        return com.ghatana.yappc.artifact.grpc.SourceLocation.newBuilder()
            .setFilePath(filePath)
            .setStartLine(startLine)
            .setStartColumn(startColumn)
            .setEndLine(endLine)
            .setEndColumn(endColumn)
            .build();
    }
}
