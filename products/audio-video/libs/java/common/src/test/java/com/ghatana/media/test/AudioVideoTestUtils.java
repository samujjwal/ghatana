/**
 * @doc.type class
 * @doc.purpose Test utilities for audio-video failure scenarios
 * @doc.layer platform
 * @doc.pattern Testing, Utility
 */
package com.ghatana.media.test;

import com.ghatana.media.common.*;
import com.ghatana.media.stt.api.*;
import com.ghatana.media.tts.api.*;
import com.ghatana.media.validation.ValidationFramework;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import io.activej.promise.Promise;

/**
 * Test utilities for comprehensive audio-video failure scenario testing.
 *
 * <p>Addresses AV-001: Provides test doubles and failure injectors for
 * comprehensive failure scenario testing including:
 * <ul>
 *   <li>Network failures and timeouts</li>
 *   <li>Resource exhaustion</li>
 *   <li>Partial failures and degradation</li>
 *   <li>Concurrent access scenarios</li>
 * </ul></p>
 *
 * @since 2026-03-27
 */
public final class AudioVideoTestUtils {

    private AudioVideoTestUtils() {} // Utility class // GH-90000

    /**
     * Creates a failing STT engine for testing error handling.
     *
     * @param failureRate rate 0.0-1.0 of operations that should fail
     * @param exceptionType type of exception to throw
     */
    public static SttEngine createFailingSttEngine(double failureRate, // GH-90000
                                                    Class<? extends Exception> exceptionType) {
        return new FailingSttEngine(failureRate, exceptionType); // GH-90000
    }

    /**
     * Creates a slow STT engine for testing timeouts.
     *
     * @param delayMs delay per operation in milliseconds
     */
    public static SttEngine createSlowSttEngine(long delayMs) { // GH-90000
        return new SlowSttEngine(delayMs); // GH-90000
    }

    /**
     * Creates a resource-exhausting engine for testing resource limits.
     *
     * @param maxConcurrent max concurrent operations before failing
     */
    public static SttEngine createResourceExhaustedEngine(int maxConcurrent) { // GH-90000
        return new ResourceExhaustedSttEngine(maxConcurrent); // GH-90000
    }

