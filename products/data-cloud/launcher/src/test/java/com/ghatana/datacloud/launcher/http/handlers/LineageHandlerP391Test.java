package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LineageHandler} (P3.9.1). // GH-90000
 */
@DisplayName("LineageHandler — P3.9.1 [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class LineageHandlerP391Test {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private LineagePlugin lineagePlugin;

    @Mock
    private HttpRequest request;

    private LineageHandler handler;
    private LineageHandler handlerNoPlugin;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        handler = new LineageHandler(http, objectMapper, lineagePlugin); // GH-90000
        handlerNoPlugin = new LineageHandler(http, objectMapper, null); // GH-90000

        lenient().when(http.requireTenantIdOrFail(any())).thenReturn("tenant-1 [GH-90000]");

        HttpResponse errorResponse = mock(HttpResponse.class); // GH-90000
        lenient().when(http.errorResponse(eq(501), any())).thenReturn(errorResponse); // GH-90000
        lenient().when(http.errorResponse(eq(400), any())).thenReturn(errorResponse); // GH-90000
        lenient().when(http.errorResponse(eq(500), any())).thenReturn(errorResponse); // GH-90000
    }

    @Nested
    @DisplayName("handleGetLineage() — no plugin wired [GH-90000]")
    class NoPluginLineageTests {

        @Test
        @DisplayName("returns 501 when LineagePlugin is null [GH-90000]")
        void returnsNotImplementedWhenNoPlugin() throws Exception { // GH-90000
            HttpResponse mock501 = mock(HttpResponse.class); // GH-90000
            when(http.errorResponse(eq(501), any())).thenReturn(mock501); // GH-90000

            HttpResponse result = handlerNoPlugin.handleGetLineage(request).getResult(); // GH-90000
            assertThat(result).isSameAs(mock501); // GH-90000
        }
    }

    @Nested
    @DisplayName("handleGetLineage() — plugin wired [GH-90000]")
    class WithPluginLineageTests {

        @BeforeEach
        void setUpPlugin() throws Exception { // GH-90000
            LineagePlugin.LineageGraph graph = LineagePlugin.LineageGraph.builder() // GH-90000
                    .tenantId("tenant-1 [GH-90000]")
                    .upstream(Map.of("orders", Set.of("tenant-1:events [GH-90000]")))
                    .downstream(Map.of("orders", Set.of("tenant-1:analytics [GH-90000]")))
                    .timestamp(java.time.Instant.now()) // GH-90000
                    .build(); // GH-90000

            lenient().when(lineagePlugin.getLineageGraph("tenant-1 [GH-90000]")).thenReturn(Promise.of(graph));
            lenient().when(lineagePlugin.getUpstreamLineage(eq("tenant-1 [GH-90000]"), any()))
                    .thenReturn(Promise.of(Set.of("events [GH-90000]")));
            lenient().when(lineagePlugin.getDownstreamLineage(eq("tenant-1 [GH-90000]"), any()))
                    .thenReturn(Promise.of(Set.of("analytics [GH-90000]")));

            HttpResponse ok = mock(HttpResponse.class); // GH-90000
            lenient().when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(ok); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when collection parameter is missing [GH-90000]")
        void returns400WhenCollectionMissing() throws Exception { // GH-90000
            HttpResponse mock400 = mock(HttpResponse.class); // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn(null);
            when(http.errorResponse(eq(400), any())).thenReturn(mock400); // GH-90000

            HttpResponse result = handler.handleGetLineage(request).getResult(); // GH-90000
            assertThat(result).isSameAs(mock400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when tenant header is missing [GH-90000]")
        void returns400WhenTenantMissing() throws Exception { // GH-90000
            HttpResponse mock400 = mock(HttpResponse.class); // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(http.requireTenantIdOrFail(any())).thenReturn(null); // GH-90000
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(mock400); // GH-90000

            HttpResponse result = handler.handleGetLineage(request).getResult(); // GH-90000

            assertThat(result).isSameAs(mock400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when direction is invalid [GH-90000]")
        void returns400WhenDirectionInvalid() throws Exception { // GH-90000
            HttpResponse mock400 = mock(HttpResponse.class); // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(request.getQueryParameter("direction [GH-90000]")).thenReturn("INVALID [GH-90000]");
            when(http.errorResponse(eq(400), any())).thenReturn(mock400); // GH-90000

            HttpResponse result = handler.handleGetLineage(request).getResult(); // GH-90000
            assertThat(result).isSameAs(mock400); // GH-90000
        }

        @Test
        @DisplayName("queries upstream lineage for tenant and collection [GH-90000]")
        void queriesUpstreamLineage() throws Exception { // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(request.getQueryParameter("direction [GH-90000]")).thenReturn("UPSTREAM [GH-90000]");

            handler.handleGetLineage(request).getResult(); // GH-90000

            verify(lineagePlugin).getUpstreamLineage("tenant-1", "orders"); // GH-90000
        }

        @Test
        @DisplayName("queries downstream lineage for tenant and collection [GH-90000]")
        void queriesDownstreamLineage() throws Exception { // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(request.getQueryParameter("direction [GH-90000]")).thenReturn("DOWNSTREAM [GH-90000]");

            handler.handleGetLineage(request).getResult(); // GH-90000

            verify(lineagePlugin).getDownstreamLineage("tenant-1", "orders"); // GH-90000
        }

        @Test
        @DisplayName("uses BOTH direction when direction param is absent [GH-90000]")
        void defaultsToBothDirection() throws Exception { // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(request.getQueryParameter("direction [GH-90000]")).thenReturn(null);

            handler.handleGetLineage(request).getResult(); // GH-90000

            // Both upstream and downstream should be queried
            verify(lineagePlugin).getUpstreamLineage("tenant-1", "orders"); // GH-90000
            verify(lineagePlugin).getDownstreamLineage("tenant-1", "orders"); // GH-90000
        }

        @Test
        @DisplayName("returns JSON response via http.jsonResponse() [GH-90000]")
        void returnsJsonResponse() throws Exception { // GH-90000
            HttpResponse expected = mock(HttpResponse.class); // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(request.getQueryParameter("direction [GH-90000]")).thenReturn("BOTH [GH-90000]");
            when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(expected); // GH-90000

            HttpResponse result = handler.handleGetLineage(request).getResult(); // GH-90000
            assertThat(result).isSameAs(expected); // GH-90000
        }
    }

    @Nested
    @DisplayName("handleGetImpact() — no plugin wired [GH-90000]")
    class NoPluginImpactTests {

        @Test
        @DisplayName("returns 501 when LineagePlugin is null [GH-90000]")
        void returnsNotImplementedWhenNoPlugin() throws Exception { // GH-90000
            HttpResponse mock501 = mock(HttpResponse.class); // GH-90000
            when(http.errorResponse(eq(501), any())).thenReturn(mock501); // GH-90000

            HttpResponse result = handlerNoPlugin.handleGetImpact(request).getResult(); // GH-90000
            assertThat(result).isSameAs(mock501); // GH-90000
        }
    }

    @Nested
    @DisplayName("handleGetImpact() — plugin wired [GH-90000]")
    class WithPluginImpactTests {

        @Test
        @DisplayName("returns 400 when collection parameter is missing [GH-90000]")
        void returns400WhenCollectionMissing() throws Exception { // GH-90000
            HttpResponse mock400 = mock(HttpResponse.class); // GH-90000
            when(request.getPathParameter("collection [GH-90000]")).thenReturn(null);
            when(http.errorResponse(eq(400), any())).thenReturn(mock400); // GH-90000

            HttpResponse result = handler.handleGetImpact(request).getResult(); // GH-90000
            assertThat(result).isSameAs(mock400); // GH-90000
        }

        @Test
        @DisplayName("calls analyzeImpact on plugin for tenant and collection [GH-90000]")
        void callsAnalyzeImpact() throws Exception { // GH-90000
            LineagePlugin.ImpactAnalysis analysis = LineagePlugin.ImpactAnalysis.builder() // GH-90000
                    .collection("orders [GH-90000]")
                    .affectedCollections(Set.of("analytics", "reports")) // GH-90000
                    .impactLevel("HIGH [GH-90000]")
                    .timestamp(java.time.Instant.now()) // GH-90000
                    .build(); // GH-90000

            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(lineagePlugin.analyzeImpact("tenant-1", "orders")).thenReturn(Promise.of(analysis)); // GH-90000
            when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(mock(HttpResponse.class)); // GH-90000

            handler.handleGetImpact(request).getResult(); // GH-90000
            verify(lineagePlugin).analyzeImpact("tenant-1", "orders"); // GH-90000
        }

        @Test
        @DisplayName("returns JSON response with affected collections [GH-90000]")
        void returnsJsonResponseWithAffectedCollections() throws Exception { // GH-90000
            LineagePlugin.ImpactAnalysis analysis = LineagePlugin.ImpactAnalysis.builder() // GH-90000
                    .collection("orders [GH-90000]")
                    .affectedCollections(Set.of("reports [GH-90000]"))
                    .impactLevel("MEDIUM [GH-90000]")
                    .timestamp(java.time.Instant.now()) // GH-90000
                    .build(); // GH-90000

            when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
            when(lineagePlugin.analyzeImpact("tenant-1", "orders")).thenReturn(Promise.of(analysis)); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object>[] capturedMap = new Map[1];
            HttpResponse expected = mock(HttpResponse.class); // GH-90000
            when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenAnswer(inv -> { // GH-90000
                capturedMap[0] = inv.getArgument(0); // GH-90000
                return expected;
            });

            HttpResponse result = handler.handleGetImpact(request).getResult(); // GH-90000
            assertThat(result).isSameAs(expected); // GH-90000
            assertThat(capturedMap[0]).containsEntry("impactLevel", "MEDIUM"); // GH-90000
            assertThat(capturedMap[0]).containsEntry("affectedCount", 1); // GH-90000
        }
    }

    @Nested
    @DisplayName("recordTransformation() [GH-90000]")
    class RecordTransformationTests {

        @Test
        @DisplayName("delegates to lineagePlugin.recordTransformation [GH-90000]")
        void delegatesToPlugin() throws Exception { // GH-90000
            when(lineagePlugin.recordTransformation(any(), any(), any(), any(), any())) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000

            handler.recordTransformation("t1", "src", "dst", "API_WRITE", Map.of()).getResult(); // GH-90000
            verify(lineagePlugin).recordTransformation("t1", "src", "dst", "API_WRITE", Map.of()); // GH-90000
        }

        @Test
        @DisplayName("completes silently when lineagePlugin is null [GH-90000]")
        void completesWhenNoPlugin() throws Exception { // GH-90000
            // Should not throw
            handlerNoPlugin.recordTransformation("t1", "src", "dst", "API_WRITE", Map.of()).getResult(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Constants [GH-90000]")
    class ConstantsTests {

        @Test
        @DisplayName("DEFAULT_GRAPH_DEPTH is -1 (unlimited) [GH-90000]")
        void defaultGraphDepth() { // GH-90000
            assertThat(LineageHandler.DEFAULT_GRAPH_DEPTH).isEqualTo(-1); // GH-90000
        }
    }
}
