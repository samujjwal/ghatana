/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a documentation source that indicates obsolescence.
 *
 * @doc.type record
 * @doc.purpose Value object for documentation sources
 * @doc.layer agent-core
 * @doc.pattern Value Object
 */
public record DocumentationSource(
        @NotNull String sourceId,
        @NotNull String sourceType,
        @NotNull String title,
        @NotNull String url,
        @NotNull String relevantSection,
        @NotNull String contradiction,
        @NotNull Instant lastUpdated
) {
    public DocumentationSource {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(relevantSection, "relevantSection must not be null");
        Objects.requireNonNull(contradiction, "contradiction must not be null");
        Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
    }

    /**
     * Types of documentation sources.
     */
    public enum SourceType {
        OFFICIAL_DOCS,
        API_REFERENCE,
        CHANGELOG,
        RELEASE_NOTES,
        BLOG_POST,
        STACK_OVERFLOW,
        GITHUB_ISSUE,
        FORUM_POST,
        INTERNAL_WIKI
    }

    /**
     * Creates a documentation source for official documentation.
     */
    @NotNull
    public static DocumentationSource officialDocs(
            @NotNull String title,
            @NotNull String url,
            @NotNull String relevantSection,
            @NotNull String contradiction) {
        return new DocumentationSource(
                "doc-" + System.currentTimeMillis(),
                SourceType.OFFICIAL_DOCS.name(),
                title,
                url,
                relevantSection,
                contradiction,
                Instant.now()
        );
    }

    /**
     * Creates a documentation source for a changelog.
     */
    @NotNull
    public static DocumentationSource changelog(
            @NotNull String title,
            @NotNull String url,
            @NotNull String version,
            @NotNull String contradiction) {
        return new DocumentationSource(
                "changelog-" + System.currentTimeMillis(),
                SourceType.CHANGELOG.name(),
                title,
                url,
                version,
                contradiction,
                Instant.now()
        );
    }

    /**
     * Creates a documentation source for a GitHub issue.
     */
    @NotNull
    public static DocumentationSource githubIssue(
            @NotNull String title,
            @NotNull String url,
            @NotNull String contradiction) {
        return new DocumentationSource(
                "issue-" + System.currentTimeMillis(),
                SourceType.GITHUB_ISSUE.name(),
                title,
                url,
                "Issue discussion",
                contradiction,
                Instant.now()
        );
    }
}
