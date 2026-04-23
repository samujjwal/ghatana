package com.ghatana.stt.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LanguageDetectionService} — AV-007.2.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the automatic language detection service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LanguageDetectionService")
class LanguageDetectionServiceTest {

    /** Stub model: returns "en" at 0.97 confidence for any text containing "hello",
     *  "fr" at 0.95 for "bonjour", else "de" at 0.60. */
    private static final LanguageDetectionService.LanguageDetectionModel STUB_MODEL = textSample -> {
        if (textSample.toLowerCase().contains("hello")) {
            return List.of( // GH-90000
                    LanguageDetectionService.LanguageCandidate.of("en", 0.97), // GH-90000
                    LanguageDetectionService.LanguageCandidate.of("en-US", 0.90)); // GH-90000
        }
        if (textSample.toLowerCase().contains("bonjour")) {
            return List.of( // GH-90000
                    LanguageDetectionService.LanguageCandidate.of("fr", 0.95), // GH-90000
                    LanguageDetectionService.LanguageCandidate.of("fr-FR", 0.85)); // GH-90000
        }
        return List.of(LanguageDetectionService.LanguageCandidate.of("de", 0.60)); // GH-90000
    };

    // ─── of() factories ─────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("of(model): null model throws NullPointerException")
    void of_nullModel_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> LanguageDetectionService.of(null)); // GH-90000
    }

    @Test
    @DisplayName("of(model, threshold): threshold > 1 throws IllegalArgumentException")
    void of_invalidThreshold_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> LanguageDetectionService.of(STUB_MODEL, 1.5)); // GH-90000
    }

    // ─── detect() ───────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("detect(text)")
    class Detect {

        @Test
        @DisplayName("detects English for 'hello world' with high confidence")
        void detect_english_highConfidence() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            LanguageDetectionService.DetectionResult result = service.detect("hello world");

            assertThat(result.topLanguageTag()).contains("en");
            assertThat(result.isReliable()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("detects French for 'bonjour monde'")
        void detect_french_detected() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            LanguageDetectionService.DetectionResult result = service.detect("bonjour monde");

            assertThat(result.topLanguageTag()).contains("fr");
        }

        @Test
        @DisplayName("below-threshold result returns empty topLanguageTag")
        void detect_belowThreshold_emptyTop() { // GH-90000
            // 0.60 < 0.80 threshold
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL, 0.80); // GH-90000
            LanguageDetectionService.DetectionResult result = service.detect("xyz123");

            assertThat(result.topLanguageTag()).isEmpty(); // GH-90000
            assertThat(result.isReliable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("null textSample throws NullPointerException")
        void detect_null_throwsNPE() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.detect(null)); // GH-90000
        }

        @Test
        @DisplayName("blank textSample throws IllegalArgumentException")
        void detect_blank_throwsIAE() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.detect("   "));
        }

        @Test
        @DisplayName("result.candidates() is unmodifiable")
        void detect_candidatesUnmodifiable() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            LanguageDetectionService.DetectionResult result = service.detect("hello world");
            assertThatThrownBy(() -> result.candidates().add( // GH-90000
                    LanguageDetectionService.LanguageCandidate.of("de", 0.1))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─── detectFromAudio() ──────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("detectFromAudio()")
    class DetectFromAudio {

        @Test
        @DisplayName("non-empty audio returns at least one candidate")
        void detectFromAudio_returnsCandidate() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            LanguageDetectionService.DetectionResult result =
                    service.detectFromAudio(new byte[]{1, 2, 3, 4}); // GH-90000

            assertThat(result.candidates()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null audioBytes throws NullPointerException")
        void detectFromAudio_null_throwsNPE() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.detectFromAudio(null)); // GH-90000
        }

        @Test
        @DisplayName("empty audioBytes throws IllegalArgumentException")
        void detectFromAudio_empty_throwsIAE() { // GH-90000
            LanguageDetectionService service = LanguageDetectionService.of(STUB_MODEL); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.detectFromAudio(new byte[0])); // GH-90000
        }
    }

    // ─── LanguageCandidate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("LanguageCandidate")
    class LanguageCandidateTests {

        @Test
        @DisplayName("of() resolves locale from language tag")
        void candidate_localeResolved() { // GH-90000
            var candidate = LanguageDetectionService.LanguageCandidate.of("fr-CA", 0.90); // GH-90000
            assertThat(candidate.locale().getLanguage()).isEqualTo("fr");
            assertThat(candidate.locale().getCountry()).isEqualTo("CA");
        }

        @Test
        @DisplayName("confidence > 1 throws IllegalArgumentException")
        void candidate_invalidConfidence_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> LanguageDetectionService.LanguageCandidate.of("en", 1.1)); // GH-90000
        }

        @Test
        @DisplayName("null languageTag throws NullPointerException")
        void candidate_nullTag_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> LanguageDetectionService.LanguageCandidate.of(null, 0.9)); // GH-90000
        }
    }
}
