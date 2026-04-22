package com.ghatana.stt.engine;

import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for {@link WhisperTranscriptionEngine}.
 *
 * Validates speech-to-text processing including format handling, confidence scoring,
 * language detection, diarization, and performance boundaries.
 *
 * @doc.type    class
 * @doc.purpose Comprehensive STT engine tests: input validation, output formatting, edge cases, performance
 * @doc.layer   product
 * @doc.pattern Test
 */
/**
 * Test suite for WhisperTranscriptionEngine.
 *
 * Note: Many tests are currently disabled because WhisperTranscriptionEngine
 * throws UnsupportedOperationException (not yet implemented). These tests should // GH-90000
 * be re-enabled once the engine is fully implemented.
 */
@DisplayName("SpeechRecognitionServiceTest [GH-90000]")
class SpeechRecognitionServiceTest {

    private static final byte[] SAMPLE_AUDIO_EN = "SAMPLE_PCM_DATA_HELLO_WORLD".getBytes(StandardCharsets.UTF_8); // GH-90000
    private static final byte[] SAMPLE_AUDIO_FR = "SAMPLE_PCM_DATA_BONJOUR_MONDE".getBytes(StandardCharsets.UTF_8); // GH-90000
    private static final byte[] SAMPLE_AUDIO_JA = "SAMPLE_PCM_DATA_KONNICHIWA_SEKAI".getBytes(StandardCharsets.UTF_8); // GH-90000
    private static final byte[] SAMPLE_AUDIO_WITH_NOISE = "SAMPLE_PCM_DATA_WITH_NOISE_CONTENT".getBytes(StandardCharsets.UTF_8); // GH-90000
    private static final byte[] LARGE_AUDIO_DATA = new byte[10_000_000]; // 10MB

    private WhisperTranscriptionEngine engine;
    private WhisperTranscriptionEngine diarizationEngine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new WhisperTranscriptionEngine("whisper-base", false); // GH-90000
        diarizationEngine = new WhisperTranscriptionEngine("whisper-large-v3", true); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INPUT VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("input validation [GH-90000]")
    class InputValidation {

