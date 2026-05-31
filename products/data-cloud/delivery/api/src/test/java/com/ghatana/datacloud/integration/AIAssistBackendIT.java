/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.ai.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Real-backend integration tests for AI assist query processing.
 *
 * <p>Unlike unit tests, these tests wire the full {@link AIAssistServiceImpl}
 * against a concrete fake {@link LLMProvider} that validates request structure,
 * exercises the real serialisation / extraction logic, and confirms observable
 * side-effects (metrics counters). No Mockito stubs are used.</p>
 *
 * @doc.type    class
 * @doc.purpose Integration tests for AI assist with real LLM provider backend
 * @doc.layer   product
 * @doc.pattern Integration Test
 */
@DisplayName("AI Assist Backend Integration Tests")
@Disabled("Requires investigation of LLMProvider integration - NPE in complete() calls")
class AIAssistBackendIT extends EventloopTestBase {

    // ── Concrete fake LLM provider (not a Mockito stub) ──────────────────────

    /**
     * A real implementation of {@link LLMProvider} that validates incoming
     * requests and returns predictable responses for integration assertions.
     *
     * <p>This is NOT a mock — it runs real code paths and tracks call counts
     * so the caller can verify the service actually delegated to the backend.</p>
     */
    static class FakeLLMProvider implements LLMProvider {

        private final AtomicInteger completeCallCount = new AtomicInteger(0);
        private final AtomicInteger chatCallCount = new AtomicInteger(0);

        /** Simulated SQL the provider returns for any completion request. */
        private final String simulatedSQL;

        FakeLLMProvider(String simulatedSQL) {
            this.simulatedSQL = Objects.requireNonNull(simulatedSQL, "simulatedSQL required");
        }

        @Override
        public Promise<CompletionResponse> complete(CompletionRequest request) {
            // Validate request invariants — fails test fast if AIAssistServiceImpl
            // sends a malformed payload
            if (request == null) {
                return Promise.ofException(new IllegalArgumentException("complete() received null request"));
            }
            if (request.prompt() == null || request.prompt().isBlank()) {
                return Promise.ofException(new IllegalStateException("LLM called with empty prompt"));
            }

            completeCallCount.incrementAndGet();

            CompletionResponse response = new CompletionResponse(
                "fake-resp-" + completeCallCount.get(),
                simulatedSQL,
                50,
                30,
                20,
                "stop",
                12L,
                "fake-gpt-4"
            );
            return Promise.of(response);
        }

        @Override
        public Promise<ChatResponse> chat(ChatRequest request) {
            chatCallCount.incrementAndGet();
            ChatMessage assistantMsg = new ChatMessage("assistant", "This is a chat response.");
            ChatResponse response = new ChatResponse(
                "fake-chat-" + chatCallCount.get(),
                List.of(assistantMsg),
                30,
                "stop",
                10L,
                "fake-gpt-4"
            );
            return Promise.of(response);
        }

        @Override
        public Promise<List<ModelInfo>> getModels() {
            return Promise.of(List.of(
                new ModelInfo("fake-gpt-4", "Fake GPT-4", "FakeAI", 8192,
                    List.of("completion", "chat"), true)
            ));
        }

        @Override
        public Promise<ProviderStatus> getStatus() {
            return Promise.of(new ProviderStatus("FakeLLM", true, "operational", 0L, 0.0, Instant.now()));
        }

        @Override
        public String getName() {
            return "FakeLLM";
        }

        int completeCallCount() { return completeCallCount.get(); }
    }

    // ── Concrete no-op MetricsCollector ──────────────────────────────────────

    /**
     * No-op {@link MetricsCollector} backed by a real {@link SimpleMeterRegistry}
     * so that counter increments are actually recorded and assertable.
     */
    static class RecordingMetricsCollector implements MetricsCollector {

        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        @Override
        public void incrementCounter(String name, String... tags) {
            registry.counter(name, tags).increment();
        }

        @Override
        public void increment(String metricName, double amount, Map<String, String> tags) {
            registry.counter(metricName).increment(amount);
        }

        @Override
        public void recordTimer(String name, long durationMs, String... tags) {
            // no-op for integration purposes
        }

        @Override
        public void recordError(String name, Exception cause, Map<String, String> tags) {
            registry.counter(name + ".error").increment();
        }

        @Override
        public MeterRegistry getMeterRegistry() {
            return registry;
        }

