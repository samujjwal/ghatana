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
 * Time range for metrics queries.
 *
 * @doc.type enum
 * @doc.purpose Define time windows for metric aggregation
 * @doc.layer product
 */
enum TimeRange {
    LAST_HOUR,
    LAST_DAY,
    LAST_WEEK,
    LAST_MONTH,
    ALL_TIME
}

/**
 * AI evaluation metrics for measuring pipeline quality.
 *
 * @doc.type record
 * @doc.purpose Track AI evaluation quality metrics
 * @doc.layer product
 */
record AIEvaluationMetrics(
    double accuracy,
    double precision,
    double recall,
    double f1Score,
    double latencyMs,
    int totalEvaluations,
    int successfulEvaluations,
    Instant timestamp
) {}

/**
 * AI evaluation result for regression tracking.
 *
 * @doc.type record
 * @doc.purpose Record individual AI evaluation outcomes
 * @doc.layer product
 */
record AIEvaluationResult(
    String evaluationId,
    String tenantId,
    String userId,
    String modelVersion,
    String taskType,
    String input,
    String output,
    String expectedOutput,
    boolean passed,
    double confidence,
    double latencyMs,
    Instant timestamp,
    Map<String, Object> metadata
) {}

/**
 * Service for AI assistance and query processing.
 *
 * @doc.type interface
 * @doc.purpose AI query processing and assistance
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface AIAssistService {

    /**
     * Process a natural language query.
     *
     * @param query user query
     * @param context query context
     * @return promise of query result
     */
    Promise<QueryResult> processQuery(String query, QueryContext context);

    /**
     * Generate SQL from natural language.
     *
     * @param description natural language description
     * @param schema database schema
     * @return promise of generated SQL
     */
    Promise<GeneratedSQL> generateSQL(String description, DatabaseSchema schema);

    /**
     * Explain query results.
     *
     * @param query original query
     * @param results query results
     * @param context explanation context
     * @return promise of explanation
     */
    Promise<Explanation> explainResults(String query, List<Map<String, Object>> results, QueryContext context);

    /**
     * Suggest queries based on context.
     *
     * @param context query context
     * @param limit maximum suggestions
     * @return promise of suggestions
     */
    Promise<List<QuerySuggestion>> suggestQueries(QueryContext context, int limit);

    /**
     * Get conversation history.
     *
     * @param conversationId conversation identifier
     * @return promise of conversation
     */
    Promise<Conversation> getConversation(String conversationId);

    /**
     * Create new conversation.
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @return promise of created conversation
     */
    Promise<Conversation> createConversation(String tenantId, String userId);

    /**
     * Add message to conversation.
     *
     * @param conversationId conversation identifier
     * @param message message to add
     * @return promise of updated conversation
     */
    Promise<Conversation> addMessage(String conversationId, Message message);

    /**
     * Clear conversation history.
     *
     * @param conversationId conversation identifier
     * @return promise completing when cleared
     */
    Promise<Void> clearConversation(String conversationId);

    /**
     * Get AI service status.
     *
     * @return promise of status
     */
    Promise<ServiceStatus> getStatus();

    /**
     * Get AI evaluation metrics for measuring pipeline quality.
     *
     * @param timeRange time range for metrics
     * @return promise of evaluation metrics
     */
    Promise<AIEvaluationMetrics> getEvaluationMetrics(TimeRange timeRange);

    /**
     * Record AI evaluation result for regression tracking.
     *
     * @param result evaluation result to record
     * @return promise completing when recorded
     */
    Promise<Void> recordEvaluationResult(AIEvaluationResult result);

    /**
     * Query context.
     */
    record QueryContext(
        String tenantId,
        String userId,
        String conversationId,
        String databaseSchema,
        List<String> availableTables,
        Map<String, Object> userPreferences,
        String previousQuery
    ) {}

    /**
     * Query result.
     */
    record QueryResult(
        String queryId,
        String originalQuery,
        String interpretedIntent,
        GeneratedSQL generatedSQL,
        List<Map<String, Object>> results,
        Explanation explanation,
        long processingTimeMs,
        boolean usedCache,
        double confidenceScore
    ) {}

    /**
     * Generated SQL.
     */
    record GeneratedSQL(
        String sql,
        String explanation,
        List<String> tablesUsed,
        List<String> warnings,
        boolean isReadOnly
    ) {
        public boolean isSafe() {
            return isReadOnly && warnings.isEmpty();
        }
    }

    /**
     * Explanation.
     */
    record Explanation(
        String summary,
        List<String> keyPoints,
        List<String> recommendations,
        String visualizationSuggestion
    ) {}

    /**
     * Query suggestion.
     */
    record QuerySuggestion(
        String query,
        String description,
        double relevanceScore,
        String category
    ) {}

    /**
     * Conversation.
     */
    record Conversation(
        String id,
        String tenantId,
        String userId,
        List<Message> messages,
        Instant createdAt,
        Instant lastActivityAt,
        int messageCount
    ) {}

    /**
     * Message.
     */
    record Message(
        String id,
        MessageRole role,
        String content,
        Instant timestamp,
        Map<String, Object> metadata
    ) {}

    /**
     * Message role.
     */
    enum MessageRole {
        USER, ASSISTANT, SYSTEM
    }

    /**
     * Database schema.
     */
    record DatabaseSchema(
        String name,
        List<TableInfo> tables
    ) {}

    /**
     * Table info.
     */
    record TableInfo(
        String name,
        List<ColumnInfo> columns,
        List<String> sampleData
    ) {}

    /**
     * Column info.
     */
    record ColumnInfo(
        String name,
        String type,
        boolean nullable,
        String description
    ) {}

    /**
     * Service status.
     */
    record ServiceStatus(
        boolean available,
        String provider,
        String model,
        long requestsProcessed,
        double averageLatencyMs,
        Instant lastHealthCheck
    ) {}
}
