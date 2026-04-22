/**
 * @doc.type test
 * @doc.purpose Tests for CircuitBreaker-protected STT Engine
 * @doc.layer platform
 */
package com.ghatana.media.resilience;

import com.ghatana.media.common.*;
import com.ghatana.media.stt.api.*;
import com.ghatana.platform.resilience.CircuitBreaker;

import io.activej.eventloop.Eventloop;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/**
 * Tests for AV-003: Circuit Breaker integration with STT Engine
 */
class CircuitBreakerSttEngineTest {

    private SttEngine mockDelegate;
    private Eventloop eventloop;
    private CircuitBreakerSttEngine protectedEngine;
    private ExecutorService executor;

    @BeforeEach
    void setUp() { // GH-90000
        mockDelegate = mock(SttEngine.class); // GH-90000
        eventloop = Eventloop.create(); // GH-90000
        executor = Executors.newSingleThreadExecutor(); // GH-90000

        // Start eventloop in background
        executor.submit(() -> eventloop.run()); // GH-90000

        // Create circuit breaker with sensitive settings for testing
        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-stt-circuit [GH-90000]")
            .failureThreshold(3) // GH-90000
            .successThreshold(1) // GH-90000
            .resetTimeout(Duration.ofMillis(100)) // GH-90000
            .maxBackoff(Duration.ofMillis(500)) // GH-90000
            .backoffMultiplier(1.0) // GH-90000
            .build(); // GH-90000

        protectedEngine = new CircuitBreakerSttEngine(mockDelegate, eventloop, circuitBreaker); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        eventloop.breakEventloop(); // GH-90000
        executor.shutdown(); // GH-90000
        protectedEngine.close(); // GH-90000
    }

    @Test
    @DisplayName("Should allow normal transcription when circuit is closed [GH-90000]")
    void testNormalTranscription() { // GH-90000
        // Given
        AudioData audio = createTestAudio(); // GH-90000
        TranscriptionOptions options = TranscriptionOptions.defaults(); // GH-90000
        TranscriptionResult expectedResult = createTestResult("Hello world [GH-90000]");

        when(mockDelegate.transcribe(audio, options)).thenReturn(expectedResult); // GH-90000
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000

        // When
        TranscriptionResult result = protectedEngine.transcribe(audio, options); // GH-90000

        // Then
        assertEquals(expectedResult, result); // GH-90000
        assertEquals(CircuitBreaker.State.CLOSED, protectedEngine.getCircuitBreakerState()); // GH-90000
        verify(mockDelegate).transcribe(audio, options); // GH-90000
    }

