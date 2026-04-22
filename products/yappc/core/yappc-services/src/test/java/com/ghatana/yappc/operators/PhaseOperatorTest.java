package com.ghatana.yappc.operators;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.services.intent.IntentService;
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
    void shouldExecuteIntentPhase() { // GH-90000
        // GIVEN
        IntentService intentService = mock(IntentService.class); // GH-90000
        IntentSpec expectedSpec = IntentSpec.builder() // GH-90000
                .id("intent-123 [GH-90000]")
                .productName("Test Product [GH-90000]")
                .description("Test product intent [GH-90000]")
                .goals(List.of()) // GH-90000
                .personas(List.of()) // GH-90000
                .constraints(List.of()) // GH-90000
                .build(); // GH-90000

        when(intentService.capture(any(IntentInput.class))) // GH-90000
                .thenReturn(Promise.of(expectedSpec)); // GH-90000

        PhaseOperator operator = new PhaseOperator( // GH-90000
                PhaseType.INTENT, intentService, null, null, null, null, null, null, null);

        IntentInput input = IntentInput.builder() // GH-90000
                .rawText("Build an app [GH-90000]")
                .format("text [GH-90000]")
                .build(); // GH-90000

        // WHEN
        Object result = runPromise(() -> operator.execute(input)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertInstanceOf(IntentSpec.class, result); // GH-90000
        assertEquals("intent-123", ((IntentSpec) result).id()); // GH-90000
        verify(intentService, times(1)).capture(any(IntentInput.class)); // GH-90000
    }

    @Test
    void shouldReturnOperatorId() { // GH-90000
        // GIVEN
        PhaseOperator operator = new PhaseOperator( // GH-90000
                PhaseType.SHAPE, null, null, null, null, null, null, null, null);

        // WHEN
        String operatorId = operator.getOperatorId(); // GH-90000

        // THEN
        assertEquals("yappc.phase.shape", operatorId); // GH-90000
    }

    @Test
    void shouldReturnMetadata() { // GH-90000
        // GIVEN
        PhaseOperator operator = new PhaseOperator( // GH-90000
                PhaseType.VALIDATE, null, null, null, null, null, null, null, null);

        // WHEN
        Map<String, String> metadata = operator.getMetadata(); // GH-90000

        // THEN
        assertNotNull(metadata); // GH-90000
        assertEquals("VALIDATE", metadata.get("phase [GH-90000]"));
        assertEquals("yappc.phase.validate", metadata.get("operator_id [GH-90000]"));
        assertEquals("1.0.0", metadata.get("version [GH-90000]"));
    }

    @Test
    void shouldHandleInvalidInputType() { // GH-90000
        // GIVEN
        PhaseOperator operator = new PhaseOperator( // GH-90000
                PhaseType.INTENT, mock(IntentService.class), null, null, null, null, null, null, null); // GH-90000

        // WHEN/THEN
        Exception e = assertThrows(Exception.class, () -> // GH-90000
                runPromise(() -> operator.execute("invalid input [GH-90000]")));
        assertTrue(e.getMessage().contains("Invalid input type [GH-90000]"));
    }
}
