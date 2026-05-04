package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
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
        @DisplayName("invalid limit type → uses default, still returns 200")
        void invalidLimit_usesDefault_returns200() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            QueryResult result = buildResult("qid-lim", 1);
            when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.of(result));
            HttpResponse ok200 = mock(HttpResponse.class);
            when(ok200.getCode()).thenReturn(200);
            when(httpSupport.jsonResponse(anyMap())).thenReturn(ok200);

            HttpRequest req = mockRequest("{\"query\":\"SELECT 1\",\"limit\":\"not-a-number\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(200);
            verify(analyticsEngine).submitQuery(anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("engine throws exception → 500 with sanitized message")
        void engineThrows_returns500() {            when(httpSupport.requireTenantIdOrFail(any())).thenReturn("tenant-test");
            when(httpSupport.resolveCorrelationId(any())).thenReturn("trace-unit-001");
            when(httpSupport.objectMapper()).thenReturn(mapper);            when(analyticsEngine.submitQuery(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("db connection lost")));
            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Query execution failed"))).thenReturn(err500);

            HttpRequest req = mockRequest("{\"query\":\"SELECT * FROM orders\"}");
            HttpResponse response = runPromise(() -> handler.handleAnalyticsQuery(req));

            assertThat(response.getCode()).isEqualTo(500);
            // Verify raw exception message is NOT forwarded to client
            verify(httpSupport).errorResponse(eq(500), eq("Query execution failed"));
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
        @DisplayName("cancel returns 501 — cancellation not supported in this deployment")
        void cancel_returns501() {
            HttpResponse err501 = mock(HttpResponse.class);
            when(err501.getCode()).thenReturn(501);
            when(httpSupport.errorResponse(501,
                    "Analytics query cancellation is not supported in this deployment."))
                    .thenReturn(err501);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("q-cancel");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsCancelQuery(req));

            assertThat(response.getCode()).isEqualTo(501);
            verifyNoInteractions(analyticsEngine);
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
        @DisplayName("engine exception → 500 with sanitized message")
        void engineException_returns500() {
            when(analyticsEngine.getResult(anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("timeout")));
            HttpResponse err500 = mock(HttpResponse.class);
            when(err500.getCode()).thenReturn(500);
            when(httpSupport.errorResponse(eq(500), eq("Failed to retrieve result"))).thenReturn(err500);

            HttpRequest req = mockRequest("");
            when(req.getPathParameter("queryId")).thenReturn("q-fail");

            HttpResponse response = runPromise(() -> handler.handleAnalyticsGetResult(req));

            assertThat(response.getCode()).isEqualTo(500);
            verify(httpSupport).errorResponse(eq(500), eq("Failed to retrieve result"));
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
