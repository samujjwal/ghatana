package com.ghatana.stt.core.integration;

import com.ghatana.stt.core.api.*;
import com.ghatana.stt.core.config.EngineConfig;
import com.ghatana.stt.core.pipeline.DefaultAdaptiveSTTEngine;
import com.ghatana.stt.core.storage.ProfileEncryption;
import com.ghatana.stt.core.storage.ProfileStorage;
import com.ghatana.testing.nativesupport.NativeDependencySupport;
import com.ghatana.testing.nativesupport.NativeDependencySupport.NativeType;
import com.ghatana.testing.nativesupport.NativeDependencySupport.RequireNative;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for STT end-to-end workflows.
 *
 * @doc.type test
 * @doc.purpose Integration testing for complete STT pipeline
 * @doc.layer integration
 */
@DisplayName("STT Integration Tests")
@RequireNative(NativeType.WHISPER_CPP)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SttIntegrationTest {

    @TempDir
    Path tempDir;

    private AdaptiveSTTEngine engine;
    private ProfileStorage profileStorage;
    private EngineConfig config;

    @BeforeEach
    void setUp() throws Exception {
        // Create test configuration
        config = EngineConfig.builder()
                .modelPath(tempDir.resolve("models"))
                .dataPath(tempDir.resolve("data"))
                .defaultModel("whisper-tiny")
                .build();

        // Initialize engine
        engine = new DefaultAdaptiveSTTEngine(config);

        // Initialize profile storage
        ProfileEncryption encryption = new ProfileEncryption();
        profileStorage = new ProfileStorage(config.dataPath().resolve("profiles"), encryption);

        // Wait for engine to initialize
        Thread.sleep(500);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                // Ignore cleanup errors in tests
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Integration: Audio → Transcription → Result")
    void testBasicTranscriptionWorkflow() {
        // GIVEN - Synthetic audio data (1 second at 16kHz)
        byte[] audioData = generateTestAudio(16000, 1.0);
        AudioData audio = AudioData.fromPcm(audioData, 16000);
        TranscriptionOptions options = TranscriptionOptions.builder()
                .language("en-US")
                .enablePunctuation(true)
                .build();

        // WHEN - Transcribe
        TranscriptionResult result = engine.transcribe(audio, options);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.text()).isNotEmpty();
        assertThat(result.confidence()).isGreaterThan(0.0f);
        assertThat(result.processingTimeMs()).isGreaterThan(0);
        assertThat(result.modelUsed()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Integration: Profile Create → Save → Load → Verify")
    void testProfileLifecycle() throws Exception {
        // GIVEN - Create profile
        List<AudioSample> enrollmentData = List.of();
        UserProfile profile = engine.createUserProfile(enrollmentData);
        profile.setDisplayName("Test User");

        // WHEN - Save profile
        engine.saveUserProfile(profile);

        // THEN - Load and verify
        UserProfile loaded = engine.loadUserProfile(profile.getProfileId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getProfileId()).isEqualTo(profile.getProfileId());
        assertThat(loaded.getDisplayName()).isEqualTo("Test User");

        // Verify encrypted on disk
        assertThat(profileStorage.exists(profile.getProfileId())).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Integration: Streaming Session → Transcription → Cleanup")
    void testStreamingSessionWorkflow() throws Exception {
        // GIVEN - Create profile and session
        UserProfile profile = UserProfile.create("Streaming Test");
        StreamingSession session = engine.createStreamingSession(profile);

        CompletableFuture<TranscriptionResult> resultFuture = new CompletableFuture<>();

        // Register callback
        session.onTranscription(result -> {
            if (result.isFinal()) {
                resultFuture.complete(result);
            }
        });

        // WHEN - Start session and feed audio
        session.start();

        // Feed multiple chunks
        for (int i = 0; i < 3; i++) {
            byte[] chunkData = generateTestAudio(16000, 0.5); // 500ms chunks
            StreamingSession.AudioChunk chunk = StreamingSession.AudioChunk.of(chunkData, 16000);
            session.feedAudio(chunk);
            Thread.sleep(100); // Small delay between chunks
        }

        session.stop();

        // THEN - Verify transcription received
        TranscriptionResult result = resultFuture.get(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.isFinal()).isTrue();

        // Verify stats
        StreamingSession.SessionStats stats = session.getStats();
        assertThat(stats.chunksProcessed()).isGreaterThan(0);
        assertThat(stats.transcriptionsEmitted()).isGreaterThan(0);

        // Cleanup
        session.close();
    }

    @Test
    @Order(4)
    @DisplayName("Integration: Async Transcription")
    void testAsyncTranscriptionWorkflow() throws Exception {
        // GIVEN
        byte[] audioData = generateTestAudio(16000, 2.0);
        AudioData audio = AudioData.fromPcm(audioData, 16000);
        TranscriptionOptions options = TranscriptionOptions.builder()
                .language("en-US")
                .build();

        // WHEN — transcribeAsync now returns Promise<TranscriptionResult>;
        // bridge to CompletableFuture so the test can block.
        io.activej.promise.Promise<TranscriptionResult> promise = engine.transcribeAsync(audio, options);
        CompletableFuture<TranscriptionResult> future = new CompletableFuture<>();
        promise.whenResult(future::complete);
        promise.whenException(future::completeExceptionally);

        // THEN
        TranscriptionResult result = future.get(10, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.text()).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("Integration: Metrics Collection During Operations")
    void testMetricsCollection() {
        // GIVEN - Initial metrics
        EngineMetrics initialMetrics = engine.getMetrics();
        long initialTranscriptions = initialMetrics.totalTranscriptions();

        // WHEN - Perform multiple transcriptions
        for (int i = 0; i < 5; i++) {
            byte[] audioData = generateTestAudio(16000, 1.0);
            AudioData audio = AudioData.fromPcm(audioData, 16000);
            engine.transcribe(audio, TranscriptionOptions.builder().build());
        }

        // THEN - Verify metrics updated
        EngineMetrics finalMetrics = engine.getMetrics();
        assertThat(finalMetrics.totalTranscriptions())
                .isEqualTo(initialTranscriptions + 5);
        assertThat(finalMetrics.realTimeFactor()).isGreaterThan(0.0f);
        assertThat(finalMetrics.averageConfidence()).isGreaterThan(0.0f);
    }

    @Test
    @Order(6)
    @DisplayName("Integration: Model Loading")
    void testModelLoadingWorkflow() {
        // GIVEN
        List<ModelInfo> models = engine.getAvailableModels();
        assertThat(models).isNotEmpty();

        // WHEN - Load a model
        ModelInfo model = models.get(0);
        engine.loadModel(model.modelId(), null);

        // THEN
        ModelInfo activeModel = engine.getActiveModel();
        assertThat(activeModel).isNotNull();
        assertThat(activeModel.modelId()).isEqualTo(model.modelId());
    }

    @Test
    @Order(7)
    @DisplayName("Integration: Profile Encryption End-to-End")
    void testProfileEncryptionIntegration() throws Exception {
        // GIVEN - Create and configure profile
        UserProfile profile = UserProfile.create("Encryption Test");
        profile.addVocabularyTerms(List.of("test", "integration", "encryption"));

        // WHEN - Save with encryption
        engine.saveUserProfile(profile);

        // THEN - Verify encrypted on disk
        Path profileFile = config.dataPath()
                .resolve("profiles")
                .resolve(profile.getProfileId() + ".enc");

        assertThat(profileFile).exists();

        // Load and verify
        UserProfile loaded = engine.loadUserProfile(profile.getProfileId());
        assertThat(loaded.getProfileId()).isEqualTo(profile.getProfileId());
        assertThat(loaded.getPersonalVocabulary()).containsAll(profile.getPersonalVocabulary());
    }

    @Test
    @Order(8)
    @DisplayName("Integration: Engine Status Throughout Lifecycle")
    void testEngineStatusWorkflow() {
        // WHEN - Check status at various stages
        EngineStatus status = engine.getStatus();

        // THEN - Verify status is valid
        assertThat(status).isNotNull();
        assertThat(status.state()).isIn(
                EngineStatus.State.READY,
                EngineStatus.State.INITIALIZING);

        // After operations
        byte[] audioData = generateTestAudio(16000, 1.0);
        AudioData audio = AudioData.fromPcm(audioData, 16000);
        engine.transcribe(audio, TranscriptionOptions.builder().build());

        EngineStatus afterStatus = engine.getStatus();
        assertThat(afterStatus.state()).isEqualTo(EngineStatus.State.READY);
    }

    @Test
    @Order(9)
    @DisplayName("Integration: Multiple Profiles Concurrent Access")
    void testMultipleProfilesConcurrent() throws Exception {
        // GIVEN - Create multiple profiles
        UserProfile profile1 = UserProfile.create("User 1");
        UserProfile profile2 = UserProfile.create("User 2");
        UserProfile profile3 = UserProfile.create("User 3");

        // WHEN - Save all
        engine.saveUserProfile(profile1);
        engine.saveUserProfile(profile2);
        engine.saveUserProfile(profile3);

        // THEN - Load all and verify
        UserProfile loaded1 = engine.loadUserProfile(profile1.getProfileId());
        UserProfile loaded2 = engine.loadUserProfile(profile2.getProfileId());
        UserProfile loaded3 = engine.loadUserProfile(profile3.getProfileId());

        assertThat(loaded1.getDisplayName()).isEqualTo("User 1");
        assertThat(loaded2.getDisplayName()).isEqualTo("User 2");
        assertThat(loaded3.getDisplayName()).isEqualTo("User 3");
    }

    @Test
    @Order(10)
    @DisplayName("Integration: Engine Shutdown and Cleanup")
    void testEngineShutdownWorkflow() {
        // GIVEN - Active sessions
        UserProfile profile = UserProfile.create("Shutdown Test");
        StreamingSession session = engine.createStreamingSession(profile);
        session.start();

        // WHEN - Shutdown
        try {
            engine.close();
        } catch (Exception e) {
            // Expected during shutdown
        }

        // THEN - Verify state
        EngineStatus status = engine.getStatus();
        assertThat(status.state()).isEqualTo(EngineStatus.State.SHUTDOWN);
    }

    // ... (rest of the code remains the same)
    // Helper method to generate test audio data
    private byte[] generateTestAudio(int sampleRate, double durationSeconds) {
        int numSamples = (int) (sampleRate * durationSeconds);
        byte[] audioData = new byte[numSamples * 2]; // 16-bit samples

        // Generate simple sine wave
        double frequency = 440.0; // A4 note
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double sample = 0.5 * Math.sin(2 * Math.PI * frequency * t);
            short sampleShort = (short) (sample * Short.MAX_VALUE);

            // Convert to bytes (little-endian)
            audioData[i * 2] = (byte) (sampleShort & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sampleShort >> 8) & 0xFF);
        }

        return audioData;
    }
}
