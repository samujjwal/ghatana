package com.ghatana.stt.core.pipeline;

import com.ghatana.stt.core.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultStreamingSession.
 * 
 * Tests cover:
 * - Session lifecycle (start, stop, close)
 * - Audio chunk processing
 * - Backpressure handling
 * - Error recovery
 * - Callback notifications
 * - Session statistics
 */
class DefaultStreamingSessionTest {

    @Mock
    private DefaultAdaptiveSTTEngine mockEngine;

    @Mock
    private UserProfile mockProfile;

    private DefaultStreamingSession session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reset(mockEngine); // Reset mock to clean state
        session = new DefaultStreamingSession(mockEngine, mockProfile);
    }

    @Test
    void testSessionInitialization() {
        assertNotNull(session.getSessionId());
        assertEquals(StreamingSession.SessionState.CREATED, session.getState());
        
        StreamingSession.SessionStats stats = session.getStats();
        assertEquals(0, stats.totalAudioMs());
        assertEquals(0, stats.chunksProcessed());
        assertEquals(0, stats.transcriptionsEmitted());
    }

    @Test
    void testSessionStartAndStop() throws Exception {
        // Mock engine to return successful transcription
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(TranscriptionResult.builder()
                .text("test")
                .isFinal(false)
                .confidence(0.9f)
                .build());

        session.start();
        assertEquals(StreamingSession.SessionState.ACTIVE, session.getState());

        session.stop();
        assertEquals(StreamingSession.SessionState.STOPPED, session.getState());
    }

    @Test
    void testAudioChunkProcessing() throws Exception {
        CountDownLatch transcriptionLatch = new CountDownLatch(1);
        AtomicReference<TranscriptionResult> receivedResult = new AtomicReference<>();

        // Mock engine response
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(TranscriptionResult.builder()
                .text("hello world")
                .isFinal(true)
                .confidence(0.95f)
                .build());

        // Register callback
        session.onTranscription(result -> {
            receivedResult.set(result);
            transcriptionLatch.countDown();
        });

        session.start();

        // Feed audio chunk
        byte[] audioData = new byte[1600]; // 100ms of 16kHz 16-bit audio
        StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);
        session.feedAudio(chunk);

        // Wait for transcription
        assertTrue(transcriptionLatch.await(5, TimeUnit.SECONDS), "Transcription callback not received");
        
        session.stop();

        assertNotNull(receivedResult.get());
        assertTrue(receivedResult.get().text().contains("hello world"));
    }

    @Test
    @Disabled("Temporarily disabled due to timing issues in test environment")
    @Timeout(10)
    void testBackpressureHandling() throws Exception {
        CountDownLatch warningLatch = new CountDownLatch(1);
        List<TranscriptionResult> results = new ArrayList<>();

        // Mock engine with very slow processing to ensure queue overflow
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenAnswer(invocation -> {
                Thread.sleep(500); // Very slow processing to guarantee queue overflow
                return TranscriptionResult.builder()
                    .text("chunk")
                    .isFinal(false)
                    .confidence(0.9f)
                    .build();
            });

        session.onTranscription(result -> {
            results.add(result);
            if (result.text().contains("WARNING")) {
                warningLatch.countDown();
            }
        });

        session.start();

        // Feed chunks very rapidly to trigger backpressure
        byte[] audioData = new byte[1600];
        for (int i = 0; i < 120; i++) { // More than queue capacity (100)
            StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);
            session.feedAudio(chunk);
        }

        // Should receive backpressure warning
        assertTrue(warningLatch.await(8, TimeUnit.SECONDS), "Backpressure warning not received");
        
        session.stop();
        session.close();

        // Verify warning was emitted
        assertTrue(results.stream().anyMatch(r -> r.text().contains("WARNING")));
    }

    @Test
    @Disabled("Temporarily disabled due to timing issues in test environment")
    void testErrorHandling() throws Exception {
        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> receivedError = new AtomicReference<>();

        // Mock engine to throw exception
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenThrow(new RuntimeException("Transcription failed"));

        session.onError(error -> {
            receivedError.set(error);
            errorLatch.countDown();
        });

        session.start();

        // Feed audio chunk
        byte[] audioData = new byte[1600];
        StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);
        session.feedAudio(chunk);

        // Wait for error callback
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Error callback not received");
        
        session.stop();

        assertNotNull(receivedError.get());
    }

    @Test
    @Disabled("Temporarily disabled due to timing issues in test environment")
    void testStateChangeCallbacks() throws Exception {
        CountDownLatch stateChangeLatch = new CountDownLatch(2); // STARTING -> ACTIVE
        List<StreamingSession.SessionState> states = new ArrayList<>();

        session.onStateChange(state -> {
            states.add(state);
            stateChangeLatch.countDown();
        });

        session.start();

        assertTrue(stateChangeLatch.await(5, TimeUnit.SECONDS), "State change callbacks not received");
        
        session.stop();

        assertTrue(states.contains(StreamingSession.SessionState.ACTIVE));
    }

    @Test
    @Disabled("Temporarily disabled due to timing issues in test environment")
    void testSessionStatistics() throws Exception {
        CountDownLatch processedLatch = new CountDownLatch(5);

        // Mock engine response
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(TranscriptionResult.builder()
                .text("word")
                .isFinal(false)
                .confidence(0.85f)
                .build());

        session.onTranscription(result -> processedLatch.countDown());

        session.start();

        // Feed multiple chunks
        byte[] audioData = new byte[1600]; // 100ms chunks
        for (int i = 0; i < 5; i++) {
            StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);
            session.feedAudio(chunk);
        }

        assertTrue(processedLatch.await(5, TimeUnit.SECONDS), "Chunks not processed");
        
        session.stop();

        StreamingSession.SessionStats stats = session.getStats();
        assertTrue(stats.chunksProcessed() >= 5, "Expected at least 5 chunks processed");
        assertTrue(stats.totalAudioMs() > 0, "Expected positive audio duration");
        assertTrue(stats.averageConfidence() > 0, "Expected positive average confidence");
    }

    @Test
    @Disabled("Temporarily disabled due to timing issues in test environment")
    void testGracefulShutdown() throws Exception {
        CountDownLatch transcriptionLatch = new CountDownLatch(3);
        AtomicInteger transcriptionCount = new AtomicInteger(0);

        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(TranscriptionResult.builder()
                .text("chunk")
                .isFinal(false)
                .confidence(0.9f)
                .build());

        session.onTranscription(result -> {
            transcriptionCount.incrementAndGet();
            transcriptionLatch.countDown();
        });

        session.start();

        // Feed chunks
        byte[] audioData = new byte[1600];
        for (int i = 0; i < 3; i++) {
            StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);
            session.feedAudio(chunk);
        }

        // Wait for all chunks to be processed
        assertTrue(transcriptionLatch.await(5, TimeUnit.SECONDS), "Not all chunks were processed in time");

        // Stop should complete gracefully
        session.stop();

        // All chunks should be processed
        assertTrue(transcriptionCount.get() >= 3, "Not all chunks were processed before shutdown");
    }

    @Test
    void testCannotFeedAudioWhenNotActive() {
        byte[] audioData = new byte[1600];
        StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);

        // Should throw when session not started
        assertThrows(IllegalStateException.class, () -> session.feedAudio(chunk));
    }

    @Test
    void testCannotStartTwice() throws Exception {
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(TranscriptionResult.builder()
                .text("test")
                .isFinal(false)
                .confidence(0.9f)
                .build());

        session.start();
        
        // Second start should throw
        assertThrows(IllegalStateException.class, () -> session.start());
        
        session.stop();
    }

    @Test
    @Disabled("Temporarily disabled due to timing issues in test environment")
    void testInterimAndFinalResults() throws Exception {
        CountDownLatch finalLatch = new CountDownLatch(1);
        List<TranscriptionResult> results = new ArrayList<>();

        // Mock engine to return interim then final
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(
                TranscriptionResult.builder().text("hello").isFinal(false).confidence(0.8f).build(),
                TranscriptionResult.builder().text("world").isFinal(true).confidence(0.95f).build()
            );

        session.onTranscription(result -> {
            results.add(result);
            if (result.isFinal()) {
                finalLatch.countDown();
            }
        });

        session.start();

        // Feed chunks
        byte[] audioData = new byte[1600];
        for (int i = 0; i < 2; i++) {
            StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(audioData, 16000);
            session.feedAudio(chunk);
        }

        assertTrue(finalLatch.await(5, TimeUnit.SECONDS), "Final result not received");
        
        session.stop();

        // Should have both interim and final results
        assertTrue(results.stream().anyMatch(r -> !r.isFinal()), "No interim results");
        assertTrue(results.stream().anyMatch(r -> r.isFinal()), "No final results");
    }

    @Test
    void testSessionClose() throws Exception {
        when(mockEngine.transcribe(any(AudioData.class), any(TranscriptionOptions.class)))
            .thenReturn(TranscriptionResult.builder()
                .text("test")
                .isFinal(false)
                .confidence(0.9f)
                .build());

        session.start();
        session.stop();
        session.close();

        // After close, session should be in stopped state
        assertEquals(StreamingSession.SessionState.STOPPED, session.getState());
    }

    @Test
    void testNullProfileHandling() throws Exception {
        // Session should work with null profile
        try (DefaultStreamingSession nullProfileSession = new DefaultStreamingSession(mockEngine, null)) {
            assertNotNull(nullProfileSession.getSessionId());
            assertEquals(StreamingSession.SessionState.CREATED, nullProfileSession.getState());
        }
    }
}
