package com.ghatana.yappc.services.intent;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for IntentService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class IntentServiceTest extends EventloopTestBase {
    
    @Test
    void shouldCaptureIntentWithAI() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Product: Task Manager\nDescription: Team collaboration tool")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        IntentService service = new IntentServiceImpl(aiService, auditLogger, metrics);
        IntentInput input = IntentInput.builder()
                .rawText("Build a task management app for teams")
                .format("text")
                .tenantId("tenant-123")
                .build();
        
        // WHEN
        IntentSpec result = runPromise(() -> service.capture(input));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.id());
        assertNotNull(result.productName());
        assertEquals("tenant-123", result.tenantId());
        
        verify(aiService, times(1)).complete(any(CompletionRequest.class));
        verify(auditLogger, times(1)).log(any(Map.class));
    }
    
    @Test
    void shouldAnalyzeIntentForFeasibility() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Feasibility: High\nRisks: Technical complexity")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        IntentService service = new IntentServiceImpl(aiService, auditLogger, metrics);
        IntentSpec spec = IntentSpec.builder()
                .id("intent-123")
                .productName("Task Manager")
                .description("Team collaboration tool")
                .tenantId("tenant-123")
                .build();
        
        // WHEN
        IntentAnalysis result = runPromise(() -> service.analyze(spec));
        
        // THEN
        assertNotNull(result);
        assertEquals("intent-123", result.intentId());
        assertNotNull(result.risks());
        assertNotNull(result.gaps());
        
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
        
        IntentService service = new IntentServiceImpl(aiService, auditLogger, metrics);
        IntentInput input = IntentInput.builder()
                .rawText("Build an app")
                .format("text")
                .build();
        
        // WHEN/THEN
        try {
            runPromise(() -> service.capture(input));
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("AI service unavailable"));
        }
        
        verify(metrics, times(1)).incrementCounter(eq("yappc.intent.capture.error"), any(Map.class));
    }
}
