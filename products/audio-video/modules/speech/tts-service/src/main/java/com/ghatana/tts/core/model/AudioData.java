package com.ghatana.tts.core.model;

import java.util.List;
import java.util.Map;

/**
 * Audio data container for text-to-speech synthesis.
 * 
 * @doc.type model
 * @doc.purpose Audio data representation for TTS
 */
public class AudioData {
    private final byte[] data;
    private final int sampleRate;
    private final int channels;
    private final AudioFormat format;
    private final Map<String, Object> metadata;
    
    private AudioData(Builder builder) {
        this.data = builder.data;
        this.sampleRate = builder.sampleRate;
        this.channels = builder.channels;
        this.format = builder.format;
        this.metadata = builder.metadata;
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
    
    public Map<String, Object> metadata() {
        return metadata;
    }
    
    public int durationMs() {
        return (data.length / (sampleRate * channels * (format.bitsPerSample() / 8))) * 1000;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
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
            return new AudioData(this);
        }
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
}
