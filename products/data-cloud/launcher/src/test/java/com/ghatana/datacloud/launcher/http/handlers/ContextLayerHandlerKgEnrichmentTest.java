/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for P3.5.1: knowledge graph entity relationship enrichment in
 * {@link ContextLayerHandler#handleGetContext}.
 *
 * <p>Tests the three acceptance criteria:
 * <ol>
 *   <li>Response includes {@code relationships} when KG is wired</li>
 *   <li>Relationships expose sourceEntity, targetEntity, type, and optional confidence</li>
 *   <li>Missing/null KG omits relationships gracefully</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Unit tests for knowledge-graph enrichment in ContextLayerHandler (P3.5.1)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ContextLayerHandler – knowledge graph enrichment (P3.5.1)")
@ExtendWith(MockitoExtension.class)
class ContextLayerHandlerKgEnrichmentTest {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private KnowledgeGraphPlugin knowledgeGraph;

    @Mock
    private HttpRequest request;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpCommon() {
        objectMapper = new ObjectMapper();
        lenient().when(http.resolveTenantId(any())).thenReturn("tenant-kg-test");
        lenient().when(http.resolveCorrelationId(any())).thenReturn("req-123");
        // Return null by default — tests that need to inspect the map provide their own Answer
        lenient().doAnswer(inv -> null).when(http).jsonResponse(any(), any());
    }

    // ── No knowledge graph ────────────────────────────────────────────────────

    @Nested
    @DisplayName("when knowledge graph is not configured")
    class WithoutKnowledgeGraph {

        @Test
        @DisplayName("handleGetContext resolves without error (no relationships field)")
        void noKgWired_resolvesCleanly() {
            ContextLayerHandler handler = new ContextLayerHandler(http, objectMapper);

            assertThatCode(() -> handler.handleGetContext(request).getResult())
                    .doesNotThrowAnyException();
        }
    }

    // ── With wired knowledge graph ────────────────────────────────────────────

    @Nested
    @DisplayName("when knowledge graph is wired")
    class WithKnowledgeGraph {

        private ContextLayerHandler handler;

        @BeforeEach
        void setUp() {
            handler = new ContextLayerHandler(http, objectMapper, knowledgeGraph);
        }

        @Test
        @DisplayName("relationships included in response body passed to jsonResponse")
        void relationships_includedInBody() throws Exception {
            GraphEdge edge = GraphEdge.builder()
                    .id("edge-1")
                    .sourceNodeId("entity-orders")
                    .targetNodeId("entity-products")
                    .relationshipType("REFERENCES")
                    .tenantId("tenant-kg-test")
                    .properties(Map.of("confidence", 0.9))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(1L)
                    .build();

            when(knowledgeGraph.queryEdges(any(GraphQuery.class)))
                    .thenReturn(Promise.of(List.of(edge)));

            // Capture the body map passed to jsonResponse
            AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0);
                captured.set(body);
                return null;
            }).when(http).jsonResponse(any(), any());

            handler.handleGetContext(request).getResult();

            Map<String, Object> capturedBody = captured.get();
            assertThat(capturedBody).isNotNull();
            assertThat(capturedBody).containsKey("relationships");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rels = (List<Map<String, Object>>) capturedBody.get("relationships");
            assertThat(rels).hasSize(1);
            assertThat(rels.get(0)).containsEntry("sourceEntity", "entity-orders");
            assertThat(rels.get(0)).containsEntry("targetEntity", "entity-products");
            assertThat(rels.get(0)).containsEntry("type", "REFERENCES");
            assertThat(rels.get(0)).containsEntry("confidence", 0.9);
        }

        @Test
        @DisplayName("empty relationships when knowledge graph returns no edges")
        void noEdges_emptyRelationshipsList() throws Exception {
            when(knowledgeGraph.queryEdges(any(GraphQuery.class)))
                    .thenReturn(Promise.of(List.of()));

            AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0);
                captured.set(body);
                return null;
            }).when(http).jsonResponse(any(), any());

            handler.handleGetContext(request).getResult();

            assertThat(captured.get()).containsKey("relationships");
            assertThat((List<?>) captured.get().get("relationships")).isEmpty();
        }

        @Test
        @DisplayName("relationship without confidence omits confidence field")
        void edgeWithoutConfidence_noConfidenceInRelationship() throws Exception {
            GraphEdge edge = GraphEdge.builder()
                    .id("edge-2")
                    .sourceNodeId("svc-a")
                    .targetNodeId("svc-b")
                    .relationshipType("DEPENDS_ON")
                    .tenantId("tenant-kg-test")
                    .properties(Map.of())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(1L)
                    .build();

            when(knowledgeGraph.queryEdges(any(GraphQuery.class)))
                    .thenReturn(Promise.of(List.of(edge)));

            AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0);
                captured.set(body);
                return null;
            }).when(http).jsonResponse(any(), any());

            handler.handleGetContext(request).getResult();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rels = (List<Map<String, Object>>) captured.get().get("relationships");
            assertThat(rels).hasSize(1);
            assertThat(rels.get(0)).doesNotContainKey("confidence");
            assertThat(rels.get(0)).containsEntry("type", "DEPENDS_ON");
        }

        @Test
        @DisplayName("knowledge graph failure falls back to response without relationships")
        void kgFailure_responseDeliveredWithoutRelationships() throws Exception {
            when(knowledgeGraph.queryEdges(any(GraphQuery.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("graph unavailable")));

            AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0);
                captured.set(body);
                return null;
            }).when(http).jsonResponse(any(), any());

            // Should not throw; graceful fallback returns base context
            assertThatCode(() -> handler.handleGetContext(request).getResult())
                    .doesNotThrowAnyException();
            assertThat(captured.get()).doesNotContainKey("relationships");
        }
    }
}