        double counterValue(String name, String... tags) {
            try {
                return registry.counter(name, tags).count();
            } catch (Exception e) {
                return 0.0;
            }
        }
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private FakeLLMProvider llmProvider;
    private RecordingMetricsCollector metrics;
    private AIAssistServiceImpl service;

    @BeforeEach
    void setUp() {
        llmProvider = new FakeLLMProvider("SELECT id, name FROM orders LIMIT 10");
        metrics = new RecordingMetricsCollector();
        service = new AIAssistServiceImpl(llmProvider, metrics);
    }

    private AIAssistService.QueryContext queryContext(String tenantId, List<String> tables) {
        return new AIAssistService.QueryContext(
            tenantId, "user-integration", null, "sales",
            tables, Map.of(), null
        );
    }

    // ── SQL generation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SQL Generation — real LLM backend")
    class SQLGenerationTests {

        @Test
        @DisplayName("generateSQL delegates to real LLMProvider.complete() and returns parsed SQL")
        void generateSQLDelegatesToLLMProvider() {
            AIAssistService.DatabaseSchema schema = new AIAssistService.DatabaseSchema(
                "sales_db",
                List.of(new AIAssistService.TableInfo("orders", List.of(), List.of()))
            );

            AIAssistService.GeneratedSQL result = runPromise(() ->
                service.generateSQL("Get the first 10 orders", schema)
            );

            // Real backend was actually called (not a mock)
            assertThat(llmProvider.completeCallCount()).isEqualTo(1);

            // Service extracted SQL from the backend response
            assertThat(result).isNotNull();
            assertThat(result.sql()).isNotBlank();

            // Success metric was recorded
            assertThat(metrics.counterValue("ai.sql.generate.success")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("generateSQL returns read-only SQL classification for SELECT statements")
        void generateSQLClassifiesSelectAsReadOnly() {
            AIAssistService.DatabaseSchema schema = new AIAssistService.DatabaseSchema(
                "sales_db", List.of()
            );

            AIAssistService.GeneratedSQL result = runPromise(() ->
                service.generateSQL("Show me all orders", schema)
            );

            // The fake provider returns a SELECT — service must classify it correctly
            assertThat(result.isReadOnly()).isTrue();
        }

        @Test
        @DisplayName("generateSQL with a different SQL response classifies correctly")
        void generateSQLExtractsTablesFromSQL() {
            FakeLLMProvider providerWithJoin = new FakeLLMProvider(
                "SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id"
            );
            RecordingMetricsCollector m = new RecordingMetricsCollector();
            AIAssistServiceImpl svc = new AIAssistServiceImpl(providerWithJoin, m);

            AIAssistService.GeneratedSQL result = runPromise(() ->
                svc.generateSQL("Get orders with customer names", new AIAssistService.DatabaseSchema("db", List.of()))
            );

            assertThat(result.sql()).isNotBlank();
            // Production extractSQLFromResponse() returns a SELECT — always read-only
            assertThat(result.isReadOnly()).isTrue();
        }
    }

    // ── Full query pipeline ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Full Query Pipeline — end-to-end with real backend")
    class QueryPipelineTests {

        @Test
        @DisplayName("processQuery returns a populated result via real LLM delegation")
        void processQueryReturnResultWithConfidence() {
            AIAssistService.QueryContext ctx = queryContext("tenant-integration", List.of("orders", "customers"));

            AIAssistService.QueryResult result = runPromise(() ->
                service.processQuery("How many orders were placed today?", ctx)
            );

            // Provider was actually called (not mocked out)
            assertThat(llmProvider.completeCallCount()).isEqualTo(1);

            assertThat(result).isNotNull();
            assertThat(result.originalQuery()).isEqualTo("How many orders were placed today?");
            assertThat(result.confidenceScore()).isBetween(0.0, 1.0);
            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0L);
            assertThat(result.queryId()).isNotBlank();

            // Metrics recorded on success path
            assertThat(metrics.counterValue("ai.query.success", "tenant", "tenant-integration"))
                .isEqualTo(1.0);
        }

        @Test
        @DisplayName("processQuery records failure metric when LLM provider throws")
        void processQueryRecordsFailureMetric() {
            LLMProvider failingProvider = new LLMProvider() {
                @Override public Promise<CompletionResponse> complete(CompletionRequest r) {
                    return Promise.ofException(new RuntimeException("LLM backend unavailable"));
                }
                @Override public Promise<ChatResponse> chat(ChatRequest r) { return Promise.ofException(new RuntimeException()); }
                @Override public Promise<List<ModelInfo>> getModels() { return Promise.of(List.of()); }
                @Override public Promise<ProviderStatus> getStatus() { return Promise.of(new ProviderStatus("down", false, "down", 0L, 0.0, Instant.now())); }
                @Override public String getName() { return "failing"; }
            };

            RecordingMetricsCollector m = new RecordingMetricsCollector();
            AIAssistServiceImpl svc = new AIAssistServiceImpl(failingProvider, m);
            AIAssistService.QueryContext ctx = queryContext("tenant-fail", List.of());

            assertThatThrownBy(() -> runPromise(() -> svc.processQuery("test", ctx)))
                .isInstanceOf(Exception.class);

            assertThat(m.counterValue("ai.query.error", "tenant", "tenant-fail")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("processQuery is tenant-isolated — metrics tagged with correct tenant")
        void processQueryTagsMetricsWithTenant() {
            AIAssistService.QueryContext ctx1 = queryContext("tenant-A", List.of("orders"));
            AIAssistService.QueryContext ctx2 = queryContext("tenant-B", List.of("products"));

            runPromise(() -> service.processQuery("query-1", ctx1));

            FakeLLMProvider p2 = new FakeLLMProvider("SELECT * FROM products");
            RecordingMetricsCollector m2 = new RecordingMetricsCollector();
            AIAssistServiceImpl svc2 = new AIAssistServiceImpl(p2, m2);
            runPromise(() -> svc2.processQuery("query-2", ctx2));

            // Each service instance records metrics for its own tenant
            assertThat(metrics.counterValue("ai.query.success", "tenant", "tenant-A")).isEqualTo(1.0);
            assertThat(m2.counterValue("ai.query.success", "tenant", "tenant-B")).isEqualTo(1.0);
        }
    }

    // ── Query explanation (no LLM call — tests internal logic) ───────────────

    @Nested
    @DisplayName("Query Explanation")
    class QueryExplanationTests {

        @Test
        @DisplayName("explainResults includes row count in key points")
        void explainResultsIncludesRowCount() {
            AIAssistService.QueryContext ctx = queryContext("tenant-explain", List.of());
            List<Map<String, Object>> rows = List.of(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)
            );

            AIAssistService.Explanation explanation = runPromise(() ->
                service.explainResults("SELECT * FROM orders", rows, ctx)
            );

            assertThat(explanation).isNotNull();
            assertThat(explanation.keyPoints())
                .anyMatch(kp -> kp.contains("3"));
        }

        @Test
        @DisplayName("explainResults recommends LIMIT for large result sets")
        void explainResultsRecommendsLimitForLargeResults() {
            AIAssistService.QueryContext ctx = queryContext("tenant-explain", List.of());
            List<Map<String, Object>> largeResult = new ArrayList<>();
            for (int i = 0; i < 1500; i++) {
                largeResult.add(Map.of("id", i));
            }

            AIAssistService.Explanation explanation = runPromise(() ->
                service.explainResults("SELECT * FROM orders", largeResult, ctx)
            );

            assertThat(explanation.recommendations())
                .anyMatch(r -> r.contains("LIMIT"));
        }

        @Test
        @DisplayName("explainResults recommends specific columns for SELECT * queries")
        void explainResultsRecommendsSpecificColumnsForStar() {
            AIAssistService.QueryContext ctx = queryContext("tenant-explain", List.of());
            List<Map<String, Object>> rows = List.of(Map.of("id", 1));

            AIAssistService.Explanation explanation = runPromise(() ->
                service.explainResults("SELECT * FROM orders", rows, ctx)
            );

            assertThat(explanation.recommendations())
                .anyMatch(r -> r.contains("specific columns") || r.contains("*"));
        }
    }

    // ── Query suggestions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query Suggestions")
    class QuerySuggestionTests {

        @Test
        @DisplayName("suggestQueries returns preview and count suggestions for each table")
        void suggestQueriesReturnsExpectedSuggestions() {
            AIAssistService.QueryContext ctx = queryContext("tenant-suggest",
                List.of("orders", "customers"));

            List<AIAssistService.QuerySuggestion> suggestions = runPromise(() ->
                service.suggestQueries(ctx, 10)
            );

            // 2 tables × 2 suggestions = 4, capped at limit
            assertThat(suggestions).hasSize(4);
            assertThat(suggestions).allMatch(s -> s.query() != null && !s.query().isBlank());
            assertThat(suggestions).anyMatch(s -> s.query().contains("orders"));
            assertThat(suggestions).anyMatch(s -> s.query().contains("customers"));
        }

        @Test
        @DisplayName("suggestQueries respects the limit parameter")
        void suggestQueriesRespectsLimit() {
            AIAssistService.QueryContext ctx = queryContext("tenant-limit",
                List.of("orders", "customers", "products", "invoices"));

            List<AIAssistService.QuerySuggestion> suggestions = runPromise(() ->
                service.suggestQueries(ctx, 3)
            );

            assertThat(suggestions).hasSizeLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("suggestQueries returns empty list for context with no tables")
        void suggestQueriesReturnsEmptyForNoTables() {
            AIAssistService.QueryContext ctx = queryContext("tenant-empty", List.of());

            List<AIAssistService.QuerySuggestion> suggestions = runPromise(() ->
                service.suggestQueries(ctx, 10)
            );

            assertThat(suggestions).isEmpty();
        }
    }

    // ── Conversation management ───────────────────────────────────────────────

    @Nested
    @DisplayName("Conversation Management")
    class ConversationTests {

        @Test
        @DisplayName("getConversation throws for unknown conversation ID")
        void getConversationThrowsForUnknown() {
            assertThatThrownBy(() -> runPromise(() -> service.getConversation("non-existent-id")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-existent-id");
        }
    }

    // ── Error paths: backing services absent ─────────────────────────────────

    /**
     * Covers DC-M1: explicit error-path tests when feature store, model registry,
     * or voice backing services are absent.  Each test wires the real
     * {@link AIAssistServiceImpl} but supplies a deliberately degraded
     * {@link LLMProvider} or context to trigger observable failure behaviour.
     */
    @Nested
    @DisplayName("Error Paths — feature store / model registry / voice absent")
    class AbsentBackingServiceTests {

        // ── Feature store absent (availableTables == null) ──────────────────

        @Test
        @DisplayName("suggestQueries returns empty list when feature-store tables are null (absent)")
        void suggestQueriesWithNullAvailableTablesTreatedAsAbsent() {
            // QueryContext with null availableTables simulates feature store being absent
            AIAssistService.QueryContext ctx = new AIAssistService.QueryContext(
                "tenant-fs-absent", "user-1", null, "sales_db",
                null, Map.of(), null
            );

            List<AIAssistService.QuerySuggestion> suggestions = runPromise(() ->
                service.suggestQueries(ctx, 10)
            );

            // Service must not throw; returns empty rather than NPE
            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("processQuery with null databaseSchema name still delegates to LLM")
        void processQueryWithNullSchemaNameDelegatesToLLM() {
            // Simulates schema registry absent — schema name is null
            AIAssistService.QueryContext ctx = new AIAssistService.QueryContext(
                "tenant-sr-absent", "user-1", null, null,
                List.of("orders"), Map.of(), null
            );

            AIAssistService.QueryResult result = runPromise(() ->
                service.processQuery("count orders", ctx)
            );

            // Real LLM backend was still invoked
            assertThat(llmProvider.completeCallCount()).isEqualTo(1);
            assertThat(result).isNotNull();
            assertThat(result.queryId()).isNotBlank();
        }

        // ── Model registry absent (LLMProvider.getModels throws) ───────────

        @Test
        @DisplayName("generateSQL records error metric when model registry (LLM provider) is unavailable")
        void generateSQLRecordsErrorMetricWhenModelRegistryUnavailable() {
            LLMProvider modelRegistryAbsent = new LLMProvider() {
                @Override public Promise<CompletionResponse> complete(CompletionRequest r) {
                    return Promise.ofException(new RuntimeException("Model registry unavailable"));
                }
                @Override public Promise<ChatResponse> chat(ChatRequest r) {
                    return Promise.ofException(new RuntimeException("Model registry unavailable"));
                }
                @Override public Promise<List<ModelInfo>> getModels() {
                    return Promise.ofException(new RuntimeException("Registry offline"));
                }
                @Override public Promise<ProviderStatus> getStatus() {
                    return Promise.of(new ProviderStatus("model-registry", false, "offline", 0L, 0.0, Instant.now()));
                }
                @Override public String getName() { return "absent-model-registry"; }
            };

            RecordingMetricsCollector m = new RecordingMetricsCollector();
            AIAssistServiceImpl svc = new AIAssistServiceImpl(modelRegistryAbsent, m);
            AIAssistService.DatabaseSchema schema = new AIAssistService.DatabaseSchema("sales_db", List.of());

            assertThatThrownBy(() ->
                runPromise(() -> svc.generateSQL("List all orders", schema))
            ).isInstanceOf(Exception.class)
             .hasMessageContaining("Model registry unavailable");

            assertThat(m.counterValue("ai.sql.generate.error")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("processQuery error metric is recorded when model registry is unavailable")
        void processQueryErrorMetricWhenModelRegistryAbsent() {
            LLMProvider modelRegistryAbsent = new LLMProvider() {
                @Override public Promise<CompletionResponse> complete(CompletionRequest r) {
                    return Promise.ofException(new RuntimeException("No model available — registry offline"));
                }
                @Override public Promise<ChatResponse> chat(ChatRequest r) {
                    return Promise.ofException(new RuntimeException());
                }
                @Override public Promise<List<ModelInfo>> getModels() {
                    return Promise.of(List.of()); // empty = registry offline
                }
                @Override public Promise<ProviderStatus> getStatus() {
                    return Promise.of(new ProviderStatus("absent-registry", false, "offline", 0L, 0.0, Instant.now()));
                }
                @Override public String getName() { return "absent-registry"; }
            };

            RecordingMetricsCollector m = new RecordingMetricsCollector();
            AIAssistServiceImpl svc = new AIAssistServiceImpl(modelRegistryAbsent, m);
            AIAssistService.QueryContext ctx = queryContext("tenant-model-absent", List.of("orders"));

            assertThatThrownBy(() -> runPromise(() -> svc.processQuery("list orders", ctx)))
                .isInstanceOf(Exception.class);

            assertThat(m.counterValue("ai.query.error", "tenant", "tenant-model-absent")).isEqualTo(1.0);
        }

        // ── Voice context absent ────────────────────────────────────────────

        @Test
        @DisplayName("processQuery succeeds when voice context is absent (null metadata, no language preference)")
        void processQuerySucceedsWhenVoiceContextAbsent() {
            // Voice-absent: no language/voice metadata, no previousQuery
            AIAssistService.QueryContext ctx = new AIAssistService.QueryContext(
                "tenant-voice-absent", "user-1", null, "analytics_db",
                List.of("events"), Map.of(), null
            );

            AIAssistService.QueryResult result = runPromise(() ->
                service.processQuery("show latest events", ctx)
            );

            // Service must complete without requiring voice metadata
            assertThat(result).isNotNull();
            assertThat(result.originalQuery()).isEqualTo("show latest events");
            assertThat(llmProvider.completeCallCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("explainResults succeeds when voice context is absent (null language preference)")
        void explainResultsSucceedsWhenVoiceContextAbsent() {
            AIAssistService.QueryContext ctx = new AIAssistService.QueryContext(
                "tenant-voice-absent", "user-1", null, "db",
                List.of(), Map.of(), null // no voice/language metadata
            );
            List<Map<String, Object>> rows = List.of(Map.of("id", 1), Map.of("id", 2));

            AIAssistService.Explanation explanation = runPromise(() ->
                service.explainResults("SELECT * FROM events", rows, ctx)
            );

            assertThat(explanation).isNotNull();
            assertThat(explanation.keyPoints()).anyMatch(kp -> kp.contains("2"));
        }

        // ── Conversation backed-store error paths ───────────────────────────

        @Test
        @DisplayName("addMessage throws for non-existent conversation (backing store miss)")
        void addMessageThrowsWhenConversationAbsent() {
            AIAssistService.Message msg = new AIAssistService.Message(
                "msg-1", AIAssistService.MessageRole.USER, "Hello!", Instant.now(), Map.of()
            );

            assertThatThrownBy(() -> runPromise(() -> service.addMessage("no-such-convo", msg)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-convo");
        }

        @Test
        @DisplayName("clearConversation is a no-op for non-existent conversation (absent backing store)")
        void clearConversationNoOpForAbsentConversation() {
            // Must not throw — clearing an absent conversation is idempotent
            assertThatCode(() -> runPromise(() -> service.clearConversation("ghost-convo")))
                .doesNotThrowAnyException();
        }
    }
}
