package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.DistributedQueryTracker;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnalyticsHandler}.
 *
 * <p>Covers: valid query, invalid JSON, missing query, blank query, invalid limit,
 * analytics engine absent, and analytics engine exception paths as required by DC-P0-001.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AnalyticsHandler query paths (DC-P0-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsHandler unit tests")
class AnalyticsHandlerTest extends EventloopTestBase {

    @Mock
    private AnalyticsQueryEngine analyticsEngine;

    @Mock
    private HttpHandlerSupport httpSupport;

    private AnalyticsHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new AnalyticsHandler(analyticsEngine, httpSupport);
    }

    // ── Analytics engine absent (null) ────────────────────────────────────────

    @Nested
    @DisplayName("Analytics engine not configured (null)")
    class EngineAbsentTests {

        @BeforeEach
        void setUp() {
            handler = new AnalyticsHandler(null, httpSupport);
        }

        @Test
        @DisplayName("handleAnalyticsQuery → 503 when engine is null")
        void query_engineNull_returns503() {
            HttpResponse error503 = mock(HttpResponse.class);
            when(error503.getCode()).thenReturn(503);
            when(httpSupport.errorResponse(503, "Analytics engine not available in this deployment"))
                    .thenReturn(error503);

            HttpRequest req = mockRequest("{\"query\":\"SELECT 1\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(503);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("handleAnalyticsGetResult → 503 when engine is null")
        void getResult_engineNull_returns503() {
            HttpResponse error503 = mock(HttpResponse.class);
            when(error503.getCode()).thenReturn(503);
            when(httpSupport.errorResponse(503, "Analytics engine not available in this deployment"))
                    .thenReturn(error503);
            HttpRequest req = mockRequest("");
            lenient().when(req.getPathParameter("queryId")).thenReturn("q-1");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("handleAnalyticsAggregate → 503 when engine is null")
        void aggregate_engineNull_returns503() {
            HttpResponse error503 = mock(HttpResponse.class);
            when(error503.getCode()).thenReturn(503);
            when(httpSupport.errorResponse(503, "Analytics engine not available in this deployment"))
                    .thenReturn(error503);

            HttpRequest req = mockRequest("{\"query\":\"SELECT COUNT(*) FROM t GROUP BY x\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsAggregate(req));

            assertThat(response.getCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("handleAnalyticsExplain → 503 when engine is null")
        void explain_engineNull_returns503() {
            HttpResponse error503 = mock(HttpResponse.class);
            when(error503.getCode()).thenReturn(503);
            when(httpSupport.errorResponse(503, "Analytics engine not available in this deployment"))
                    .thenReturn(error503);

            HttpRequest req = mockRequest("{\"query\":\"SELECT 1\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsExplain(req));

            assertThat(response.getCode()).isEqualTo(503);
        }
    }

    // ── handleAnalyticsQuery ─────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAnalyticsQuery")
    class SubmitQueryTests {

        @Test
        @DisplayName("valid query → delegates to engine and returns 200")
        void validQuery_delegatesToEngineAndReturns200() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            QueryResult result = buildResult("qid-001", 2);
            when(analyticsEngine.submitQuery(eq("tenant-test"), eq("SELECT * FROM events"), anyMap()))
                    .thenReturn(Promise.of(result));
            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("{\"query\":\"SELECT * FROM events\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(200);
            verify(analyticsEngine).submitQuery(eq("tenant-test"), eq("SELECT * FROM events"), anyMap());
        }

        @Test
        @DisplayName("missing 'query' field → 400 without calling engine")
        void missingQueryField_returns400() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            HttpResponse err400 = mock(HttpResponse.class);
            when(err400.getCode()).thenReturn(400);
            when(httpSupport.errorResponse(eq(400), anyString())).thenReturn(err400);

            HttpRequest req = mockRequest("{\"limit\":100}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(400);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("blank 'query' field → 400 without calling engine")
        void blankQueryField_returns400() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            HttpResponse err400 = mock(HttpResponse.class);
            when(err400.getCode()).thenReturn(400);
            when(httpSupport.errorResponse(eq(400), anyString())).thenReturn(err400);

            HttpRequest req = mockRequest("{\"query\":\"   \"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(400);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("invalid JSON body → 400 without calling engine")
        void invalidJson_returns400() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            HttpResponse err400 = mock(HttpResponse.class);
            when(err400.getCode()).thenReturn(400);
            when(httpSupport.errorResponse(eq(400), anyString())).thenReturn(err400);

            HttpRequest req = mockRequest("not-json");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(400);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("invalid limit type → returns 400 with validation error")
        void invalidLimit_usesDefault_returns200() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);
            HttpResponse err400 = mock(HttpResponse.class);
            when(err400.getCode()).thenReturn(400);
            when(httpSupport.errorResponse(eq(400), anyString())).thenReturn(err400);

            HttpRequest req = mockRequest("{\"query\":\"SELECT 1\",\"limit\":\"not-a-number\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(400);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("engine throws exception → 500 with sanitized message and traceId (DC-P1-007)")
        void engineThrows_returns500() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("db connection lost")));
            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Query execution failed"), eq("trace-unit-001"))).thenReturn(err500);

            HttpRequest req = mockRequest("{\"query\":\"SELECT * FROM orders\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(500);
            // DC-P1-007: Verify raw exception message is NOT forwarded to client; traceId is propagated
            verify(httpSupport).errorResponse(eq(500), eq("Query execution failed"), eq("trace-unit-001"));
        }

        @Test
        @DisplayName("missing tenant ID → 400 without calling engine")
        void missingTenantId_returns400() {
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn(null);
            HttpResponse err400 = mock(HttpResponse.class);
            when(err400.getCode()).thenReturn(400);
            when(httpSupport.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(err400);

            HttpRequest req = mockRequest("{\"query\":\"SELECT 1\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(400);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("row limit is enforced at MAX_ROW_LIMIT boundary")
        void rowLimitCappedAtMax() {
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);
            QueryResult result = buildResult("qid-max", 0);
            when(analyticsEngine.submitQuery(anyString(), anyString(), argThat(params ->
                    params.containsKey("_rowLimit")
                    && ((Number) params.get("_rowLimit")).intValue() == 50000)))
                    .thenReturn(Promise.of(result));
            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("{\"query\":\"SELECT 1\",\"limit\":9999999}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("DC-P1-005: defensive row cap — engine returning more rows than limit is capped (DC-P1-005)")
        void submitQuery_defensiveRowCap_engineOverage() {
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-005");
            when(httpSupport.objectMapper()).thenReturn(mapper);

            // Engine returns 3 rows but request limit is 2
            QueryResult result = mock(QueryResult.class);
            when(result.getQueryId()).thenReturn("qid-cap");
            when(result.getQueryType()).thenReturn("SELECT");
            when(result.getColumnCount()).thenReturn(2);
            when(result.getExecutionTimeMs()).thenReturn(10L);
            when(result.isOptimized()).thenReturn(false);
            when(result.getTotalRows()).thenReturn(3);
            when(result.getRows()).thenReturn(List.of(
                    Map.of("id", "1"), Map.of("id", "2"), Map.of("id", "3")
            ));
            when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(result));

            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            // Request limit=2 — handler must not return the 3rd engine row
            HttpRequest req = mockRequest("{\"query\":\"SELECT * FROM events\",\"limit\":2}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpSupport).jsonResponse(bodyCaptor.capture());
            Map<String, Object> body = bodyCaptor.getValue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            assertThat(rows).hasSize(2);
            assertThat(((Number) body.get("rowCount")).intValue()).isEqualTo(2);
            assertThat(body.get("truncated")).isEqualTo(true);
            assertThat(((Number) body.get("limit")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("truncated uses totalRows from engine result")
        void submitQuery_truncatedUsesTotalRows() {
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);

            QueryResult result = buildResult("qid-trunc", 1);
            when(result.getTotalRows()).thenReturn(10);
            when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(result));

            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("{\"query\":\"SELECT * FROM events\",\"limit\":100}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpSupport).jsonResponse(bodyCaptor.capture());
            Map<String, Object> body = bodyCaptor.getValue();

            assertThat(body.get("truncated")).isEqualTo(true);
            assertThat(((Number) body.get("rowCount")).intValue()).isEqualTo(1);
            assertThat(((Number) body.get("limit")).intValue()).isEqualTo(100);
        }
    }

    // ── handleAnalyticsCancelQuery ────────────────────────────────────────────

    @Nested
    @DisplayName("handleAnalyticsCancelQuery")
    class CancelQueryTests {

        @Test
        @DisplayName("cancel returns 501 — cancellationSupported=false (default) — capability-consistent NOT_IMPLEMENTED")
        void cancel_unsupported_returns501() {
            String traceId = "trace-cancel-001";
            HttpResponse err501 = mock(HttpResponse.class);
            when(err501.getCode()).thenReturn(501);
            when(httpSupport.resolveCorrelationId(any())).thenReturn(traceId);
            when(httpSupport.errorResponse(eq(501),
                    contains("analytics.cancellation"),
                    eq(traceId)))
                    .thenReturn(err501);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("q-cancel");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsCancelQuery(req));

            assertThat(response.getCode()).isEqualTo(501);
            verifyNoInteractions(analyticsEngine);
        }

        @Test
        @DisplayName("cancel delegates to engine when cancellationSupported=true")
        void cancel_supported_flag_set_delegatesToEngine() {
            String traceId = "trace-cancel-002";
            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.resolveCorrelationId(any())).thenReturn(traceId);
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-123");
            when(analyticsEngine.cancelQuery(eq("q-cancel-supported"), eq("tenant-123")))
                .thenReturn(Promise.of(DistributedQueryTracker.CancellationResult.cancelled("q-cancel-supported")));
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            AnalyticsHandler supported = new AnalyticsHandler(analyticsEngine, httpSupport)
                .withCancellationSupported(true);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("q-cancel-supported");
            HttpResponse response = runPromise(() -> supported.handleAnalyticsCancelQuery(req));

            assertThat(response.getCode()).isEqualTo(200);
            verify(analyticsEngine).cancelQuery("q-cancel-supported", "tenant-123");
        }
    }

    // ── handleAnalyticsGetResult ──────────────────────────────────────────────

    @Nested
    @DisplayName("handleAnalyticsGetResult")
    class GetResultTests {

        @Test
        @DisplayName("known queryId → 200 with result")
        void knownQueryId_returns200() {
            QueryResult result = buildResult("qid-get", 3);
            when(analyticsEngine.getResult("qid-get")).thenReturn(Promise.of(result));
            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("qid-get");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("unknown queryId → 404")
        void unknownQueryId_returns404() {
            when(analyticsEngine.getResult("missing")).thenReturn(Promise.of(null));
            HttpResponse err404 = mock(HttpResponse.class);
            when(err404.getCode()).thenReturn(404);
            when(httpSupport.errorResponse(eq(404), anyString())).thenReturn(err404);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("missing");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("engine exception → 500 with sanitized message and traceId (DC-P1-007)")
        void engineException_returns500() {
            String traceId = "trace-get-fail";
            when(httpSupport.resolveCorrelationId(any())).thenReturn(traceId);
            when(analyticsEngine.getResult(anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("timeout")));
            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Failed to retrieve result"), eq(traceId))).thenReturn(err500);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("q-fail");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(500);
            // DC-P1-007: Verify raw exception message is NOT forwarded to client; traceId is propagated
            verify(httpSupport).errorResponse(eq(500), eq("Failed to retrieve result"), eq(traceId));
        }

        @Test
        @DisplayName("result retrieval truncation considers totalRows")
        void getResult_truncationUsesTotalRows() {
            QueryResult result = buildResult("qid-get-trunc", 1);
            when(result.getTotalRows()).thenReturn(10);
            when(analyticsEngine.getResult("qid-get-trunc")).thenReturn(Promise.of(result));

            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("qid-get-trunc");
            when(req.getQueryParameter("limit")).thenReturn("100");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpSupport).jsonResponse(bodyCaptor.capture());
            Map<String, Object> body = bodyCaptor.getValue();

            assertThat(body.get("truncated")).isEqualTo(true);
            assertThat(((Number) body.get("rowCount")).intValue()).isEqualTo(1);
            assertThat(((Number) body.get("limit")).intValue()).isEqualTo(100);
        }

        @Test
        @DisplayName("result retrieval clamps requested limit to MAX_ROW_LIMIT")
        void getResult_limitClampedToMax() {
            QueryResult result = buildResult("qid-get-max", 1);
            when(result.getTotalRows()).thenReturn(100_000);
            when(analyticsEngine.getResult("qid-get-max")).thenReturn(Promise.of(result));

            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("qid-get-max");
            when(req.getQueryParameter("limit")).thenReturn("9999999");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpSupport).jsonResponse(bodyCaptor.capture());
            Map<String, Object> body = bodyCaptor.getValue();

            assertThat(((Number) body.get("limit")).intValue()).isEqualTo(50_000);
            assertThat(((Number) body.get("rowCount")).intValue()).isEqualTo(1);
            assertThat(body.get("truncated")).isEqualTo(true);
        }

        @Test
        @DisplayName("non-numeric limit query param → 400 (DC-P1-005)")
        void getResult_invalidLimitQueryParam_returns400() {
            // DC-P1-005: invalid limit string must return 400; it must not silently fall back to default.
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            HttpResponse err400 = mock(HttpResponse.class);
            when(err400.getCode()).thenReturn(400);
            when(httpSupport.errorResponse(eq(400), contains("positive integer")))
                    .thenReturn(err400);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("qid-get-bad-limit");
            when(req.getQueryParameter("limit")).thenReturn("not-a-number");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(400);
            verifyNoInteractions(analyticsEngine);
        }
    }

    // ── handleAnalyticsGetPlan ────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAnalyticsGetPlan")
    class GetPlanTests {

        @Test
        @DisplayName("engine exception → 500 with sanitized message and traceId (DC-P1-007)")
        void engineException_returns500() {
            String traceId = "trace-plan-fail";
            when(httpSupport.resolveCorrelationId(any())).thenReturn(traceId);
            when(analyticsEngine.getPlan(anyString()))
                .thenReturn(Promise.ofException(new RuntimeException("planner timeout: SELECT secret FROM t")));

            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Failed to retrieve query plan"), eq(traceId))).thenReturn(err500);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("q-plan-fail");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetPlan(req));

            assertThat(response.getCode()).isEqualTo(500);
            verify(httpSupport).errorResponse(eq(500), eq("Failed to retrieve query plan"), eq(traceId));
        }
    }

    // ── handleAnalyticsAggregate ──────────────────────────────────────────────

    @Nested
    @DisplayName("handleAnalyticsAggregate")
    class AggregateTests {

        @Test
        @DisplayName("engine exception → 500 with sanitized message and traceId (DC-P1-007)")
        void engineException_returns500() {
            String traceId = "trace-agg-fail";
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn(traceId);
            when(httpSupport.objectMapper()).thenReturn(mapper);
            when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.ofException(new RuntimeException("driver failed: jdbc://secret-host")));

            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Aggregate query failed"), eq(traceId))).thenReturn(err500);

            HttpRequest req = mockRequest("{\"query\":\"SELECT type, COUNT(*) FROM events GROUP BY type\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsAggregate(req));

            assertThat(response.getCode()).isEqualTo(500);
            verify(httpSupport).errorResponse(eq(500), eq("Aggregate query failed"), eq(traceId));
        }
    }

    // ── handleAnalyticsExplain ────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAnalyticsExplain")
    class ExplainTests {

        @Test
        @DisplayName("engine exception → 500 with sanitized message and traceId (DC-P1-007)")
        void engineException_returns500() {
            String traceId = "trace-explain-fail";
            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn(traceId);
            when(httpSupport.objectMapper()).thenReturn(mapper);
            when(analyticsEngine.explainQuery(anyString(), anyString(), anyMap()))
                .thenReturn(Promise.ofException(new RuntimeException("optimizer crashed for query: SELECT ssn FROM users")));

            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Explain query failed"), eq(traceId))).thenReturn(err500);

            HttpRequest req = mockRequest("{\"query\":\"SELECT * FROM users\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsExplain(req));

            assertThat(response.getCode()).isEqualTo(500);
            verify(httpSupport).errorResponse(eq(500), eq("Explain query failed"), eq(traceId));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpRequest mockRequest(String bodyJson) {
        HttpRequest req = mock(HttpRequest.class);
        ByteBuf buf = ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8));
        lenient().when(req.loadBody()).thenReturn(Promise.of(buf));
        lenient().when(req.getQueryParameter("limit")).thenReturn(null);
        return req;
    }

    private QueryResult buildResult(String queryId, int rowCount) {
        QueryResult result = mock(QueryResult.class);
        when(result.getQueryId()).thenReturn(queryId);
        when(result.getQueryType()).thenReturn("SELECT");
        when(result.getRows()).thenReturn(
                rowCount == 0 ? List.of() : List.of(Map.of("id", "1")));
        when(result.getColumnCount()).thenReturn(1);
        when(result.getExecutionTimeMs()).thenReturn(5L);
        when(result.isOptimized()).thenReturn(false);
        return result;
    }
}
