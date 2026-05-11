/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.plugins.vector.SimilaritySearch;
import com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin;
import com.ghatana.datacloud.plugins.vector.VectorRecord;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-P2-008 — Semantic search / RAG safety tests.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Cross-tenant isolation: results from other tenants are excluded in both
 *       {@code handleSimilarEntities} and {@code handleCollectionRag}.</li>
 *   <li>Low-confidence / no-result behavior: empty plugin results produce a valid empty response.</li>
 *   <li>Retrieval provenance: {@code requestId} is always present in the response.</li>
 *   <li>Missing required parameters return HTTP 400 before any vector lookup occurs.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-008 RAG/semantic-search safety and tenant isolation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DC-P2-008 — Semantic search / RAG safety")
@ExtendWith(MockitoExtension.class)
@Tag("production")
class SemanticSearchSafetyTest extends EventloopTestBase {

    private static final String CALLER_TENANT = "tenant-caller";
    private static final String OTHER_TENANT  = "tenant-intruder";
    private static final String COLLECTION    = "products";
    private static final String CORRELATION   = "req-correlation-123";

    @Mock
    private VectorMemoryPlugin vectorPlugin;

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    private SemanticSearchHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SemanticSearchHandler(vectorPlugin, client, http, new ObjectMapper());
        lenient().when(http.requireTenantIdWithError(any())).thenReturn(TenantResolutionResult.success(CALLER_TENANT, null));
        lenient().when(http.resolveCorrelationId(any())).thenReturn(CORRELATION);
        lenient().when(request.getPathParameter("collection")).thenReturn(COLLECTION);
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(mock(HttpResponse.class));
        lenient().when(http.jsonResponse(any(), anyString())).thenReturn(mock(HttpResponse.class));
    }

    // ─── SIMILAR ENTITIES — TENANT ISOLATION ────────────────────────────────

    @Nested
    @DisplayName("handleSimilarEntities – cross-tenant isolation")
    class SimilarEntitiesTenantIsolation {

        @BeforeEach
        void setUpRequest() {
            lenient().when(request.getQueryParameter("id")).thenReturn(UUID.randomUUID().toString());
            lenient().when(request.getQueryParameter("k")).thenReturn("3");
        }

        @Test
        @DisplayName("Results from caller's tenant are included in the response")
        @SuppressWarnings("unchecked")
        void callerTenantResultsIncluded() {
            SimilaritySearch.SearchResults results = buildFindSimilarResults(List.of(
                buildScoredResult(CALLER_TENANT, COLLECTION, Map.of("name", "Widget A"))));
            when(vectorPlugin.findSimilar(anyString(), anyInt(), any(Boolean.class), anyString()))
                .thenReturn(Promise.of(results));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleSimilarEntities(request));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) bodyCaptor.getValue().get("matches");
            assertThat(matches).hasSize(1);
        }

        @Test
        @DisplayName("Results from another tenant are excluded to prevent cross-tenant leakage")
        @SuppressWarnings("unchecked")
        void otherTenantResultsExcluded() {
            SimilaritySearch.SearchResults results = buildFindSimilarResults(List.of(
                buildScoredResult(CALLER_TENANT, COLLECTION, Map.of("name", "Widget A")),
                buildScoredResult(OTHER_TENANT, COLLECTION, Map.of("ssn", "123-45-6789")))); // other-tenant data
            when(vectorPlugin.findSimilar(anyString(), anyInt(), any(Boolean.class), anyString()))
                .thenReturn(Promise.of(results));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleSimilarEntities(request));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) bodyCaptor.getValue().get("matches");
            assertThat(matches).hasSize(1);
            // Verify the remaining match belongs to the caller tenant (no SSN in response)
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) matches.get(0).get("data");
            assertThat(data).doesNotContainKey("ssn");
        }

        @Test
        @DisplayName("When all plugin results belong to other tenants, matches is empty")
        @SuppressWarnings("unchecked")
        void allOtherTenantResults_emptyMatches() {
            SimilaritySearch.SearchResults results = buildFindSimilarResults(List.of(
                buildScoredResult(OTHER_TENANT, COLLECTION, Map.of("secret", "value"))));
            when(vectorPlugin.findSimilar(anyString(), anyInt(), any(Boolean.class), anyString()))
                .thenReturn(Promise.of(results));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleSimilarEntities(request));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) bodyCaptor.getValue().get("matches");
            assertThat(matches).isEmpty();
            assertThat(bodyCaptor.getValue().get("count")).isEqualTo(0);
        }

        @Test
        @DisplayName("Empty plugin results produce valid empty-match response without error")
        @SuppressWarnings("unchecked")
        void emptyResults_returnsValidResponse() {
            when(vectorPlugin.findSimilar(anyString(), anyInt(), any(Boolean.class), anyString()))
                .thenReturn(Promise.of(SimilaritySearch.SearchResults.empty()));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleSimilarEntities(request));

            Map<String, Object> body = bodyCaptor.getValue();
            assertThat(body.get("matches")).isEqualTo(List.of());
            assertThat(body.get("count")).isEqualTo(0);
            assertThat(body.get("requestId")).isEqualTo(CORRELATION);
        }

        @Test
        @DisplayName("Retrieval provenance (requestId) is always present in the response")
        void retrievalProvenance_requestIdPresent() {
            when(vectorPlugin.findSimilar(anyString(), anyInt(), any(Boolean.class), anyString()))
                .thenReturn(Promise.of(SimilaritySearch.SearchResults.empty()));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleSimilarEntities(request));

            assertThat(bodyCaptor.getValue().get("requestId")).isEqualTo(CORRELATION);
        }

        @Test
        @DisplayName("Missing entity id returns HTTP 400 before any vector lookup")
        void missingEntityId_returns400() {
            when(request.getQueryParameter("id")).thenReturn(null);
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleSimilarEntities(request));

            assertThat(response).isSameAs(errorResp);
            verify(vectorPlugin, never()).findSimilar(any(), anyInt(), any(Boolean.class), anyString());
        }
    }

    // ─── COLLECTION RAG — TENANT ISOLATION ──────────────────────────────────

    @Nested
    @DisplayName("handleCollectionRag – cross-tenant isolation")
    class RagTenantIsolation {

        @BeforeEach
        void setUpBody() {
            String body = "{\"question\":\"What is a Widget?\",\"k\":3}";
            lenient().when(request.loadBody()).thenReturn(
                Promise.of(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
        }

        @Test
        @DisplayName("Only caller-tenant records are included in RAG context")
        @SuppressWarnings("unchecked")
        void callerTenantRecordsIncluded() {
            SimilaritySearch.SearchResults results = buildSearchResults(List.of(
                buildScoredResult(CALLER_TENANT, COLLECTION, Map.of("description", "A quality widget"))));
            when(vectorPlugin.search(any())).thenReturn(Promise.of(results));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleCollectionRag(request));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> context = (List<Map<String, Object>>) bodyCaptor.getValue().get("context");
            assertThat(context).hasSize(1);
        }

        @Test
        @DisplayName("Records from another tenant are excluded from RAG context")
        @SuppressWarnings("unchecked")
        void otherTenantRecordsExcluded() {
            SimilaritySearch.SearchResults results = buildSearchResults(List.of(
                buildScoredResult(CALLER_TENANT, COLLECTION, Map.of("description", "A quality widget")),
                buildScoredResult(OTHER_TENANT, COLLECTION, Map.of("ssn", "987-65-4321")),   // other-tenant PII
                buildScoredResult(CALLER_TENANT, "other-collection", Map.of("x", "y")))); // other collection
            when(vectorPlugin.search(any())).thenReturn(Promise.of(results));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleCollectionRag(request));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> context = (List<Map<String, Object>>) bodyCaptor.getValue().get("context");
            // Only the caller-tenant + correct-collection record should be present
            assertThat(context).hasSize(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) context.get(0).get("data");
            assertThat(data).doesNotContainKey("ssn");
        }

        @Test
        @DisplayName("Empty plugin results produce valid response with empty context")
        @SuppressWarnings("unchecked")
        void emptyResults_returnsValidResponse() {
            when(vectorPlugin.search(any())).thenReturn(Promise.of(SimilaritySearch.SearchResults.empty()));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleCollectionRag(request));

            Map<String, Object> body = bodyCaptor.getValue();
            assertThat(body.get("context")).isEqualTo(List.of());
            assertThat(body.get("requestId")).isEqualTo(CORRELATION);
        }

        @Test
        @DisplayName("Missing 'question' field returns HTTP 400 before vector search")
        void missingQuestion_returns400() {
            String body = "{\"k\":3}";
            when(request.loadBody()).thenReturn(
                Promise.of(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8))));
            HttpResponse errorResp = mock(HttpResponse.class);
            when(http.errorResponse(eq(400), anyString())).thenReturn(errorResp);

            HttpResponse response = runPromise(() -> handler.handleCollectionRag(request));

            assertThat(response).isSameAs(errorResp);
            verify(vectorPlugin, never()).search(any());
        }

        @Test
        @DisplayName("Retrieval provenance (requestId) is always present in the RAG response")
        void retrievalProvenance_requestIdPresent() {
            when(vectorPlugin.search(any())).thenReturn(Promise.of(SimilaritySearch.SearchResults.empty()));

            ArgumentCaptor<Map<String, Object>> bodyCaptor = bodyCaptor();
            when(http.jsonResponse(bodyCaptor.capture(), anyString())).thenReturn(mock(HttpResponse.class));

            runPromise(() -> handler.handleCollectionRag(request));

            assertThat(bodyCaptor.getValue().get("requestId")).isEqualTo(CORRELATION);
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private SimilaritySearch.ScoredResult buildScoredResult(
            String tenantId, String collectionName, Map<String, Object> data) {
        EntityRecord record = EntityRecord.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .collectionName(collectionName)
            .data(data)
            .build();
        VectorRecord vectorRecord = VectorRecord.builder()
            .record(record)
            .tenantId(tenantId)
            .embedding(new float[]{0.1f, 0.2f, 0.3f})
            .embeddedContent("sample content from " + collectionName)
            .build();
        return SimilaritySearch.ScoredResult.builder()
            .record(vectorRecord)
            .score(0.95f)
            .rank(1)
            .distance(0.05f)
            .build();
    }

    private SimilaritySearch.SearchResults buildFindSimilarResults(
            List<SimilaritySearch.ScoredResult> scoredResults) {
        return SimilaritySearch.SearchResults.builder()
            .results(scoredResults)
            .totalMatches(scoredResults.size())
            .searchTimeMs(5)
            .truncated(false)
            .build();
    }

    private SimilaritySearch.SearchResults buildSearchResults(
            List<SimilaritySearch.ScoredResult> scoredResults) {
        return buildFindSimilarResults(scoredResults);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> bodyCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
