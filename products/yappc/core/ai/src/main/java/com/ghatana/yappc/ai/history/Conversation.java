/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Conversation Entity
 */
package com.ghatana.yappc.ai.history;

import com.ghatana.yappc.domain.Identifiable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain entity for YAPPC AI conversation history.
 *
 * <p>Represents a conversation between a user and AI, including all turns
 * and metadata for tracking, analysis, and context reconstruction.
 *
 * @doc.type class
 * @doc.purpose Domain entity for AI conversations
 * @doc.layer product
 * @doc.pattern Entity
 */
public record Conversation(
        /** Unique conversation identifier */ UUID conversationId,
        /** User who owns this conversation */ String userId,
        /** Optional project scope */ String projectId,
        /** Product feature that owns this conversation */ String feature,
        /** List of conversation turns */ List<Turn> turns,
        /** When the conversation was created */ Instant createdAt,
        /** When the conversation was last updated */ Instant updatedAt
) implements Identifiable<UUID> {

    @Override
    public UUID getId() {
        return conversationId;
    }

    /**
     * Represents a single turn in a conversation.
     */
    public record Turn(
            String role,
            String content,
            String model,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            Instant timestamp
    ) {}
}