    @Test
    @DisplayName("Should count failures and open circuit after threshold [GH-90000]")
    void testCircuitOpensAfterFailures() { // GH-90000
        // Given
        AudioData audio = createTestAudio(); // GH-90000
        TranscriptionOptions options = TranscriptionOptions.defaults(); // GH-90000

        when(mockDelegate.transcribe(any(), any())) // GH-90000
            .thenThrow(new InferenceError("Test failure", new RuntimeException("fail [GH-90000]"), false));
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000

        // When - trigger 3 failures
        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                protectedEngine.transcribe(audio, options); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }
        }

        // Then - circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, protectedEngine.getCircuitBreakerState()); // GH-90000

        // Additional call should return degraded result, not call delegate
        TranscriptionResult result = protectedEngine.transcribe(audio, options); // GH-90000
        assertEquals("", result.text()); // Degraded result has empty text // GH-90000
        verify(mockDelegate, times(3)).transcribe(any(), any()); // Only 3 calls, not 4 // GH-90000
    }

    @Test
    @DisplayName("Should return degraded result when circuit is open [GH-90000]")
    void testDegradedResultWhenOpen() throws Exception { // GH-90000
        // Given - manually create engine with open circuit
        CircuitBreaker openCircuitBreaker = CircuitBreaker.builder("open-test-circuit [GH-90000]")
            .failureThreshold(1) // GH-90000
            .successThreshold(1) // GH-90000
            .resetTimeout(Duration.ofHours(1)) // Long timeout to keep it open // GH-90000
            .build(); // GH-90000

        // Force circuit open by triggering failure
        when(mockDelegate.transcribe(any(), any())) // GH-90000
            .thenThrow(new InferenceError("Test failure", new RuntimeException("fail [GH-90000]"), false));
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000

        CircuitBreakerSttEngine engineWithOpenCircuit = new CircuitBreakerSttEngine( // GH-90000
            mockDelegate, eventloop, openCircuitBreaker
        );

        // Trigger failure to open circuit
        try {
            engineWithOpenCircuit.transcribe(createTestAudio(), TranscriptionOptions.defaults()); // GH-90000
        } catch (Exception e) { // GH-90000
            // Expected
        }

        // Verify circuit is open
        assertEquals(CircuitBreaker.State.OPEN, engineWithOpenCircuit.getCircuitBreakerState()); // GH-90000

        // When - call while circuit is open
        TranscriptionResult result = engineWithOpenCircuit.transcribe( // GH-90000
            createTestAudio(), // GH-90000
            TranscriptionOptions.builder().language(Locale.ENGLISH).build() // GH-90000
        );

        // Then - should return degraded result
        assertNotNull(result); // GH-90000
        assertEquals("", result.text()); // GH-90000
        assertEquals(0.0, result.confidence()); // GH-90000
        assertTrue(result.words().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should include circuit breaker metrics in engine metrics [GH-90000]")
    void testCircuitBreakerMetrics() { // GH-90000
        // Given
        EngineMetrics delegateMetrics = new EngineMetrics(100, 5, 50, 2, 0); // GH-90000
        when(mockDelegate.getMetrics()).thenReturn(delegateMetrics); // GH-90000

        // When
        EngineMetrics metrics = protectedEngine.getMetrics(); // GH-90000

        // Then
        assertEquals(100, metrics.requestCount()); // GH-90000
        // Errors should include both delegate errors and circuit breaker failures
        assertTrue(metrics.errorCount() >= 5); // GH-90000
    }

    @Test
    @DisplayName("Should include circuit breaker state in engine status [GH-90000]")
    void testCircuitBreakerStateInStatus() { // GH-90000
        // Given
        EngineStatus delegateStatus = new EngineStatus( // GH-90000
            EngineStatus.State.READY, "test-model", "1.0", 1000L, null
        );
        when(mockDelegate.getStatus()).thenReturn(delegateStatus); // GH-90000
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000

        // When
        EngineStatus status = protectedEngine.getStatus(); // GH-90000

        // Then
        assertNotNull(status.state().name()); // GH-90000
        assertTrue(status.errorMessage().contains("CLOSED [GH-90000]"));
    }

    @Test
    @DisplayName("Should show ERROR state in status when circuit is open [GH-90000]")
    void testErrorStateWhenCircuitOpen() throws Exception { // GH-90000
        // Given - create engine with quickly opening circuit
        CircuitBreaker sensitiveBreaker = CircuitBreaker.builder("sensitive-circuit [GH-90000]")
            .failureThreshold(1) // GH-90000
            .resetTimeout(Duration.ofHours(1)) // GH-90000
            .build(); // GH-90000

        CircuitBreakerSttEngine engine = new CircuitBreakerSttEngine(mockDelegate, eventloop, sensitiveBreaker); // GH-90000

        EngineStatus delegateStatus = new EngineStatus( // GH-90000
            EngineStatus.State.READY, "test-model", "1.0", 1000L, null
        );
        when(mockDelegate.getStatus()).thenReturn(delegateStatus); // GH-90000
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        when(mockDelegate.transcribe(any(), any())) // GH-90000
            .thenThrow(new InferenceError("Fail", new RuntimeException(), false)); // GH-90000

        // Trigger failure
        try {
            engine.transcribe(createTestAudio(), TranscriptionOptions.defaults()); // GH-90000
        } catch (Exception e) { // GH-90000
            // Expected
        }

        // When
        EngineStatus status = engine.getStatus(); // GH-90000

        // Then
        assertEquals(EngineStatus.State.ERROR, status.state()); // GH-90000
        assertTrue(status.errorMessage().contains("OPEN [GH-90000]"));
    }

    @Test
    @DisplayName("Should expose detailed circuit breaker metrics [GH-90000]")
    void testDetailedCircuitBreakerMetrics() { // GH-90000
        // When
        CircuitBreakerSttEngine.CircuitBreakerMetrics metrics = protectedEngine.getCircuitBreakerMetrics(); // GH-90000

        // Then
        assertNotNull(metrics); // GH-90000
        assertEquals("CLOSED", metrics.state()); // GH-90000
        assertTrue(metrics.totalCalls() >= 0); // GH-90000
    }

    @Test
    @DisplayName("Should allow manual circuit breaker reset [GH-90000]")
    void testManualReset() throws Exception { // GH-90000
        // Given - open the circuit
        CircuitBreaker breaker = CircuitBreaker.builder("reset-test-circuit [GH-90000]")
            .failureThreshold(1) // GH-90000
            .resetTimeout(Duration.ofHours(1)) // GH-90000
            .build(); // GH-90000

        CircuitBreakerSttEngine engine = new CircuitBreakerSttEngine(mockDelegate, eventloop, breaker); // GH-90000
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        when(mockDelegate.transcribe(any(), any())) // GH-90000
            .thenThrow(new InferenceError("Fail", new RuntimeException(), false)); // GH-90000

        // Open circuit
        try {
            engine.transcribe(createTestAudio(), TranscriptionOptions.defaults()); // GH-90000
        } catch (Exception e) { // GH-90000
            // Expected
        }

        assertEquals(CircuitBreaker.State.OPEN, engine.getCircuitBreakerState()); // GH-90000

        // When - reset circuit
        engine.resetCircuitBreaker(); // GH-90000

        // Then
        assertEquals(CircuitBreaker.State.CLOSED, engine.getCircuitBreakerState()); // GH-90000
    }

    @Test
    @DisplayName("Should provide degraded streaming session when circuit open [GH-90000]")
    void testDegradedStreamingSession() throws Exception { // GH-90000
        // Given - circuit is open
        CircuitBreaker breaker = CircuitBreaker.builder("streaming-test-circuit [GH-90000]")
            .failureThreshold(1) // GH-90000
            .resetTimeout(Duration.ofHours(1)) // GH-90000
            .build(); // GH-90000

        CircuitBreakerSttEngine engine = new CircuitBreakerSttEngine(mockDelegate, eventloop, breaker); // GH-90000
        when(mockDelegate.getActiveModel()).thenReturn(new ModelInfo("test-model", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        when(mockDelegate.createStreamingSession(any())) // GH-90000
            .thenThrow(new RuntimeException("Session creation failed [GH-90000]"));

        // Open circuit
        try {
            engine.createStreamingSession(null); // GH-90000
        } catch (Exception e) { // GH-90000
            // Expected
        }

        // When - create session with open circuit
        StreamingSession session = engine.createStreamingSession(null); // GH-90000

        // Then - should get degraded session
        assertNotNull(session); // GH-90000
        assertTrue(session.isActive()); // GH-90000

        // Feed audio should not throw
        session.feedAudio(new AudioChunk(new byte[100], 0, false, 16000)); // GH-90000

        session.close(); // GH-90000
    }

    @Test
    @DisplayName("Should delegate non-protected methods directly [GH-90000]")
    void testDirectDelegation() { // GH-90000
        // Given
        List<ModelInfo> models = List.of(new ModelInfo("m1", "Model 1", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        when(mockDelegate.getAvailableModels()).thenReturn(models); // GH-90000
        when(mockDelegate.listProfiles()).thenReturn(List.of("profile1 [GH-90000]"));

        // When & Then - these should delegate directly
        assertEquals(models, protectedEngine.getAvailableModels()); // GH-90000
        assertEquals(List.of("profile1 [GH-90000]"), protectedEngine.listProfiles());

        verify(mockDelegate).getAvailableModels(); // GH-90000
        verify(mockDelegate).listProfiles(); // GH-90000
    }

    // Helper methods
    private AudioData createTestAudio() { // GH-90000
        return new AudioData(new byte[16000], 16000, 1, 16); // GH-90000
    }

    private TranscriptionResult createTestResult(String text) { // GH-90000
        return new TranscriptionResult( // GH-90000
            text, 0.95, List.of(), List.of(), // GH-90000
            Duration.ofMillis(100), "en", "test-model" // GH-90000
        );
    }
}
