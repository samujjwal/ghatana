/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * @doc.purpose Unit tests for knowledge-graph enrichment in ContextLayerHandler (P3.5.1) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ContextLayerHandler – knowledge graph enrichment (P3.5.1)")
@ExtendWith(MockitoExtension.class) // GH-90000
class ContextLayerHandlerKgEnrichmentTest {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private KnowledgeGraphPlugin knowledgeGraph;

    @Mock
    private HttpRequest request;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpCommon() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        lenient().when(http.requireTenantIdOrFail(any())).thenReturn("tenant-kg-test");
        lenient().when(http.resolveCorrelationId(any())).thenReturn("req-123");
        lenient().when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(null); // GH-90000
        // Return null by default — tests that need to inspect the map provide their own Answer
        lenient().doAnswer(inv -> null).when(http).jsonResponse(any(), any()); // GH-90000
    }

    // ── No knowledge graph ────────────────────────────────────────────────────

    @Nested
    @DisplayName("when knowledge graph is not configured")
    class WithoutKnowledgeGraph {

        @Test
        @DisplayName("handleGetContext resolves without error (no relationships field)")
        void noKgWired_resolvesCleanly() { // GH-90000
            ContextLayerHandler handler = new ContextLayerHandler(http, objectMapper); // GH-90000

            assertThatCode(() -> handler.handleGetContext(request).getResult()) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("handleGetContext returns 400 when tenant header is missing")
        void missingTenant_returns400() { // GH-90000
            ContextLayerHandler handler = new ContextLayerHandler(http, objectMapper); // GH-90000
            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000

            assertThatCode(() -> handler.handleGetContext(request).getResult()) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000

            org.mockito.Mockito.verify(http).errorResponse(400, "X-Tenant-Id header is required"); // GH-90000
        }
    }

    // ── With wired knowledge graph ────────────────────────────────────────────

    @Nested
    @DisplayName("when knowledge graph is wired")
    class WithKnowledgeGraph {

        private ContextLayerHandler handler;

        @BeforeEach
        void setUp() { // GH-90000
            handler = new ContextLayerHandler(http, objectMapper, knowledgeGraph); // GH-90000
        }

        @Test
        @DisplayName("relationships included in response body passed to jsonResponse")
        void relationships_includedInBody() throws Exception { // GH-90000
            GraphEdge edge = GraphEdge.builder() // GH-90000
                    .id("edge-1")
                    .sourceNodeId("entity-orders")
                    .targetNodeId("entity-products")
                    .relationshipType("REFERENCES")
                    .tenantId("tenant-kg-test")
                    .properties(Map.of("confidence", 0.9)) // GH-90000
                    .createdAt(Instant.now()) // GH-90000
                    .updatedAt(Instant.now()) // GH-90000
                    .version(1L) // GH-90000
                    .build(); // GH-90000

            when(knowledgeGraph.queryEdges(any(GraphQuery.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(edge))); // GH-90000

            // Capture the body map passed to jsonResponse
            AtomicReference<Map<String, Object>> captured = new AtomicReference<>(); // GH-90000
            doAnswer(inv -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0); // GH-90000
                captured.set(body); // GH-90000
                return null;
            }).when(http).jsonResponse(any(), any()); // GH-90000

            handler.handleGetContext(request).getResult(); // GH-90000

            Map<String, Object> capturedBody = captured.get(); // GH-90000
            assertThat(capturedBody).isNotNull(); // GH-90000
            assertThat(capturedBody).containsKey("relationships");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rels = (List<Map<String, Object>>) capturedBody.get("relationships");
            assertThat(rels).hasSize(1); // GH-90000
            assertThat(rels.get(0)).containsEntry("sourceEntity", "entity-orders"); // GH-90000
            assertThat(rels.get(0)).containsEntry("targetEntity", "entity-products"); // GH-90000
            assertThat(rels.get(0)).containsEntry("type", "REFERENCES"); // GH-90000
            assertThat(rels.get(0)).containsEntry("confidence", 0.9); // GH-90000
        }

        @Test
        @DisplayName("empty relationships when knowledge graph returns no edges")
        void noEdges_emptyRelationshipsList() throws Exception { // GH-90000
            when(knowledgeGraph.queryEdges(any(GraphQuery.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            AtomicReference<Map<String, Object>> captured = new AtomicReference<>(); // GH-90000
            doAnswer(inv -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0); // GH-90000
                captured.set(body); // GH-90000
                return null;
            }).when(http).jsonResponse(any(), any()); // GH-90000

            handler.handleGetContext(request).getResult(); // GH-90000

            assertThat(captured.get()).containsKey("relationships");
            assertThat((List<?>) captured.get().get("relationships")).isEmpty();
        }

        @Test
        @DisplayName("relationship without confidence omits confidence field")
        void edgeWithoutConfidence_noConfidenceInRelationship() throws Exception { // GH-90000
            GraphEdge edge = GraphEdge.builder() // GH-90000
                    .id("edge-2")
                    .sourceNodeId("svc-a")
                    .targetNodeId("svc-b")
                    .relationshipType("DEPENDS_ON")
                    .tenantId("tenant-kg-test")
                    .properties(Map.of()) // GH-90000
                    .createdAt(Instant.now()) // GH-90000
                    .updatedAt(Instant.now()) // GH-90000
                    .version(1L) // GH-90000
                    .build(); // GH-90000

            when(knowledgeGraph.queryEdges(any(GraphQuery.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(edge))); // GH-90000

            AtomicReference<Map<String, Object>> captured = new AtomicReference<>(); // GH-90000
            doAnswer(inv -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0); // GH-90000
                captured.set(body); // GH-90000
                return null;
            }).when(http).jsonResponse(any(), any()); // GH-90000

            handler.handleGetContext(request).getResult(); // GH-90000

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rels = (List<Map<String, Object>>) captured.get().get("relationships");
            assertThat(rels).hasSize(1); // GH-90000
            assertThat(rels.get(0)).doesNotContainKey("confidence");
            assertThat(rels.get(0)).containsEntry("type", "DEPENDS_ON"); // GH-90000
        }

        @Test
        @DisplayName("knowledge graph failure falls back to response without relationships")
        void kgFailure_responseDeliveredWithoutRelationships() throws Exception { // GH-90000
            when(knowledgeGraph.queryEdges(any(GraphQuery.class))) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("graph unavailable")));

            AtomicReference<Map<String, Object>> captured = new AtomicReference<>(); // GH-90000
            doAnswer(inv -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) inv.getArgument(0); // GH-90000
                captured.set(body); // GH-90000
                return null;
            }).when(http).jsonResponse(any(), any()); // GH-90000

            // Should not throw; graceful fallback returns base context
            assertThatCode(() -> handler.handleGetContext(request).getResult()) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            assertThat(captured.get()).doesNotContainKey("relationships");
        }
    }
}
