package com.ghatana.media.common;

import java.time.Duration;
import java.util.Objects;

/**
 * Canonical audio data representation used across all engines.
 *
 * @doc.type record
 * @doc.purpose Immutable audio PCM data container
 * @doc.layer common
 * @doc.pattern ValueObject
 */
public record AudioData(
    byte[] data,
    int sampleRate,
    int channels,
    int bitsPerSample,
    Duration duration,
    AudioFormat format
) {
    public AudioData {
        Objects.requireNonNull(data, "data cannot be null");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (channels <= 0) throw new IllegalArgumentException("channels must be positive");
        if (bitsPerSample <= 0) throw new IllegalArgumentException("bitsPerSample must be positive");
    }

    /** Get the number of samples per channel. */
    public int getSampleCount() {
        return data.length / (channels * (bitsPerSample / 8));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private byte[] data;
        private int sampleRate = 16000;
        private int channels = 1;
        private int bitsPerSample = 16;
        private Duration duration;
        private AudioFormat format = AudioFormat.PCM;

        public Builder data(byte[] data) { this.data = data; return this; }
        public Builder sampleRate(int v) { this.sampleRate = v; return this; }
        public Builder channels(int v) { this.channels = v; return this; }
        public Builder bitsPerSample(int v) { this.bitsPerSample = v; return this; }
        public Builder duration(Duration v) { this.duration = v; return this; }
        public Builder format(AudioFormat v) { this.format = v; return this; }

        public AudioData build() {
            return new AudioData(data, sampleRate, channels, bitsPerSample, duration, format);
        }
    }
}
