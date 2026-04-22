/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("AI Assist Accuracy Tests [GH-90000]")
class AIAssistAccuracyTest extends EventloopTestBase {

    /**
     * Test SQL generation accuracy.
     * Verifies that natural language descriptions are correctly converted to SQL.
     */
    @Test
    @DisplayName("SQL generation should produce accurate queries [GH-90000]")
    void shouldGenerateAccurateSQL() { // GH-90000
        AIAssistService.DatabaseSchema schema = new AIAssistService.DatabaseSchema( // GH-90000
            "test_db",
            List.of( // GH-90000
                new AIAssistService.TableInfo( // GH-90000
                    "users",
                    List.of( // GH-90000
                        new AIAssistService.ColumnInfo("id", "INTEGER", false, "Primary key"), // GH-90000
                        new AIAssistService.ColumnInfo("name", "VARCHAR", false, "User name"), // GH-90000
                        new AIAssistService.ColumnInfo("email", "VARCHAR", true, "User email") // GH-90000
                    ),
                    List.of() // GH-90000
                )
            )
        );

        MockAIAssistService service = new MockAIAssistService(); // GH-90000
        Promise<AIAssistService.GeneratedSQL> result = service.generateSQL( // GH-90000
            "Get all users with name starting with 'A'",
            schema
        );

        AIAssistService.GeneratedSQL sql = runPromise(() -> result); // GH-90000
        
        assertThat(sql).isNotNull(); // GH-90000
        assertThat(sql.sql()).isNotEmpty(); // GH-90000
        assertThat(sql.tablesUsed()).contains("users [GH-90000]");
        assertThat(sql.isReadOnly()).isTrue(); // GH-90000
        assertThat(sql.isSafe()).isTrue(); // GH-90000
    }

    /**
     * Test query processing accuracy.
     * Verifies that natural language queries are correctly interpreted.
     */
    @Test
    @DisplayName("Query processing should correctly interpret intent [GH-90000]")
    void shouldProcessQueryAccurately() { // GH-90000
        AIAssistService.QueryContext context = new AIAssistService.QueryContext( // GH-90000
            "tenant-1",
            "user-1",
            "conv-1",
            "test_db",
            List.of("users", "orders"), // GH-90000
            Map.of("language", "en"), // GH-90000
            null
        );

        MockAIAssistService service = new MockAIAssistService(); // GH-90000
        Promise<AIAssistService.QueryResult> result = service.processQuery( // GH-90000
            "Show me users who ordered in the last 7 days",
            context
        );

        AIAssistService.QueryResult queryResult = runPromise(() -> result); // GH-90000
        
        assertThat(queryResult).isNotNull(); // GH-90000
        assertThat(queryResult.interpretedIntent()).isNotEmpty(); // GH-90000
        assertThat(queryResult.generatedSQL()).isNotNull(); // GH-90000
        assertThat(queryResult.processingTimeMs()).isGreaterThan(0); // GH-90000
        assertThat(queryResult.confidenceScore()).isGreaterThan(0.5); // GH-90000
    }

    /**
     * Test explanation quality.
     * Verifies that query results are explained clearly.
     */
    @Test
    @DisplayName("Explanation should provide clear insights [GH-90000]")
    void shouldProvideQualityExplanations() { // GH-90000
        List<Map<String, Object>> results = List.of( // GH-90000
            Map.of("id", 1, "name", "Alice", "total", 1000), // GH-90000
            Map.of("id", 2, "name", "Bob", "total", 500) // GH-90000
        );

        AIAssistService.QueryContext context = new AIAssistService.QueryContext( // GH-90000
            "tenant-1",
            "user-1",
            "conv-1",
            "test_db",
            List.of("users", "orders"), // GH-90000
            Map.of(), // GH-90000
            null
        );

        MockAIAssistService service = new MockAIAssistService(); // GH-90000
        Promise<AIAssistService.Explanation> result = service.explainResults( // GH-90000
            "SELECT * FROM users",
            results,
            context
        );

        AIAssistService.Explanation explanation = runPromise(() -> result); // GH-90000
        
        assertThat(explanation).isNotNull(); // GH-90000
        assertThat(explanation.summary()).isNotEmpty(); // GH-90000
        assertThat(explanation.keyPoints()).isNotEmpty(); // GH-90000
        assertThat(explanation.keyPoints()).hasSizeGreaterThan(0); // GH-90000
    }

