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
        InMemoryIntentService intentService = new InMemoryIntentService();
        IntentSpec expectedSpec = IntentSpec.builder()
                .id("intent-123")
                .productName("Test Product")
                .description("Test product intent")
                .goals(List.of())
                .personas(List.of())
                .constraints(List.of())
                .build();

        intentService.setCaptureResult(expectedSpec);

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
        assertEquals(1, intentService.getCaptureCallCount());
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
                PhaseType.INTENT, new InMemoryIntentService(), null, null, null, null, null, null, null);

        // WHEN/THEN
        Exception e = assertThrows(Exception.class, () ->
                runPromise(() -> operator.execute("invalid input")));
        assertTrue(e.getMessage().contains("Invalid input type"));
    }

    private static final class InMemoryIntentService implements IntentService {
        private IntentSpec captureResult = null;
        private com.ghatana.yappc.domain.intent.IntentAnalysis analyzeResult = null;
        private int captureCallCount = 0;
        private int analyzeCallCount = 0;

        void setCaptureResult(IntentSpec result) {
            this.captureResult = result;
        }

        void setAnalyzeResult(com.ghatana.yappc.domain.intent.IntentAnalysis result) {
            this.analyzeResult = result;
        }

        int getCaptureCallCount() {
            return captureCallCount;
        }

        int getAnalyzeCallCount() {
            return analyzeCallCount;
        }

        @Override
        public Promise<IntentSpec> capture(IntentInput input) {
            captureCallCount++;
            return Promise.of(captureResult);
        }

        @Override
        public Promise<com.ghatana.yappc.domain.intent.IntentAnalysis> analyze(IntentSpec spec) {
            analyzeCallCount++;
            return Promise.of(analyzeResult);
        }
    }
}
