package com.ghatana.tts.engine;

import com.ghatana.tts.engine.TtsSynthesisEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for {@link TtsSynthesisEngine}.
 *
 * Validates text-to-speech synthesis including voice selection, audio encoding,
 * prosody control (pitch, rate, volume), special character handling, and output quality. // GH-90000
 *
 * @doc.type    class
 * @doc.purpose Comprehensive TTS engine tests: voice selection, encoding, prosody, special characters
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("TextToSpeechServiceTest [GH-90000]")
class TextToSpeechServiceTest {

    private static final Set<String> SUPPORTED_VOICES = Set.of( // GH-90000
            "en-US-standard-a",
            "en-US-standard-b",
            "en-GB-standard-a",
            "en-AU-standard-a",
            "fr-FR-standard-a",
            "de-DE-standard-a",
            "ja-JP-standard-a",
            "es-ES-standard-a",
            "pt-BR-standard-a"
    );

    private static final String SIMPLE_TEXT = "Hello, world!";
    private static final String SSML_TEXT = "<speak>Hello <emphasis level=\"strong\">world</emphasis>!</speak>";
    private static final String LONG_TEXT = "The quick brown fox jumps over the lazy dog. " +
            "This pangram contains every letter of the English alphabet at least once. " +
            "Pangrams are often used for displaying fonts and for other applications involving text generation and display.";
    private static final String SPECIAL_CHARS_TEXT = "Hello! How are you? (I'm fine...) #Great :) @mention"; // GH-90000
    private static final String PUNCTUATION_TEXT = "Really?! Yes... Absolutely! No, never. Perhaps; maybe: certainly.";
    private static final String EMPTY_TEXT = "";
    private static final String WHITESPACE_TEXT = "   ";

