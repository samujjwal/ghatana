/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manager for LLM context windows.
 *
 * @doc.type interface
 * @doc.purpose Context window management for conversations
 * @doc.layer product
 * @doc.pattern Manager
 */
public interface ContextWindowManager {

    /**
     * Get current context window for conversation.
     *
     * @param conversationId conversation identifier
     * @return promise of context window
     */
    Promise<ContextWindow> getContextWindow(String conversationId);

    /**
     * Add content to context window.
     *
     * @param conversationId conversation identifier
     * @param content content to add
     * @return promise of updated window
     */
    Promise<ContextWindow> addContent(String conversationId, ContextContent content);

    /**
     * Trim context window to fit within token limit.
     *
     * @param conversationId conversation identifier
     * @param maxTokens maximum tokens allowed
     * @return promise of trimmed window
     */
    Promise<ContextWindow> trimToFit(String conversationId, int maxTokens);

    /**
     * Summarize context window content.
     *
     * @param conversationId conversation identifier
     * @return promise of summary
     */
    Promise<ContextSummary> summarize(String conversationId);

    /**
     * Clear context window.
     *
     * @param conversationId conversation identifier
     * @return promise completing when cleared
     */
    Promise<Void> clear(String conversationId);

    /**
     * Get token usage statistics.
     *
     * @param conversationId conversation identifier
     * @return promise of usage stats
     */
    Promise<TokenUsage> getTokenUsage(String conversationId);

    /**
     * Optimize context window for specific query.
     *
     * @param conversationId conversation identifier
     * @param query query to optimize for
     * @return promise of optimized window
     */
    Promise<ContextWindow> optimizeForQuery(String conversationId, String query);

    /**
     * Context window.
     */
    record ContextWindow(
        String conversationId,
        List<ContextContent> contents,
        int totalTokens,
        int maxTokens,
        boolean isTruncated,
        Instant createdAt,
        Instant updatedAt
    ) {
        /**
         * Check if window is within limits.
         */
        public boolean isWithinLimits() {
            return totalTokens <= maxTokens;
        }

        /**
         * Get remaining tokens.
         */
        public int getRemainingTokens() {
            return Math.max(0, maxTokens - totalTokens);
        }
    }

    /**
     * Context content item.
     */
    record ContextContent(
        String id,
        ContentType type,
        String content,
        int tokens,
        double relevanceScore,
        Instant timestamp,
        Map<String, Object> metadata
    ) {
        /**
         * Content type.
         */
        public enum ContentType {
            USER_MESSAGE, ASSISTANT_MESSAGE, SYSTEM_MESSAGE,
            SCHEMA_INFO, QUERY_RESULT, SUMMARY, EXTERNAL_CONTEXT
        }
    }

    /**
     * Context summary.
     */
    record ContextSummary(
        String conversationId,
        String summary,
        int originalTokens,
        int summaryTokens,
        double compressionRatio,
        List<String> keyPoints,
        Instant generatedAt
    ) {
        /**
         * Calculate compression ratio.
         */
        public double getCompressionRatio() {
            if (originalTokens == 0) return 1.0;
            return (double) summaryTokens / originalTokens;
        }
    }

    /**
     * Token usage statistics.
     */
    record TokenUsage(
        String conversationId,
        int totalTokensUsed,
        int userMessageTokens,
        int assistantMessageTokens,
        int contextTokens,
        int availableTokens,
        List<UsageByMessage> usageByMessage
    ) {}

    /**
     * Usage by message.
     */
    record UsageByMessage(
        String messageId,
        int tokens,
        ContentType type,
        Instant timestamp
    ) {}

    /**
     * Content type.
     */
    enum ContentType {
        USER_MESSAGE, ASSISTANT_MESSAGE, SYSTEM_MESSAGE,
        SCHEMA_INFO, QUERY_RESULT, SUMMARY, EXTERNAL_CONTEXT
    }
}