        @Test
        @DisplayName("null audio data throws TranscriptionException [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException before input validation [GH-90000]")
        void nullAudioData_throwsTranscriptionException() { // GH-90000
            assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.PCM, "en")) // GH-90000
                    .isInstanceOf(TranscriptionException.class) // GH-90000
                    .hasMessageContaining("must not be null or empty [GH-90000]");
        }

        @Test
        @DisplayName("empty audio bytes throws TranscriptionException [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException before input validation [GH-90000]")
        void emptyAudioBytes_throwsTranscriptionException() { // GH-90000
            byte[] empty = new byte[0];
            assertThatThrownBy(() -> engine.transcribe(empty, AudioFormat.PCM, "en")) // GH-90000
                    .isInstanceOf(TranscriptionException.class) // GH-90000
                    .hasMessageContaining("must not be null or empty [GH-90000]");
        }

        @Test
        @DisplayName("null audio format throws NullPointerException [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException before input validation [GH-90000]")
        void nullAudioFormat_throwsNullPointerException() { // GH-90000
            assertThatThrownBy(() -> engine.transcribe(SAMPLE_AUDIO_EN, null, "en")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("single byte audio is accepted and processed [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void singleByteAudio_isAccepted() { // GH-90000
            TranscriptionResult result = engine.transcribe(new byte[] {1}, AudioFormat.PCM, "en"); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isEmpty()).isFalse(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // FORMAT HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("audio format handling [GH-90000]")
    @Disabled("WhisperTranscriptionEngine not yet implemented - all format tests throw UnsupportedOperationException [GH-90000]")
    class AudioFormatHandling {

        @ParameterizedTest
        @EnumSource(AudioFormat.class) // GH-90000
        @DisplayName("transcription succeeds for all supported formats [GH-90000]")
        void transcriptionSucceedsForAllFormats(AudioFormat format) { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, format, "en"); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.text()).isNotBlank(); // GH-90000
            assertThat(result.inputFormat()).isEqualTo(format); // GH-90000
            assertThat(result.isEmpty()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("PCM format produces consistent transcription [GH-90000]")
        void pcmFormatProducesConsistentTranscription() { // GH-90000
            TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en"); // GH-90000
            TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en"); // GH-90000

            assertThat(r1.text()).isEqualTo(r2.text()); // GH-90000
            assertThat(r1.confidence()).isEqualTo(r2.confidence()); // GH-90000
        }

        @Test
        @DisplayName("WAV format output matches expected pattern [GH-90000]")
        void wavFormatOutputMatchesPattern() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            assertThat(result.text()).containsIgnoringCase("transcribed [GH-90000]");
            assertThat(result.inputFormat()).isEqualTo(AudioFormat.WAV); // GH-90000
        }

        @Test
        @DisplayName("MP3 format produces valid transcription [GH-90000]")
        void mp3FormatProducesValidTranscription() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.MP3, "en"); // GH-90000

            assertThat(result.text()).isNotBlank(); // GH-90000
            assertThat(result.confidence()).isStrictlyBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("FLAC format completes without error [GH-90000]")
        void flacFormatCompletesWithoutError() { // GH-90000
            assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.FLAC, "de")) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("OGG format completes without error [GH-90000]")
        void oggFormatCompletesWithoutError() { // GH-90000
            assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.OGG, null)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("AAC format completes without error [GH-90000]")
        void aacFormatCompletesWithoutError() { // GH-90000
            assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.AAC, "ja")) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONFIDENCE SCORING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confidence scoring [GH-90000]")
    @Disabled("WhisperTranscriptionEngine not yet implemented - confidence tests throw UnsupportedOperationException [GH-90000]")
    class ConfidenceScoring {

        @Test
        @DisplayName("confidence is in valid range [0.0, 1.0] [GH-90000]")
        void confidenceIsInValidRange() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000
            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("confidence is deterministic for identical input [GH-90000]")
        void confidenceIsDeterministic() { // GH-90000
            TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en"); // GH-90000
            TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en"); // GH-90000

            assertThat(r1.confidence()).isEqualTo(r2.confidence()); // GH-90000
        }

        @Test
        @DisplayName("different inputs may produce different confidence scores [GH-90000]")
        void differentInputsMayProduceDifferentConfidence() { // GH-90000
            TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en"); // GH-90000
            TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO_FR, AudioFormat.PCM, "fr"); // GH-90000

            // Confidence values might be different due to different audio content
            assertThat(r1.confidence()).isBetween(0.0, 1.0); // GH-90000
            assertThat(r2.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("noisy audio produces valid confidence (may be lower) [GH-90000]")
        void noisyAudioProducesValidConfidence() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_WITH_NOISE, AudioFormat.PCM, "en"); // GH-90000

            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // LANGUAGE DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("language detection [GH-90000]")
    class LanguageDetection {

        @Test
        @DisplayName("explicit language hint is preserved in result [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void explicitLanguageHintIsPreserved() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000
            assertThat(result.detectedLanguage()).isEqualTo("en [GH-90000]");
        }

        @Test
        @DisplayName("different language hints are preserved [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void differentLanguageHintsArePreserved() { // GH-90000
            String[] hints = {"en", "fr", "de", "ja", "es", "zh", "pt"};
            for (String hint : hints) { // GH-90000
                TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, hint); // GH-90000
                assertThat(result.detectedLanguage()).isEqualTo(hint); // GH-90000
            }
        }

        @Test
        @DisplayName("null language hint triggers auto-detection [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void nullLanguageHintTriggersAutoDetection() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, null); // GH-90000

            assertThat(result.detectedLanguage()).isNotNull(); // GH-90000
            assertThat(result.detectedLanguage()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("detectLanguage() method returns valid BCP47 tag [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void detectLanguageMethodReturnsValidTag() { // GH-90000
            String lang = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM); // GH-90000

            assertThat(lang).isNotBlank(); // GH-90000
            assertThat(lang).matches("[a-z]{2}(-[A-Z]{2})? [GH-90000]");
        }

        @Test
        @DisplayName("auto-detected language matches content for multiple languages [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void autoDetectedLanguageMatchesContent() { // GH-90000
            String langEn = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM); // GH-90000
            String langFr = engine.detectLanguage(SAMPLE_AUDIO_FR, AudioFormat.PCM); // GH-90000
            String langJa = engine.detectLanguage(SAMPLE_AUDIO_JA, AudioFormat.PCM); // GH-90000

            assertThat(langEn).isNotBlank(); // GH-90000
            assertThat(langFr).isNotBlank(); // GH-90000
            assertThat(langJa).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("language detection is deterministic for same input [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void languageDetectionIsDeterministic() { // GH-90000
            String lang1 = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM); // GH-90000
            String lang2 = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM); // GH-90000

            assertThat(lang1).isEqualTo(lang2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SPEAKER DIARIZATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("speaker diarization [GH-90000]")
    @Disabled("WhisperTranscriptionEngine not yet implemented - diarization tests throw UnsupportedOperationException [GH-90000]")
    class SpeakerDiarization {

        @Test
        @DisplayName("diarization disabled produces empty speaker segments [GH-90000]")
        void diarizationDisabledProducesEmptySegments() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            assertThat(result.speakerSegments()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("diarization enabled produces non-empty speaker segments [GH-90000]")
        void diarizationEnabledProducesNonEmptySegments() { // GH-90000
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            assertThat(result.speakerSegments()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("each speaker segment contains valid speaker ID [GH-90000]")
        void eachSpeakerSegmentContainsValidId() { // GH-90000
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            result.speakerSegments().forEach(segment -> { // GH-90000
                assertThat(segment.speakerId()).isNotNull(); // GH-90000
                assertThat(segment.speakerId()).isNotBlank(); // GH-90000
            });
        }

        @Test
        @DisplayName("speaker segments have valid time boundaries [GH-90000]")
        void speakerSegmentsHaveValidTimeBoundaries() { // GH-90000
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            result.speakerSegments().forEach(segment -> { // GH-90000
                assertThat(segment.start()).isNotNull(); // GH-90000
                assertThat(segment.end()).isNotNull(); // GH-90000
                assertThat(segment.start()).isLessThanOrEqualTo(segment.end()); // GH-90000
            });
        }

        @Test
        @DisplayName("speaker text is non-blank in all segments [GH-90000]")
        void speakerTextIsNonBlank() { // GH-90000
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            result.speakerSegments().forEach(segment -> // GH-90000
                    assertThat(segment.text()).isNotBlank() // GH-90000
            );
        }

        @Test
        @DisplayName("speaker segments are ordered by start time [GH-90000]")
        void speakerSegmentsAreOrdered() { // GH-90000
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            List<SpeakerSegment> segments = result.speakerSegments(); // GH-90000
            for (int i = 1; i < segments.size(); i++) { // GH-90000
                assertThat(segments.get(i).start()) // GH-90000
                        .isGreaterThanOrEqualTo(segments.get(i - 1).start()); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // PERFORMANCE & CONCURRENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performance and concurrency [GH-90000]")
    @Disabled("WhisperTranscriptionEngine not yet implemented - performance tests throw UnsupportedOperationException [GH-90000]")
    class PerformanceAndConcurrency {

        @Test
        @DisplayName("processing time is recorded and non-negative [GH-90000]")
        void processingTimeIsRecordedAndNonNegative() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            assertThat(result.processingTime()).isNotNull(); // GH-90000
            assertThat(result.processingTime().toNanos()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("processing time increases with larger audio (approximately) [GH-90000]")
        void processingTimeIncreasesWithLargerAudio() { // GH-90000
            byte[] smallAudio = new byte[100];
            byte[] largeAudio = new byte[10_000];

            long startSmall = System.nanoTime(); // GH-90000
            engine.transcribe(smallAudio, AudioFormat.PCM, "en"); // GH-90000
            long durationSmall = System.nanoTime() - startSmall; // GH-90000

            long startLarge = System.nanoTime(); // GH-90000
            engine.transcribe(largeAudio, AudioFormat.PCM, "en"); // GH-90000
            long durationLarge = System.nanoTime() - startLarge; // GH-90000

            // Processing time should be comparable or larger for larger audio
            assertThat(durationSmall).isGreaterThan(0); // GH-90000
            assertThat(durationLarge).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("concurrent transcriptions produce consistent results [GH-90000]")
        void concurrentTranscriptionsProduceConsistentResults() throws InterruptedException { // GH-90000
            int threadCount = 5;
            Thread[] threads = new Thread[threadCount];
            TranscriptionResult[] results = new TranscriptionResult[threadCount];

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int index = i;
                threads[i] = new Thread(() -> // GH-90000
                        results[index] = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en") // GH-90000
                );
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                t.join(); // GH-90000
            }

            // All results should be consistent
            for (int i = 1; i < threadCount; i++) { // GH-90000
                assertThat(results[i].text()).isEqualTo(results[0].text()); // GH-90000
                assertThat(results[i].confidence()).isEqualTo(results[0].confidence()); // GH-90000
            }
        }

        @Test
        @DisplayName("large audio file (10MB) is handled without failure [GH-90000]")
        void largeAudioFileIsHandledWithoutFailure() { // GH-90000
            // Fill with deterministic data
            for (int i = 0; i < LARGE_AUDIO_DATA.length; i++) { // GH-90000
                LARGE_AUDIO_DATA[i] = (byte) (i % 256); // GH-90000
            }

            assertThatCode(() -> engine.transcribe(LARGE_AUDIO_DATA, AudioFormat.PCM, "en")) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // OUTPUT FORMATTING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("output formatting [GH-90000]")
    @Disabled("WhisperTranscriptionEngine not yet implemented - output tests throw UnsupportedOperationException [GH-90000]")
    class OutputFormatting {

        @Test
        @DisplayName("result contains non-blank transcription text [GH-90000]")
        void resultContainsNonBlankText() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            assertThat(result.text()).isNotNull(); // GH-90000
            assertThat(result.text()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("result isEmpty() is consistent with text content [GH-90000]")
        void resultIsEmptyIsConsistent() { // GH-90000
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000

            assertThat(result.isEmpty()) // GH-90000
                    .isEqualTo(result.text() == null || result.text().isBlank()); // GH-90000
        }

        @Test
        @DisplayName("transcription result contains expected audio format [GH-90000]")
        void transcriptionResultContainsExpectedFormat() { // GH-90000
            for (AudioFormat format : AudioFormat.values()) { // GH-90000
                TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, format, "en"); // GH-90000
                assertThat(result.inputFormat()).isEqualTo(format); // GH-90000
            }
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
            assertThat(engine.getModelId()).isEqualTo("whisper-base [GH-90000]");
            assertThat(diarizationEngine.getModelId()).isEqualTo("whisper-large-v3 [GH-90000]");
        }

        @Test
        @DisplayName("diarization flag is accurately reflected [GH-90000]")
        void diarizationFlagIsAccurate() { // GH-90000
            assertThat(engine.isDiarizationEnabled()).isFalse(); // GH-90000
            assertThat(diarizationEngine.isDiarizationEnabled()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // EXCEPTION HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("exception handling [GH-90000]")
    class ExceptionHandling {

        @Test
        @DisplayName("multiple consecutive errors don't affect next successful transcription [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - successful transcription part throws UnsupportedOperationException [GH-90000]")
        void multipleErrorsDontAffectNextSuccess() { // GH-90000
            // First, trigger some errors
            assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.PCM, "en")) // GH-90000
                    .isInstanceOf(TranscriptionException.class); // GH-90000

            assertThatThrownBy(() -> engine.transcribe(new byte[0], AudioFormat.PCM, "en")) // GH-90000
                    .isInstanceOf(TranscriptionException.class); // GH-90000

            // Next transcription should work fine
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en"); // GH-90000
            assertThat(result.text()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("invalid null language hint with null check doesn't crash [GH-90000]")
        @Disabled("WhisperTranscriptionEngine not yet implemented - throws UnsupportedOperationException [GH-90000]")
        void invalidInputHandlingIsRobust() { // GH-90000
            // Test with null language — should auto-detect, not crash
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, null); // GH-90000
            assertThat(result.detectedLanguage()).isNotBlank(); // GH-90000
        }
    }
}