    /**
     * Waits for async operation with timeout.
     *
     * @param future the future to wait for
     * @param timeout timeout duration
     * @param <T> return type
     * @return the result
     * @throws TimeoutException if operation times out
     */
    public static <T> T awaitWithTimeout(CompletableFuture<T> future, Duration timeout) // GH-90000
            throws TimeoutException, ExecutionException, InterruptedException {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); // GH-90000
    }

    /**
     * Simulates network latency.
     *
     * @param duration duration to sleep
     */
    public static void simulateLatency(Duration duration) { // GH-90000
        try {
            Thread.sleep(duration.toMillis()); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
            throw new RuntimeException("Interrupted during latency simulation", e); // GH-90000
        }
    }

    /**
     * STT Engine that fails at a specified rate.
     */
    private static class FailingSttEngine implements SttEngine {
        private final double failureRate;
        private final Class<? extends Exception> exceptionType;
        private final AtomicInteger callCount = new AtomicInteger(0); // GH-90000
        private final AtomicBoolean closed = new AtomicBoolean(false); // GH-90000

        FailingSttEngine(double failureRate, Class<? extends Exception> exceptionType) { // GH-90000
            this.failureRate = failureRate;
            this.exceptionType = exceptionType;
        }

        @Override
        public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) { // GH-90000
            ensureOpen(); // GH-90000
            ValidationFramework.validateAudio(audio); // GH-90000
            int call = callCount.incrementAndGet(); // GH-90000
            if (shouldFail(call)) { // GH-90000
                throw new RuntimeException("Simulated failure", createException()); // GH-90000
            }
            return new TranscriptionResult( // GH-90000
                "test", 0.9, List.of(), List.of(), Duration.ofMillis(100), "en", "test-model" // GH-90000
            );
        }

        @Override
        public Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.Executors.newSingleThreadExecutor(), // GH-90000
                () -> transcribe(audio, options)); // GH-90000
        }

        @Override
        public StreamingSession createStreamingSession() { // GH-90000
            return createStreamingSession(null); // GH-90000
        }

        @Override
        public StreamingSession createStreamingSession(UserProfile profile) { // GH-90000
            ensureOpen(); // GH-90000
            return new FailingStreamingSession(failureRate, exceptionType); // GH-90000
        }

        @Override
        public UserProfile createProfile(String profileId, List<AudioData> enrollmentAudio) { // GH-90000
            return new UserProfile(profileId, "Test", Locale.ENGLISH, List.of(), new byte[0]); // GH-90000
        }

        @Override
        public java.util.Optional<UserProfile> loadProfile(String profileId) { // GH-90000
            return java.util.Optional.of(createProfile(profileId, List.of())); // GH-90000
        }

        @Override
        public void saveProfile(UserProfile profile) {} // GH-90000

        @Override
        public boolean deleteProfile(String profileId) { return true; } // GH-90000

        @Override
        public List<String> listProfiles() { return List.of(); } // GH-90000

        @Override
        public List<ModelInfo> getAvailableModels() { // GH-90000
            return List.of(new ModelInfo("test", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        }

        @Override
        public void loadModel(String modelId) {} // GH-90000

        @Override
        public ModelInfo getActiveModel() { // GH-90000
            return new ModelInfo("test", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false); // GH-90000
        }

        @Override
        public void warmup() {} // GH-90000

        @Override
        public void close() { closed.set(true); } // GH-90000

        @Override
        public EngineStatus getStatus() { // GH-90000
            return new EngineStatus( // GH-90000
                closed.get() ? EngineStatus.State.CLOSED : EngineStatus.State.READY, // GH-90000
                "test", "1.0", 0, null
            );
        }

        @Override
        public EngineMetrics getMetrics() { // GH-90000
            return new EngineMetrics(callCount.get(), (int)(callCount.get() * failureRate), 100, 0, 0); // GH-90000
        }

        private void ensureOpen() { // GH-90000
            if (closed.get()) throw new IllegalStateException("Engine closed");
        }

        private boolean shouldFail(int callNumber) { // GH-90000
            return (callNumber % (int)(1.0 / failureRate)) == 0; // GH-90000
        }

        private Exception createException() { // GH-90000
            try {
                return exceptionType.getDeclaredConstructor(String.class).newInstance("Simulated");
            } catch (Exception e) { // GH-90000
                return new RuntimeException("Simulated");
            }
        }
    }

    /**
     * Slow STT Engine for timeout testing.
     */
    private static class SlowSttEngine implements SttEngine {
        private final long delayMs;

        SlowSttEngine(long delayMs) { this.delayMs = delayMs; } // GH-90000

        @Override
        public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) { // GH-90000
            simulateLatency(Duration.ofMillis(delayMs)); // GH-90000
            return new TranscriptionResult( // GH-90000
                "slow result", 0.9, List.of(), List.of(), Duration.ofMillis(delayMs), "en", "test-model" // GH-90000
            );
        }

        @Override
        public Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.Executors.newSingleThreadExecutor(), // GH-90000
                () -> transcribe(audio, options)); // GH-90000
        }

        @Override
        public StreamingSession createStreamingSession() { return new DummyStreamingSession(); } // GH-90000

        @Override
        public StreamingSession createStreamingSession(UserProfile profile) { return new DummyStreamingSession(); } // GH-90000

        @Override
        public UserProfile createProfile(String profileId, List<AudioData> enrollmentAudio) { // GH-90000
            return new UserProfile(profileId, "Test", Locale.ENGLISH, List.of(), new byte[0]); // GH-90000
        }

        @Override
        public java.util.Optional<UserProfile> loadProfile(String profileId) { // GH-90000
            return java.util.Optional.of(createProfile(profileId, List.of())); // GH-90000
        }

        @Override
        public void saveProfile(UserProfile profile) {} // GH-90000

        @Override
        public boolean deleteProfile(String profileId) { return true; } // GH-90000

        @Override
        public List<String> listProfiles() { return List.of(); } // GH-90000

        @Override
        public List<ModelInfo> getAvailableModels() { // GH-90000
            return List.of(new ModelInfo("test", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        }

        @Override
        public void loadModel(String modelId) {} // GH-90000

        @Override
        public ModelInfo getActiveModel() { // GH-90000
            return new ModelInfo("test", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false); // GH-90000
        }

        @Override
        public void warmup() {} // GH-90000

        @Override
        public void close() {} // GH-90000

        @Override
        public EngineStatus getStatus() { // GH-90000
            return new EngineStatus(EngineStatus.State.READY, "test", "1.0", 0, null); // GH-90000
        }

        @Override
        public EngineMetrics getMetrics() { // GH-90000
            return new EngineMetrics(0, 0, delayMs, 0, 0); // GH-90000
        }
    }

    /**
     * Resource-exhausted engine for testing resource limits.
     */
    private static class ResourceExhaustedSttEngine implements SttEngine {
        private final Semaphore semaphore;

        ResourceExhaustedSttEngine(int maxConcurrent) { // GH-90000
            this.semaphore = new Semaphore(maxConcurrent); // GH-90000
        }

        @Override
        public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) { // GH-90000
            if (!semaphore.tryAcquire()) { // GH-90000
                throw new RuntimeException("Resource exhausted: max concurrent operations reached");
            }
            try {
                return new TranscriptionResult( // GH-90000
                    "result", 0.9, List.of(), List.of(), Duration.ofMillis(100), "en", "test-model" // GH-90000
                );
            } finally {
                semaphore.release(); // GH-90000
            }
        }

        @Override
        public Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.Executors.newSingleThreadExecutor(), // GH-90000
                () -> transcribe(audio, options)); // GH-90000
        }

        @Override
        public StreamingSession createStreamingSession() { return new DummyStreamingSession(); } // GH-90000

        @Override
        public StreamingSession createStreamingSession(UserProfile profile) { return new DummyStreamingSession(); } // GH-90000

        @Override
        public UserProfile createProfile(String profileId, List<AudioData> enrollmentAudio) { // GH-90000
            return new UserProfile(profileId, "Test", Locale.ENGLISH, List.of(), new byte[0]); // GH-90000
        }

        @Override
        public java.util.Optional<UserProfile> loadProfile(String profileId) { // GH-90000
            return java.util.Optional.of(createProfile(profileId, List.of())); // GH-90000
        }

        @Override
        public void saveProfile(UserProfile profile) {} // GH-90000

        @Override
        public boolean deleteProfile(String profileId) { return true; } // GH-90000

        @Override
        public List<String> listProfiles() { return List.of(); } // GH-90000

        @Override
        public List<ModelInfo> getAvailableModels() { // GH-90000
            return List.of(new ModelInfo("test", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false)); // GH-90000
        }

        @Override
        public void loadModel(String modelId) {} // GH-90000

        @Override
        public ModelInfo getActiveModel() { // GH-90000
            return new ModelInfo("test", "Test", "1.0", new Locale[]{Locale.ENGLISH}, 1000L, false); // GH-90000
        }

        @Override
        public void warmup() {} // GH-90000

        @Override
        public void close() {} // GH-90000

        @Override
        public EngineStatus getStatus() { // GH-90000
            return new EngineStatus(EngineStatus.State.READY, "test", "1.0", 0, null); // GH-90000
        }

        @Override
        public EngineMetrics getMetrics() { // GH-90000
            return new EngineMetrics(0, 0, 100, semaphore.getQueueLength(), 0); // GH-90000
        }
    }

    /**
     * Failing streaming session for testing.
     */
    private static class FailingStreamingSession implements StreamingSession {
        private final double failureRate;
        private final Class<? extends Exception> exceptionType;
        private final AtomicInteger chunkCount = new AtomicInteger(0); // GH-90000

        FailingStreamingSession(double failureRate, Class<? extends Exception> exceptionType) { // GH-90000
            this.failureRate = failureRate;
            this.exceptionType = exceptionType;
        }

        @Override
        public void feedAudio(AudioChunk chunk) { // GH-90000
            int count = chunkCount.incrementAndGet(); // GH-90000
            if ((count % (int)(1.0 / failureRate)) == 0) { // GH-90000
                throw new RuntimeException("Simulated streaming failure");
            }
        }

        @Override
        public void onTranscription(Consumer<StreamingTranscription> callback) { // GH-90000
            callback.accept(new StreamingTranscription("test", false, 0.9, List.of())); // GH-90000
        }

        @Override
        public void onError(Consumer<ProcessingError> callback) { // GH-90000
            callback.accept(new ProcessingError("Simulated error", ProcessingError.ErrorCategory.INTERNAL, true)); // GH-90000
        }

        @Override
        public void endStream() {} // GH-90000

        @Override
        public boolean isActive() { return true; } // GH-90000

        @Override
        public void close() {} // GH-90000
    }

    /**
     * Dummy streaming session.
     */
    private static class DummyStreamingSession implements StreamingSession {
        @Override
        public void feedAudio(AudioChunk chunk) {} // GH-90000

        @Override
        public void onTranscription(Consumer<StreamingTranscription> callback) {} // GH-90000

        @Override
        public void onError(Consumer<ProcessingError> callback) {} // GH-90000

        @Override
        public void endStream() {} // GH-90000

        @Override
        public boolean isActive() { return true; } // GH-90000

        @Override
        public void close() {} // GH-90000
    }
}