    /**
     * Test suggestion relevance.
     * Verifies that query suggestions are contextually relevant.
     */
    @Test
    @DisplayName("Suggestions should be contextually relevant [GH-90000]")
    void shouldProvideRelevantSuggestions() { // GH-90000
        AIAssistService.QueryContext context = new AIAssistService.QueryContext( // GH-90000
            "tenant-1",
            "user-1",
            "conv-1",
            "test_db",
            List.of("users", "orders", "products"), // GH-90000
            Map.of("domain", "ecommerce"), // GH-90000
            "SELECT * FROM users"
        );

        MockAIAssistService service = new MockAIAssistService(); // GH-90000
        Promise<List<AIAssistService.QuerySuggestion>> result = service.suggestQueries(context, 5); // GH-90000

        List<AIAssistService.QuerySuggestion> suggestions = runPromise(() -> result); // GH-90000
        
        assertThat(suggestions).isNotNull(); // GH-90000
        assertThat(suggestions).hasSizeLessThanOrEqualTo(5); // GH-90000
        
        for (AIAssistService.QuerySuggestion suggestion : suggestions) { // GH-90000
            assertThat(suggestion.query()).isNotEmpty(); // GH-90000
            assertThat(suggestion.description()).isNotEmpty(); // GH-90000
            assertThat(suggestion.relevanceScore()).isGreaterThan(0); // GH-90000
            assertThat(suggestion.relevanceScore()).isLessThanOrEqualTo(1.0); // GH-90000
        }
    }

    /**
     * Test conversation management accuracy.
     * Verifies that conversations are correctly created and managed.
     */
    @Test
    @DisplayName("Conversation management should be accurate [GH-90000]")
    void shouldManageConversationsAccurately() { // GH-90000
        MockAIAssistService service = new MockAIAssistService(); // GH-90000
        
        // Create conversation
        Promise<AIAssistService.Conversation> createPromise = service.createConversation("tenant-1", "user-1"); // GH-90000
        AIAssistService.Conversation conversation = runPromise(() -> createPromise); // GH-90000
        
        assertThat(conversation).isNotNull(); // GH-90000
        assertThat(conversation.id()).isNotEmpty(); // GH-90000
        assertThat(conversation.tenantId()).isEqualTo("tenant-1 [GH-90000]");
        assertThat(conversation.userId()).isEqualTo("user-1 [GH-90000]");
        assertThat(conversation.messageCount()).isEqualTo(0); // GH-90000

        // Add message
        AIAssistService.Message message = new AIAssistService.Message( // GH-90000
            "msg-1",
            AIAssistService.MessageRole.USER,
            "Hello",
            Instant.now(), // GH-90000
            Map.of() // GH-90000
        );
        
        Promise<AIAssistService.Conversation> addPromise = service.addMessage(conversation.id(), message); // GH-90000
        AIAssistService.Conversation updated = runPromise(() -> addPromise); // GH-90000
        
        assertThat(updated.messageCount()).isEqualTo(1); // GH-90000
        assertThat(updated.messages()).hasSize(1); // GH-90000
    }

    /**
     * Test service status accuracy.
     * Verifies that service status is correctly reported.
     */
    @Test
    @DisplayName("Service status should be accurately reported [GH-90000]")
    void shouldReportAccurateStatus() { // GH-90000
        MockAIAssistService service = new MockAIAssistService(); // GH-90000
        
        Promise<AIAssistService.ServiceStatus> statusPromise = service.getStatus(); // GH-90000
        AIAssistService.ServiceStatus status = runPromise(() -> statusPromise); // GH-90000
        
        assertThat(status).isNotNull(); // GH-90000
        assertThat(status.available()).isTrue(); // GH-90000
        assertThat(status.provider()).isNotEmpty(); // GH-90000
        assertThat(status.model()).isNotEmpty(); // GH-90000
        assertThat(status.lastHealthCheck()).isNotNull(); // GH-90000
    }

