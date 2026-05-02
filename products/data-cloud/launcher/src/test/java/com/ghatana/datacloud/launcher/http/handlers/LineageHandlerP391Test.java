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
 * Unit tests for {@link LineageHandler} (P3.9.1). 
 */
@DisplayName("LineageHandler — P3.9.1")
@ExtendWith(MockitoExtension.class) 
class LineageHandlerP391Test {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private LineagePlugin lineagePlugin;

    @Mock
    private HttpRequest request;

    private LineageHandler handler;
    private LineageHandler handlerNoPlugin;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); 

    @BeforeEach
    void setUp() { 
        handler = new LineageHandler(http, objectMapper, lineagePlugin); 
        handlerNoPlugin = new LineageHandler(http, objectMapper, null); 

        lenient().when(http.requireTenantIdOrFail(any())).thenReturn("tenant-1");

        HttpResponse errorResponse = mock(HttpResponse.class); 
        lenient().when(http.errorResponse(eq(501), any())).thenReturn(errorResponse); 
        lenient().when(http.errorResponse(eq(400), any())).thenReturn(errorResponse); 
        lenient().when(http.errorResponse(eq(500), any())).thenReturn(errorResponse); 
    }

    @Nested
    @DisplayName("handleGetLineage() — no plugin wired")
    class NoPluginLineageTests {

        @Test
        @DisplayName("returns 501 when LineagePlugin is null")
        void returnsNotImplementedWhenNoPlugin() throws Exception { 
            HttpResponse mock501 = mock(HttpResponse.class); 
            when(http.errorResponse(eq(501), any())).thenReturn(mock501); 

            HttpResponse result = handlerNoPlugin.handleGetLineage(request).getResult(); 
            assertThat(result).isSameAs(mock501); 
        }
    }

    @Nested
    @DisplayName("handleGetLineage() — plugin wired")
    class WithPluginLineageTests {

        @BeforeEach
        void setUpPlugin() throws Exception { 
            LineagePlugin.LineageGraph graph = LineagePlugin.LineageGraph.builder() 
                    .tenantId("tenant-1")
                    .upstream(Map.of("orders", Set.of("tenant-1:events")))
                    .downstream(Map.of("orders", Set.of("tenant-1:analytics")))
                    .timestamp(java.time.Instant.now()) 
                    .build(); 

            lenient().when(lineagePlugin.getLineageGraph("tenant-1")).thenReturn(Promise.of(graph));
            lenient().when(lineagePlugin.getUpstreamLineage(eq("tenant-1"), any()))
                    .thenReturn(Promise.of(Set.of("events")));
            lenient().when(lineagePlugin.getDownstreamLineage(eq("tenant-1"), any()))
                    .thenReturn(Promise.of(Set.of("analytics")));

            HttpResponse ok = mock(HttpResponse.class); 
            lenient().when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(ok); 
        }

        @Test
        @DisplayName("returns 400 when collection parameter is missing")
        void returns400WhenCollectionMissing() throws Exception { 
            HttpResponse mock400 = mock(HttpResponse.class); 
            when(request.getPathParameter("collection")).thenReturn(null);
            when(http.errorResponse(eq(400), any())).thenReturn(mock400); 

            HttpResponse result = handler.handleGetLineage(request).getResult(); 
            assertThat(result).isSameAs(mock400); 
        }

        @Test
        @DisplayName("returns 400 when tenant header is missing")
        void returns400WhenTenantMissing() throws Exception { 
            HttpResponse mock400 = mock(HttpResponse.class); 
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(http.requireTenantIdOrFail(any())).thenReturn(null); 
            when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(mock400); 

            HttpResponse result = handler.handleGetLineage(request).getResult(); 

            assertThat(result).isSameAs(mock400); 
        }

        @Test
        @DisplayName("returns 400 when direction is invalid")
        void returns400WhenDirectionInvalid() throws Exception { 
            HttpResponse mock400 = mock(HttpResponse.class); 
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(request.getQueryParameter("direction")).thenReturn("INVALID");
            when(http.errorResponse(eq(400), any())).thenReturn(mock400); 

            HttpResponse result = handler.handleGetLineage(request).getResult(); 
            assertThat(result).isSameAs(mock400); 
        }

        @Test
        @DisplayName("queries upstream lineage for tenant and collection")
        void queriesUpstreamLineage() throws Exception { 
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(request.getQueryParameter("direction")).thenReturn("UPSTREAM");

            handler.handleGetLineage(request).getResult(); 

            verify(lineagePlugin).getUpstreamLineage("tenant-1", "orders"); 
        }

        @Test
        @DisplayName("queries downstream lineage for tenant and collection")
        void queriesDownstreamLineage() throws Exception { 
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(request.getQueryParameter("direction")).thenReturn("DOWNSTREAM");

            handler.handleGetLineage(request).getResult(); 

            verify(lineagePlugin).getDownstreamLineage("tenant-1", "orders"); 
        }

        @Test
        @DisplayName("uses BOTH direction when direction param is absent")
        void defaultsToBothDirection() throws Exception { 
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(request.getQueryParameter("direction")).thenReturn(null);

            handler.handleGetLineage(request).getResult(); 

            // Both upstream and downstream should be queried
            verify(lineagePlugin).getUpstreamLineage("tenant-1", "orders"); 
            verify(lineagePlugin).getDownstreamLineage("tenant-1", "orders"); 
        }

        @Test
        @DisplayName("returns JSON response via http.jsonResponse()")
        void returnsJsonResponse() throws Exception { 
            HttpResponse expected = mock(HttpResponse.class); 
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(request.getQueryParameter("direction")).thenReturn("BOTH");
            when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(expected); 

            HttpResponse result = handler.handleGetLineage(request).getResult(); 
            assertThat(result).isSameAs(expected); 
        }
    }

    @Nested
    @DisplayName("handleGetImpact() — no plugin wired")
    class NoPluginImpactTests {

        @Test
        @DisplayName("returns 501 when LineagePlugin is null")
        void returnsNotImplementedWhenNoPlugin() throws Exception { 
            HttpResponse mock501 = mock(HttpResponse.class); 
            when(http.errorResponse(eq(501), any())).thenReturn(mock501); 

            HttpResponse result = handlerNoPlugin.handleGetImpact(request).getResult(); 
            assertThat(result).isSameAs(mock501); 
        }
    }

    @Nested
    @DisplayName("handleGetImpact() — plugin wired")
    class WithPluginImpactTests {

        @Test
        @DisplayName("returns 400 when collection parameter is missing")
        void returns400WhenCollectionMissing() throws Exception { 
            HttpResponse mock400 = mock(HttpResponse.class); 
            when(request.getPathParameter("collection")).thenReturn(null);
            when(http.errorResponse(eq(400), any())).thenReturn(mock400); 

            HttpResponse result = handler.handleGetImpact(request).getResult(); 
            assertThat(result).isSameAs(mock400); 
        }

        @Test
        @DisplayName("calls analyzeImpact on plugin for tenant and collection")
        void callsAnalyzeImpact() throws Exception { 
            LineagePlugin.ImpactAnalysis analysis = LineagePlugin.ImpactAnalysis.builder() 
                    .collection("orders")
                    .affectedCollections(Set.of("analytics", "reports")) 
                    .impactLevel("HIGH")
                    .timestamp(java.time.Instant.now()) 
                    .build(); 

            when(request.getPathParameter("collection")).thenReturn("orders");
            when(lineagePlugin.analyzeImpact("tenant-1", "orders")).thenReturn(Promise.of(analysis)); 
            when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenReturn(mock(HttpResponse.class)); 

            handler.handleGetImpact(request).getResult(); 
            verify(lineagePlugin).analyzeImpact("tenant-1", "orders"); 
        }

        @Test
        @DisplayName("returns JSON response with affected collections")
        void returnsJsonResponseWithAffectedCollections() throws Exception { 
            LineagePlugin.ImpactAnalysis analysis = LineagePlugin.ImpactAnalysis.builder() 
                    .collection("orders")
                    .affectedCollections(Set.of("reports"))
                    .impactLevel("MEDIUM")
                    .timestamp(java.time.Instant.now()) 
                    .build(); 

            when(request.getPathParameter("collection")).thenReturn("orders");
            when(lineagePlugin.analyzeImpact("tenant-1", "orders")).thenReturn(Promise.of(analysis)); 

            @SuppressWarnings("unchecked")
            Map<String, Object>[] capturedMap = new Map[1];
            HttpResponse expected = mock(HttpResponse.class); 
            when(http.jsonResponse(org.mockito.ArgumentMatchers.<Map<String, Object>>any())).thenAnswer(inv -> { 
                capturedMap[0] = inv.getArgument(0); 
                return expected;
            });

            HttpResponse result = handler.handleGetImpact(request).getResult(); 
            assertThat(result).isSameAs(expected); 
            assertThat(capturedMap[0]).containsEntry("impactLevel", "MEDIUM"); 
            assertThat(capturedMap[0]).containsEntry("affectedCount", 1); 
        }
    }

    @Nested
    @DisplayName("recordTransformation()")
    class RecordTransformationTests {

        @Test
        @DisplayName("delegates to lineagePlugin.recordTransformation")
        void delegatesToPlugin() throws Exception { 
            when(lineagePlugin.recordTransformation(any(), any(), any(), any(), any())) 
                    .thenReturn(Promise.complete()); 

            handler.recordTransformation("t1", "src", "dst", "API_WRITE", Map.of()).getResult(); 
            verify(lineagePlugin).recordTransformation("t1", "src", "dst", "API_WRITE", Map.of()); 
        }

        @Test
        @DisplayName("completes silently when lineagePlugin is null")
        void completesWhenNoPlugin() throws Exception { 
            // Should not throw
            handlerNoPlugin.recordTransformation("t1", "src", "dst", "API_WRITE", Map.of()).getResult(); 
        }
    }

    @Nested
    @DisplayName("Constants")
    class ConstantsTests {

        @Test
        @DisplayName("DEFAULT_GRAPH_DEPTH is -1 (unlimited)")
        void defaultGraphDepth() { 
            assertThat(LineageHandler.DEFAULT_GRAPH_DEPTH).isEqualTo(-1); 
        }
    }
}
