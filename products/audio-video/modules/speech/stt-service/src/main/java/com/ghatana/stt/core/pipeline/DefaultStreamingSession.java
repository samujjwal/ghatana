package com.ghatana.stt.core.pipeline;

import com.ghatana.stt.core.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Default implementation of StreamingSession with backpressure handling.
 * 
 * <p>This implementation processes audio chunks in real-time with:
 * <ul>
 *   <li>Bounded queue for backpressure (prevents memory exhaustion)</li>
 *   <li>Asynchronous processing to avoid blocking gRPC threads</li>
 *   <li>Graceful shutdown with pending chunk processing</li>
 *   <li>Error recovery and propagation</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Production-ready streaming transcription session
 * @doc.layer pipeline
 * @doc.pattern Producer-Consumer with backpressure
 */
public class DefaultStreamingSession implements StreamingSession {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStreamingSession.class);
    
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final long SHUTDOWN_TIMEOUT_MS = 5000;

    private final String sessionId;
    private final DefaultAdaptiveSTTEngine engine;
    private final UserProfile profile;
    private final AtomicReference<SessionState> state;
    private final BlockingQueue<AudioChunk> audioQueue;
    private final ExecutorService processingExecutor;
    
    private volatile Consumer<TranscriptionResult> transcriptionCallback;
    private volatile Consumer<Throwable> errorCallback;
    private volatile Consumer<SessionState> stateChangeCallback;
    
    private final AtomicLong totalAudioMs;
    private final AtomicLong chunksProcessed;
    private final AtomicLong transcriptionsEmitted;
    private final CopyOnWriteArrayList<Float> confidenceScores;
    private final long startTimeMs;
    
    private volatile Future<?> processingTask;
    private volatile boolean shouldStop;

    public DefaultStreamingSession(DefaultAdaptiveSTTEngine engine, UserProfile profile) {
        this.sessionId = UUID.randomUUID().toString();
        this.engine = engine;
        this.profile = profile;
        this.state = new AtomicReference<>(SessionState.CREATED);
        this.audioQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
        this.processingExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "streaming-session-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        
        this.totalAudioMs = new AtomicLong(0);
        this.chunksProcessed = new AtomicLong(0);
        this.transcriptionsEmitted = new AtomicLong(0);
        this.confidenceScores = new CopyOnWriteArrayList<>();
        this.startTimeMs = System.currentTimeMillis();
        this.shouldStop = false;
        
        LOG.info("Created streaming session: {} for profile: {}", 
            sessionId, profile != null ? profile.getProfileId() : "anonymous");
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(SessionState.CREATED, SessionState.STARTING)) {
            throw new IllegalStateException("Session already started or in invalid state: " + state.get());
        }
        
        LOG.info("Starting streaming session: {}", sessionId);
        
        try {
            // Start async processing task
            processingTask = processingExecutor.submit(this::processAudioLoop);
            
            changeState(SessionState.ACTIVE);
            LOG.info("Streaming session active: {}", sessionId);
            
        } catch (Exception e) {
            LOG.error("Failed to start streaming session: {}", sessionId, e);
            changeState(SessionState.ERROR);
            notifyError(e);
            throw new RuntimeException("Failed to start session", e);
        }
    }

    @Override
    public void stop() {
        SessionState currentState = state.get();
        if (currentState == SessionState.STOPPED || currentState == SessionState.STOPPING) {
            LOG.debug("Session already stopping/stopped: {}", sessionId);
            return;
        }
        
        LOG.info("Stopping streaming session: {}", sessionId);
        changeState(SessionState.STOPPING);
        shouldStop = true;
        
        // Wait for processing to complete
        if (processingTask != null) {
            try {
                processingTask.get(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                LOG.warn("Processing task did not complete within timeout, forcing shutdown: {}", sessionId);
                processingTask.cancel(true);
            } catch (Exception e) {
                LOG.error("Error waiting for processing task to complete: {}", sessionId, e);
            }
        }
        
        changeState(SessionState.STOPPED);
        LOG.info("Streaming session stopped: {} (processed {} chunks, emitted {} transcriptions)",
            sessionId, chunksProcessed.get(), transcriptionsEmitted.get());
    }

    @Override
    public void feedAudio(AudioChunk chunk) {
        if (state.get() != SessionState.ACTIVE) {
            throw new IllegalStateException("Cannot feed audio to session in state: " + state.get());
        }
        
        try {
            // Backpressure: offer with timeout instead of blocking indefinitely
            boolean accepted = audioQueue.offer(chunk, 1000, TimeUnit.MILLISECONDS);
            
            if (!accepted) {
                LOG.warn("Audio queue full, dropping chunk (backpressure) - session: {}", sessionId);
                // Could emit a warning transcription result here
                TranscriptionResult warning = TranscriptionResult.builder()
                    .text("[WARNING: Audio processing falling behind, some audio may be lost]")
                    .isFinal(false)
                    .confidence(0.0f)
                    .build();
                notifyTranscription(warning);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while feeding audio to session: {}", sessionId, e);
            notifyError(e);
        }
    }

    @Override
    public void onTranscription(Consumer<TranscriptionResult> callback) {
        this.transcriptionCallback = callback;
    }

    @Override
    public void onError(Consumer<Throwable> callback) {
        this.errorCallback = callback;
    }

    @Override
    public void onStateChange(Consumer<SessionState> callback) {
        this.stateChangeCallback = callback;
    }

    @Override
    public SessionState getState() {
        return state.get();
    }

    @Override
    public SessionStats getStats() {
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        float avgConfidence = confidenceScores.isEmpty() ? 0.0f :
            (float) confidenceScores.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
        
        float realTimeFactor = totalAudioMs.get() > 0 ? 
            (float) elapsedMs / totalAudioMs.get() : 0.0f;
        
        return new SessionStats(
            totalAudioMs.get(),
            (int) chunksProcessed.get(),
            (int) transcriptionsEmitted.get(),
            avgConfidence,
            realTimeFactor
        );
    }

    @Override
    public void close() throws Exception {
        stop();
        processingExecutor.shutdown();
        if (!processingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
            processingExecutor.shutdownNow();
        }
        LOG.info("Closed streaming session: {}", sessionId);
    }

    // ========================================================================
    // Internal Processing
    // ========================================================================

    private void processAudioLoop() {
        LOG.debug("Audio processing loop started for session: {}", sessionId);
        
        StringBuilder accumulatedText = new StringBuilder();
        int chunksSinceLastTranscription = 0;
        
        try {
            while (!shouldStop || !audioQueue.isEmpty()) {
                AudioChunk chunk = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                
                if (chunk == null) {
                    continue; // Timeout, check shouldStop flag
                }
                
                try {
                    // Process the audio chunk
                    TranscriptionResult result = processChunk(chunk);
                    
                    if (result != null) {
                        chunksProcessed.incrementAndGet();
                        
                        // Accumulate text for interim results
                        if (!result.text().isEmpty()) {
                            accumulatedText.append(result.text()).append(" ");
                            chunksSinceLastTranscription++;
                        }
                        
                        // Emit transcription every N chunks or on final result
                        if (result.isFinal() || chunksSinceLastTranscription >= 10) {
                            String text = accumulatedText.toString().trim();
                            if (!text.isEmpty()) {
                                TranscriptionResult emittedResult = TranscriptionResult.builder()
                                    .text(text)
                                    .isFinal(result.isFinal())
                                    .confidence(result.confidence())
                                    .language(result.language())
                                    .wordTimings(result.wordTimings())
                                    .build();
                                
                                notifyTranscription(emittedResult);
                                confidenceScores.add(result.confidence());
                                transcriptionsEmitted.incrementAndGet();
                                
                                if (result.isFinal()) {
                                    accumulatedText.setLength(0);
                                    chunksSinceLastTranscription = 0;
                                }
                            }
                        }
                        
                        // Update audio duration stats
                        long chunkDurationMs = (chunk.data().length * 1000L) / 
                            (chunk.sampleRate() * 2); // Assuming 16-bit PCM
                        totalAudioMs.addAndGet(chunkDurationMs);
                    }
                    
                } catch (Exception e) {
                    LOG.error("Error processing audio chunk in session: {}", sessionId, e);
                    notifyError(e);
                }
            }
            
            // Emit any remaining accumulated text
            if (accumulatedText.length() > 0) {
                TranscriptionResult finalResult = TranscriptionResult.builder()
                    .text(accumulatedText.toString().trim())
                    .isFinal(true)
                    .confidence(1.0f)
                    .build();
                notifyTranscription(finalResult);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Audio processing loop interrupted for session: {}", sessionId);
        } catch (Exception e) {
            LOG.error("Fatal error in audio processing loop for session: {}", sessionId, e);
            changeState(SessionState.ERROR);
            notifyError(e);
        }
        
        LOG.debug("Audio processing loop ended for session: {}", sessionId);
    }

    private TranscriptionResult processChunk(AudioChunk chunk) {
        try {
            // Convert chunk to AudioData
            AudioData audioData = AudioData.fromPcm(chunk.data(), chunk.sampleRate());
            
            // Create transcription options
            TranscriptionOptions options = TranscriptionOptions.builder()
                .profileId(profile != null ? profile.getProfileId() : null)
                .enablePunctuation(true)
                .enableWordTimings(false) // Disable for streaming to reduce latency
                .build();
            
            // Transcribe the chunk
            TranscriptionResult result = engine.transcribe(audioData, options);
            
            return result;
            
        } catch (Exception e) {
            LOG.error("Failed to process audio chunk: {}", e.getMessage(), e);
            return null;
        }
    }

    private void changeState(SessionState newState) {
        SessionState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            LOG.debug("Session {} state changed: {} -> {}", sessionId, oldState, newState);
            if (stateChangeCallback != null) {
                try {
                    stateChangeCallback.accept(newState);
                } catch (Exception e) {
                    LOG.error("Error in state change callback", e);
                }
            }
        }
    }

    private void notifyTranscription(TranscriptionResult result) {
        if (transcriptionCallback != null) {
            try {
                transcriptionCallback.accept(result);
            } catch (Exception e) {
                LOG.error("Error in transcription callback", e);
            }
        }
    }

    private void notifyError(Throwable error) {
        if (errorCallback != null) {
            try {
                errorCallback.accept(error);
            } catch (Exception e) {
                LOG.error("Error in error callback", e);
            }
        }
    }
}
