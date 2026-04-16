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
public record PromptVersion(
        /** Unique version identifier */ UUID versionId,
        /** Logical prompt identifier (e.g. "requirement-generation") */ String promptName,
        /** Full prompt text with {{varName}} placeholders */ String content,
        /** SHA-256 hex of content (deduplication key) */ String contentHash,
        /** Human-readable change description */ String description,
        /** Who created this version */ String author,
        /** Whether this version is the current default */ boolean active,
        /** When this version was created */ Instant createdAt
) implements Identifiable<UUID> {

    @Override
    public UUID getId() {
        return versionId;
    }
}
