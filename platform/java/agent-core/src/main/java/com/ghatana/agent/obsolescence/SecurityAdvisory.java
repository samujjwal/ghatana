/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a security advisory that may cause obsolescence.
 *
 * @doc.type record
 * @doc.purpose Value object for security advisories
 * @doc.layer agent-core
 * @doc.pattern Value Object
 */
public record SecurityAdvisory(
        @NotNull String advisoryId,
        @NotNull String cveId,
        @NotNull Severity severity,
        @NotNull String title,
        @NotNull String description,
        @NotNull String affectedComponent,
        @NotNull String affectedVersionRange,
        @NotNull Instant publishedAt,
        @NotNull String referenceUrl
) {
    public SecurityAdvisory {
        Objects.requireNonNull(advisoryId, "advisoryId must not be null");
        Objects.requireNonNull(cveId, "cveId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(affectedComponent, "affectedComponent must not be null");
        Objects.requireNonNull(affectedVersionRange, "affectedVersionRange must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        Objects.requireNonNull(referenceUrl, "referenceUrl must not be null");
    }

    /**
     * Severity levels for security advisories.
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Creates a security advisory from a CVE.
     */
    @NotNull
    public static SecurityAdvisory fromCve(
            @NotNull String cveId,
            @NotNull Severity severity,
            @NotNull String title,
            @NotNull String description,
            @NotNull String affectedComponent,
            @NotNull String affectedVersionRange,
            @NotNull String referenceUrl) {
        return new SecurityAdvisory(
                "adv-" + cveId.replace("CVE-", "").toLowerCase(),
                cveId,
                severity,
                title,
                description,
                affectedComponent,
                affectedVersionRange,
                Instant.now(),
                referenceUrl
        );
    }
}
