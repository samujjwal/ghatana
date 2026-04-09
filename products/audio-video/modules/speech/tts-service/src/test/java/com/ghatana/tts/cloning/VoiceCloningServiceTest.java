package com.ghatana.tts.cloning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VoiceCloningService} (AV-008.1).
 */
@DisplayName("VoiceCloningService — AV-008.1")
class VoiceCloningServiceTest {

    private VoiceCloningService service;

    /** 3 seconds of 16kHz 16-bit mono audio = 3 * 16000 * 2 = 96000 bytes */
    private static final byte[] AUDIO_3S = new byte[96_000];

    @BeforeEach
    void setUp() {
        service = VoiceCloningService.builder()
                .embeddingDimensions(64)
                .build();
    }

    @Test
    @DisplayName("createClone succeeds with sufficient audio")
    void createCloneSuccess() {
        VoiceCloningService.VoiceClone clone = service.createClone(List.of(AUDIO_3S), 16_000, "my-voice");

        assertThat(clone.cloneId()).isNotBlank();
        assertThat(clone.displayLabel()).isEqualTo("my-voice");
        assertThat(clone.embedding()).hasSize(64);
        assertThat(clone.totalDurationMs()).isGreaterThanOrEqualTo(3_000);
        assertThat(service.cloneCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getClone returns stored clone")
    void getCloneReturnsStored() {
        VoiceCloningService.VoiceClone created = service.createClone(List.of(AUDIO_3S), 16_000, "v");
        VoiceCloningService.VoiceClone retrieved = service.getClone(created.cloneId());

        assertThat(retrieved).isEqualTo(created);
    }

    @Test
    @DisplayName("getClone returns null for unknown ID")
    void getCloneNullForUnknown() {
        assertThat(service.getClone("no-such-id")).isNull();
    }

    @Test
    @DisplayName("deleteClone removes the clone")
    void deleteClone() {
        VoiceCloningService.VoiceClone clone = service.createClone(List.of(AUDIO_3S), 16_000, "v");
        boolean removed = service.deleteClone(clone.cloneId());

        assertThat(removed).isTrue();
        assertThat(service.getClone(clone.cloneId())).isNull();
        assertThat(service.cloneCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteClone returns false for unknown ID")
    void deleteCloneReturnsFalseForUnknown() {
        assertThat(service.deleteClone("no-such")).isFalse();
    }

    @Test
    @DisplayName("createClone throws when audio is too short")
    void createCloneThrowsForShortAudio() {
        byte[] shortAudio = new byte[1_000]; // ~31ms
        assertThatThrownBy(() -> service.createClone(List.of(shortAudio), 16_000, "v"))
                .isInstanceOf(VoiceCloningService.VoiceCloningException.class)
                .hasMessageContaining("Insufficient audio");
    }

    @Test
    @DisplayName("createClone throws when sample list is empty")
    void createCloneThrowsForEmptySamples() {
        assertThatThrownBy(() -> service.createClone(List.of(), 16_000, "v"))
                .isInstanceOf(VoiceCloningService.VoiceCloningException.class);
    }

    @Test
    @DisplayName("createClone throws for null samples")
    void createCloneThrowsForNullSamples() {
        assertThatThrownBy(() -> service.createClone(null, 16_000, "v"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fundamentalFrequency is in typical speech range 85–255 Hz")
    void fundamentalFrequencyInRange() {
        VoiceCloningService.VoiceClone clone = service.createClone(List.of(AUDIO_3S), 16_000, "v");
        assertThat(clone.fundamentalFrequency()).isBetween(85.0, 300.0);
    }

    @Test
    @DisplayName("Builder rejects non-positive embeddingDimensions")
    void builderRejectsZeroDimensions() {
        assertThatThrownBy(() -> VoiceCloningService.builder().embeddingDimensions(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
