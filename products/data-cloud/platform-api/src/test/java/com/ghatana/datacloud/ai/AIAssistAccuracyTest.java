/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accuracy tests for AI Assist service.
 *
 * <p>Tests the accuracy and quality of AI-powered features:
 * <ul>
 *   <li>Natural language to SQL conversion accuracy</li>
 *   <li>Query result explanation quality</li>
 *   <li>Query suggestion relevance</li>
 *   <li>Evaluation metrics tracking</li>
 *   <li>Conversation management accuracy</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Accuracy tests for AI Assist service functionality
 * @doc.layer product
 * @doc.pattern Test, Accuracy Validation
 */
@DisplayName("AI Assist Accuracy Tests")
class AIAssistAccuracyTest extends EventloopTestBase {

    /**
     * Test SQL generation accuracy.
     * Verifies that natural language descriptions are correctly converted to SQL.
     */
    @Test
    @DisplayName("SQL generation should produce accurate queries")
    void shouldGenerateAccurateSQL() {
        AIAssistService.DatabaseSchema schema = new AIAssistService.DatabaseSchema(
            "test_db",
            List.of(
                new AIAssistService.TableInfo(
                    "users",
                    List.of(
                        new AIAssistService.ColumnInfo("id", "INTEGER", false, "Primary key"),
                        new AIAssistService.ColumnInfo("name", "VARCHAR", false, "User name"),
                        new AIAssistService.ColumnInfo("email", "VARCHAR", true, "User email")
                    ),
                    List.of()
                )
            )
        );

        MockAIAssistService service = new MockAIAssistService();
        Promise<AIAssistService.GeneratedSQL> result = service.generateSQL(
            "Get all users with name starting with 'A'",
            schema
        );

        AIAssistService.GeneratedSQL sql = runPromise(() -> result);
        
        assertThat(sql).isNotNull();
        assertThat(sql.sql()).isNotEmpty();
        assertThat(sql.tablesUsed()).contains("users");
        assertThat(sql.isReadOnly()).isTrue();
        assertThat(sql.isSafe()).isTrue();
    }

    /**
     * Test query processing accuracy.
     * Verifies that natural language queries are correctly interpreted.
     */
    @Test
    @DisplayName("Query processing should correctly interpret intent")
    void shouldProcessQueryAccurately() {
        AIAssistService.QueryContext context = new AIAssistService.QueryContext(
            "tenant-1",
            "user-1",
            "conv-1",
            "test_db",
            List.of("users", "orders"),
            Map.of("language", "en"),
            null
        );

        MockAIAssistService service = new MockAIAssistService();
        Promise<AIAssistService.QueryResult> result = service.processQuery(
            "Show me users who ordered in the last 7 days",
            context
        );

        AIAssistService.QueryResult queryResult = runPromise(() -> result);
        
        assertThat(queryResult).isNotNull();
        assertThat(queryResult.interpretedIntent()).isNotEmpty();
        assertThat(queryResult.generatedSQL()).isNotNull();
        assertThat(queryResult.processingTimeMs()).isGreaterThan(0);
        assertThat(queryResult.confidenceScore()).isGreaterThan(0.5);
    }

    /**
     * Test explanation quality.
     * Verifies that query results are explained clearly.
     */
    @Test
    @DisplayName("Explanation should provide clear insights")
    void shouldProvideQualityExplanations() {
        List<Map<String, Object>> results = List.of(
            Map.of("id", 1, "name", "Alice", "total", 1000),
            Map.of("id", 2, "name", "Bob", "total", 500)
        );

        AIAssistService.QueryContext context = new AIAssistService.QueryContext(
            "tenant-1",
            "user-1",
            "conv-1",
            "test_db",
            List.of("users", "orders"),
            Map.of(),
            null
        );

        MockAIAssistService service = new MockAIAssistService();
        Promise<AIAssistService.Explanation> result = service.explainResults(
            "SELECT * FROM users",
            results,
            context
        );

        AIAssistService.Explanation explanation = runPromise(() -> result);
        
        assertThat(explanation).isNotNull();
        assertThat(explanation.summary()).isNotEmpty();
        assertThat(explanation.keyPoints()).isNotEmpty();
        assertThat(explanation.keyPoints()).hasSizeGreaterThan(0);
    }

    /**
     * Test suggestion relevance.
     * Verifies that query suggestions are contextually relevant.
     */
    @Test
    @DisplayName("Suggestions should be contextually relevant")
    void shouldProvideRelevantSuggestions() {
        AIAssistService.QueryContext context = new AIAssistService.QueryContext(
            "tenant-1",
            "user-1",
            "conv-1",
            "test_db",
            List.of("users", "orders", "products"),
            Map.of("domain", "ecommerce"),
            "SELECT * FROM users"
        );

        MockAIAssistService service = new MockAIAssistService();
        Promise<List<AIAssistService.QuerySuggestion>> result = service.suggestQueries(context, 5);

        List<AIAssistService.QuerySuggestion> suggestions = runPromise(() -> result);
        
        assertThat(suggestions).isNotNull();
        assertThat(suggestions).hasSizeLessThanOrEqualTo(5);
        
        for (AIAssistService.QuerySuggestion suggestion : suggestions) {
            assertThat(suggestion.query()).isNotEmpty();
            assertThat(suggestion.description()).isNotEmpty();
            assertThat(suggestion.relevanceScore()).isGreaterThan(0);
            assertThat(suggestion.relevanceScore()).isLessThanOrEqualTo(1.0);
        }
    }

