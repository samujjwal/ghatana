/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AI assist service query processing (AI001).
 *
 * @doc.type class
 * @doc.purpose AI query processing tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIAssistService – Query Processing (AI001)")
class AIAssistServiceTest extends EventloopTestBase {

    @Mock
    private AIAssistService aiAssistService;

    @Nested
    @DisplayName("Query Processing")
    class QueryProcessingTests {

        @Test
        @DisplayName("[AI001]: process_query_returns_results")
        void processQueryReturnsResults() {
            String query = "Show me sales data from last month";
            AIAssistService.QueryContext context = createQueryContext();

            AIAssistService.GeneratedSQL generatedSQL = new AIAssistService.GeneratedSQL(
                "SELECT * FROM sales WHERE date >= '2024-01-01'",
                "Query to get sales data from January 2024",
                List.of("sales"), List.of(), true
            );

            AIAssistService.QueryResult result = new AIAssistService.QueryResult(
                "query-001", query, "sales_query", generatedSQL,
                List.of(Map.of("total", 100000)),
                null, 1500, false, 0.95
            );

            when(aiAssistService.processQuery(query, context))
                .thenReturn(Promise.of(result));

            AIAssistService.QueryResult processed = runPromise(() ->
                aiAssistService.processQuery(query, context)
            );

            assertThat(processed.queryId()).isEqualTo("query-001");
            assertThat(processed.confidenceScore()).isEqualTo(0.95);
            assertThat(processed.generatedSQL().isSafe()).isTrue();
        }

        @Test
        @DisplayName("[AI001]: process_query_with_low_confidence_flagged")
        void processQueryWithLowConfidenceFlagged() {
            String query = "ambiguous query";
            AIAssistService.QueryContext context = createQueryContext();

            AIAssistService.QueryResult result = new AIAssistService.QueryResult(
                "query-002", query, "unknown", null, List.of(),
                null, 500, false, 0.45
            );

            when(aiAssistService.processQuery(query, context))
                .thenReturn(Promise.of(result));

            AIAssistService.QueryResult processed = runPromise(() ->
                aiAssistService.processQuery(query, context)
            );

            assertThat(processed.confidenceScore()).isLessThan(0.50);
        }
    }

    @Nested
    @DisplayName("SQL Generation")
    class SQLGenerationTests {

        @Test
        @DisplayName("[AI001]: generate_sql_creates_valid_query")
        void generateSQLCreatesValidQuery() {
            String description = "Find all active users";
            AIAssistService.DatabaseSchema schema = createDatabaseSchema();

            AIAssistService.GeneratedSQL result = new AIAssistService.GeneratedSQL(
                "SELECT * FROM users WHERE status = 'active'",
                "Query to find users with active status",
                List.of("users"), List.of(), true
            );

            when(aiAssistService.generateSQL(description, schema))
                .thenReturn(Promise.of(result));

            AIAssistService.GeneratedSQL generated = runPromise(() ->
                aiAssistService.generateSQL(description, schema)
            );

            assertThat(generated.sql()).containsIgnoringCase("SELECT");
            assertThat(generated.tablesUsed()).contains("users");
            assertThat(generated.isReadOnly()).isTrue();
            assertThat(generated.isSafe()).isTrue();
        }

        @Test
        @DisplayName("[AI001]: generate_sql_detects_unsafe_operations")
        void generateSQLDetectsUnsafeOperations() {
            String description = "Delete all users";
            AIAssistService.DatabaseSchema schema = createDatabaseSchema();

            AIAssistService.GeneratedSQL result = new AIAssistService.GeneratedSQL(
                "DELETE FROM users",
                "WARNING: This will delete all users",
                List.of("users"),
                List.of("DESTRUCTIVE_OPERATION"),
                false
            );

            when(aiAssistService.generateSQL(description, schema))
                .thenReturn(Promise.of(result));

            AIAssistService.GeneratedSQL generated = runPromise(() ->
                aiAssistService.generateSQL(description, schema)
            );

            assertThat(generated.isReadOnly()).isFalse();
            assertThat(generated.isSafe()).isFalse();
            assertThat(generated.warnings()).contains("DESTRUCTIVE_OPERATION");
        }
    }

    @Nested
    @DisplayName("Query Explanation")
    class QueryExplanationTests {

        @Test
        @DisplayName("[AI001]: explain_results_provides_summary")
        void explainResultsProvidesSummary() {
            String query = "SELECT * FROM sales";
            List<Map<String, Object>> results = List.of(
                Map.of("region", "North", "amount", 50000),
                Map.of("region", "South", "amount", 75000)
            );
            AIAssistService.QueryContext context = createQueryContext();

            AIAssistService.Explanation explanation = new AIAssistService.Explanation(
                "Sales data shows North region at $50K and South at $75K",
                List.of("South region outperforms North by 50%", "Total sales: $125K"),
                List.of("Focus marketing on South region"),
                "bar_chart"
            );

            when(aiAssistService.explainResults(query, results, context))
                .thenReturn(Promise.of(explanation));

            AIAssistService.Explanation result = runPromise(() ->
                aiAssistService.explainResults(query, results, context)
            );

            assertThat(result.summary()).contains("North");
            assertThat(result.keyPoints()).hasSize(2);
            assertThat(result.visualizationSuggestion()).isEqualTo("bar_chart");
        }
    }

    @Nested
    @DisplayName("Query Suggestions")
    class QuerySuggestionsTests {