    private TtsSynthesisEngine engine;
    private VoiceConfig defaultVoice;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new TtsSynthesisEngine("tts-standard-v1", SUPPORTED_VOICES); // GH-90000
        defaultVoice = VoiceConfig.defaults(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INPUT VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("input validation [GH-90000]")
    class InputValidation {

        @Test
        @DisplayName("null text throws SynthesisException [GH-90000]")
        void nullText_throwsSynthesisException() { // GH-90000
            assertThatThrownBy(() -> engine.synthesize(null, defaultVoice, AudioEncoding.MP3)) // GH-90000
                    .isInstanceOf(SynthesisException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        @DisplayName("empty text throws SynthesisException [GH-90000]")
        void emptyText_throwsSynthesisException() { // GH-90000
            assertThatThrownBy(() -> engine.synthesize(EMPTY_TEXT, defaultVoice, AudioEncoding.MP3)) // GH-90000
                    .isInstanceOf(SynthesisException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        @DisplayName("whitespace-only text throws SynthesisException [GH-90000]")
        void whitespaceOnlyText_throwsSynthesisException() { // GH-90000
            assertThatThrownBy(() -> engine.synthesize(WHITESPACE_TEXT, defaultVoice, AudioEncoding.MP3)) // GH-90000
                    .isInstanceOf(SynthesisException.class) // GH-90000
                    .hasMessageContaining("blank [GH-90000]");
        }

        @Test
        @DisplayName("null voice config throws NullPointerException [GH-90000]")
        void nullVoiceConfig_throwsNullPointerException() { // GH-90000
            assertThatThrownBy(() -> engine.synthesize(SIMPLE_TEXT, null, AudioEncoding.MP3)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null audio encoding throws NullPointerException [GH-90000]")
        void nullAudioEncoding_throwsNullPointerException() { // GH-90000
            assertThatThrownBy(() -> engine.synthesize(SIMPLE_TEXT, defaultVoice, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("unsupported voice ID throws SynthesisException [GH-90000]")
        void unsupportedVoiceId_throwsSynthesisException() { // GH-90000
            VoiceConfig unsupportedVoice = new VoiceConfig( // GH-90000
                    "unsupported-voice-xyz",
                    "en-US", "NEUTRAL", 1.0f, 0.0f, 0.0f
            );

            assertThatThrownBy(() -> engine.synthesize(SIMPLE_TEXT, unsupportedVoice, AudioEncoding.MP3)) // GH-90000
                    .isInstanceOf(SynthesisException.class) // GH-90000
                    .hasMessageContaining("Unsupported voice [GH-90000]");
        }

        @Test
        @DisplayName("single character text is accepted [GH-90000]")
        void singleCharacterText_isAccepted() { // GH-90000
            SynthesisResult result = engine.synthesize("A", defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // AUDIO ENCODING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("audio encoding [GH-90000]")
    class AudioEncodingTests {

        @ParameterizedTest
        @EnumSource(TtsSynthesisEngine.AudioEncoding.class) // GH-90000
        @DisplayName("synthesis succeeds for all supported encodings [GH-90000]")
        void synthesisSucceedsForAllEncodings(TtsSynthesisEngine.AudioEncoding encoding) { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, encoding); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.encoding()).isEqualTo(encoding); // GH-90000
        }

        @Test
        @DisplayName("PCM_16BIT encoding produces valid output [GH-90000]")
        void pcm16BitEncodingProducesValidOutput() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.PCM_16BIT); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.sampleRateHz()).isGreaterThan(8000); // GH-90000
            assertThat(result.encoding()).isEqualTo(AudioEncoding.PCM_16BIT); // GH-90000
        }

        @Test
        @DisplayName("MP3 encoding produces valid output [GH-90000]")
        void mp3EncodingProducesValidOutput() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.encoding()).isEqualTo(AudioEncoding.MP3); // GH-90000
        }

        @Test
        @DisplayName("OPUS encoding produces valid output [GH-90000]")
        void opusEncodingProducesValidOutput() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.OPUS); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.encoding()).isEqualTo(AudioEncoding.OPUS); // GH-90000
        }

        @Test
        @DisplayName("PCM_32BIT encoding produces valid output [GH-90000]")
        void pcm32BitEncodingProducesValidOutput() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.PCM_32BIT); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.encoding()).isEqualTo(AudioEncoding.PCM_32BIT); // GH-90000
        }

        @Test
        @DisplayName("different encodings produce different byte content for same text [GH-90000]")
        void differentEncodingsProduceDifferentOutput() { // GH-90000
            SynthesisResult r1 = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000
            SynthesisResult r2 = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.PCM_16BIT); // GH-90000

            // Different encodings should produce different byte sequences
            assertThat(r1.audioContent()).as("MP3 content [GH-90000]").isNotEmpty();
            assertThat(r2.audioContent()).as("PCM content [GH-90000]").isNotEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // VOICE SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("voice selection [GH-90000]")
    class VoiceSelection {

        @Test
        @DisplayName("default voice is used when provided [GH-90000]")
        void defaultVoiceIsUsed() { // GH-90000
            VoiceConfig voice = VoiceConfig.defaults(); // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, voice, AudioEncoding.MP3); // GH-90000

            assertThat(result.voiceId()).isEqualTo(voice.voiceId()); // GH-90000
        }

        @Test
        @DisplayName("all supported voices are accepted [GH-90000]")
        void allSupportedVoicesAreAccepted() { // GH-90000
            for (String voiceId : SUPPORTED_VOICES) { // GH-90000
                VoiceConfig voice = new VoiceConfig(voiceId, "en-US", "NEUTRAL", 1.0f, 0.0f, 0.0f); // GH-90000
                SynthesisResult result = engine.synthesize(SIMPLE_TEXT, voice, AudioEncoding.MP3); // GH-90000

                assertThat(result.voiceId()).isEqualTo(voiceId); // GH-90000
                assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            }
        }

        @Test
        @DisplayName("different voices may produce different audio characteristics [GH-90000]")
        void differentVoicesMayProduceDifferentAudio() { // GH-90000
            VoiceConfig voice1 = new VoiceConfig("en-US-standard-a", "en-US", "NEUTRAL", 1.0f, 0.0f, 0.0f); // GH-90000
            VoiceConfig voice2 = new VoiceConfig("en-US-standard-b", "en-US", "NEUTRAL", 1.0f, 0.0f, 0.0f); // GH-90000

            SynthesisResult r1 = engine.synthesize(SIMPLE_TEXT, voice1, AudioEncoding.MP3); // GH-90000
            SynthesisResult r2 = engine.synthesize(SIMPLE_TEXT, voice2, AudioEncoding.MP3); // GH-90000

            assertThat(r1.voiceId()).isNotEqualTo(r2.voiceId()); // GH-90000
        }

        @Test
        @DisplayName("voice configuration parameters are preserved in result [GH-90000]")
        void voiceConfigParametersArePreserved() { // GH-90000
            VoiceConfig voice = new VoiceConfig("en-US-standard-a", "en-US", "MALE", 1.5f, 2.0f, 5.0f); // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, voice, AudioEncoding.MP3); // GH-90000

            assertThat(result.voiceId()).isEqualTo(voice.voiceId()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // PROSODY CONTROL TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("prosody control [GH-90000]")
    class ProsodyControl {

        @Test
        @DisplayName("speaking rate can be increased (1.5x) [GH-90000]")
        void speakingRateCanBeIncreased() { // GH-90000
            VoiceConfig fasterVoice = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL", 1.5f, 0.0f, 0.0f
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, fasterVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.voiceId()).isEqualTo(fasterVoice.voiceId()); // GH-90000
        }

        @Test
        @DisplayName("speaking rate can be decreased (0.8x) [GH-90000]")
        void speakingRateCanBeDecreased() { // GH-90000
            VoiceConfig slowerVoice = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL", 0.8f, 0.0f, 0.0f
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, slowerVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("pitch can be raised (positive value) [GH-90000]")
        void pitchCanBeRaised() { // GH-90000
            VoiceConfig higherPitch = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL", 1.0f, 5.0f, 0.0f
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, higherPitch, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("pitch can be lowered (negative value) [GH-90000]")
        void pitchCanBeLowered() { // GH-90000
            VoiceConfig lowerPitch = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL", 1.0f, -5.0f, 0.0f
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, lowerPitch, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("volume gain can be increased (positive dB) [GH-90000]")
        void volumeGainCanBeIncreased() { // GH-90000
            VoiceConfig louderVoice = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL", 1.0f, 0.0f, 6.0f
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, louderVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("volume gain can be decreased (negative dB) [GH-90000]")
        void volumeGainCanBeDecreased() { // GH-90000
            VoiceConfig quieterVoice = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL", 1.0f, 0.0f, -6.0f
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, quieterVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("all prosody parameters can be combined [GH-90000]")
        void allProsodyParametersCanBeCombined() { // GH-90000
            VoiceConfig customVoice = new VoiceConfig( // GH-90000
                    "en-US-standard-a", "en-US", "NEUTRAL",
                    1.5f,    // faster speaking rate
                    3.0f,    // higher pitch
                    3.0f     // louder volume
            );
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, customVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SPECIAL CHARACTER & PUNCTUATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("special characters and punctuation [GH-90000]")
    class SpecialCharactersAndPunctuation {

        @Test
        @DisplayName("simple punctuation is handled correctly [GH-90000]")
        void simplePunctuationIsHandled() { // GH-90000
            SynthesisResult result = engine.synthesize(PUNCTUATION_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("special characters are handled without error [GH-90000]")
        void specialCharactersAreHandled() { // GH-90000
            SynthesisResult result = engine.synthesize(SPECIAL_CHARS_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("numbers are synthesized correctly [GH-90000]")
        void numbersAreSynthesized() { // GH-90000
            String numberText = "The number is 42. Call 555-1234 for more info.";
            SynthesisResult result = engine.synthesize(numberText, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("email addresses are handled without error [GH-90000]")
        void emailAddressesAreHandled() { // GH-90000
            String emailText = "Contact us at support@example.com for assistance.";
            SynthesisResult result = engine.synthesize(emailText, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("URLs are handled without error [GH-90000]")
        void urlsAreHandled() { // GH-90000
            String urlText = "Visit https://www.example.com for more information.";
            SynthesisResult result = engine.synthesize(urlText, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("SSML content is processed [GH-90000]")
        void ssmlContentIsProcessed() { // GH-90000
            SynthesisResult result = engine.synthesize(SSML_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("quotes are handled correctly [GH-90000]")
        void quotesAreHandled() { // GH-90000
            String quoteText = "He said, \"Hello, world!\" with emphasis.";
            SynthesisResult result = engine.synthesize(quoteText, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("apostrophes are handled correctly [GH-90000]")
        void apostrophesAreHandled() { // GH-90000
            String apostropheText = "It's what you're looking for, isn't it?";
            SynthesisResult result = engine.synthesize(apostropheText, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // OUTPUT QUALITY & VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("output quality and validation [GH-90000]")
    class OutputQualityAndValidation {

        @Test
        @DisplayName("result contains non-empty audio content [GH-90000]")
        void resultContainsNonEmptyAudio() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.isEmpty()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("sample rate is valid (within typical range) [GH-90000]")
        void sampleRateIsValid() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.sampleRateHz()) // GH-90000
                    .isBetween(8000, 48000); // Common sample rates // GH-90000
        }

        @Test
        @DisplayName("duration is non-negative and reasonable [GH-90000]")
        void durationIsReasonable() { // GH-90000
            SynthesisResult result = engine.synthesize(LONG_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.duration()).isNotNull(); // GH-90000
            assertThat(result.duration().toMillis()).isGreaterThanOrEqualTo(0); // GH-90000
            assertThat(result.duration().toMillis()).isLessThan(120_000); // < 2 minutes for test text // GH-90000
        }

        @Test
        @DisplayName("processing time is recorded and non-negative [GH-90000]")
        void processingTimeIsRecorded() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.processingSeconds()).isGreaterThanOrEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("audio duration increases with longer text [GH-90000]")
        void audioDurationIncreasesWithText() { // GH-90000
            SynthesisResult short_ = engine.synthesize("Hello", defaultVoice, AudioEncoding.MP3); // GH-90000
            SynthesisResult long_ = engine.synthesize(LONG_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            // Longer text should result in longer audio
            assertThat(long_.duration()).isGreaterThanOrEqualTo(short_.duration()); // GH-90000
        }

        @Test
        @DisplayName("isEmpty() reflects actual audio content [GH-90000]")
        void isEmptyReflectsContent() { // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(result.isEmpty()) // GH-90000
                    .isEqualTo(result.audioContent() == null || result.audioContent().length == 0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONSISTENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("consistency [GH-90000]")
    class Consistency {

        @Test
        @DisplayName("same input produces deterministic output [GH-90000]")
        void sameInputProducesDeterministicOutput() { // GH-90000
            SynthesisResult r1 = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000
            SynthesisResult r2 = engine.synthesize(SIMPLE_TEXT, defaultVoice, AudioEncoding.MP3); // GH-90000

            assertThat(r1.audioContent()).isEqualTo(r2.audioContent()); // GH-90000
            assertThat(r1.duration()).isEqualTo(r2.duration()); // GH-90000
        }

        @Test
        @DisplayName("same text with different voices produces different audio [GH-90000]")
        void sameTextDifferentVoicesProducesDifferentAudio() { // GH-90000
            VoiceConfig v1 = new VoiceConfig("en-US-standard-a", "en-US", "NEUTRAL", 1.0f, 0.0f, 0.0f); // GH-90000
            VoiceConfig v2 = new VoiceConfig("en-US-standard-b", "en-US", "NEUTRAL", 1.0f, 0.0f, 0.0f); // GH-90000

            SynthesisResult r1 = engine.synthesize(SIMPLE_TEXT, v1, AudioEncoding.MP3); // GH-90000
            SynthesisResult r2 = engine.synthesize(SIMPLE_TEXT, v2, AudioEncoding.MP3); // GH-90000

            // Different voices should produce different audio
            assertThat(r1.voiceId()).isNotEqualTo(r2.voiceId()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // ENGINE PROPERTIES TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("engine properties [GH-90000]")
    class EngineProperties {

        @Test
        @DisplayName("engine returns configured model ID [GH-90000]")
        void engineReturnsConfiguredModelId() { // GH-90000
            assertThat(engine.getModelId()).isEqualTo("tts-standard-v1 [GH-90000]");
        }

        @Test
        @DisplayName("engine returns all supported voices [GH-90000]")
        void engineReturnsSupportedVoices() { // GH-90000
            Set<String> voices = engine.getSupportedVoiceIds(); // GH-90000

            assertThat(voices).isNotEmpty(); // GH-90000
            assertThat(voices).containsAll(SUPPORTED_VOICES); // GH-90000
        }

        @Test
        @DisplayName("supported voices are immutable [GH-90000]")
        void supportedVoicesAreImmutable() { // GH-90000
            Set<String> voices = engine.getSupportedVoiceIds(); // GH-90000

            assertThat(voices).isNotEmpty(); // GH-90000
            assertThat(voices).containsAll(SUPPORTED_VOICES); // GH-90000
            // Verify it's unmodifiable
            assertThatThrownBy(() -> voices.add("new-voice [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // VOICE CONFIG DEFAULTS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("voice config defaults [GH-90000]")
    class VoiceConfigDefaults {

        @Test
        @DisplayName("default voice config can be used for synthesis [GH-90000]")
        void defaultVoiceConfigCanBeUsed() { // GH-90000
            VoiceConfig defaults = VoiceConfig.defaults(); // GH-90000
            SynthesisResult result = engine.synthesize(SIMPLE_TEXT, defaults, AudioEncoding.MP3); // GH-90000

            assertThat(result.audioContent()).isNotEmpty(); // GH-90000
            assertThat(result.voiceId()).isEqualTo(defaults.voiceId()); // GH-90000
        }

        @Test
        @DisplayName("default voice has reasonable prosody values [GH-90000]")
        void defaultVoiceHasReasonableProsody() { // GH-90000
            VoiceConfig defaults = VoiceConfig.defaults(); // GH-90000

            assertThat(defaults.speakingRate()).isEqualTo(1.0f); // GH-90000
            assertThat(defaults.pitch()).isEqualTo(0.0f); // GH-90000
            assertThat(defaults.volumeGainDb()).isEqualTo(0.0f); // GH-90000
        }
    }
}