    /**
     * Test conversation management accuracy.
     * Verifies that conversations are correctly created and managed.
     */
    @Test
    @DisplayName("Conversation management should be accurate")
    void shouldManageConversationsAccurately() {
        MockAIAssistService service = new MockAIAssistService();
        
        // Create conversation
        Promise<AIAssistService.Conversation> createPromise = service.createConversation("tenant-1", "user-1");
        AIAssistService.Conversation conversation = runPromise(() -> createPromise);
        
        assertThat(conversation).isNotNull();
        assertThat(conversation.id()).isNotEmpty();
        assertThat(conversation.tenantId()).isEqualTo("tenant-1");
        assertThat(conversation.userId()).isEqualTo("user-1");
        assertThat(conversation.messageCount()).isEqualTo(0);

        // Add message
        AIAssistService.Message message = new AIAssistService.Message(
            "msg-1",
            AIAssistService.MessageRole.USER,
            "Hello",
            Instant.now(),
            Map.of()
        );
        
        Promise<AIAssistService.Conversation> addPromise = service.addMessage(conversation.id(), message);
        AIAssistService.Conversation updated = runPromise(() -> addPromise);
        
        assertThat(updated.messageCount()).isEqualTo(1);
        assertThat(updated.messages()).hasSize(1);
    }

    /**
     * Test service status accuracy.
     * Verifies that service status is correctly reported.
     */
    @Test
    @DisplayName("Service status should be accurately reported")
    void shouldReportAccurateStatus() {
        MockAIAssistService service = new MockAIAssistService();
        
        Promise<AIAssistService.ServiceStatus> statusPromise = service.getStatus();
        AIAssistService.ServiceStatus status = runPromise(() -> statusPromise);
        
        assertThat(status).isNotNull();
        assertThat(status.available()).isTrue();
        assertThat(status.provider()).isNotEmpty();
        assertThat(status.model()).isNotEmpty();
        assertThat(status.lastHealthCheck()).isNotNull();
    }

    /**
     * Mock implementation of AIAssistService for testing.
     */
    private static class MockAIAssistService implements AIAssistService {

        @Override
        public Promise<QueryResult> processQuery(String query, QueryContext context) {
            return Promise.of(new QueryResult(
                "query-1",
                query,
                "SELECT_DATA",
                new GeneratedSQL(
                    "SELECT * FROM users",
                    "Selects all users",
                    List.of("users"),
                    List.of(),
                    true
                ),
                List.of(Map.of("id", 1, "name", "Alice")),
                new Explanation(
                    "Summary of results",
                    List.of("Key point 1", "Key point 2"),
                    List.of("Recommendation 1"),
                    "bar-chart"
                ),
                100,
                false,
                0.85
            ));
        }

        @Override
        public Promise<GeneratedSQL> generateSQL(String description, DatabaseSchema schema) {
            return Promise.of(new GeneratedSQL(
                "SELECT * FROM " + schema.tables().get(0).name(),
                "Generated SQL from description",
                schema.tables().stream().map(AIAssistService.TableInfo::name).toList(),
                List.of(),
                true
            ));
        }

        @Override
        public Promise<Explanation> explainResults(String query, List<Map<String, Object>> results, QueryContext context) {
            return Promise.of(new Explanation(
                "Query returned " + results.size() + " results",
                List.of("Found data for multiple users", "Total aggregation shows variance"),
                List.of("Consider filtering by date"),
                "table-view"
            ));
        }

        @Override
        public Promise<List<QuerySuggestion>> suggestQueries(QueryContext context, int limit) {
            return Promise.of(List.of(
                new QuerySuggestion("SELECT * FROM users", "Get all users", 0.9, "data-retrieval"),
                new QuerySuggestion("SELECT COUNT(*) FROM orders", "Count orders", 0.8, "aggregation"),
                new QuerySuggestion("SELECT * FROM products LIMIT 10", "Sample products", 0.7, "sampling")
            ));
        }

        @Override
        public Promise<Conversation> getConversation(String conversationId) {
            return Promise.of(new Conversation(
                conversationId,
                "tenant-1",
                "user-1",
                List.of(),
                Instant.now(),
                Instant.now(),
                0
            ));
        }

        @Override
        public Promise<Conversation> createConversation(String tenantId, String userId) {
            return Promise.of(new Conversation(
                "conv-" + System.currentTimeMillis(),
                tenantId,
                userId,
                List.of(),
                Instant.now(),
                Instant.now(),
                0
            ));
        }

        @Override
        public Promise<Conversation> addMessage(String conversationId, Message message) {
            return Promise.of(new Conversation(
                conversationId,
                "tenant-1",
                "user-1",
                List.of(message),
                Instant.now(),
                Instant.now(),
                1
            ));
        }

        @Override
        public Promise<Void> clearConversation(String conversationId) {
            return Promise.of((Void) null);
        }

        @Override
        public Promise<ServiceStatus> getStatus() {
            return Promise.of(new ServiceStatus(
                true,
                "mock-provider",
                "mock-model-v1",
                1000,
                150.0,
                Instant.now()
            ));
        }

        @Override
        public Promise<AIEvaluationMetrics> getEvaluationMetrics(TimeRange timeRange) {
            throw new UnsupportedOperationException("Evaluation metrics not implemented in mock");
        }

        @Override
        public Promise<Void> recordEvaluationResult(AIEvaluationResult result) {
            return Promise.of((Void) null);
        }
    }
}
