package com.ghatana.tts.core.api;

import java.util.Map;
import java.util.Objects;

/**
 * Audio payload produced by the TTS engine.
 *
 * @doc.type record
 * @doc.purpose Synthesized audio container for TTS output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AudioData(
    byte[] data,
    int sampleRate,
    int channels,
    AudioFormat format,
    Map<String, Object> metadata
) {
    public AudioData {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(format, "format must not be null");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channels must be positive");
        }
    }

    public int durationMs() {
        return (data.length / (sampleRate * channels * (format.bitsPerSample() / 8))) * 1000;
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum AudioFormat {
        PCM_16BIT(16),
        PCM_24BIT(24),
        PCM_32BIT(32),
        FLOAT_32BIT(32),
        MULAW(8),
        ALAW(8);

        private final int bitsPerSample;

        AudioFormat(int bitsPerSample) {
            this.bitsPerSample = bitsPerSample;
        }

        public int bitsPerSample() {
            return bitsPerSample;
        }
    }

    public static final class Builder {
        private byte[] data;
        private int sampleRate = 22050;
        private int channels = 1;
        private AudioFormat format = AudioFormat.PCM_16BIT;
        private Map<String, Object> metadata = Map.of();

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        public Builder format(AudioFormat format) {
            this.format = format;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AudioData build() {
            return new AudioData(data, sampleRate, channels, format, metadata);
        }
    }
}