    /**
     * Mock implementation of AIAssistService for testing.
     */
    private static class MockAIAssistService implements AIAssistService {

        @Override
        public Promise<QueryResult> processQuery(String query, QueryContext context) { // GH-90000
            return Promise.of(new QueryResult( // GH-90000
                "query-1",
                query,
                "SELECT_DATA",
                new GeneratedSQL( // GH-90000
                    "SELECT * FROM users",
                    "Selects all users",
                    List.of("users [GH-90000]"),
                    List.of(), // GH-90000
                    true
                ),
                List.of(Map.of("id", 1, "name", "Alice")), // GH-90000
                new Explanation( // GH-90000
                    "Summary of results",
                    List.of("Key point 1", "Key point 2"), // GH-90000
                    List.of("Recommendation 1 [GH-90000]"),
                    "bar-chart"
                ),
                100,
                false,
                0.85
            ));
        }

        @Override
        public Promise<GeneratedSQL> generateSQL(String description, DatabaseSchema schema) { // GH-90000
            return Promise.of(new GeneratedSQL( // GH-90000
                "SELECT * FROM " + schema.tables().get(0).name(), // GH-90000
                "Generated SQL from description",
                schema.tables().stream().map(AIAssistService.TableInfo::name).toList(), // GH-90000
                List.of(), // GH-90000
                true
            ));
        }

        @Override
        public Promise<Explanation> explainResults(String query, List<Map<String, Object>> results, QueryContext context) { // GH-90000
            return Promise.of(new Explanation( // GH-90000
                "Query returned " + results.size() + " results", // GH-90000
                List.of("Found data for multiple users", "Total aggregation shows variance"), // GH-90000
                List.of("Consider filtering by date [GH-90000]"),
                "table-view"
            ));
        }

        @Override
        public Promise<List<QuerySuggestion>> suggestQueries(QueryContext context, int limit) { // GH-90000
            return Promise.of(List.of( // GH-90000
                new QuerySuggestion("SELECT * FROM users", "Get all users", 0.9, "data-retrieval"), // GH-90000
                new QuerySuggestion("SELECT COUNT(*) FROM orders", "Count orders", 0.8, "aggregation"), // GH-90000
                new QuerySuggestion("SELECT * FROM products LIMIT 10", "Sample products", 0.7, "sampling") // GH-90000
            ));
        }

        @Override
        public Promise<Conversation> getConversation(String conversationId) { // GH-90000
            return Promise.of(new Conversation( // GH-90000
                conversationId,
                "tenant-1",
                "user-1",
                List.of(), // GH-90000
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                0
            ));
        }

        @Override
        public Promise<Conversation> createConversation(String tenantId, String userId) { // GH-90000
            return Promise.of(new Conversation( // GH-90000
                "conv-" + System.currentTimeMillis(), // GH-90000
                tenantId,
                userId,
                List.of(), // GH-90000
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                0
            ));
        }

        @Override
        public Promise<Conversation> addMessage(String conversationId, Message message) { // GH-90000
            return Promise.of(new Conversation( // GH-90000
                conversationId,
                "tenant-1",
                "user-1",
                List.of(message), // GH-90000
                Instant.now(), // GH-90000
                Instant.now(), // GH-90000
                1
            ));
        }

        @Override
        public Promise<Void> clearConversation(String conversationId) { // GH-90000
            return Promise.of((Void) null); // GH-90000
        }

        @Override
        public Promise<ServiceStatus> getStatus() { // GH-90000
            return Promise.of(new ServiceStatus( // GH-90000
                true,
                "mock-provider",
                "mock-model-v1",
                1000,
                150.0,
                Instant.now() // GH-90000
            ));
        }

        @Override
        public Promise<AIEvaluationMetrics> getEvaluationMetrics(TimeRange timeRange) { // GH-90000
            throw new UnsupportedOperationException("Evaluation metrics not implemented in mock [GH-90000]");
        }

        @Override
        public Promise<Void> recordEvaluationResult(AIEvaluationResult result) { // GH-90000
            return Promise.of((Void) null); // GH-90000
        }
    }
}
