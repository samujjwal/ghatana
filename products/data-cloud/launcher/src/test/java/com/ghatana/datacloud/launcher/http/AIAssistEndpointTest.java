/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP integration tests for AI assist endpoints (AI004).
 *
 * @doc.type class
 * @doc.purpose AI assist endpoint tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("AIAssistEndpoint – AI HTTP Endpoints (AI004)")
class AIAssistEndpointTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("Query Processing")
    class QueryProcessingTests {

        @Test
        @DisplayName("[AI004]: process_query_via_api")
        void processQueryViaApi() throws Exception {
            Map<String, Object> request = Map.of(
                "query", "Show me sales data from last month",
                "tenantId", "tenant-alpha",
                "userId", "user-001"
            );

            Map<String, Object> response = Map.of(
                "queryId", "query-001",
                "originalQuery", "Show me sales data from last month",
                "interpretedIntent", "sales_query",
                "confidenceScore", 0.95,
                "processingTimeMs", 1200
            );

            lenient().when(mockClient.processAIQuery(any(), any()))
                .thenReturn(Promise.of(response));

            var httpResponse = postJson("/api/v1/ai/query", request, withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("queryId")).isEqualTo("query-001");
            assertThat(body.get("confidenceScore")).isEqualTo(0.95);
        }

        @Test
        @DisplayName("[AI004]: generate_sql_via_api")
        void generateSQLViaApi() throws Exception {
            Map<String, Object> request = Map.of(
                "description", "Find all active users",
                "schema", Map.of(
                    "name", "default",
                    "tables", List.of(Map.of("name", "users"))
                )
            );

            Map<String, Object> response = Map.of(
                "sql", "SELECT * FROM users WHERE status = 'active'",
                "explanation", "Query to find users with active status",
                "tablesUsed", List.of("users"),
                "isReadOnly", true
            );

            lenient().when(mockClient.generateSQL(any(), any(), any()))
                .thenReturn(Promise.of(response));

            var httpResponse = postJson("/api/v1/ai/sql", request, withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("sql")).isEqualTo("SELECT * FROM users WHERE status = 'active'");
        }
    }

    @Nested
    @DisplayName("Conversation Management")
    class ConversationManagementTests {

        @Test
        @DisplayName("[AI004]: create_conversation_via_api")
        void createConversationViaApi() throws Exception {
            Map<String, Object> response = Map.of(
                "id", "conv-001",
                "tenantId", "tenant-alpha",
                "userId", "user-001",
                "messageCount", 0,
                "createdAt", Instant.now().toString()
            );

            lenient().when(mockClient.createAIConversation(any(), any()))
                .thenReturn(Promise.of(response));

            var httpResponse = post("/api/v1/ai/conversations", withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("id")).isEqualTo("conv-001");
        }

        @Test
        @DisplayName("[AI004]: get_conversation_via_api")
        void getConversationViaApi() throws Exception {
            String conversationId = "conv-001";

            Map<String, Object> response = Map.of(
                "id", conversationId,
                "messages", List.of(
                    Map.of("role", "USER", "content", "Hello"),
                    Map.of("role", "ASSISTANT", "content", "Hi! How can I help?")
                ),
                "messageCount", 2
            );

            lenient().when(mockClient.getAIConversation(any(), eq(conversationId)))
                .thenReturn(Promise.of(response));

            var httpResponse = get("/api/v1/ai/conversations/" + conversationId, withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
            assertThat(messages).hasSize(2);
        }

        @Test
        @DisplayName("[AI004]: add_message_via_api")
        void addMessageViaApi() throws Exception {
            String conversationId = "conv-001";

            Map<String, Object> request = Map.of(
                "content", "Show me sales data"
            );

            Map<String, Object> response = Map.of(
                "id", conversationId,
                "messageCount", 3,
                "lastMessage", Map.of("role", "USER", "content", "Show me sales data")
            );

            lenient().when(mockClient.addAIMessage(any(), eq(conversationId), any()))
                .thenReturn(Promise.of(response));

            var httpResponse = postJson("/api/v1/ai/conversations/" + conversationId + "/messages",
                request, withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("messageCount")).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Suggestions")
    class SuggestionsTests {

        @Test
        @DisplayName("[AI004]: get_suggestions_via_api")
        void getSuggestionsViaApi() throws Exception {
            String conversationId = "conv-001";

            List<Map<String, Object>> suggestions = List.of(
                Map.of("query", "Show sales by region", "relevanceScore", 0.95),
                Map.of("query", "Compare monthly sales", "relevanceScore", 0.90),
                Map.of("query", "Top products", "relevanceScore", 0.85)
            );

            lenient().when(mockClient.getAIQuerySuggestions(any(), eq(conversationId), anyInt()))
                .thenReturn(Promise.of(Map.of("suggestions", suggestions)));

            var httpResponse = get("/api/v1/ai/suggestions?conversationId=" + conversationId + "&limit=5",
                withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) body.get("suggestions");
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Explanation")
    class ExplanationTests {

        @Test
        @DisplayName("[AI004]: explain_results_via_api")
        void explainResultsViaApi() throws Exception {
            Map<String, Object> request = Map.of(
                "query", "SELECT * FROM sales",
                "results", List.of(
                    Map.of("region", "North", "amount", 50000),
                    Map.of("region", "South", "amount", 75000)
                )
            );

            Map<String, Object> response = Map.of(
                "summary", "Sales data shows North at $50K and South at $75K",
                "keyPoints", List.of("South outperforms North by 50%"),
                "visualizationSuggestion", "bar_chart"
            );

            lenient().when(mockClient.explainAIResults(any(), any(), any()))
                .thenReturn(Promise.of(response));

            var httpResponse = postJson("/api/v1/ai/explain", request, withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("summary")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Status")
    class StatusTests {

        @Test
        @DisplayName("[AI004]: get_status_via_api")
        void getStatusViaApi() throws Exception {
            Map<String, Object> response = Map.of(
                "available", true,
                "provider", "openai",
                "model", "gpt-4",
                "requestsProcessed", 10000,
                "averageLatencyMs", 500.0
            );

            lenient().when(mockClient.getAIStatus(any()))
                .thenReturn(Promise.of(response));

            var httpResponse = get("/api/v1/ai/status", withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("available")).isEqualTo(true);
            assertThat(body.get("provider")).isEqualTo("openai");
        }
    }

    @Nested
    @DisplayName("Template Management")
    class TemplateManagementTests {

        @Test
        @DisplayName("[AI004]: list_templates_via_api")
        void listTemplatesViaApi() throws Exception {
            List<Map<String, Object>> templates = List.of(
                Map.of("id", "t1", "name", "SQL Generator", "category", "sql"),
                Map.of("id", "t2", "name", "Data Analyzer", "category", "analytics")
            );

            lenient().when(mockClient.listAITemplates(any()))
                .thenReturn(Promise.of(Map.of("templates", templates, "total", 2)));

            var httpResponse = get("/api/v1/ai/templates", withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) body.get("templates");
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("[AI004]: render_template_via_api")
        void renderTemplateViaApi() throws Exception {
            String templateId = "greeting";

            Map<String, Object> request = Map.of(
                "name", "Alice",
                "day", "Monday"
            );

            Map<String, Object> response = Map.of(
                "rendered", "Hello Alice! Happy Monday!"
            );

            lenient().when(mockClient.renderAITemplate(any(), eq(templateId), any()))
                .thenReturn(Promise.of(response));

            var httpResponse = postJson("/api/v1/ai/templates/" + templateId + "/render",
                request, withTenant("tenant-alpha"));

            assertStatusCode(httpResponse, 200);
            Map<String, Object> body = parseJsonResponse(httpResponse);
            assertThat(body.get("rendered")).isEqualTo("Hello Alice! Happy Monday!");
        }
    }
}
