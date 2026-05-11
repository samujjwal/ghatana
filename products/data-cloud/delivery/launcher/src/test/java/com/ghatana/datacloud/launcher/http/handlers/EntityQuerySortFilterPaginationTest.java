/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.entity.storage.FilterCriteria;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-P2-007 — Deterministic sorting, filter validation, and pagination tests for Data Explorer.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Default sort is always by {@code id:asc} (deterministic tiebreaker).</li>
 *   <li>User-specified sorts have {@code id:asc} appended as tiebreaker when absent.</li>
 *   <li>Sort on {@code id} field does not add a duplicate tiebreaker.</li>
 *   <li>Offset and limit are forwarded to the client query without mutation.</li>
 *   <li>Excessive or invalid limit values return HTTP 400.</li>
 *   <li>Valid filter expressions are forwarded to the client query.</li>
 *   <li>Unknown filter operators return HTTP 400.</li>
 *   <li>Malformed filter expressions (< 3 tokens) return HTTP 400.</li>
 *   <li>Empty collection returns a valid response with empty entities list.</li>
 *   <li>{@code hasMore} flag is true when more data exists beyond the current page.</li>
 *   <li>{@code hasMore} flag is false when the page exhausts available data.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-007 Data Explorer sort/filter/pagination determinism tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-P2-007 — Data Explorer: deterministic sort, filter validation, pagination")
@ExtendWith(MockitoExtension.class)
@Tag("production")
class EntityQuerySortFilterPaginationTest extends EventloopTestBase {

    private static final String TENANT = "tenant-explorer-test";
    private static final String COLLECTION = "products";

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    @SuppressWarnings("unchecked")
    private BiConsumer<String, Map<String, Object>> wsBroadcaster;

    private EntityCrudHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EntityCrudHandler(client, http, wsBroadcaster)
            .withTraceSupport(TraceSpanSupport.disabled());

