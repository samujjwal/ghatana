package com.ghatana.tts.core.model;

import java.util.Map;
import com.ghatana.tts.core.grpc.proto.AudioFormat;

/**
 * Options for text-to-speech synthesis.
 * 
 * @doc.type model
 * @doc.purpose Synthesis configuration
 */
public class SynthesisOptions {
    private final String voiceId;
    private final String language;
    private final double speakingRate;
    private final double pitch;
    private final double volume;
    private final boolean enableSSML;
    private final boolean enableProsody;
    private final AudioFormat audioFormat;
    private final int sampleRate;
    private final Map<String, Object> customOptions;
    
    private SynthesisOptions(Builder builder) {
        this.voiceId = builder.voiceId;
        this.language = builder.language;
        this.speakingRate = builder.speakingRate;
        this.pitch = builder.pitch;
        this.volume = builder.volume;
        this.enableSSML = builder.enableSSML;
        this.enableProsody = builder.enableProsody;
        this.audioFormat = builder.audioFormat;
        this.sampleRate = builder.sampleRate;
        this.customOptions = builder.customOptions;
    }
    
    public String voiceId() {
        return voiceId;
    }
    
    public String language() {
        return language;
    }
    
    public double speakingRate() {
        return speakingRate;
    }
    
    public double pitch() {
        return pitch;
    }
    
    public double volume() {
        return volume;
    }
    
    public boolean enableSSML() {
        return enableSSML;
    }
    
    public boolean enableProsody() {
        return enableProsody;
    }
    
    public AudioFormat audioFormat() {
        return audioFormat;
    }
    
    public int sampleRate() {
        return sampleRate;
    }
    
    public Map<String, Object> customOptions() {
        return customOptions;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String voiceId;
        private String language = "en-US";
        private double speakingRate = 1.0;
        private double pitch = 1.0;
        private double volume = 1.0;
        private boolean enableSSML = false;
        private boolean enableProsody = true;
        private AudioFormat audioFormat = AudioFormat.AUDIO_FORMAT_PCM_S16LE;
        private int sampleRate = 22050;
        private Map<String, Object> customOptions = Map.of();
        
        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder speakingRate(double speakingRate) {
            this.speakingRate = Math.max(0.25, Math.min(4.0, speakingRate));
            return this;
        }
        
        public Builder pitch(double pitch) {
            this.pitch = Math.max(-20.0, Math.min(20.0, pitch));
            return this;
        }
        
        public Builder volume(double volume) {
            this.volume = Math.max(0.0, Math.min(1.0, volume));
            return this;
        }
        
        public Builder enableSSML(boolean enableSSML) {
            this.enableSSML = enableSSML;
            return this;
        }
        
        public Builder enableProsody(boolean enableProsody) {
            this.enableProsody = enableProsody;
            return this;
        }
        
        public Builder audioFormat(AudioFormat audioFormat) {
            this.audioFormat = audioFormat;
            return this;
        }
        
        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }
        
        public Builder customOptions(Map<String, Object> customOptions) {
            this.customOptions = customOptions;
            return this;
        }
        
        public SynthesisOptions build() {
            return new SynthesisOptions(this);
        }
    }
}
