package com.ghatana.media.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests shared audio conversion and metadata extraction utilities
 * @doc.layer platform
 * @doc.pattern TestCase
 */
@DisplayName("AudioConverter [GH-90000]")
class AudioConverterTest {

    @Test
    @DisplayName("PCM16 round-trip keeps sample shape [GH-90000]")
    void pcm16RoundTrip() { // GH-90000
        float[] samples = new float[]{-1.0f, -0.5f, 0.0f, 0.5f, 1.0f};

        byte[] pcm = AudioConverter.floatSamplesToPcm16(samples); // GH-90000
        float[] restored = AudioConverter.pcmToFloatSamples(pcm, 16); // GH-90000

        assertThat(restored).hasSize(samples.length); // GH-90000
        assertThat(restored[0]).isCloseTo(-1.0f, within(0.01f)); // GH-90000
        assertThat(restored[1]).isCloseTo(-0.5f, within(0.01f)); // GH-90000
        assertThat(restored[3]).isCloseTo(0.5f, within(0.01f)); // GH-90000
        assertThat(restored[4]).isCloseTo(1.0f, within(0.01f)); // GH-90000
    }

    @Test
    @DisplayName("Signed 24-bit PCM values are sign-extended correctly [GH-90000]")
    void pcm24SignExtension() { // GH-90000
        byte[] data = new byte[]{0x00, 0x00, (byte) 0x80, (byte) 0xFF, (byte) 0xFF, 0x7F}; // GH-90000

        float[] restored = AudioConverter.pcmToFloatSamples(data, 24); // GH-90000

        assertThat(restored[0]).isCloseTo(-1.0f, within(0.01f)); // GH-90000
        assertThat(restored[1]).isCloseTo(1.0f, within(0.01f)); // GH-90000
    }

    @Test
    @DisplayName("Audio metadata is extracted from AudioData and WAV bytes [GH-90000]")
    void extractsAudioMetadata() { // GH-90000
        AudioData audioData = AudioConverter.fromFloatSamples(new float[]{0.0f, 0.5f, -0.5f, 0.25f}, 16000, 1); // GH-90000

        AudioMetadata dataMetadata = AudioMetadataExtractor.fromAudioData(audioData); // GH-90000
        AudioMetadata wavMetadata = AudioMetadataExtractor.fromWavBytes(createWavBytes(16000, 1, 16, 4)); // GH-90000

        assertThat(dataMetadata.sampleRate()).isEqualTo(16000); // GH-90000
        assertThat(dataMetadata.channels()).isEqualTo(1); // GH-90000
        assertThat(dataMetadata.bitsPerSample()).isEqualTo(16); // GH-90000
        assertThat(dataMetadata.sampleCount()).isEqualTo(4); // GH-90000

        assertThat(wavMetadata.sampleRate()).isEqualTo(16000); // GH-90000
        assertThat(wavMetadata.channels()).isEqualTo(1); // GH-90000
        assertThat(wavMetadata.bitsPerSample()).isEqualTo(16); // GH-90000
        assertThat(wavMetadata.sampleCount()).isEqualTo(4); // GH-90000
        assertThat(wavMetadata.containerFormat()).isEqualTo("WAV [GH-90000]");
    }

    private static org.assertj.core.data.Offset<Float> within(float value) { // GH-90000
        return org.assertj.core.data.Offset.offset(value); // GH-90000
    }

    private static byte[] createWavBytes(int sampleRate, int channels, int bitsPerSample, int sampleCount) { // GH-90000
        int dataSize = sampleCount * channels * (bitsPerSample / 8); // GH-90000
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN); // GH-90000
        buffer.put("RIFF".getBytes()); // GH-90000
        buffer.putInt(36 + dataSize); // GH-90000
        buffer.put("WAVE".getBytes()); // GH-90000
        buffer.put("fmt ".getBytes()); // GH-90000
        buffer.putInt(16); // GH-90000
        buffer.putShort((short) 1); // GH-90000
        buffer.putShort((short) channels); // GH-90000
        buffer.putInt(sampleRate); // GH-90000
        buffer.putInt(sampleRate * channels * (bitsPerSample / 8)); // GH-90000
        buffer.putShort((short) (channels * (bitsPerSample / 8))); // GH-90000
        buffer.putShort((short) bitsPerSample); // GH-90000
        buffer.put("data".getBytes()); // GH-90000
        buffer.putInt(dataSize); // GH-90000
        for (int index = 0; index < dataSize; index++) { // GH-90000
            buffer.put((byte) 0); // GH-90000
        }
        return buffer.array(); // GH-90000
    }
}
