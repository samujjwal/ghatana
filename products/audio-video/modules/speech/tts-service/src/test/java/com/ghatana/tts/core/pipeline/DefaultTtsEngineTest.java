// filepath: /Users/samujjwal/Development/ghatana/products/shared-services/text-to-speech/libs/tts-core-java/src/test/java/com/ghatana/tts/core/pipeline/DefaultTtsEngineTest.java
package com.ghatana.tts.core.pipeline;

import com.ghatana.tts.core.api.*;
import com.ghatana.tts.core.config.EngineConfig;
import com.ghatana.platform.testing.nativesupport.NativeDependencySupport;
import com.ghatana.platform.testing.nativesupport.NativeDependencySupport.NativeType;
import com.ghatana.platform.testing.nativesupport.NativeDependencySupport.RequireNative;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for DefaultTtsEngine.
 *
 * @doc.type class
 * @doc.purpose Unit tests for TTS engine
 * @doc.layer test
 * @doc.pattern TestCase
 */
@DisplayName("DefaultTtsEngine Tests")
@RequireNative(NativeType.COQUI_TTS)
@Tag("integration")
class DefaultTtsEngineTest {

    private Path tempDir;
    private DefaultTtsEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("tts-test");

        EngineConfig config = EngineConfig.builder()
            .modelsDirectory(tempDir.resolve("models"))
            .profilesDirectory(tempDir.resolve("profiles"))
            .cacheDirectory(tempDir.resolve("cache"))
            .defaultVoice("default-en")
            .defaultSampleRate(22050)
            .build();

        engine = new DefaultTtsEngine(config);

        // Wait for engine initialization
        int maxWait = 50; // 5 seconds
        while (!engine.getStatus().state().equals(EngineState.READY) && maxWait > 0) {
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

        assertThat(status.state()).isEqualTo(EngineState.READY);
        assertThat(status.errorMessage()).isNull();
    }

    @Test
    @DisplayName("Should synthesize text to audio")
    void shouldSynthesizeTextToAudio() {
        // GIVEN
        String text = "Hello, this is a test.";
        SynthesisOptions options = SynthesisOptions.builder()
            .voiceId("default-en")
            .language("en-US")
            .build();

        // WHEN
        SynthesisResult result = engine.synthesize(text, options);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.sampleRate()).isEqualTo(22050);
        assertThat(result.durationMs()).isGreaterThan(0);
        assertThat(result.voiceUsed()).isEqualTo("default-en");
    }

    @Test
    @DisplayName("Should stream synthesize text to audio chunks")
    void shouldStreamSynthesizeText() throws InterruptedException {
        // GIVEN
        String text = "First sentence. Second sentence. Third sentence.";
        SynthesisOptions options = SynthesisOptions.builder()
            .voiceId("default-en")
            .build();

        List<AudioChunk> chunks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // WHEN
        Thread synthThread = new Thread(() -> {
            engine.synthesizeStreaming(text, options, chunk -> {
                chunks.add(chunk);
                if (chunk.isFinal()) {
                    latch.countDown();
                }
            });
        });
        synthThread.start();

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        // THEN
        assertThat(completed).isTrue();
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(chunks.size() - 1).isFinal()).isTrue();
    }

    @Test
    @DisplayName("Should return available voices")
    void shouldReturnAvailableVoices() {
        // WHEN
        List<VoiceInfo> voices = engine.getAvailableVoices(null);

        // THEN
        assertThat(voices).isNotEmpty();
        assertThat(voices).anyMatch(v -> v.voiceId().equals("default-en"));
    }

    @Test
    @DisplayName("Should filter voices by language")
    void shouldFilterVoicesByLanguage() {
        // WHEN
        List<VoiceInfo> voices = engine.getAvailableVoices("en");

        // THEN
        assertThat(voices).isNotEmpty();
        assertThat(voices).allMatch(v ->
            v.languages().stream().anyMatch(lang -> lang.toLowerCase().startsWith("en"))
        );
    }

    @Test
    @DisplayName("Should create and retrieve user profile")
    void shouldCreateAndRetrieveProfile() {
        // GIVEN
        String profileId = "test-profile";
        String displayName = "Test User";
        ProfileSettings settings = ProfileSettings.builder()
            .preferredLanguage("en-US")
            .defaultVoiceId("default-en")
            .adaptationMode(AdaptationMode.BALANCED)
            .privacyLevel(PrivacyLevel.HIGH)
            .build();

        // WHEN
        UserProfile created = engine.createProfile(profileId, displayName, settings);
        UserProfile retrieved = engine.getProfile(profileId);

        // THEN
        assertThat(created).isNotNull();
        assertThat(created.profileId()).isEqualTo(profileId);
        assertThat(created.displayName()).isEqualTo(displayName);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.profileId()).isEqualTo(profileId);
    }

    @Test
    @DisplayName("Should update user profile")
    void shouldUpdateUserProfile() {
        // GIVEN
        String profileId = "update-test-profile";
        ProfileSettings initialSettings = ProfileSettings.builder()
            .preferredLanguage("en-US")
            .build();
        engine.createProfile(profileId, "Test", initialSettings);

        ProfileSettings newSettings = ProfileSettings.builder()
            .preferredLanguage("en-GB")
            .defaultVoiceId("aria-en")
            .build();

        // WHEN
        UserProfile updated = engine.updateProfile(profileId, newSettings);

        // THEN
        assertThat(updated.settings().preferredLanguage()).isEqualTo("en-GB");
        assertThat(updated.settings().defaultVoiceId()).isEqualTo("aria-en");
    }

    @Test
    @DisplayName("Should return engine metrics")
    void shouldReturnEngineMetrics() {
        // GIVEN - perform some synthesis
        engine.synthesize("Test", SynthesisOptions.builder().build());

        // WHEN
        EngineMetrics metrics = engine.getMetrics();

        // THEN
        assertThat(metrics).isNotNull();
        assertThat(metrics.totalSyntheses()).isGreaterThan(0);
        assertThat(metrics.memoryUsageBytes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should clone voice")
    void shouldCloneVoice() {
        // GIVEN
        String voiceName = "My Cloned Voice";
        List<byte[]> samples = List.of(
            new byte[22050 * 2], // 1 second of silence
            new byte[22050 * 2]
        );

        // WHEN
        CloneResult result = engine.cloneVoice(voiceName, samples, 100, 0.001f);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.voiceId()).isNotNull();
        assertThat(result.voice()).isNotNull();
        assertThat(result.voice().isCloned()).isTrue();
    }

    @Test
    @DisplayName("Should shutdown cleanly")
    void shouldShutdownCleanly() {
        // WHEN
        engine.close();

        // THEN
        assertThat(engine.getStatus().state()).isEqualTo(EngineState.SHUTDOWN);
    }
}

