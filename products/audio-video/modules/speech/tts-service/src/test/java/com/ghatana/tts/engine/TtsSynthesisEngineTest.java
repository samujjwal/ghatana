package com.ghatana.tts.engine;

import com.ghatana.tts.engine.TtsSynthesisEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TtsSynthesisEngine}.
 *
 * @doc.type    class
 * @doc.purpose TtsSynthesisEngine: voice selection, SSML, prosody, encoding, error cases
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("TtsSynthesisEngineTest [GH-90000]")
class TtsSynthesisEngineTest {

    private static final Set<String> VOICES = Set.of( // GH-90000
            "en-US-standard-a", "en-US-standard-b", "fr-FR-standard-a"
    );

    private TtsSynthesisEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new TtsSynthesisEngine("tts-v2", VOICES); // GH-90000
    }

    // ── Default voice synthesis ───────────────────────────────────────────────

    @Test
    @DisplayName("synthesis with default voice produces non-empty audio [GH-90000]")
    void synthesisDefaultVoiceProducesAudio() { // GH-90000
        VoiceConfig voice = VoiceConfig.defaults(); // GH-90000
        SynthesisResult result = engine.synthesize("Hello world", voice, AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.isEmpty()).isFalse(); // GH-90000
        assertThat(result.audioContent().length).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("synthesis result carries the voice ID from the config [GH-90000]")
    void synthesisResultHasVoiceId() { // GH-90000
        VoiceConfig voice = VoiceConfig.defaults(); // GH-90000
        SynthesisResult result = engine.synthesize("Test input", voice, AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(result.voiceId()).isEqualTo(voice.voiceId()); // GH-90000
    }

    // ── Multiple voices ───────────────────────────────────────────────────────

    @ParameterizedTest(name = "voice={0}") // GH-90000
    @EnumSource(AudioEncoding.class) // GH-90000
    @DisplayName("all output encodings are accepted for the default voice [GH-90000]")
    void allEncodingsAccepted(AudioEncoding encoding) { // GH-90000
        SynthesisResult result = engine.synthesize("Test", VoiceConfig.defaults(), encoding); // GH-90000
        assertThat(result.encoding()).isEqualTo(encoding); // GH-90000
    }

    @Test
    @DisplayName("second registered voice produces audio [GH-90000]")
    void secondVoiceProducesAudio() { // GH-90000
        VoiceConfig voice = new VoiceConfig("en-US-standard-b", "en-US", "MALE", 1.0f, 0.0f, 0.0f); // GH-90000
        SynthesisResult result = engine.synthesize("Hello again", voice, AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(result.audioContent().length).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("French voice produces audio [GH-90000]")
    void frenchVoiceProducesAudio() { // GH-90000
        VoiceConfig voice = new VoiceConfig("fr-FR-standard-a", "fr-FR", "FEMALE", 1.0f, 0.0f, 0.0f); // GH-90000
        SynthesisResult result = engine.synthesize("Bonjour le monde", voice, AudioEncoding.OPUS); // GH-90000
        assertThat(result.isEmpty()).isFalse(); // GH-90000
    }

    // ── SSML synthesis ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SSML break tag is accepted without error [GH-90000]")
    void ssmlBreakAccepted() { // GH-90000
        String ssml = "<speak>Hello <break time=\"500ms\"/> world.</speak>";
        assertThatCode(() -> engine.synthesize(ssml, VoiceConfig.defaults(), AudioEncoding.PCM_16BIT)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("SSML prosody tag is accepted without error [GH-90000]")
    void ssmlProsodyAccepted() { // GH-90000
        String ssml = "<speak><prosody rate=\"slow\">Test text to speak.</prosody></speak>";
        assertThatCode(() -> engine.synthesize(ssml, VoiceConfig.defaults(), AudioEncoding.MP3)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("SSML emphasis tag produces non-empty audio [GH-90000]")
    void ssmlEmphasisProducesAudio() { // GH-90000
        String ssml = "<speak>This is <emphasis level=\"strong\">important</emphasis>.</speak>";
        SynthesisResult result = engine.synthesize(ssml, VoiceConfig.defaults(), AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(result.audioContent()).isNotEmpty(); // GH-90000
    }

    // ── Prosody control ───────────────────────────────────────────────────────

    @Test
    @DisplayName("faster speaking rate produces shorter estimated duration [GH-90000]")
    void fasterRateShortsDuration() { // GH-90000
        VoiceConfig fast = new VoiceConfig("en-US-standard-a", "en-US", "NEUTRAL", 2.0f, 0.0f, 0.0f); // GH-90000
        VoiceConfig slow = new VoiceConfig("en-US-standard-a", "en-US", "NEUTRAL", 0.5f, 0.0f, 0.0f); // GH-90000
        String text = "This is a test sentence with several words";
        SynthesisResult fastResult = engine.synthesize(text, fast, AudioEncoding.PCM_16BIT); // GH-90000
        SynthesisResult slowResult = engine.synthesize(text, slow, AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(fastResult.duration()).isLessThan(slowResult.duration()); // GH-90000
    }

    @Test
    @DisplayName("pitch and volume settings do not cause exceptions [GH-90000]")
    void pitchAndVolumeAccepted() { // GH-90000
        VoiceConfig voice = new VoiceConfig("en-US-standard-a", "en-US", "NEUTRAL", 1.0f, 2.0f, -3.0f); // GH-90000
        assertThatCode(() -> engine.synthesize("Test pitch and volume", voice, AudioEncoding.PCM_16BIT)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── Synthesis quality attributes ──────────────────────────────────────────

    @Test
    @DisplayName("sample rate is positive [GH-90000]")
    void sampleRateIsPositive() { // GH-90000
        SynthesisResult result = engine.synthesize("Sample rate check", VoiceConfig.defaults(), AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(result.sampleRateHz()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("processing time is non-negative [GH-90000]")
    void processingTimeIsNonNegative() { // GH-90000
        SynthesisResult result = engine.synthesize("Processing time", VoiceConfig.defaults(), AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(result.processingSeconds()).isGreaterThanOrEqualTo(0.0); // GH-90000
    }

    @Test
    @DisplayName("longer text produces longer audio duration [GH-90000]")
    void longerTextHasLongerDuration() { // GH-90000
        String longText = "The quick brown fox jumps over the lazy dog. " .repeat(10); // GH-90000
        String shortText = "Hello world";
        SynthesisResult longResult = engine.synthesize(longText, VoiceConfig.defaults(), AudioEncoding.PCM_16BIT); // GH-90000
        SynthesisResult shortResult = engine.synthesize(shortText, VoiceConfig.defaults(), AudioEncoding.PCM_16BIT); // GH-90000
        assertThat(longResult.duration()).isGreaterThan(shortResult.duration()); // GH-90000
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("null text throws SynthesisException [GH-90000]")
    void nullTextThrows() { // GH-90000
        assertThatThrownBy(() -> engine.synthesize(null, VoiceConfig.defaults(), AudioEncoding.PCM_16BIT)) // GH-90000
                .isInstanceOf(TtsSynthesisEngine.SynthesisException.class); // GH-90000
    }

    @Test
    @DisplayName("blank text throws SynthesisException [GH-90000]")
    void blankTextThrows() { // GH-90000
        assertThatThrownBy(() -> engine.synthesize("   ", VoiceConfig.defaults(), AudioEncoding.PCM_16BIT)) // GH-90000
                .isInstanceOf(TtsSynthesisEngine.SynthesisException.class); // GH-90000
    }

    @Test
    @DisplayName("unsupported voice ID throws SynthesisException [GH-90000]")
    void unsupportedVoiceThrows() { // GH-90000
        VoiceConfig unknown = new VoiceConfig("xx-XX-unknown", "xx-XX", "NEUTRAL", 1.0f, 0.0f, 0.0f); // GH-90000
        assertThatThrownBy(() -> engine.synthesize("Hello", unknown, AudioEncoding.PCM_16BIT)) // GH-90000
                .isInstanceOf(TtsSynthesisEngine.SynthesisException.class) // GH-90000
                .hasMessageContaining("Unsupported voice [GH-90000]");
    }

    // ── Engine metadata ───────────────────────────────────────────────────────

    @Test
    @DisplayName("engine reports correct model ID [GH-90000]")
    void engineModelId() { // GH-90000
        assertThat(engine.getModelId()).isEqualTo("tts-v2 [GH-90000]");
    }

    @Test
    @DisplayName("engine reports supported voice IDs [GH-90000]")
    void engineSupportedVoices() { // GH-90000
        assertThat(engine.getSupportedVoiceIds()).containsAll(VOICES); // GH-90000
    }
}
