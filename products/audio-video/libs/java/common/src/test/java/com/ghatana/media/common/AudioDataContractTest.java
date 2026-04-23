package com.ghatana.media.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("AudioData contract")
class AudioDataContractTest {

    @Test
    @DisplayName("computes duration when omitted")
    void computesDurationWhenOmitted() { // GH-90000
        AudioData audio = AudioData.builder() // GH-90000
            .data(new byte[32_000]) // GH-90000
            .sampleRate(16_000) // GH-90000
            .channels(1) // GH-90000
            .bitsPerSample(16) // GH-90000
            .format(AudioFormat.PCM) // GH-90000
            .build(); // GH-90000

        assertEquals(Duration.ofSeconds(1), audio.duration()); // GH-90000
        assertEquals(16_000, audio.getSampleCount()); // GH-90000
    }

    @Test
    @DisplayName("defensively copies byte content")
    void defensivelyCopiesByteContent() { // GH-90000
        byte[] raw = new byte[] {1, 2, 3, 4};
        AudioData audio = new AudioData(raw, 16_000, 1, 16); // GH-90000

        raw[0] = 9;
        byte[] roundTrip = audio.data(); // GH-90000
        roundTrip[1] = 8;

        assertArrayEquals(new byte[] {1, 2, 3, 4}, audio.data()); // GH-90000
    }

    @Test
    @DisplayName("exposes canonical format descriptor")
    void exposesCanonicalFormatDescriptor() { // GH-90000
        AudioData audio = new AudioData(new byte[8_000], 8_000, 1, 16, Duration.ofSeconds(1), AudioFormat.WAV); // GH-90000

        CanonicalAudioFormat canonical = audio.canonicalFormat(); // GH-90000

        assertNotNull(canonical); // GH-90000
        assertEquals(8_000, canonical.sampleRate()); // GH-90000
        assertEquals(AudioFormat.WAV, canonical.format()); // GH-90000
    }
}