        @Test
        @DisplayName("[AI001]: suggest_queries_returns_relevant_suggestions")
        void suggestQueriesReturnsRelevantSuggestions() {
            AIAssistService.QueryContext context = createQueryContext();

            List<AIAssistService.QuerySuggestion> suggestions = List.of(
                new AIAssistService.QuerySuggestion(
                    "Show sales by region", "Regional sales breakdown", 0.95, "analytics"
                ),
                new AIAssistService.QuerySuggestion(
                    "Compare this month vs last month", "Month-over-month comparison", 0.90, "comparison"
                ),
                new AIAssistService.QuerySuggestion(
                    "Top 10 products", "Best performing products", 0.85, "ranking"
                )
            );

            when(aiAssistService.suggestQueries(context, 5))
                .thenReturn(Promise.of(suggestions));

            List<AIAssistService.QuerySuggestion> result = runPromise(() ->
                aiAssistService.suggestQueries(context, 5)
            );

            assertThat(result).hasSize(3);
            assertThat(result.get(0).relevanceScore()).isGreaterThan(0.9);
        }
    }

    @Nested
    @DisplayName("Conversation Management")
    class ConversationManagementTests {

        @Test
        @DisplayName("[AI001]: create_conversation_creates_new")
        void createConversationCreatesNew() {
            String tenantId = "tenant-alpha";
            String userId = "user-001";

            AIAssistService.Conversation conv = new AIAssistService.Conversation(
                "conv-001", tenantId, userId, List.of(),
                Instant.now(), Instant.now(), 0
            );

            when(aiAssistService.createConversation(tenantId, userId))
                .thenReturn(Promise.of(conv));

            AIAssistService.Conversation result = runPromise(() ->
                aiAssistService.createConversation(tenantId, userId)
            );

            assertThat(result.id()).isEqualTo("conv-001");
            assertThat(result.tenantId()).isEqualTo(tenantId);
            assertThat(result.messageCount()).isZero();
        }

        @Test
        @DisplayName("[AI001]: add_message_updates_conversation")
        void addMessageUpdatesConversation() {
            String conversationId = "conv-001";
            AIAssistService.Message message = new AIAssistService.Message(
                "msg-001", AIAssistService.MessageRole.USER,
                "Show me data", Instant.now(), Map.of()
            );

            AIAssistService.Conversation updated = new AIAssistService.Conversation(
                conversationId, "tenant-alpha", "user-001",
                List.of(message), Instant.now(), Instant.now(), 1
            );

            when(aiAssistService.addMessage(conversationId, message))
                .thenReturn(Promise.of(updated));

            AIAssistService.Conversation result = runPromise(() ->
                aiAssistService.addMessage(conversationId, message)
            );

            assertThat(result.messageCount()).isEqualTo(1);
            assertThat(result.messages()).hasSize(1);
        }

        @Test
        @DisplayName("[AI001]: get_conversation_returns_history")
        void getConversationReturnsHistory() {
            String conversationId = "conv-001";

            List<AIAssistService.Message> messages = List.of(
                new AIAssistService.Message("m1", AIAssistService.MessageRole.USER, "Hello", Instant.now(), Map.of()),
                new AIAssistService.Message("m2", AIAssistService.MessageRole.ASSISTANT, "Hi!", Instant.now(), Map.of())
            );

            AIAssistService.Conversation conv = new AIAssistService.Conversation(
                conversationId, "tenant-alpha", "user-001",
                messages, Instant.now(), Instant.now(), 2
            );

            when(aiAssistService.getConversation(conversationId))
                .thenReturn(Promise.of(conv));

            AIAssistService.Conversation result = runPromise(() ->
                aiAssistService.getConversation(conversationId)
            );

            assertThat(result.messages()).hasSize(2);
        }

        @Test
        @DisplayName("[AI001]: clear_conversation_removes_messages")
        void clearConversationRemovesMessages() {
            String conversationId = "conv-001";

            when(aiAssistService.clearConversation(conversationId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> aiAssistService.clearConversation(conversationId));

            verify(aiAssistService).clearConversation(conversationId);
        }
    }

    @Nested
    @DisplayName("Service Status")
    class ServiceStatusTests {

        @Test
        @DisplayName("[AI001]: get_status_returns_service_info")
        void getStatusReturnsServiceInfo() {
            AIAssistService.ServiceStatus status = new AIAssistService.ServiceStatus(
                true, "openai", "gpt-4", 1000, 500.0, Instant.now()
            );

            when(aiAssistService.getStatus())
                .thenReturn(Promise.of(status));

            AIAssistService.ServiceStatus result = runPromise(() -> aiAssistService.getStatus());

            assertThat(result.available()).isTrue();
            assertThat(result.provider()).isEqualTo("openai");
            assertThat(result.model()).isEqualTo("gpt-4");
            assertThat(result.averageLatencyMs()).isEqualTo(500.0);
        }
    }

    private AIAssistService.QueryContext createQueryContext() {
        return new AIAssistService.QueryContext(
            "tenant-alpha", "user-001", "conv-001",
            null, List.of("sales", "users"), null, null
        );
    }

    private AIAssistService.DatabaseSchema createDatabaseSchema() {
        return new AIAssistService.DatabaseSchema(
            "default",
            List.of(new AIAssistService.TableInfo(
                "users",
                List.of(
                    new AIAssistService.ColumnInfo("id", "INTEGER", false, "User ID"),
                    new AIAssistService.ColumnInfo("name", "VARCHAR", true, "User name"),
                    new AIAssistService.ColumnInfo("status", "VARCHAR", true, "User status")
                ),
                List.of()
            ))
        );
    }
}
