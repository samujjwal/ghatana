package com.ghatana.yappc.services.shape;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for ShapeService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class ShapeServiceTest extends EventloopTestBase {
    
    @Test
    void shouldDeriveShapeFromIntent() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("{\"architecture\": {\"name\": \"microservices\"}, \"domainModel\": {}}")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        ShapeService service = new ShapeServiceImpl(aiService, auditLogger, metrics);
        IntentSpec intent = IntentSpec.builder()
                .id("intent-123")
                .productName("Task Manager")
                .description("Team collaboration tool")
                .goals(List.of())
                .personas(List.of())
                .constraints(List.of())
                .tenantId("tenant-123")
                .build();
        
        // WHEN
        ShapeSpec result = runPromise(() -> service.derive(intent));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("intent-123", result.intentRef());
        assertEquals("tenant-123", result.tenantId());
        assertNotNull(result.domainModel());
        
        verify(aiService, times(1)).complete(any(CompletionRequest.class));
        verify(auditLogger, times(1)).log(any(Map.class));
    }
    
    @Test
    void shouldGenerateSystemModel() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Design Rationale: Microservices for scalability")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        ShapeService service = new ShapeServiceImpl(aiService, auditLogger, metrics);
        ShapeSpec spec = ShapeSpec.builder()
                .id("shape-123")
                .intentRef("intent-123")
                .domainModel(null)
                .workflows(List.of())
                .integrations(List.of())
                .build();
        
        // WHEN
        SystemModel result = runPromise(() -> service.generateModel(spec));
        
        // THEN
        assertNotNull(result);
        assertEquals(spec, result.shape());
        assertNotNull(result.designRationale());
        
        verify(aiService, times(1)).complete(any(CompletionRequest.class));
    }
    
    @Test
    void shouldHandleAIServiceFailure() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("AI service unavailable")));
        
        ShapeService service = new ShapeServiceImpl(aiService, auditLogger, metrics);
        IntentSpec intent = IntentSpec.builder()
                .id("intent-123")
                .productName("Test")
                .build();
        
        // WHEN/THEN
        try {
            runPromise(() -> service.derive(intent));
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("AI service unavailable"));
        }
        
        verify(metrics, times(1)).incrementCounter(eq("yappc.shape.derive.error"), any(Map.class));
    }
}
