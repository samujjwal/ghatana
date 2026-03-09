package com.ghatana.tts.core.integration;

import com.ghatana.tts.core.api.*;
import com.ghatana.tts.core.config.EngineConfig;
import com.ghatana.tts.core.pipeline.DefaultTtsEngine;
import com.ghatana.tts.core.storage.ProfileEncryption;
import com.ghatana.tts.core.storage.ProfileStorage;
import com.ghatana.platform.testing.nativesupport.NativeDependencySupport;
import com.ghatana.platform.testing.nativesupport.NativeDependencySupport.NativeType;
import com.ghatana.platform.testing.nativesupport.NativeDependencySupport.RequireNative;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TTS end-to-end workflows.
 *
 * @doc.type test
 * @doc.purpose Integration testing for complete TTS pipeline
 * @doc.layer integration
 */
@DisplayName("TTS Integration Tests")
@RequireNative(NativeType.COQUI_TTS)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TtsIntegrationTest {

    @TempDir
    Path tempDir;

    private TtsEngine engine;
    private ProfileStorage profileStorage;
    private EngineConfig config;

    @BeforeEach
    void setUp() throws Exception {
        // Create test configuration
        config = EngineConfig.builder()
                .modelsDirectory(tempDir.resolve("models"))
                .profilesDirectory(tempDir.resolve("profiles"))
                .cacheDirectory(tempDir.resolve("cache"))
                .defaultVoice("default-en")
                .defaultSampleRate(22050)
                .build();

        // Initialize engine
        engine = new DefaultTtsEngine(config);

        // Initialize profile storage
        ProfileEncryption encryption = new ProfileEncryption();
        profileStorage = new ProfileStorage(config.profilesDirectory(), encryption);

        // Wait for engine initialization
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
    @DisplayName("Integration: Text → Synthesis → Audio")
    void testBasicSynthesisWorkflow() {
        // GIVEN
        String text = "Hello world, this is a test of text to speech synthesis.";
        SynthesisOptions options = new SynthesisOptions.Builder()
                .voiceId("default-en")
                .speed(1.0f)
                .build();

        // WHEN
        SynthesisResult result = engine.synthesize(text, options);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.sampleRate()).isEqualTo(config.defaultSampleRate());
        assertThat(result.durationMs()).isGreaterThan(0);
        assertThat(result.voiceUsed()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Integration: Streaming Synthesis with Chunks")
    void testStreamingSynthesisWorkflow() throws Exception {
        // GIVEN
        String text = "This is a longer text for streaming synthesis. " +
                "It should be split into multiple chunks. " +
                "Each chunk will be synthesized separately.";
        SynthesisOptions options = new SynthesisOptions.Builder()
                .voiceId("default-en")
                .build();

        AtomicInteger chunkCount = new AtomicInteger(0);
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();

        // WHEN
        engine.synthesizeStreaming(text, options, chunk -> {
            chunkCount.incrementAndGet();
            assertThat(chunk.audioData()).isNotEmpty();
            assertThat(chunk.sampleRate()).isEqualTo(config.defaultSampleRate());

            if (chunk.isFinal()) {
                completionFuture.complete(null);
            }
        });

        // THEN
        completionFuture.get(10, TimeUnit.SECONDS);
        assertThat(chunkCount.get()).isGreaterThan(0);
    }

    @Test
    @Order(3)
    @DisplayName("Integration: Voice Management Lifecycle")
    void testVoiceManagementWorkflow() {
        // GIVEN
        List<VoiceInfo> voices = engine.getAvailableVoices(null);
        assertThat(voices).isNotEmpty();

        // WHEN - Load a voice
        VoiceInfo voice = voices.get(0);
        VoiceInfo loaded = engine.loadVoice(voice.voiceId());

        // THEN
        assertThat(loaded).isNotNull();
        assertThat(loaded.isLoaded()).isTrue();
        assertThat(loaded.voiceId()).isEqualTo(voice.voiceId());

        // Filter by language
        List<VoiceInfo> englishVoices = engine.getAvailableVoices("en");
        assertThat(englishVoices).isNotEmpty();
        englishVoices.forEach(v -> assertThat(v.languages()).anyMatch(lang -> lang.startsWith("en")));
    }

    @Test
    @Order(4)
    @DisplayName("Integration: Profile Create → Save → Load")
    void testProfileLifecycle() throws Exception {
        // GIVEN
        UserProfile profile = engine.createProfile("test-profile-1", "Test User",
                new ProfileSettings("en-US", null,
                        AdaptationMode.BALANCED,
                        PrivacyLevel.MEDIUM));

        // WHEN - Retrieve and modify
        UserProfile retrieved = engine.getProfile("test-profile-1");

        // THEN
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.profileId()).isEqualTo("test-profile-1");
        assertThat(retrieved.displayName()).isEqualTo("Test User");

        // Verify encrypted on disk
        assertThat(profileStorage.exists("test-profile-1")).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Integration: Profile Update Workflow")
    void testProfileUpdateWorkflow() {
        // GIVEN
        UserProfile profile = engine.createProfile("test-profile-2", "Original Name",
                new ProfileSettings("en-US", null,
                        AdaptationMode.BALANCED,
                        PrivacyLevel.HIGH));

        // WHEN - Update settings
        ProfileSettings newSettings = new ProfileSettings(
                "en-GB", null,
                AdaptationMode.AGGRESSIVE,
                PrivacyLevel.MEDIUM);
        UserProfile updated = engine.updateProfile("test-profile-2", newSettings);

        // THEN
        assertThat(updated.getSettings().preferredLanguage()).isEqualTo("en-GB");
        assertThat(updated.getSettings().adaptationMode())
                .isEqualTo(AdaptationMode.AGGRESSIVE);

        // Reload and verify persistence
        UserProfile reloaded = engine.getProfile("test-profile-2");
        assertThat(reloaded.getSettings().preferredLanguage()).isEqualTo("en-GB");
    }

    @Test
    @Order(6)
    @DisplayName("Integration: Voice Cloning Workflow")
    void testVoiceCloning() {
        // GIVEN - Simulated voice samples
        byte[] sample1 = generateAudioSample(22050, 2.0);
        byte[] sample2 = generateAudioSample(22050, 2.0);
        byte[] sample3 = generateAudioSample(22050, 2.0);

        List<byte[]> samples = List.of(sample1, sample2, sample3);

        // WHEN - Clone voice
        CloneResult result = engine.cloneVoice("Custom Voice", samples, 100, 0.001f);

        // THEN
        assertThat(result.success()).isTrue();
        assertThat(result.voiceId()).isNotNull();
        assertThat(result.similarityScore()).isGreaterThan(0.0f);
        assertThat(result.voice()).isNotNull();
        assertThat(result.voice().isCloned()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("Integration: Engine Metrics During Operations")
    void testMetricsCollection() {
        // GIVEN - Initial metrics
        EngineMetrics initialMetrics = engine.getMetrics();
        long initialSyntheses = initialMetrics.totalSyntheses();

        // WHEN - Perform syntheses
        for (int i = 0; i < 5; i++) {
            engine.synthesize("Test synthesis " + i,
                    new SynthesisOptions.Builder().build());
        }

        // THEN
        EngineMetrics finalMetrics = engine.getMetrics();
        assertThat(finalMetrics.totalSyntheses())
                .isEqualTo(initialSyntheses + 5);
        assertThat(finalMetrics.realTimeFactor()).isGreaterThanOrEqualTo(0.0f);
        assertThat(finalMetrics.averageLatencyMs()).isGreaterThanOrEqualTo(0.0f);
    }

    @Test
    @Order(8)
    @DisplayName("Integration: Engine Status Monitoring")
    void testEngineStatusWorkflow() {
        // WHEN
        EngineStatus status = engine.getStatus();

        // THEN
        assertThat(status).isNotNull();
        assertThat(status.state()).isIn(
                EngineState.READY,
                EngineState.INITIALIZING);

        // After synthesis
        engine.synthesize("Status test", new SynthesisOptions.Builder().build());

        EngineStatus afterStatus = engine.getStatus();
        assertThat(afterStatus.state()).isEqualTo(EngineState.READY);
    }

    @Test
    @Order(9)
    @DisplayName("Integration: Feedback Submission")
    void testFeedbackWorkflow() {
        // GIVEN - Synthesize some text
        String text = "This is for feedback testing.";
        SynthesisResult result = engine.synthesize(text,
                new SynthesisOptions.Builder().voiceId("default-en").build());

        // WHEN - Submit feedback
        engine.submitFeedback(
                "test-profile",
                "synthesis-" + System.currentTimeMillis(),
                "quality",
                "Sounds great!");

        // THEN - No exception should be thrown
        assertThat(result).isNotNull();
    }

    @Test
    @Order(10)
    @DisplayName("Integration: Multiple Voices Concurrent Synthesis")
    void testMultipleVoicesConcurrent() {
        // GIVEN
        List<VoiceInfo> voices = engine.getAvailableVoices(null);
        assertThat(voices.size()).isGreaterThan(1);

        // WHEN - Synthesize with different voices
        String text = "Testing multiple voices.";

        SynthesisResult result1 = engine.synthesize(text,
                new SynthesisOptions.Builder().voiceId(voices.get(0).voiceId()).build());

        SynthesisResult result2 = engine.synthesize(text,
                new SynthesisOptions.Builder().voiceId(voices.get(1).voiceId()).build());

        // THEN
        assertThat(result1.voiceUsed()).isEqualTo(voices.get(0).voiceId());
        assertThat(result2.voiceUsed()).isEqualTo(voices.get(1).voiceId());

        // Both should produce audio
        assertThat(result1.audioData()).isNotEmpty();
        assertThat(result2.audioData()).isNotEmpty();
    }

    @Test
    @Order(11)
    @DisplayName("Integration: Profile Encryption End-to-End")
    void testProfileEncryptionIntegration() throws Exception {
        // GIVEN
        UserProfile profile = engine.createProfile("encrypted-test", "Encrypted User",
                new ProfileSettings("en-US", null,
                        AdaptationMode.BALANCED,
                        PrivacyLevel.HIGH));

        // WHEN - Verify saved encrypted
        Path profileFile = config.profilesDirectory()
                .resolve(profile.profileId() + ".enc");

        // THEN
        assertThat(profileFile).exists();

        // Load and verify
        UserProfile loaded = engine.getProfile(profile.profileId());
        assertThat(loaded.profileId()).isEqualTo(profile.profileId());
        assertThat(loaded.displayName()).isEqualTo("Encrypted User");
    }

    @Test
    @Order(12)
    @DisplayName("Integration: Engine Shutdown and Cleanup")
    void testEngineShutdownWorkflow() throws Exception {
        // WHEN
        engine.close();

        // THEN
        EngineStatus status = engine.getStatus();
        assertThat(status.state()).isEqualTo(EngineState.SHUTDOWN);
    }

    // Helper to generate audio sample
    private byte[] generateAudioSample(int sampleRate, double durationSeconds) {
        int numSamples = (int) (sampleRate * durationSeconds);
        byte[] audioData = new byte[numSamples * 2];

        double frequency = 440.0;
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double sample = 0.5 * Math.sin(2 * Math.PI * frequency * t);
            short sampleShort = (short) (sample * Short.MAX_VALUE);

            audioData[i * 2] = (byte) (sampleShort & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sampleShort >> 8) & 0xFF);
        }

        return audioData;
    }
}
