/**
 * @doc.type implementation
 * @doc.purpose ONNX Runtime based Whisper STT Engine
 * @doc.layer platform
 * @doc.pattern Strategy
 */
package com.ghatana.media.stt.engine.onnx;

import ai.onnxruntime.*;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.SttConfig;
import com.ghatana.media.stt.api.*;
import io.activej.promise.Promise;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * ONNX Runtime based Whisper speech-to-text engine.
 *
 * <p>Uses Microsoft ONNX Runtime for cross-platform inference.
 * Supports GPU acceleration via CUDA/Metal/DirectML execution providers.
 */
public final class WhisperOnnxEngine implements SttEngine {

    private static final Logger LOG = Logger.getLogger(WhisperOnnxEngine.class.getName());

    // Model constants
    private static final int SAMPLE_RATE = 16000;
    private static final int N_MELS = 80;
    private static final int N_FFT = 400;
    private static final int HOP_LENGTH = 160;
    private static final int CHUNK_LENGTH = 30; // seconds
    private static final int N_SAMPLES = CHUNK_LENGTH * SAMPLE_RATE;

    private final SttConfig config;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Semaphore concurrencyLimiter;
    private final ExecutorService executor;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicReference<EngineStatus.State> state;

    public WhisperOnnxEngine(SttConfig config, AudioVideoLibrary.LibraryState libraryState) throws ModelLoadingError {
        this.config = config;
        this.state = new AtomicReference<>(EngineStatus.State.INITIALIZING);
        this.concurrencyLimiter = new Semaphore(config.maxConcurrentRequests());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            LOG.info("Loading Whisper ONNX model from: " + config.modelPath());

            this.environment = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            // Configure GPU if enabled
            if (config.useGpu()) {
                try {
                    options.addCUDA(0);
                    LOG.info("CUDA execution provider enabled");
                } catch (OrtException e) {
                    LOG.warning("CUDA not available, falling back to CPU: " + e.getMessage());
                }
            }

            this.session = environment.createSession(config.modelPath().toString(), options);

            LOG.info("Whisper model loaded successfully. Input: " + session.getInputNames() +
                     ", Output: " + session.getOutputNames());

            state.set(EngineStatus.State.READY);

        } catch (OrtException e) {
            state.set(EngineStatus.State.ERROR);
            throw new ModelLoadingError("Failed to load Whisper ONNX model", e);
        }
    }

    @Override
    public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) {
        ensureReady();
        validateAudio(audio);

        long startTime = System.currentTimeMillis();
        requestCount.incrementAndGet();

        try {
            concurrencyLimiter.acquire();
            try {
                // Preprocess audio to mel spectrogram
                float[][] melSpectrogram = preprocess(audio);

                // Run inference
                String text = runInference(melSpectrogram, options);

                long latency = System.currentTimeMillis() - startTime;
                totalLatency.addAndGet(latency);

                // Generate word timings if requested
                List<WordTiming> words = options.enableTimestamps()
                    ? generateWordTimings(text, melSpectrogram.length)
                    : List.of();

                return new TranscriptionResult(
                    text,
                    0.9, // confidence estimate
                    words,
                    List.of(), // alternatives
                    java.time.Duration.ofMillis(latency),
                    options.language().toLanguageTag(),
                    config.modelId()
                );

            } finally {
                concurrencyLimiter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorCount.incrementAndGet();
            throw new InferenceError("Transcription interrupted", e, true);
        } catch (Exception e) {
            errorCount.incrementAndGet();
            throw new InferenceError("Transcription failed", e, false);
        }
    }

    @Override
    public Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) {
        return Promise.ofBlocking(executor, () -> transcribe(audio, options));
    }

    @Override
    public StreamingSession createStreamingSession() {
        return createStreamingSession(null);
    }

    @Override
    public StreamingSession createStreamingSession(UserProfile profile) {
        ensureReady();
        return new WhisperStreamingSession(profile, config, this);
    }

    @Override
    public UserProfile createProfile(String profileId, java.util.List<com.ghatana.media.common.AudioData> enrollmentAudio) {
        // Stub - would extract speaker embedding from enrollment audio
        return new UserProfile(
            profileId,
            "User " + profileId,
            Locale.getDefault(),
            List.of(),
            new byte[0]
        );
    }

    @Override
    public Optional<UserProfile> loadProfile(String profileId) {
        return Optional.of(createProfile(profileId, List.of()));
    }

    @Override
    public void saveProfile(UserProfile profile) {
        // Persist to storage
    }

    @Override
    public boolean deleteProfile(String profileId) {
        return true;
    }

    @Override
    public List<String> listProfiles() {
        return List.of();
    }

    @Override
    public List<ModelInfo> getAvailableModels() {
        return List.of(
            new ModelInfo("whisper-tiny", "Whisper Tiny", "1.0", new Locale[]{Locale.ENGLISH}, 39000000L, false),
            new ModelInfo("whisper-base", "Whisper Base", "1.0", new Locale[]{Locale.ENGLISH}, 74000000L, true),
            new ModelInfo("whisper-small", "Whisper Small", "1.0", new Locale[]{Locale.ENGLISH}, 244000000L, true),
            new ModelInfo("whisper-medium", "Whisper Medium", "1.0", new Locale[]{Locale.ENGLISH}, 769000000L, true),
            new ModelInfo("whisper-large", "Whisper Large", "1.0", new Locale[]{Locale.ENGLISH}, 1550000000L, true)
        );
    }

    @Override
    public void loadModel(String modelId) {
        // Model hot-swapping would be implemented here
        LOG.info("Model switching not yet implemented for: " + modelId);
    }

    @Override
    public ModelInfo getActiveModel() {
        return new ModelInfo(
            config.modelId(),
            config.modelId(),
            "1.0",
            new Locale[]{Locale.ENGLISH},
            0L,
            config.useGpu()
        );
    }

    @Override
    public void warmup() {
        LOG.info("Warming up Whisper engine...");
        try {
            // Run a dummy inference to warm up caches
            float[][] dummyInput = new float[3000][N_MELS];
            runInference(dummyInput, TranscriptionOptions.defaults());
            LOG.info("Warmup complete");
        } catch (Exception e) {
            LOG.warning("Warmup failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        LOG.info("Closing Whisper ONNX engine...");
        state.set(EngineStatus.State.CLOSED);

        try {
            session.close();
            environment.close();
        } catch (OrtException e) {
            LOG.warning("Error closing ONNX session: " + e.getMessage());
        }

        executor.shutdown();
    }

    @Override
    public EngineStatus getStatus() {
        return new EngineStatus(
            state.get(),
            config.modelId(),
            "1.0.0",
            0L,
            null
        );
    }

    @Override
    public EngineMetrics getMetrics() {
        long requests = requestCount.get();
        long errors = errorCount.get();
        long avgLatency = requests > 0 ? totalLatency.get() / requests : 0;

        return new EngineMetrics(
            requests,
            errors,
            avgLatency,
            config.maxConcurrentRequests() - concurrencyLimiter.availablePermits(),
            0L
        );
    }

    // ====================================================================================
    // Private Implementation
    // ====================================================================================

    private void ensureReady() {
        if (state.get() != EngineStatus.State.READY) {
            throw new IllegalStateException("Whisper engine not ready. State: " + state.get());
        }
    }

    private void validateAudio(AudioData audio) {
        if (audio == null) {
            throw new ValidationError("Audio data cannot be null");
        }
        if (audio.sampleRate() != SAMPLE_RATE) {
            throw new ValidationError("Audio must be 16kHz, got: " + audio.sampleRate());
        }
        if (audio.duration() != null && audio.duration().getSeconds() > config.maxAudioLengthSeconds()) {
            throw new ValidationError("Audio too long: " + audio.duration().getSeconds() + "s > " + config.maxAudioLengthSeconds() + "s");
        }
    }

    /**
     * Preprocess audio to mel spectrogram.
     */
    private float[][] preprocess(AudioData audio) {
        // Convert bytes to float samples
        float[] samples = bytesToFloats(audio.data(), audio.bitsPerSample());

        // Pad or trim to N_SAMPLES
        if (samples.length > N_SAMPLES) {
            samples = Arrays.copyOf(samples, N_SAMPLES);
        } else if (samples.length < N_SAMPLES) {
            samples = Arrays.copyOf(samples, N_SAMPLES); // Zero-pad
        }

        // Compute mel spectrogram
        return computeMelSpectrogram(samples, SAMPLE_RATE, N_MELS, N_FFT, HOP_LENGTH);
    }

    private float[] bytesToFloats(byte[] data, int bitsPerSample) {
        int bytesPerSample = bitsPerSample / 8;
        int numSamples = data.length / bytesPerSample;
        float[] floats = new float[numSamples];

        for (int i = 0; i < numSamples; i++) {
            int sample = 0;
            for (int j = 0; j < bytesPerSample; j++) {
                sample |= (data[i * bytesPerSample + j] & 0xFF) << (j * 8);
            }
            // Convert to float in range [-1, 1]
            floats[i] = sample / (float) (1 << (bitsPerSample - 1));
        }

        return floats;
    }

    private float[][] computeMelSpectrogram(float[] samples, int sampleRate, int nMels, int nFft, int hopLength) {
        int nFrames = (samples.length - nFft) / hopLength + 1;
        float[][] melSpec = new float[nFrames][nMels];

        // STFT and mel filterbank computation
        // This is a simplified placeholder - real implementation would use
        // a proper DSP library or JNI to native code

        // Placeholder: fill with random values for structure demonstration
        Random rand = new Random(42);
        for (int i = 0; i < nFrames; i++) {
            for (int j = 0; j < nMels; j++) {
                melSpec[i][j] = (float) rand.nextGaussian() * 0.5f;
            }
        }

        return melSpec;
    }

    /**
     * Run ONNX inference.
     */
    private String runInference(float[][] melSpectrogram, TranscriptionOptions options) throws OrtException {
        // Prepare input tensor
        int nFrames = melSpectrogram.length;
        float[] flatInput = new float[nFrames * N_MELS];
        for (int i = 0; i < nFrames; i++) {
            System.arraycopy(melSpectrogram[i], 0, flatInput, i * N_MELS, N_MELS);
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(flatInput),
            new long[]{1, nFrames, N_MELS}
        );

        // Run inference
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(session.getInputNames().iterator().next(), inputTensor);

        OrtSession.Result results = session.run(inputs);

        // Process output
        // This would decode the token IDs to text using the Whisper tokenizer
        // Placeholder: return dummy text
        return "Transcribed text from ONNX inference";
    }

    private List<WordTiming> generateWordTimings(String text, int nFrames) {
        // Simple word-level timing estimation
        // Real implementation would use attention weights from model
        List<WordTiming> timings = new ArrayList<>();
        String[] words = text.split(" ");
        double duration = (double) nFrames * HOP_LENGTH / SAMPLE_RATE;
        double wordDuration = duration / words.length;

        for (int i = 0; i < words.length; i++) {
            timings.add(new WordTiming(
                words[i],
                i * wordDuration,
                (i + 1) * wordDuration,
                0.9
            ));
        }

        return timings;
    }

    // ====================================================================================
    // Streaming Implementation
    // ====================================================================================

    private static class WhisperStreamingSession implements StreamingSession {
        private final UserProfile profile;
        private final SttConfig config;
        private final WhisperOnnxEngine engine;
        private final AtomicReference<Consumer<StreamingTranscription>> transcriptionCallback = new AtomicReference<>();
        private final AtomicReference<Consumer<ProcessingError>> errorCallback = new AtomicReference<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final List<AudioChunk> buffer = new ArrayList<>();
        private final StringBuilder accumulatedText = new StringBuilder();

        WhisperStreamingSession(UserProfile profile, SttConfig config, WhisperOnnxEngine engine) {
            this.profile = profile;
            this.config = config;
            this.engine = engine;
        }

        @Override
        public void feedAudio(AudioChunk chunk) {
            if (!active.get()) {
                throw new IllegalStateException("Session not active");
            }

            buffer.add(chunk);

            // Process when buffer reaches threshold or isLast
            if (chunk.isLast() || buffer.size() >= 30) { // 3 seconds of audio
                processBuffer();
            }
        }

        @Override
        public void onTranscription(Consumer<StreamingTranscription> callback) {
            transcriptionCallback.set(callback);
        }

        @Override
        public void onError(java.util.function.Consumer<com.ghatana.media.common.ProcessingError> callback) {
            errorCallback.set(callback);
        }

        @Override
        public void endStream() {
            active.set(false);
            processBuffer(); // Process remaining audio
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void close() {
            active.set(false);
        }

        private void processBuffer() {
            if (buffer.isEmpty()) return;

            // Combine chunks
            int totalLength = buffer.stream().mapToInt(c -> c.data().length).sum();
            byte[] combined = new byte[totalLength];
            int offset = 0;
            for (AudioChunk chunk : buffer) {
                System.arraycopy(chunk.data(), 0, combined, offset, chunk.data().length);
                offset += chunk.data().length;
            }
            buffer.clear();

            // Create audio data
            AudioData audio = AudioData.builder()
                .data(combined)
                .sampleRate(SAMPLE_RATE)
                .channels(1)
                .bitsPerSample(16)
                .build();

            // Transcribe
            try {
                TranscriptionResult result = engine.transcribe(audio, TranscriptionOptions.builder()
                    .language(Locale.getDefault())
                    .enablePunctuation(false) // Disable for streaming
                    .build());

                accumulatedText.append(result.getText()).append(" ");

                var callback = transcriptionCallback.get();
                if (callback != null) {
                    callback.accept(new StreamingTranscription(
                        accumulatedText.toString().trim(),
                        !active.get(), // isFinal when stream ends
                        result.confidence(),
                        result.words()
                    ));
                }
            } catch (Exception e) {
                var callback = errorCallback.get();
                if (callback != null) {
                    callback.accept(new InferenceError("Streaming transcription failed", e, true));
                }
            }
        }
    }
}
