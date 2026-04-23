package com.ghatana.tts.mixing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link VoiceMixingService} — AV-008.4.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the voice mixing service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("VoiceMixingService")
class VoiceMixingServiceTest {

    /** Stub mixer: concatenates all track bytes. */
    private static final VoiceMixingService.AudioMixer STUB_MIXER = tracks -> {
        int total = tracks.stream().mapToInt(t -> t.audioBytes().length).sum(); // GH-90000
        byte[] out = new byte[total];
        int pos = 0;
        for (VoiceMixingService.AudioTrack t : tracks) { // GH-90000
            System.arraycopy(t.audioBytes(), 0, out, pos, t.audioBytes().length); // GH-90000
            pos += t.audioBytes().length; // GH-90000
        }
        return out;
    };

    private VoiceMixingService buildService() { // GH-90000
        return VoiceMixingService.of(STUB_MIXER); // GH-90000
    }

    // ─── of() ───────────────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("of(null) throws NullPointerException")
    void of_null_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> VoiceMixingService.of(null)); // GH-90000
    }

    // ─── mix() ──────────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("mix()")
    class Mix {

        @Test
        @DisplayName("single track produces output bytes matching track bytes")
        void mix_singleTrack_correctOutput() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            var track = VoiceMixingService.AudioTrack.of(new byte[]{1, 2, 3}, 500L, "alice"); // GH-90000
            VoiceMixingService.MixedAudio result = service.mix(List.of(track)); // GH-90000

            assertThat(result.audioBytes()).containsExactly(1, 2, 3); // GH-90000
            assertThat(result.trackCount()).isEqualTo(1); // GH-90000
            assertThat(result.totalDurationMs()).isEqualTo(500L); // GH-90000
        }

        @Test
        @DisplayName("multiple tracks produce concatenated bytes (stub behaviour)")
        void mix_multipleTracks_concatenated() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            var track1 = VoiceMixingService.AudioTrack.of(new byte[]{1, 2}, 300L, "alice"); // GH-90000
            var track2 = VoiceMixingService.AudioTrack.of(new byte[]{3, 4}, 500L, "bob"); // GH-90000

            VoiceMixingService.MixedAudio result = service.mix(List.of(track1, track2)); // GH-90000

            assertThat(result.audioBytes()).containsExactly(1, 2, 3, 4); // GH-90000
            assertThat(result.trackCount()).isEqualTo(2); // GH-90000
            assertThat(result.totalDurationMs()).isEqualTo(500L); // max of 300 and 500 // GH-90000
        }

        @Test
        @DisplayName("null tracks throws NullPointerException")
        void mix_null_throwsNPE() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> service.mix(null)); // GH-90000
        }

        @Test
        @DisplayName("empty tracks throws IllegalArgumentException")
        void mix_empty_throwsIAE() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.mix(Collections.emptyList())); // GH-90000
        }

        @Test
        @DisplayName("hasAudio is true when output is non-empty")
        void mix_hasAudio_true() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            var track = VoiceMixingService.AudioTrack.of(new byte[]{1}, 100L, "v"); // GH-90000
            assertThat(service.mix(List.of(track)).hasAudio()).isTrue(); // GH-90000
        }
    }

    // ─── session ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("session()")
    class Session {

        @Test
        @DisplayName("session mixes all added tracks correctly")
        void session_mixesAllTracks() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            var track1 = VoiceMixingService.AudioTrack.of(new byte[]{10, 20}, 200L, "a"); // GH-90000
            var track2 = VoiceMixingService.AudioTrack.of(new byte[]{30}, 100L, "b"); // GH-90000

            VoiceMixingService.MixedAudio result = service.session().build() // GH-90000
                    .addTrack(track1) // GH-90000
                    .addTrack(track2) // GH-90000
                    .mix(); // GH-90000

            assertThat(result.trackCount()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("adding null track to session throws NullPointerException")
        void session_addNullTrack_throwsNPE() { // GH-90000
            VoiceMixingService service = buildService(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> service.session().build().addTrack(null)); // GH-90000
        }
    }

    // ─── AudioTrack validation ────────────────────────────────────────────────

    @Nested
    @DisplayName("AudioTrack")
    class AudioTrackTests {

        @Test
        @DisplayName("volume > 1.0 throws IllegalArgumentException")
        void audioTrack_invalidVolume_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new VoiceMixingService.AudioTrack( // GH-90000
                            new byte[]{1}, 1.5, 0.0, 0L, 100L, "track"));
        }

        @Test
        @DisplayName("pan < -1.0 throws IllegalArgumentException")
        void audioTrack_invalidPan_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new VoiceMixingService.AudioTrack( // GH-90000
                            new byte[]{1}, 1.0, -1.5, 0L, 100L, "track"));
        }

        @Test
        @DisplayName("negative offsetMs throws IllegalArgumentException")
        void audioTrack_negativeOffset_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> new VoiceMixingService.AudioTrack( // GH-90000
                            new byte[]{1}, 1.0, 0.0, -1L, 100L, "track"));
        }

        @Test
        @DisplayName("AudioTrack.of: creates track at full volume, centred, zero offset")
        void audioTrack_of_defaults() { // GH-90000
            var track = VoiceMixingService.AudioTrack.of(new byte[]{1}, 500L, "voice"); // GH-90000
            assertThat(track.volume()).isEqualTo(1.0); // GH-90000
            assertThat(track.pan()).isEqualTo(0.0); // GH-90000
            assertThat(track.offsetMs()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("null audioBytes throws NullPointerException")
        void audioTrack_nullBytes_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> VoiceMixingService.AudioTrack.of(null, 100L, "x")); // GH-90000
        }
    }
}
