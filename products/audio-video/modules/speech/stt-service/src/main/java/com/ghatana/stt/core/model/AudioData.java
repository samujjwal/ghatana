package com.ghatana.stt.core.model;

import java.util.List;

/**
 * Audio data container for speech-to-text processing.
 * 
 * @doc.type model
 * @doc.purpose Audio data representation
 */
public class AudioData {
    private final byte[] data;
    private final int sampleRate;
    private final int channels;
    private final AudioFormat format;
    
    public AudioData(byte[] data, int sampleRate, int channels, AudioFormat format) {
        this.data = data;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.format = format;
    }
    
    public byte[] data() {
        return data;
    }
    
    public int sampleRate() {
        return sampleRate;
    }
    
    public int channels() {
        return channels;
    }
    
    public AudioFormat format() {
        return format;
    }
    
    public int durationMs() {
        return (data.length / (sampleRate * channels * (format.bitsPerSample() / 8))) * 1000;
    }
    
    public enum AudioFormat {
        PCM_16BIT(16),
        PCM_24BIT(24),
        PCM_32BIT(32),
        FLOAT_32BIT(32);
        
        private final int bitsPerSample;
        
        AudioFormat(int bitsPerSample) {
            this.bitsPerSample = bitsPerSample;
        }
        
        public int bitsPerSample() {
            return bitsPerSample;
        }
    }
}
