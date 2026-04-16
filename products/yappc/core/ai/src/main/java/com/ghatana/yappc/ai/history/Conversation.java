/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — Conversation Entity
 */
package com.ghatana.yappc.ai.history;

import com.ghatana.products.yappc.domain.Identifiable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
public record Conversation implements Identifiable<UUID> {

    /** Unique conversation identifier */
    private final UUID conversationId;

    /** User who owns this conversation */
    private final String userId;

    /** Optional project scope */
    private final String projectId;

    /** Product feature that owns this conversation */
    private final String feature;

    /** List of conversation turns */
    private final List<Turn> turns;

    /** When the conversation was created */
    private final Instant createdAt;

    /** When the conversation was last updated */
    private final Instant updatedAt;

    public Conversation(
            UUID conversationId,
            String userId,
            String projectId,
            String feature,
            List<Turn> turns,
            Instant createdAt,
            Instant updatedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.projectId = projectId;
        this.feature = feature;
        this.turns = turns;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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
