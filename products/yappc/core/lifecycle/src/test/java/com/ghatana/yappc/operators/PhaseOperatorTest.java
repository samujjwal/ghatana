package com.ghatana.yappc.operators;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.evolve.EvolutionService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for PhaseOperator
 * @doc.layer test
 * @doc.pattern Test
 */
class PhaseOperatorTest extends EventloopTestBase {
    
    @Test
    void shouldExecuteIntentPhase() {
        // GIVEN
        IntentService intentService = mock(IntentService.class);
        IntentSpec expectedSpec = IntentSpec.builder()
                .id("intent-123")
                .productName("Test Product")
                .goals(List.of())
                .personas(List.of())
                .constraints(List.of())
                .build();
        
        when(intentService.capture(any(IntentInput.class)))
                .thenReturn(Promise.of(expectedSpec));
        
        PhaseOperator operator = new PhaseOperator(
                PhaseType.INTENT, intentService, null, null, null, null, null, null, null);
        
        IntentInput input = IntentInput.builder()
                .rawText("Build an app")
                .format("text")
                .build();
        
        // WHEN
        Object result = runPromise(() -> operator.execute(input));
        
        // THEN
        assertNotNull(result);
        assertInstanceOf(IntentSpec.class, result);
        assertEquals("intent-123", ((IntentSpec) result).id());
        verify(intentService, times(1)).capture(any(IntentInput.class));
    }
    
    @Test
    void shouldReturnOperatorId() {
        // GIVEN
        PhaseOperator operator = new PhaseOperator(
                PhaseType.SHAPE, null, null, null, null, null, null, null, null);
        
        // WHEN
        String operatorId = operator.getOperatorId();
        
        // THEN
        assertEquals("yappc.phase.shape", operatorId);
    }
    
    @Test
    void shouldReturnMetadata() {
        // GIVEN
        PhaseOperator operator = new PhaseOperator(
                PhaseType.VALIDATE, null, null, null, null, null, null, null, null);
        
        // WHEN
        Map<String, String> metadata = operator.getMetadata();
        
        // THEN
        assertNotNull(metadata);
        assertEquals("VALIDATE", metadata.get("phase"));
        assertEquals("yappc.phase.validate", metadata.get("operator_id"));
        assertEquals("1.0.0", metadata.get("version"));
    }
    
    @Test
    void shouldHandleInvalidInputType() {
        // GIVEN
        PhaseOperator operator = new PhaseOperator(
                PhaseType.INTENT, mock(IntentService.class), null, null, null, null, null, null, null);
        
        // WHEN/THEN
        Exception e = assertThrows(Exception.class, () ->
                runPromise(() -> operator.execute("invalid input")));
        assertTrue(e.getMessage().contains("Invalid input type"));
    }
}
