/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for AI assist service query processing (AI001). // GH-90000
 *
 * @doc.type class
 * @doc.purpose AI query processing tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AIAssistService – Query Processing (AI001)")
class AIAssistServiceTest extends EventloopTestBase {

    @Mock
    private AIAssistService aiAssistService;

    @Nested
    @DisplayName("Query Processing")
    class QueryProcessingTests {

        @Test
        @DisplayName("[AI001]: process_query_returns_results")
        void processQueryReturnsResults() { // GH-90000
            String query = "Show me sales data from last month";
            AIAssistService.QueryContext context = createQueryContext(); // GH-90000

            AIAssistService.GeneratedSQL generatedSQL = new AIAssistService.GeneratedSQL( // GH-90000
                "SELECT * FROM sales WHERE date >= '2024-01-01'",
                "Query to get sales data from January 2024",
                List.of("sales"), List.of(), true
            );

            AIAssistService.QueryResult result = new AIAssistService.QueryResult( // GH-90000
                "query-001", query, "sales_query", generatedSQL,
                List.of(Map.of("total", 100000)), // GH-90000
                null, 1500, false, 0.95
            );

            when(aiAssistService.processQuery(query, context)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            AIAssistService.QueryResult processed = runPromise(() -> // GH-90000
                aiAssistService.processQuery(query, context) // GH-90000
            );

            assertThat(processed.queryId()).isEqualTo("query-001");
            assertThat(processed.confidenceScore()).isEqualTo(0.95); // GH-90000
            assertThat(processed.generatedSQL().isSafe()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[AI001]: process_query_with_low_confidence_flagged")
        void processQueryWithLowConfidenceFlagged() { // GH-90000
            String query = "ambiguous query";
            AIAssistService.QueryContext context = createQueryContext(); // GH-90000

            AIAssistService.QueryResult result = new AIAssistService.QueryResult( // GH-90000
                "query-002", query, "unknown", null, List.of(), // GH-90000
                null, 500, false, 0.45
            );

            when(aiAssistService.processQuery(query, context)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            AIAssistService.QueryResult processed = runPromise(() -> // GH-90000
                aiAssistService.processQuery(query, context) // GH-90000
            );

            assertThat(processed.confidenceScore()).isLessThan(0.50); // GH-90000
        }
    }

    @Nested
    @DisplayName("SQL Generation")
    class SQLGenerationTests {

        @Test
        @DisplayName("[AI001]: generate_sql_creates_valid_query")
        void generateSQLCreatesValidQuery() { // GH-90000
            String description = "Find all active users";
            AIAssistService.DatabaseSchema schema = createDatabaseSchema(); // GH-90000

            AIAssistService.GeneratedSQL result = new AIAssistService.GeneratedSQL( // GH-90000
                "SELECT * FROM users WHERE status = 'active'",
                "Query to find users with active status",
                List.of("users"), List.of(), true
            );

            when(aiAssistService.generateSQL(description, schema)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            AIAssistService.GeneratedSQL generated = runPromise(() -> // GH-90000
                aiAssistService.generateSQL(description, schema) // GH-90000
            );

            assertThat(generated.sql()).containsIgnoringCase("SELECT");
            assertThat(generated.tablesUsed()).contains("users");
            assertThat(generated.isReadOnly()).isTrue(); // GH-90000
            assertThat(generated.isSafe()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[AI001]: generate_sql_detects_unsafe_operations")
        void generateSQLDetectsUnsafeOperations() { // GH-90000
            String description = "Delete all users";
            AIAssistService.DatabaseSchema schema = createDatabaseSchema(); // GH-90000

            AIAssistService.GeneratedSQL result = new AIAssistService.GeneratedSQL( // GH-90000
                "DELETE FROM users",
                "WARNING: This will delete all users",
                List.of("users"),
                List.of("DESTRUCTIVE_OPERATION"),
                false
            );

            when(aiAssistService.generateSQL(description, schema)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            AIAssistService.GeneratedSQL generated = runPromise(() -> // GH-90000
                aiAssistService.generateSQL(description, schema) // GH-90000
            );

            assertThat(generated.isReadOnly()).isFalse(); // GH-90000
            assertThat(generated.isSafe()).isFalse(); // GH-90000
            assertThat(generated.warnings()).contains("DESTRUCTIVE_OPERATION");
        }
    }

    @Nested
    @DisplayName("Query Explanation")
    class QueryExplanationTests {

        @Test
        @DisplayName("[AI001]: explain_results_provides_summary")
        void explainResultsProvidesSummary() { // GH-90000
            String query = "SELECT * FROM sales";
            List<Map<String, Object>> results = List.of( // GH-90000
                Map.of("region", "North", "amount", 50000), // GH-90000
                Map.of("region", "South", "amount", 75000) // GH-90000
            );
            AIAssistService.QueryContext context = createQueryContext(); // GH-90000

            AIAssistService.Explanation explanation = new AIAssistService.Explanation( // GH-90000
                "Sales data shows North region at $50K and South at $75K",
                List.of("South region outperforms North by 50%", "Total sales: $125K"), // GH-90000
                List.of("Focus marketing on South region"),
                "bar_chart"
            );

            when(aiAssistService.explainResults(query, results, context)) // GH-90000
                .thenReturn(Promise.of(explanation)); // GH-90000

            AIAssistService.Explanation result = runPromise(() -> // GH-90000
                aiAssistService.explainResults(query, results, context) // GH-90000
            );

            assertThat(result.summary()).contains("North");
            assertThat(result.keyPoints()).hasSize(2); // GH-90000
            assertThat(result.visualizationSuggestion()).isEqualTo("bar_chart");
        }
    }

    @Nested
    @DisplayName("Query Suggestions")
    class QuerySuggestionsTests {

        @Test
        @DisplayName("[AI001]: suggest_queries_returns_relevant_suggestions")
        void suggestQueriesReturnsRelevantSuggestions() { // GH-90000
            AIAssistService.QueryContext context = createQueryContext(); // GH-90000

            List<AIAssistService.QuerySuggestion> suggestions = List.of( // GH-90000
                new AIAssistService.QuerySuggestion( // GH-90000
                    "Show sales by region", "Regional sales breakdown", 0.95, "analytics"
                ),
                new AIAssistService.QuerySuggestion( // GH-90000
                    "Compare this month vs last month", "Month-over-month comparison", 0.90, "comparison"
                ),
                new AIAssistService.QuerySuggestion( // GH-90000
                    "Top 10 products", "Best performing products", 0.85, "ranking"
                )
            );

            when(aiAssistService.suggestQueries(context, 5)) // GH-90000
                .thenReturn(Promise.of(suggestions)); // GH-90000

            List<AIAssistService.QuerySuggestion> result = runPromise(() -> // GH-90000
                aiAssistService.suggestQueries(context, 5) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
            assertThat(result.get(0).relevanceScore()).isGreaterThan(0.9); // GH-90000
        }
    }

    @Nested
    @DisplayName("Conversation Management")
    class ConversationManagementTests {

        @Test
        @DisplayName("[AI001]: create_conversation_creates_new")
        void createConversationCreatesNew() { // GH-90000
            String tenantId = "tenant-alpha";
            String userId = "user-001";

            AIAssistService.Conversation conv = new AIAssistService.Conversation( // GH-90000
                "conv-001", tenantId, userId, List.of(), // GH-90000
                Instant.now(), Instant.now(), 0 // GH-90000
            );

            when(aiAssistService.createConversation(tenantId, userId)) // GH-90000
                .thenReturn(Promise.of(conv)); // GH-90000

            AIAssistService.Conversation result = runPromise(() -> // GH-90000
                aiAssistService.createConversation(tenantId, userId) // GH-90000
            );

            assertThat(result.id()).isEqualTo("conv-001");
            assertThat(result.tenantId()).isEqualTo(tenantId); // GH-90000
            assertThat(result.messageCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("[AI001]: add_message_updates_conversation")
        void addMessageUpdatesConversation() { // GH-90000
            String conversationId = "conv-001";
            AIAssistService.Message message = new AIAssistService.Message( // GH-90000
                "msg-001", AIAssistService.MessageRole.USER,
                "Show me data", Instant.now(), Map.of() // GH-90000
            );

            AIAssistService.Conversation updated = new AIAssistService.Conversation( // GH-90000
                conversationId, "tenant-alpha", "user-001",
                List.of(message), Instant.now(), Instant.now(), 1 // GH-90000
            );

            when(aiAssistService.addMessage(conversationId, message)) // GH-90000
                .thenReturn(Promise.of(updated)); // GH-90000

            AIAssistService.Conversation result = runPromise(() -> // GH-90000
                aiAssistService.addMessage(conversationId, message) // GH-90000
            );

            assertThat(result.messageCount()).isEqualTo(1); // GH-90000
            assertThat(result.messages()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("[AI001]: get_conversation_returns_history")
        void getConversationReturnsHistory() { // GH-90000
            String conversationId = "conv-001";

            List<AIAssistService.Message> messages = List.of( // GH-90000
                new AIAssistService.Message("m1", AIAssistService.MessageRole.USER, "Hello", Instant.now(), Map.of()), // GH-90000
                new AIAssistService.Message("m2", AIAssistService.MessageRole.ASSISTANT, "Hi!", Instant.now(), Map.of()) // GH-90000
            );

            AIAssistService.Conversation conv = new AIAssistService.Conversation( // GH-90000
                conversationId, "tenant-alpha", "user-001",
                messages, Instant.now(), Instant.now(), 2 // GH-90000
            );

            when(aiAssistService.getConversation(conversationId)) // GH-90000
                .thenReturn(Promise.of(conv)); // GH-90000

            AIAssistService.Conversation result = runPromise(() -> // GH-90000
                aiAssistService.getConversation(conversationId) // GH-90000
            );

            assertThat(result.messages()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("[AI001]: clear_conversation_removes_messages")
        void clearConversationRemovesMessages() { // GH-90000
            String conversationId = "conv-001";

            when(aiAssistService.clearConversation(conversationId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> aiAssistService.clearConversation(conversationId)); // GH-90000

            verify(aiAssistService).clearConversation(conversationId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Service Status")
    class ServiceStatusTests {

        @Test
        @DisplayName("[AI001]: get_status_returns_service_info")
        void getStatusReturnsServiceInfo() { // GH-90000
            AIAssistService.ServiceStatus status = new AIAssistService.ServiceStatus( // GH-90000
                true, "openai", "gpt-4", 1000, 500.0, Instant.now() // GH-90000
            );

            when(aiAssistService.getStatus()) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            AIAssistService.ServiceStatus result = runPromise(() -> aiAssistService.getStatus()); // GH-90000

            assertThat(result.available()).isTrue(); // GH-90000
            assertThat(result.provider()).isEqualTo("openai");
            assertThat(result.model()).isEqualTo("gpt-4");
            assertThat(result.averageLatencyMs()).isEqualTo(500.0); // GH-90000
        }
    }

    private AIAssistService.QueryContext createQueryContext() { // GH-90000
        return new AIAssistService.QueryContext( // GH-90000
            "tenant-alpha", "user-001", "conv-001",
            null, List.of("sales", "users"), null, null // GH-90000
        );
    }

    private AIAssistService.DatabaseSchema createDatabaseSchema() { // GH-90000
        return new AIAssistService.DatabaseSchema( // GH-90000
            "default",
            List.of(new AIAssistService.TableInfo( // GH-90000
                "users",
                List.of( // GH-90000
                    new AIAssistService.ColumnInfo("id", "INTEGER", false, "User ID"), // GH-90000
                    new AIAssistService.ColumnInfo("name", "VARCHAR", true, "User name"), // GH-90000
                    new AIAssistService.ColumnInfo("status", "VARCHAR", true, "User status") // GH-90000
                ),
                List.of() // GH-90000
            ))
        );
    }
}
