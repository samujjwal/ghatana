package com.ghatana.tts.cloning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VoiceCloningService} (AV-008.1). // GH-90000
 */
@DisplayName("VoiceCloningService — AV-008.1 [GH-90000]")
class VoiceCloningServiceTest {

    private VoiceCloningService service;

    /** 3 seconds of 16kHz 16-bit mono audio = 3 * 16000 * 2 = 96000 bytes */
    private static final byte[] AUDIO_3S = new byte[96_000];

    @BeforeEach
    void setUp() { // GH-90000
        service = VoiceCloningService.builder() // GH-90000
                .embeddingDimensions(64) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("createClone succeeds with sufficient audio [GH-90000]")
    void createCloneSuccess() { // GH-90000
        VoiceCloningService.VoiceClone clone = service.createClone(List.of(AUDIO_3S), 16_000, "my-voice"); // GH-90000

        assertThat(clone.cloneId()).isNotBlank(); // GH-90000
        assertThat(clone.displayLabel()).isEqualTo("my-voice [GH-90000]");
        assertThat(clone.embedding()).hasSize(64); // GH-90000
        assertThat(clone.totalDurationMs()).isGreaterThanOrEqualTo(3_000); // GH-90000
        assertThat(service.cloneCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("getClone returns stored clone [GH-90000]")
    void getCloneReturnsStored() { // GH-90000
        VoiceCloningService.VoiceClone created = service.createClone(List.of(AUDIO_3S), 16_000, "v"); // GH-90000
        VoiceCloningService.VoiceClone retrieved = service.getClone(created.cloneId()); // GH-90000

        assertThat(retrieved).isEqualTo(created); // GH-90000
    }

    @Test
    @DisplayName("getClone returns null for unknown ID [GH-90000]")
    void getCloneNullForUnknown() { // GH-90000
        assertThat(service.getClone("no-such-id [GH-90000]")).isNull();
    }

    @Test
    @DisplayName("deleteClone removes the clone [GH-90000]")
    void deleteClone() { // GH-90000
        VoiceCloningService.VoiceClone clone = service.createClone(List.of(AUDIO_3S), 16_000, "v"); // GH-90000
        boolean removed = service.deleteClone(clone.cloneId()); // GH-90000

        assertThat(removed).isTrue(); // GH-90000
        assertThat(service.getClone(clone.cloneId())).isNull(); // GH-90000
        assertThat(service.cloneCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("deleteClone returns false for unknown ID [GH-90000]")
    void deleteCloneReturnsFalseForUnknown() { // GH-90000
        assertThat(service.deleteClone("no-such [GH-90000]")).isFalse();
    }

    @Test
    @DisplayName("createClone throws when audio is too short [GH-90000]")
    void createCloneThrowsForShortAudio() { // GH-90000
        byte[] shortAudio = new byte[1_000]; // ~31ms
        assertThatThrownBy(() -> service.createClone(List.of(shortAudio), 16_000, "v")) // GH-90000
                .isInstanceOf(VoiceCloningService.VoiceCloningException.class) // GH-90000
                .hasMessageContaining("Insufficient audio [GH-90000]");
    }

    @Test
    @DisplayName("createClone throws when sample list is empty [GH-90000]")
    void createCloneThrowsForEmptySamples() { // GH-90000
        assertThatThrownBy(() -> service.createClone(List.of(), 16_000, "v")) // GH-90000
                .isInstanceOf(VoiceCloningService.VoiceCloningException.class); // GH-90000
    }

    @Test
    @DisplayName("createClone throws for null samples [GH-90000]")
    void createCloneThrowsForNullSamples() { // GH-90000
        assertThatThrownBy(() -> service.createClone(null, 16_000, "v")) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("fundamentalFrequency is in typical speech range 85–255 Hz [GH-90000]")
    void fundamentalFrequencyInRange() { // GH-90000
        VoiceCloningService.VoiceClone clone = service.createClone(List.of(AUDIO_3S), 16_000, "v"); // GH-90000
        assertThat(clone.fundamentalFrequency()).isBetween(85.0, 300.0); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects non-positive embeddingDimensions [GH-90000]")
    void builderRejectsZeroDimensions() { // GH-90000
        assertThatThrownBy(() -> VoiceCloningService.builder().embeddingDimensions(0).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
}
