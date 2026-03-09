package com.ghatana.stt.core.api;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Container for audio data to be transcribed.
 * 
 * <p>Supports various audio formats and provides utilities for
 * format conversion and validation.
 * 
 * @doc.type record
 * @doc.purpose Audio data container for transcription input
 * @doc.layer api
 */
public record AudioData(
    /** Raw audio bytes */
    byte[] data,
    
    /** Sample rate in Hz (e.g., 16000, 44100) */
    int sampleRate,
    
    /** Number of audio channels (1 = mono, 2 = stereo) */
    int channels,
    
    /** Bits per sample (typically 16 or 32) */
    int bitsPerSample,
    
    /** Audio format */
    AudioFormat format
) {
    public AudioData {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(format, "format must not be null");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channels must be positive");
        }
        if (bitsPerSample <= 0) {
            throw new IllegalArgumentException("bitsPerSample must be positive");
        }
    }

    /**
     * Create AudioData from raw PCM bytes with standard settings.
     * 
     * @param pcmData Raw PCM audio data
     * @param sampleRate Sample rate in Hz
     * @return AudioData configured for mono 16-bit PCM
     */
    public static AudioData fromPcm(byte[] pcmData, int sampleRate) {
        return new AudioData(pcmData, sampleRate, 1, 16, AudioFormat.PCM_S16LE);
    }

    /**
     * Create AudioData from float samples.
     * 
     * @param samples Float samples (-1.0 to 1.0)
     * @param sampleRate Sample rate in Hz
     * @return AudioData configured for mono 32-bit float PCM
     */
    public static AudioData fromFloatSamples(float[] samples, int sampleRate) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 4);
        for (float sample : samples) {
            buffer.putFloat(sample);
        }
        return new AudioData(buffer.array(), sampleRate, 1, 32, AudioFormat.PCM_F32LE);
    }

    /**
     * Get duration in milliseconds.
     */
    public long durationMs() {
        int bytesPerSample = bitsPerSample / 8;
        int totalSamples = data.length / (bytesPerSample * channels);
        return (totalSamples * 1000L) / sampleRate;
    }

    /**
     * Get number of samples.
     */
    public int sampleCount() {
        int bytesPerSample = bitsPerSample / 8;
        return data.length / (bytesPerSample * channels);
    }

    /**
     * Check if audio needs resampling to target rate.
     */
    public boolean needsResampling(int targetSampleRate) {
        return sampleRate != targetSampleRate;
    }

    /**
     * Supported audio formats.
     */
    public enum AudioFormat {
        /** 16-bit signed little-endian PCM */
        PCM_S16LE,
        /** 32-bit float little-endian PCM */
        PCM_F32LE,
        /** WAV container */
        WAV,
        /** MP3 compressed */
        MP3,
        /** FLAC lossless */
        FLAC,
        /** Ogg Vorbis */
        OGG_VORBIS,
        /** WebM/Opus */
        WEBM_OPUS
    }
}
