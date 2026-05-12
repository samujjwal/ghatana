/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Common response metadata for all platform client responses.
 * Standardizes the metadata returned from platform services.
 *
 * @doc.type record
 * @doc.purpose Common response metadata for platform client responses
 * @doc.layer product
 * @doc.pattern DTO
 */
public record PlatformResponseMetadata(
    String status,
    @Nullable Double confidence,
    @Nullable String confidenceReason,
    @Nullable String traceId,
    @Nullable List<String> evidenceIds,
    @Nullable String policyDecisionId,
    boolean degraded,
    @Nullable String degradedReason,
    Instant createdAt,
    @Nullable Instant completedAt,
    @Nullable String runId,
    @Nullable List<String> memoryRecordIds,
    @Nullable List<String> searchResultIds
) {
    public PlatformResponseMetadata {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }

    /**
     * Creates a successful response metadata.
     */
    public static PlatformResponseMetadata success() {
        return new PlatformResponseMetadata(
            "SUCCESS",
            1.0,
            null,
            null,
            List.of(),
            null,
            false,
            null,
            Instant.now(),
            Instant.now(),
            null,
            List.of(),
            List.of()
        );
    }

    /**
     * Creates a degraded response metadata.
     */
    public static PlatformResponseMetadata degraded(String reason) {
        return new PlatformResponseMetadata(
            "DEGRADED",
            null,
            null,
            null,
            List.of(),
            null,
            true,
            reason,
            Instant.now(),
            null,
            null,
            List.of(),
            List.of()
        );
    }

    /**
     * Creates a failed response metadata.
     */
    public static PlatformResponseMetadata failure(String reason) {
        return new PlatformResponseMetadata(
            "FAILURE",
            0.0,
            reason,
            null,
            List.of(),
            null,
            false,
            null,
            Instant.now(),
            Instant.now(),
            null,
            List.of(),
            List.of()
        );
    }
}
