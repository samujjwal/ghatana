package com.ghatana.digitalmarketing.domain.segment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Audience segment definition owned by a DMOS workspace.
 *
 * @doc.type class
 * @doc.purpose Represents an audience segment and its consent-aware membership criteria
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AudienceSegment(
    String segmentId,
    String workspaceId,
    String name,
    String description,
    List<String> includedTags,
    List<String> excludedTags,
    List<String> includedSegments,
    String customExpression,
    int memberCount,
    Instant createdAt,
    Instant updatedAt
) {
    public AudienceSegment {
        segmentId = requireNonBlank(segmentId, "segmentId");
        workspaceId = requireNonBlank(workspaceId, "workspaceId");
        name = requireNonBlank(name, "name");
        description = description == null ? "" : description;
        includedTags = includedTags == null ? List.of() : List.copyOf(includedTags);
        excludedTags = excludedTags == null ? List.of() : List.copyOf(excludedTags);
        includedSegments = includedSegments == null ? List.of() : List.copyOf(includedSegments);
        customExpression = customExpression == null ? "" : customExpression;
        if (memberCount < 0) {
            throw new IllegalArgumentException("memberCount must be non-negative");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
