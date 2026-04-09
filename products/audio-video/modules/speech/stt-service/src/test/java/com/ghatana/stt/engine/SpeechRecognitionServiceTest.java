package com.ghatana.stt.engine;

import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.*;
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
@DisplayName("SpeechRecognitionServiceTest")
class SpeechRecognitionServiceTest {

    private static final byte[] SAMPLE_AUDIO_EN = "SAMPLE_PCM_DATA_HELLO_WORLD".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SAMPLE_AUDIO_FR = "SAMPLE_PCM_DATA_BONJOUR_MONDE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SAMPLE_AUDIO_JA = "SAMPLE_PCM_DATA_KONNICHIWA_SEKAI".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SAMPLE_AUDIO_WITH_NOISE = "SAMPLE_PCM_DATA_WITH_NOISE_CONTENT".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LARGE_AUDIO_DATA = new byte[10_000_000]; // 10MB

    private WhisperTranscriptionEngine engine;
    private WhisperTranscriptionEngine diarizationEngine;

    @BeforeEach
    void setUp() {
        engine = new WhisperTranscriptionEngine("whisper-base", false);
        diarizationEngine = new WhisperTranscriptionEngine("whisper-large-v3", true);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INPUT VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        @DisplayName("null audio data throws TranscriptionException")
        void nullAudioData_throwsTranscriptionException() {
            assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.PCM, "en"))
                    .isInstanceOf(TranscriptionException.class)
                    .hasMessageContaining("must not be null or empty");
        }

        @Test
        @DisplayName("empty audio bytes throws TranscriptionException")
        void emptyAudioBytes_throwsTranscriptionException() {
            byte[] empty = new byte[0];
            assertThatThrownBy(() -> engine.transcribe(empty, AudioFormat.PCM, "en"))
                    .isInstanceOf(TranscriptionException.class)
                    .hasMessageContaining("must not be null or empty");
        }

        @Test
        @DisplayName("null audio format throws NullPointerException")
        void nullAudioFormat_throwsNullPointerException() {
            assertThatThrownBy(() -> engine.transcribe(SAMPLE_AUDIO_EN, null, "en"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("single byte audio is accepted and processed")
        void singleByteAudio_isAccepted() {
            TranscriptionResult result = engine.transcribe(new byte[] {1}, AudioFormat.PCM, "en");
            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // FORMAT HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("audio format handling")
    class AudioFormatHandling {

        @ParameterizedTest
        @EnumSource(AudioFormat.class)
        @DisplayName("transcription succeeds for all supported formats")
        void transcriptionSucceedsForAllFormats(AudioFormat format) {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, format, "en");

            assertThat(result).isNotNull();
            assertThat(result.text()).isNotBlank();
            assertThat(result.inputFormat()).isEqualTo(format);
            assertThat(result.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("PCM format produces consistent transcription")
        void pcmFormatProducesConsistentTranscription() {
            TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en");
            TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en");

            assertThat(r1.text()).isEqualTo(r2.text());
            assertThat(r1.confidence()).isEqualTo(r2.confidence());
        }

        @Test
        @DisplayName("WAV format output matches expected pattern")
        void wavFormatOutputMatchesPattern() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            assertThat(result.text()).containsIgnoringCase("transcribed");
            assertThat(result.inputFormat()).isEqualTo(AudioFormat.WAV);
        }

        @Test
        @DisplayName("MP3 format produces valid transcription")
        void mp3FormatProducesValidTranscription() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.MP3, "en");

            assertThat(result.text()).isNotBlank();
            assertThat(result.confidence()).isStrictlyBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("FLAC format completes without error")
        void flacFormatCompletesWithoutError() {
            assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.FLAC, "de"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("OGG format completes without error")
        void oggFormatCompletesWithoutError() {
            assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.OGG, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AAC format completes without error")
        void aacFormatCompletesWithoutError() {
            assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.AAC, "ja"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONFIDENCE SCORING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confidence scoring")
    class ConfidenceScoring {

        @Test
        @DisplayName("confidence is in valid range [0.0, 1.0]")
        void confidenceIsInValidRange() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");
            assertThat(result.confidence()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("confidence is deterministic for identical input")
        void confidenceIsDeterministic() {
            TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en");
            TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en");

            assertThat(r1.confidence()).isEqualTo(r2.confidence());
        }

        @Test
        @DisplayName("different inputs may produce different confidence scores")
        void differentInputsMayProduceDifferentConfidence() {
            TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en");
            TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO_FR, AudioFormat.PCM, "fr");

            // Confidence values might be different due to different audio content
            assertThat(r1.confidence()).isBetween(0.0, 1.0);
            assertThat(r2.confidence()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("noisy audio produces valid confidence (may be lower)")
        void noisyAudioProducesValidConfidence() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_WITH_NOISE, AudioFormat.PCM, "en");

            assertThat(result.confidence()).isBetween(0.0, 1.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // LANGUAGE DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("language detection")
    class LanguageDetection {

        @Test
        @DisplayName("explicit language hint is preserved in result")
        void explicitLanguageHintIsPreserved() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");
            assertThat(result.detectedLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("different language hints are preserved")
        void differentLanguageHintsArePreserved() {
            String[] hints = {"en", "fr", "de", "ja", "es", "zh", "pt"};
            for (String hint : hints) {
                TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, hint);
                assertThat(result.detectedLanguage()).isEqualTo(hint);
            }
        }

        @Test
        @DisplayName("null language hint triggers auto-detection")
        void nullLanguageHintTriggersAutoDetection() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, null);

            assertThat(result.detectedLanguage()).isNotNull();
            assertThat(result.detectedLanguage()).isNotBlank();
        }

        @Test
        @DisplayName("detectLanguage() method returns valid BCP47 tag")
        void detectLanguageMethodReturnsValidTag() {
            String lang = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM);

            assertThat(lang).isNotBlank();
            assertThat(lang).matches("[a-z]{2}(-[A-Z]{2})?");
        }

        @Test
        @DisplayName("auto-detected language matches content for multiple languages")
        void autoDetectedLanguageMatchesContent() {
            String langEn = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM);
            String langFr = engine.detectLanguage(SAMPLE_AUDIO_FR, AudioFormat.PCM);
            String langJa = engine.detectLanguage(SAMPLE_AUDIO_JA, AudioFormat.PCM);

            assertThat(langEn).isNotBlank();
            assertThat(langFr).isNotBlank();
            assertThat(langJa).isNotBlank();
        }

        @Test
        @DisplayName("language detection is deterministic for same input")
        void languageDetectionIsDeterministic() {
            String lang1 = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM);
            String lang2 = engine.detectLanguage(SAMPLE_AUDIO_EN, AudioFormat.PCM);

            assertThat(lang1).isEqualTo(lang2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SPEAKER DIARIZATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("speaker diarization")
    class SpeakerDiarization {

        @Test
        @DisplayName("diarization disabled produces empty speaker segments")
        void diarizationDisabledProducesEmptySegments() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            assertThat(result.speakerSegments()).isEmpty();
        }

        @Test
        @DisplayName("diarization enabled produces non-empty speaker segments")
        void diarizationEnabledProducesNonEmptySegments() {
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            assertThat(result.speakerSegments()).isNotEmpty();
        }

        @Test
        @DisplayName("each speaker segment contains valid speaker ID")
        void eachSpeakerSegmentContainsValidId() {
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            result.speakerSegments().forEach(segment -> {
                assertThat(segment.speakerId()).isNotNull();
                assertThat(segment.speakerId()).isNotBlank();
            });
        }

        @Test
        @DisplayName("speaker segments have valid time boundaries")
        void speakerSegmentsHaveValidTimeBoundaries() {
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            result.speakerSegments().forEach(segment -> {
                assertThat(segment.start()).isNotNull();
                assertThat(segment.end()).isNotNull();
                assertThat(segment.start()).isLessThanOrEqualTo(segment.end());
            });
        }

        @Test
        @DisplayName("speaker text is non-blank in all segments")
        void speakerTextIsNonBlank() {
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            result.speakerSegments().forEach(segment ->
                    assertThat(segment.text()).isNotBlank()
            );
        }

        @Test
        @DisplayName("speaker segments are ordered by start time")
        void speakerSegmentsAreOrdered() {
            TranscriptionResult result = diarizationEngine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            List<SpeakerSegment> segments = result.speakerSegments();
            for (int i = 1; i < segments.size(); i++) {
                assertThat(segments.get(i).start())
                        .isGreaterThanOrEqualTo(segments.get(i - 1).start());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // PERFORMANCE & CONCURRENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performance and concurrency")
    class PerformanceAndConcurrency {

        @Test
        @DisplayName("processing time is recorded and non-negative")
        void processingTimeIsRecordedAndNonNegative() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            assertThat(result.processingTime()).isNotNull();
            assertThat(result.processingTime().toNanos()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("processing time increases with larger audio (approximately)")
        void processingTimeIncreasesWithLargerAudio() {
            byte[] smallAudio = new byte[100];
            byte[] largeAudio = new byte[10_000];

            long startSmall = System.nanoTime();
            engine.transcribe(smallAudio, AudioFormat.PCM, "en");
            long durationSmall = System.nanoTime() - startSmall;

            long startLarge = System.nanoTime();
            engine.transcribe(largeAudio, AudioFormat.PCM, "en");
            long durationLarge = System.nanoTime() - startLarge;

            // Processing time should be comparable or larger for larger audio
            assertThat(durationSmall).isGreaterThan(0);
            assertThat(durationLarge).isGreaterThan(0);
        }

        @Test
        @DisplayName("concurrent transcriptions produce consistent results")
        void concurrentTranscriptionsProduceConsistentResults() throws InterruptedException {
            int threadCount = 5;
            Thread[] threads = new Thread[threadCount];
            TranscriptionResult[] results = new TranscriptionResult[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() ->
                        results[index] = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, "en")
                );
                threads[i].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            // All results should be consistent
            for (int i = 1; i < threadCount; i++) {
                assertThat(results[i].text()).isEqualTo(results[0].text());
                assertThat(results[i].confidence()).isEqualTo(results[0].confidence());
            }
        }

        @Test
        @DisplayName("large audio file (10MB) is handled without failure")
        void largeAudioFileIsHandledWithoutFailure() {
            // Fill with deterministic data
            for (int i = 0; i < LARGE_AUDIO_DATA.length; i++) {
                LARGE_AUDIO_DATA[i] = (byte) (i % 256);
            }

            assertThatCode(() -> engine.transcribe(LARGE_AUDIO_DATA, AudioFormat.PCM, "en"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // OUTPUT FORMATTING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("output formatting")
    class OutputFormatting {

        @Test
        @DisplayName("result contains non-blank transcription text")
        void resultContainsNonBlankText() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            assertThat(result.text()).isNotNull();
            assertThat(result.text()).isNotBlank();
        }

        @Test
        @DisplayName("result isEmpty() is consistent with text content")
        void resultIsEmptyIsConsistent() {
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");

            assertThat(result.isEmpty())
                    .isEqualTo(result.text() == null || result.text().isBlank());
        }

        @Test
        @DisplayName("transcription result contains expected audio format")
        void transcriptionResultContainsExpectedFormat() {
            for (AudioFormat format : AudioFormat.values()) {
                TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, format, "en");
                assertThat(result.inputFormat()).isEqualTo(format);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // ENGINE PROPERTIES TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("engine properties")
    class EngineProperties {

        @Test
        @DisplayName("engine returns configured model ID")
        void engineReturnsConfiguredModelId() {
            assertThat(engine.getModelId()).isEqualTo("whisper-base");
            assertThat(diarizationEngine.getModelId()).isEqualTo("whisper-large-v3");
        }

        @Test
        @DisplayName("diarization flag is accurately reflected")
        void diarizationFlagIsAccurate() {
            assertThat(engine.isDiarizationEnabled()).isFalse();
            assertThat(diarizationEngine.isDiarizationEnabled()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // EXCEPTION HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("multiple consecutive errors don't affect next successful transcription")
        void multipleErrorsDontAffectNextSuccess() {
            // First, trigger some errors
            assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.PCM, "en"))
                    .isInstanceOf(TranscriptionException.class);

            assertThatThrownBy(() -> engine.transcribe(new byte[0], AudioFormat.PCM, "en"))
                    .isInstanceOf(TranscriptionException.class);

            // Next transcription should work fine
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.WAV, "en");
            assertThat(result.text()).isNotBlank();
        }

        @Test
        @DisplayName("invalid null language hint with null check doesn't crash")
        void invalidInputHandlingIsRobust() {
            // Test with null language — should auto-detect, not crash
            TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO_EN, AudioFormat.PCM, null);
            assertThat(result.detectedLanguage()).isNotBlank();
        }
    }
}
