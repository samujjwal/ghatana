package com.ghatana.stt.diarization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SpeakerDiarizationService} — AV-007.1.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the speaker diarization service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SpeakerDiarizationService")
class SpeakerDiarizationServiceTest {

    /**
     * Embedding extractor stub: returns a distinct embedding per byte[] content length bucket.
     * Different lengths → different speakers; same length → same speaker.
     */
    private static final SpeakerDiarizationService.SpeakerEmbeddingExtractor STUB_EXTRACTOR =
            audioBytes -> {
                float[] emb = new float[16];
                int bucket = audioBytes.length % 3; // 3 distinct speaker buckets
                emb[bucket] = 1.0f;
                return emb;
            };

    private SpeakerDiarizationService buildService() { // GH-90000
        return SpeakerDiarizationService.builder() // GH-90000
                .embeddingExtractor(STUB_EXTRACTOR) // GH-90000
                .maxSpeakers(5) // GH-90000
                .similarityThreshold(0.80) // GH-90000
                .build(); // GH-90000
    }

    // ─── diarize ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("diarize()")
    class Diarize {

        @Test
        @DisplayName("empty segment list returns empty list")
        void diarize_empty_returnsEmpty() { // GH-90000
            SpeakerDiarizationService service = buildService(); // GH-90000
            assertThat(service.diarize(Collections.emptyList())).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("single segment returns one turn labeled SPEAKER_0")
        void diarize_singleSegment_labeledSpeaker0() { // GH-90000
            SpeakerDiarizationService service = buildService(); // GH-90000
            var segment = new SpeakerDiarizationService.AudioSegment( // GH-90000
                    new byte[]{1, 2, 3}, 0, 1000, "Hello");
            List<SpeakerDiarizationService.SpeakerTurn> turns =
                    service.diarize(List.of(segment)); // GH-90000

            assertThat(turns).hasSize(1); // GH-90000
            assertThat(turns.get(0).speakerId()).isEqualTo("SPEAKER_0");
            assertThat(turns.get(0).text()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("two acoustically distinct segments → two different speaker labels")
        void diarize_twoSpeakers_distinctLabels() { // GH-90000
            SpeakerDiarizationService service = buildService(); // GH-90000

            // bucket 0: length 3 → speaker A
            var seg1 = new SpeakerDiarizationService.AudioSegment(new byte[3], 0, 1000, "Hi"); // GH-90000
            // bucket 1: length 4 → speaker B
            var seg2 = new SpeakerDiarizationService.AudioSegment(new byte[4], 1000, 2000, "Hello"); // GH-90000

            List<SpeakerDiarizationService.SpeakerTurn> turns = service.diarize(List.of(seg1, seg2)); // GH-90000

            assertThat(turns).hasSize(2); // GH-90000
            assertThat(turns.get(0).speakerId()) // GH-90000
                    .isNotEqualTo(turns.get(1).speakerId()); // GH-90000
        }

        @Test
        @DisplayName("acoustically similar segments → same speaker label")
        void diarize_sameSpeaker_sameLabel() { // GH-90000
            SpeakerDiarizationService service = buildService(); // GH-90000

            // Same bucket (length 3 → bucket 0): identical embeddings // GH-90000
            var seg1 = new SpeakerDiarizationService.AudioSegment(new byte[3], 0, 500, "Hey"); // GH-90000
            var seg2 = new SpeakerDiarizationService.AudioSegment(new byte[3], 500, 1000, "there"); // GH-90000

            List<SpeakerDiarizationService.SpeakerTurn> turns = service.diarize(List.of(seg1, seg2)); // GH-90000

            assertThat(turns.get(0).speakerId()).isEqualTo(turns.get(1).speakerId()); // GH-90000
        }

        @Test
        @DisplayName("turns preserve start/end times from input segments")
        void diarize_timesPreserved() { // GH-90000
            SpeakerDiarizationService service = buildService(); // GH-90000
            var segment = new SpeakerDiarizationService.AudioSegment( // GH-90000
                    new byte[]{0x01}, 500, 1500, "test");
            var turns = service.diarize(List.of(segment)); // GH-90000

            assertThat(turns.get(0).startMs()).isEqualTo(500); // GH-90000
            assertThat(turns.get(0).endMs()).isEqualTo(1500); // GH-90000
        }

        @Test
        @DisplayName("null segments list throws NullPointerException")
        void diarize_null_throwsNPE() { // GH-90000
            SpeakerDiarizationService service = buildService(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.diarize(null)); // GH-90000
        }

        @Test
        @DisplayName("max speakers cap is respected")
        void diarize_maxSpeakersCap() { // GH-90000
            // Allow only 2 speakers but send 3 distinct acoustic buckets
            SpeakerDiarizationService service = SpeakerDiarizationService.builder() // GH-90000
                    .embeddingExtractor(STUB_EXTRACTOR) // GH-90000
                    .maxSpeakers(2) // GH-90000
                    .similarityThreshold(0.80) // GH-90000
                    .build(); // GH-90000

            var seg0 = new SpeakerDiarizationService.AudioSegment(new byte[3], 0, 500, "a"); // bucket 0 // GH-90000
            var seg1 = new SpeakerDiarizationService.AudioSegment(new byte[4], 500, 1000, "b"); // bucket 1 // GH-90000
            var seg2 = new SpeakerDiarizationService.AudioSegment(new byte[5], 1000, 1500, "c"); // bucket 2 // GH-90000

            List<SpeakerDiarizationService.SpeakerTurn> turns =
                    service.diarize(List.of(seg0, seg1, seg2)); // GH-90000

            long distinctSpeakers = turns.stream().map(SpeakerDiarizationService.SpeakerTurn::speakerId) // GH-90000
                    .distinct().count(); // GH-90000
            assertThat(distinctSpeakers).isLessThanOrEqualTo(2); // GH-90000
        }
    }

    // ─── builder validation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("null extractor throws NullPointerException")
        void builder_nullExtractor_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> SpeakerDiarizationService.builder() // GH-90000
                            .embeddingExtractor(null)); // GH-90000
        }

        @Test
        @DisplayName("maxSpeakers < 1 throws IllegalArgumentException")
        void builder_invalidMaxSpeakers_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> SpeakerDiarizationService.builder().maxSpeakers(0)); // GH-90000
        }

        @Test
        @DisplayName("similarityThreshold > 1 throws IllegalArgumentException")
        void builder_invalidThreshold_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> SpeakerDiarizationService.builder().similarityThreshold(1.1)); // GH-90000
        }
    }

    // ─── AudioSegment validation ──────────────────────────────────────────────

    @Nested
    @DisplayName("AudioSegment")
    class AudioSegmentTests {

        @Test
        @DisplayName("null audioBytes throws NullPointerException")
        void audioSegment_nullBytes_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> new SpeakerDiarizationService.AudioSegment(null, 0, 100, "t")); // GH-90000
        }

        @Test
        @DisplayName("negative startMs throws IllegalArgumentException")
        void audioSegment_negativeStart_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new SpeakerDiarizationService.AudioSegment(new byte[1], -1, 100, "t")); // GH-90000
        }

        @Test
        @DisplayName("endMs < startMs throws IllegalArgumentException")
        void audioSegment_endBeforeStart_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new SpeakerDiarizationService.AudioSegment(new byte[1], 500, 100, "t")); // GH-90000
        }
    }
}
