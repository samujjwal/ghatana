package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryPlan;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for StorageCostHandler collection ID validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("StorageCostHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class StorageCostHandlerTest extends EventloopTestBase {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private AnalyticsQueryEngine analyticsEngine;

    @Mock
    private MetricsCollector metrics;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private HttpResponse successResponse;

    @Mock
    private QueryResult queryResult;

    @Mock
    private QueryPlan queryPlan;

    private StorageCostHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new StorageCostHandler(http, analyticsEngine, metrics); // GH-90000
        lenient().when(http.requireTenantIdOrFail(request)).thenReturn("tenant-1 [GH-90000]");
        lenient().when(http.errorResponse(eq(400), anyString())).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("rejects invalid collection IDs before query submission [GH-90000]")
    void rejectsInvalidCollectionIdsBeforeQuerySubmission() { // GH-90000
        when(request.getPathParameter("id [GH-90000]")).thenReturn("\"; DROP TABLE events; --");

        HttpResponse response = runPromise(() -> handler.handleCollectionCostReport(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(analyticsEngine, never()).submitQuery(anyString(), anyString(), anyMap()); // GH-90000
        verify(http).errorResponse(eq(400), anyString()); // GH-90000
    }

    @Test
    @DisplayName("submits query for valid collection IDs [GH-90000]")
    void submitsQueryForValidCollectionIds() { // GH-90000
        when(request.getPathParameter("id [GH-90000]")).thenReturn("orders_2026 [GH-90000]");
        when(queryResult.getQueryId()).thenReturn("query-1 [GH-90000]");
        when(queryPlan.getEstimatedCost()).thenReturn(4.0); // GH-90000
        when(http.jsonResponse(eq(200), org.mockito.ArgumentMatchers.<Map<String, Object>>any())) // GH-90000
            .thenReturn(successResponse); // GH-90000
        when(analyticsEngine.submitQuery(eq("tenant-1 [GH-90000]"), eq("SELECT COUNT(*) FROM \"orders_2026\""), eq(Map.of())))
                .thenReturn(Promise.of(queryResult)); // GH-90000
        when(analyticsEngine.getPlan("query-1 [GH-90000]")).thenReturn(Promise.of(queryPlan));

        HttpResponse response = runPromise(() -> handler.handleCollectionCostReport(request)); // GH-90000

        assertThat(response).isSameAs(successResponse); // GH-90000
        verify(analyticsEngine).submitQuery(eq("tenant-1 [GH-90000]"), eq("SELECT COUNT(*) FROM \"orders_2026\""), eq(Map.of()));
    }

    @Test
    @DisplayName("rejects collection cost report when tenant header is missing [GH-90000]")
    void rejectsCollectionCostReportWhenTenantHeaderMissing() { // GH-90000
        when(request.getPathParameter("id [GH-90000]")).thenReturn("orders_2026 [GH-90000]");
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleCollectionCostReport(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(analyticsEngine, never()).submitQuery(anyString(), anyString(), anyMap()); // GH-90000
        verify(http).errorResponse(400, "X-Tenant-Id header is required"); // GH-90000
    }
}