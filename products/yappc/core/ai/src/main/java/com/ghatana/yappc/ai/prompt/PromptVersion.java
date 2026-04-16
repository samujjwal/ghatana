/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Prompt Version Entity
 */
package com.ghatana.yappc.ai.prompt;

import com.ghatana.products.yappc.domain.Identifiable;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity for YAPPC AI prompt versions.
 *
 * <p>Represents a versioned prompt template with full version history for
 * tracking, A/B testing, and rollback capabilities.
 *
 * @doc.type class
 * @doc.purpose Domain entity for prompt versions
 * @doc.layer product
 * @doc.pattern Entity
 */
public record PromptVersion implements Identifiable<UUID> {

    /** Unique version identifier */
    private final UUID versionId;

    /** Logical prompt identifier (e.g. "requirement-generation") */
    private final String promptName;

    /** Full prompt text with {{varName}} placeholders */
    private final String content;

    /** SHA-256 hex of content (deduplication key) */
    private final String contentHash;

    /** Human-readable change description */
    private final String description;

    /** Who created this version */
    private final String author;

    /** Whether this version is the current default */
    private final boolean active;

    /** When this version was created */
    private final Instant createdAt;

    public PromptVersion(
            UUID versionId,
            String promptName,
            String content,
            String contentHash,
            String description,
            String author,
            boolean active,
            Instant createdAt) {
        this.versionId = versionId;
        this.promptName = promptName;
        this.content = content;
        this.contentHash = contentHash;
        this.description = description;
        this.author = author;
        this.active = active;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return versionId;
    }
}
