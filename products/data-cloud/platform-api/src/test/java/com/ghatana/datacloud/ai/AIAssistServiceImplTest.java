/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AIAssistServiceImpl Tests - 100% Coverage
 *
 * @doc.type class
 * @doc.purpose Comprehensive tests for AIAssistServiceImpl
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("AIAssistServiceImpl Tests")
class AIAssistServiceImplTest extends EventloopTestBase {

    @Mock
    private LLMProvider llmProvider;

    @Mock
    private MetricsCollector metrics;

    private AIAssistServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AIAssistServiceImpl(llmProvider, metrics);
        
        when(llmProvider.getName()).thenReturn("OpenAI");
    }

    @Nested
    @DisplayName("Process Query")
    class ProcessQueryTests {

        @Test
        @DisplayName("[TEST-017]: processQuery_successfully_processes_query")
        void processQuerySuccess() {
            // Given
            String query = "Show me sales data";
            AIAssistService.QueryContext context = new AIAssistService.QueryContext(
                "tenant-alpha", "user-1", null, "sales", 
                List.of("orders", "customers"), Map.of(), null
            );

            when(llmProvider.complete(any())).thenReturn(Promise.of(new LLMProvider.CompletionResponse(
                "resp-1", "SELECT * FROM orders", 10, 5, 5, "stop", 25L, "gpt-4"
            )));

            // When
            AIAssistService.QueryResult result = runPromise(() -> service.processQuery(query, context));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.originalQuery()).isEqualTo(query);
            assertThat(result.confidenceScore()).isEqualTo(0.95);
            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);
            verify(metrics).incrementCounter("ai.query.success", "tenant", "tenant-alpha");
        }
    }

    @Nested
    @DisplayName("Generate SQL")
    class GenerateSQLTests {

        @Test
        @DisplayName("[TEST-018]: generateSQL_returns_generated_sql")
        void generateSQLSuccess() {
            // Given
            String description = "Get all customers from last month";
            AIAssistService.DatabaseSchema schema = new AIAssistService.DatabaseSchema(
                "sales", List.of()
            );

            when(llmProvider.complete(any())).thenReturn(Promise.of(new LLMProvider.CompletionResponse(
                "resp-2", "SELECT * FROM customers", 10, 5, 5, "stop", 25L, "gpt-4"
            )));

            // When
            AIAssistService.GeneratedSQL result = runPromise(() -> service.generateSQL(description, schema));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.sql()).contains("SELECT");
            assertThat(result.isReadOnly()).isTrue();
            assertThat(result.isSafe()).isTrue();
            verify(metrics).incrementCounter("ai.sql.generate.success");
        }
    }

    @Nested
    @DisplayName("Explain Results")
    class ExplainResultsTests {

        @Test
        @DisplayName("[TEST-019]: explainResults_provides_explanation")
        void explainResults() {
            // Given
            String query = "SELECT * FROM orders";
            List<Map<String, Object>> results = List.of(
                Map.of("id", 1, "amount", 100),
                Map.of("id", 2, "amount", 200)
            );
            AIAssistService.QueryContext context = new AIAssistService.QueryContext(
                "tenant-alpha", "user-1", null, null, null, Map.of(), null
            );

            // When
            AIAssistService.Explanation result = runPromise(() -> service.explainResults(query, results, context));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.keyPoints()).contains("Query returned 2 rows");
            assertThat(result.keyPoints()).contains("Query type: SELECT");
            verify(metrics).incrementCounter("ai.explain.success");
        }

        @Test
        @DisplayName("[TEST-019]: explainResults_warns_about_large_result_sets")
        void explainResultsLargeSet() {
            // Given - create large result set
            List<Map<String, Object>> largeResults = new java.util.ArrayList<>();
            for (int i = 0; i < 1500; i++) {
                largeResults.add(Map.of("id", i));
            }

            // When
            AIAssistService.Explanation result = runPromise(() -> 
                service.explainResults("SELECT *", largeResults, null));

            // Then
            assertThat(result.recommendations()).anyMatch(r -> r.contains("LIMIT"));
        }
    }

    @Nested
    @DisplayName("Query Suggestions")
    class SuggestQueriesTests {

        @Test
        @DisplayName("[TEST-020]: suggestQueries_returns_relevant_suggestions")
        void suggestQueries() {
            // Given
            AIAssistService.QueryContext context = new AIAssistService.QueryContext(
                "tenant-alpha", "user-1", null, null,
                List.of("customers", "orders"), Map.of(), null
            );

            // When
            List<AIAssistService.QuerySuggestion> results = runPromise(() -> service.suggestQueries(context, 5));

            // Then
            assertThat(results).hasSize(4); // 2 suggestions per table
            assertThat(results.get(0).category()).isEqualTo("preview");
            assertThat(results.get(1).category()).isEqualTo("aggregation");
            verify(metrics).incrementCounter("ai.suggest.success", "count", "4");
        }
    }

    @Nested
    @DisplayName("Conversation Management")
    class ConversationTests {

        @Test
        @DisplayName("[TEST-021]: createConversation_creates_new_conversation")
        void createConversation() {
            // When
            AIAssistService.Conversation result = runPromise(() -> 
                service.createConversation("tenant-alpha", "user-1"));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isNotNull();
            assertThat(result.tenantId()).isEqualTo("tenant-alpha");
            assertThat(result.userId()).isEqualTo("user-1");
            assertThat(result.messageCount()).isEqualTo(0);
            verify(metrics).incrementCounter("ai.conversation.create", "tenant", "tenant-alpha");
        }

        @Test
        @DisplayName("[TEST-022]: addMessage_adds_message_to_conversation")
        void addMessage() {
            // Given
            AIAssistService.Conversation conv = runPromise(() -> 
                service.createConversation("tenant-alpha", "user-1"));
            
            AIAssistService.Message message = new AIAssistService.Message(
                "msg-1", AIAssistService.MessageRole.USER, "Hello", Instant.now(), Map.of()
            );

            // When
            AIAssistService.Conversation result = runPromise(() -> service.addMessage(conv.id(), message));

            // Then
            assertThat(result.messageCount()).isEqualTo(1);
            assertThat(result.messages()).hasSize(1);
            verify(metrics).incrementCounter("ai.conversation.message.add", "conversation", conv.id());
        }

        @Test
        @DisplayName("[TEST-023]: getConversation_retrieves_conversation")
        void getConversation() {
            // Given
            AIAssistService.Conversation conv = runPromise(() -> 
                service.createConversation("tenant-alpha", "user-1"));

            // When
            AIAssistService.Conversation result = runPromise(() -> service.getConversation(conv.id()));

            // Then
            assertThat(result.id()).isEqualTo(conv.id());
        }

        @Test
        @DisplayName("[TEST-023]: clearConversation_removes_all_messages")
        void clearConversation() {
            // Given
            AIAssistService.Conversation conv = runPromise(() -> 
                service.createConversation("tenant-alpha", "user-1"));
            
            AIAssistService.Message message = new AIAssistService.Message(
                "msg-1", AIAssistService.MessageRole.USER, "Hello", Instant.now(), Map.of()
            );
            runPromise(() -> service.addMessage(conv.id(), message));

            // When
            runPromise(() -> service.clearConversation(conv.id()));
            AIAssistService.Conversation result = runPromise(() -> service.getConversation(conv.id()));

            // Then
            assertThat(result.messageCount()).isEqualTo(0);
            assertThat(result.messages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Service Status")
    class StatusTests {

        @Test
        @DisplayName("[TEST-024]: getStatus_returns_service_status")
        void getStatus() {
            // Given - process some queries to generate stats
            AIAssistService.QueryContext context = new AIAssistService.QueryContext(
                "tenant-alpha", "user-1", null, null, null, Map.of(), null
            );
            when(llmProvider.complete(any())).thenReturn(Promise.of(new LLMProvider.CompletionResponse(
                "resp-3", "SQL", 10, 5, 5, "stop", 25L, "gpt-4"
            )));
            
            runPromise(() -> service.processQuery("query 1", context));
            runPromise(() -> service.processQuery("query 2", context));

            // When
            AIAssistService.ServiceStatus status = runPromise(() -> service.getStatus());

            // Then
            assertThat(status.available()).isTrue();
            assertThat(status.provider()).isEqualTo("OpenAI");
            assertThat(status.model()).isEqualTo("unknown");
            assertThat(status.requestsProcessed()).isEqualTo(2);
            assertThat(status.averageLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(status.lastHealthCheck()).isNotNull();
        }
    }
}
