// filepath: /Users/samujjwal/Development/ghatana/products/shared-services/speech-to-text/libs/stt-core-java/src/test/java/com/ghatana/stt/core/pipeline/DefaultAdaptiveSTTEngineTest.java
package com.ghatana.stt.core.pipeline;

import com.ghatana.stt.core.api.*;
import com.ghatana.stt.core.config.EngineConfig;
import com.ghatana.testing.nativesupport.NativeDependencySupport;
import com.ghatana.testing.nativesupport.NativeDependencySupport.NativeType;
import com.ghatana.testing.nativesupport.NativeDependencySupport.RequireNative;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for DefaultAdaptiveSTTEngine.
 *
 * @doc.type class
 * @doc.purpose Unit tests for STT engine
 * @doc.layer test
 * @doc.pattern TestCase
 */
@DisplayName("DefaultAdaptiveSTTEngine Tests")
@RequireNative(NativeType.WHISPER_CPP)
@Tag("integration")
class DefaultAdaptiveSTTEngineTest {

    private Path tempDir;
    private DefaultAdaptiveSTTEngine engine;
    private EngineConfig config;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("stt-test");

        config = EngineConfig.builder()
            .modelPath(tempDir.resolve("models"))
            .dataPath(tempDir.resolve("data"))
            .defaultModel("whisper-tiny")
            .build();

        engine = new DefaultAdaptiveSTTEngine(config);

        // Wait for engine initialization
        int maxWait = 50; // 5 seconds
        while (!engine.isReady() && maxWait > 0) {
            Thread.sleep(100);
            maxWait--;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
        // Clean up temp directory
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try { Files.delete(path); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    @DisplayName("Engine should initialize to READY state")
    void shouldInitializeToReadyState() {
        EngineStatus status = engine.getStatus();

        assertThat(status.isReady()).isTrue();
    }

    @Test
    @DisplayName("Should transcribe audio")
    void shouldTranscribeAudio() {
        // GIVEN - create test audio data (silent audio)
        byte[] audioBytes = new byte[16000 * 2]; // 1 second of silence at 16kHz
        AudioData audio = AudioData.fromPcm(audioBytes, 16000);

        TranscriptionOptions options = TranscriptionOptions.builder()
            .language("en-US")
            .enablePunctuation(true)
            .build();

        // WHEN
        TranscriptionResult result = engine.transcribe(audio, options);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.text()).isNotNull();
        assertThat(result.isFinal()).isTrue();
    }

    @Test
    @DisplayName("Should return available models")
    void shouldReturnAvailableModels() {
        // WHEN
        List<ModelInfo> models = engine.getAvailableModels();

        // THEN
        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.modelId().equals("whisper-tiny"));
    }

    @Test
    @DisplayName("Should create and retrieve user profile")
    void shouldCreateAndRetrieveProfile() {
        // GIVEN
        List<AudioSample> enrollmentData = List.of(
            AudioSample.of(AudioData.fromPcm(new byte[16000 * 2], 16000), "Test transcript")
        );

        // WHEN
        UserProfile profile = engine.createUserProfile(enrollmentData);
        UserProfile loaded = engine.loadUserProfile(profile.getProfileId());

        // THEN
        assertThat(profile).isNotNull();
        assertThat(profile.getProfileId()).isNotNull();
        assertThat(loaded).isNotNull();
        assertThat(loaded.getProfileId()).isEqualTo(profile.getProfileId());
    }

    @Test
    @DisplayName("Should create streaming session")
    void shouldCreateStreamingSession() {
        // GIVEN
        UserProfile profile = engine.createUserProfile(List.of());

        // WHEN
        StreamingSession session = engine.createStreamingSession(profile);

        // THEN
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.getState()).isEqualTo(StreamingSession.SessionState.CREATED);
    }

    @Test
    @DisplayName("Should process streaming audio")
    void shouldProcessStreamingAudio() throws InterruptedException {
        // GIVEN
        UserProfile profile = engine.createUserProfile(List.of());
        StreamingSession session = engine.createStreamingSession(profile);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TranscriptionResult> resultRef = new AtomicReference<>();

        session.onTranscription(result -> {
            resultRef.set(result);
            if (result.isFinal()) {
                latch.countDown();
            }
        });

        // WHEN
        session.start();

        // Feed audio chunks
        byte[] chunk = new byte[3200]; // 100ms at 16kHz
        for (int i = 0; i < 10; i++) {
            session.feedAudio(StreamingSession.AudioChunk.of(chunk, 16000));
            Thread.sleep(50);
        }

        session.stop();

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        // THEN
        assertThat(completed).isTrue();
        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get().isFinal()).isTrue();
    }

    @Test
    @DisplayName("Should return engine metrics")
    void shouldReturnEngineMetrics() {
        // GIVEN - perform some transcription
        byte[] audioBytes = new byte[16000 * 2];
        AudioData audio = AudioData.fromPcm(audioBytes, 16000);
        engine.transcribe(audio, TranscriptionOptions.builder().build());

        // WHEN
        EngineMetrics metrics = engine.getMetrics();

        // THEN
        assertThat(metrics).isNotNull();
        assertThat(metrics.memoryUsageBytes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should adapt from interaction")
    void shouldAdaptFromInteraction() {
        // GIVEN
        Interaction interaction = new Interaction(
            "This is the original text",
            "This is the corrected text",
            null, // no audio features
            "test-context",
            null, // use default timestamp
            1000L
        );

        // WHEN
        AdaptationResult result = engine.adaptFromInteraction(interaction);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("Should shutdown cleanly")
    void shouldShutdownCleanly() {
        // WHEN
        try {
            engine.close();
        } catch (Exception e) {
            // Expected during shutdown
        }

        // THEN
        assertThat(engine.getStatus().state()).isEqualTo(EngineStatus.State.SHUTDOWN);
    }
}