        lenient().when(http.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success(TENANT, null));
        lenient().when(request.getPathParameter("collection")).thenReturn(COLLECTION);
        lenient().when(request.getQueryParameter("limit")).thenReturn("100");
        lenient().when(request.getQueryParameter("offset")).thenReturn("0");
        lenient().when(request.getQueryParameter("sort")).thenReturn(null);
        lenient().when(request.getQueryParameter("filter")).thenReturn(null);
        lenient().when(request.getQueryParameter("search")).thenReturn(null);
        lenient().when(client.entityStore()).thenReturn(null); // no EntityStore → total=-1
        lenient().when(http.jsonResponse(any())).thenReturn(mock(HttpResponse.class));
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(mock(HttpResponse.class));
    }

    // ─── DETERMINISTIC SORT ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic sort")
    class DeterministicSort {

        @Test
        @DisplayName("Default (no sort param) uses id:asc as only sort for deterministic results")
        void defaultSort_usesIdAscForDeterminism() {
            List<DataCloudClient.Entity> entities = List.of(
                DataCloudClient.Entity.of("a1", COLLECTION, Map.of("name", "Alpha")));
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(entities));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Sort> sorts = queryCaptor.getValue().sorts();
            assertThat(sorts).hasSize(1);
            assertThat(sorts.get(0).field()).isEqualTo("id");
            assertThat(sorts.get(0).ascending()).isTrue();
        }

        @Test
        @DisplayName("User-specified sort has id:asc appended as tiebreaker")
        void userSort_appendsIdTiebreaker() {
            when(request.getQueryParameter("sort")).thenReturn("name:asc");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Sort> sorts = queryCaptor.getValue().sorts();
            assertThat(sorts).hasSize(2);
            assertThat(sorts.get(0).field()).isEqualTo("name");
            assertThat(sorts.get(0).ascending()).isTrue();
            assertThat(sorts.get(1).field()).isEqualTo("id");
            assertThat(sorts.get(1).ascending()).isTrue();
        }

        @Test
        @DisplayName("Descending sort has id:asc tiebreaker appended")
        void descendingSort_appendsIdTiebreaker() {
            when(request.getQueryParameter("sort")).thenReturn("createdAt:desc");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Sort> sorts = queryCaptor.getValue().sorts();
            assertThat(sorts).hasSize(2);
            assertThat(sorts.get(0).field()).isEqualTo("createdAt");
            assertThat(sorts.get(0).ascending()).isFalse();
            assertThat(sorts.get(1).field()).isEqualTo("id");
            assertThat(sorts.get(1).ascending()).isTrue();
        }

        @Test
        @DisplayName("Sort on id field does not add a duplicate id tiebreaker")
        void sortOnIdField_noTiebreaker() {
            when(request.getQueryParameter("sort")).thenReturn("id:desc");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Sort> sorts = queryCaptor.getValue().sorts();
            assertThat(sorts).hasSize(1);
            assertThat(sorts.get(0).field()).isEqualTo("id");
            assertThat(sorts.get(0).ascending()).isFalse();
        }

        @Test
        @DisplayName("Multiple sorts: last sort on id suppresses tiebreaker")
        void multipleSort_withIdPresent_noExtraTiebreaker() {
            when(request.getQueryParameter("sort")).thenReturn("name:asc,id:asc");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Sort> sorts = queryCaptor.getValue().sorts();
            // name:asc and id:asc only — no second id
            assertThat(sorts).hasSize(2);
            assertThat(sorts.stream().filter(s -> "id".equals(s.field())).count()).isEqualTo(1);
        }
    }

    // ─── PAGINATION ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("Offset and limit are forwarded to client query without mutation")
        void offsetAndLimitForwardedToClient() {
            when(request.getQueryParameter("limit")).thenReturn("25");
            when(request.getQueryParameter("offset")).thenReturn("50");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            assertThat(queryCaptor.getValue().limit()).isEqualTo(25);
            assertThat(queryCaptor.getValue().offset()).isEqualTo(50);
        }

        @Test
        @DisplayName("Excessive limit returns HTTP 400")
        void excessiveLimit_returns400() {
            when(request.getQueryParameter("limit")).thenReturn("1001");
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleQueryEntities(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("Non-numeric limit returns HTTP 400")
        void nonNumericLimit_returns400() {
            when(request.getQueryParameter("limit")).thenReturn("abc");
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleQueryEntities(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("hasMore is true when total exceeds offset + page count")
        @SuppressWarnings("unchecked")
        void hasMore_trueWhenMoreDataExists() {
            List<DataCloudClient.Entity> page = List.of(
                DataCloudClient.Entity.of("e1", COLLECTION, Map.of()),
                DataCloudClient.Entity.of("e2", COLLECTION, Map.of()),
                DataCloudClient.Entity.of("e3", COLLECTION, Map.of()),
                DataCloudClient.Entity.of("e4", COLLECTION, Map.of()),
                DataCloudClient.Entity.of("e5", COLLECTION, Map.of()));
            when(request.getQueryParameter("limit")).thenReturn("5");
            when(request.getQueryParameter("offset")).thenReturn("0");

            // Use a real EntityStore mock so we can return a total count
            com.ghatana.datacloud.spi.EntityStore entityStore =
                mock(com.ghatana.datacloud.spi.EntityStore.class);
            when(client.entityStore()).thenReturn(entityStore);
            when(entityStore.count(any(), any())).thenReturn(Promise.of(10L)); // 10 total

            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(page));
            ArgumentCaptor<Map<String, Object>> bodyCaptor =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            when(http.jsonResponse(bodyCaptor.capture())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleQueryEntities(request));

            Map<String, Object> body = bodyCaptor.getValue();
            assertThat(body.get("hasMore")).isEqualTo(true);
            assertThat(body.get("total")).isEqualTo(10L);
            assertThat(body.get("count")).isEqualTo(5);
        }

        @Test
        @DisplayName("hasMore is false when page exhausts total")
        @SuppressWarnings("unchecked")
        void hasMore_falseWhenNoMoreData() {
            List<DataCloudClient.Entity> page = List.of(
                DataCloudClient.Entity.of("e1", COLLECTION, Map.of()),
                DataCloudClient.Entity.of("e2", COLLECTION, Map.of()));
            when(request.getQueryParameter("limit")).thenReturn("10");
            when(request.getQueryParameter("offset")).thenReturn("0");

            com.ghatana.datacloud.spi.EntityStore entityStore =
                mock(com.ghatana.datacloud.spi.EntityStore.class);
            when(client.entityStore()).thenReturn(entityStore);
            when(entityStore.count(any(), any())).thenReturn(Promise.of(2L)); // 2 total, page has 2

            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(page));
            ArgumentCaptor<Map<String, Object>> bodyCaptor =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            when(http.jsonResponse(bodyCaptor.capture())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleQueryEntities(request));

            Map<String, Object> body = bodyCaptor.getValue();
            assertThat(body.get("hasMore")).isEqualTo(false);
        }
    }

    // ─── FILTER VALIDATION ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Filter validation")
    class FilterValidation {

        @Test
        @DisplayName("Valid filter expression is forwarded to client")
        void validFilter_forwardedToClient() {
            when(request.getQueryParameter("filter")).thenReturn("status:eq:active");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Filter> filters = queryCaptor.getValue().filters();
            assertThat(filters).hasSize(1);
            assertThat(filters.get(0).field()).isEqualTo("status");
                assertThat(filters.get(0).operator()).isEqualTo(FilterCriteria.Operator.EQ);
            assertThat(filters.get(0).value()).isEqualTo("active");
        }

        @Test
        @DisplayName("Unknown filter operator returns HTTP 400")
        void unknownFilterOperator_returns400() {
            when(request.getQueryParameter("filter")).thenReturn("status:contains:active");
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleQueryEntities(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("Malformed filter (missing value token) returns HTTP 400")
        void malformedFilter_missingValue_returns400() {
            when(request.getQueryParameter("filter")).thenReturn("status:eq");
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleQueryEntities(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("Malformed filter (field only) returns HTTP 400")
        void malformedFilter_fieldOnly_returns400() {
            when(request.getQueryParameter("filter")).thenReturn("status");
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleQueryEntities(request));

            assertThat(response).isSameAs(errorResp);
        }

        @Test
        @DisplayName("Multiple valid filters are all forwarded to client")
        void multipleValidFilters_allForwarded() {
            when(request.getQueryParameter("filter")).thenReturn("status:eq:active,price:gt:100");
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            runPromise(() -> handler.handleQueryEntities(request));

            ArgumentCaptor<DataCloudClient.Query> queryCaptor =
                ArgumentCaptor.forClass(DataCloudClient.Query.class);
            verify(client).query(eq(TENANT), eq(COLLECTION), queryCaptor.capture());

            List<DataCloudClient.Filter> filters = queryCaptor.getValue().filters();
            assertThat(filters).hasSize(2);
            assertThat(filters).extracting(DataCloudClient.Filter::field)
                .containsExactlyInAnyOrder("status", "price");
        }
    }

    // ─── EMPTY AND ERROR STATES ──────────────────────────────────────────────

    @Nested
    @DisplayName("Empty and error states")
    class EmptyAndErrorStates {

        @Test
        @DisplayName("Empty collection returns valid response with empty entities list")
        @SuppressWarnings("unchecked")
        void emptyCollection_returnsValidResponse() {
            when(client.query(eq(TENANT), eq(COLLECTION), any())).thenReturn(Promise.of(List.of()));

            ArgumentCaptor<Map<String, Object>> bodyCaptor =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            when(http.jsonResponse(bodyCaptor.capture())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleQueryEntities(request));

            Map<String, Object> body = bodyCaptor.getValue();
            assertThat(body.get("entities")).isEqualTo(List.of());
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("hasMore")).isEqualTo(false);
        }

        @Test
        @DisplayName("Missing tenant ID returns HTTP 400 before any client access")
        void missingTenant_returns400() {
            when(http.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.error(401, "Unauthorized"));
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleQueryEntities(request));

            assertThat(response).isSameAs(errorResp);
        }
    }
}
