package com.ghatana.tts.core.model;

import java.util.List;
import java.util.Map;

/**
 * Voice configuration options for text-to-speech.
 * 
 * @doc.type model
 * @doc.purpose Voice configuration
 */
public class VoiceOptions {
    private final String voiceId;
    private final String name;
    private final String language;
    private final String gender;
    private final String age;
    private final String style;
    private final String accent;
    private final boolean isNeural;
    private final boolean isCustom;
    private final List<String> supportedLanguages;
    private final Map<String, Object> characteristics;
    
    private VoiceOptions(Builder builder) {
        this.voiceId = builder.voiceId;
        this.name = builder.name;
        this.language = builder.language;
        this.gender = builder.gender;
        this.age = builder.age;
        this.style = builder.style;
        this.accent = builder.accent;
        this.isNeural = builder.isNeural;
        this.isCustom = builder.isCustom;
        this.supportedLanguages = builder.supportedLanguages;
        this.characteristics = builder.characteristics;
    }
    
    public String voiceId() {
        return voiceId;
    }
    
    public String name() {
        return name;
    }
    
    public String language() {
        return language;
    }
    
    public String gender() {
        return gender;
    }
    
    public String age() {
        return age;
    }
    
    public String style() {
        return style;
    }
    
    public String accent() {
        return accent;
    }
    
    public boolean isNeural() {
        return isNeural;
    }
    
    public boolean isCustom() {
        return isCustom;
    }
    
    public List<String> supportedLanguages() {
        return supportedLanguages;
    }
    
    public Map<String, Object> characteristics() {
        return characteristics;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String voiceId;
        private String name;
        private String language = "en-US";
        private String gender = "neutral";
        private String age = "adult";
        private String style = "neutral";
        private String accent = "neutral";
        private boolean isNeural = true;
        private boolean isCustom = false;
        private List<String> supportedLanguages = List.of();
        private Map<String, Object> characteristics = Map.of();
        
        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder gender(String gender) {
            this.gender = gender;
            return this;
        }
        
        public Builder age(String age) {
            this.age = age;
            return this;
        }
        
        public Builder style(String style) {
            this.style = style;
            return this;
        }
        
        public Builder accent(String accent) {
            this.accent = accent;
            return this;
        }
        
        public Builder isNeural(boolean isNeural) {
            this.isNeural = isNeural;
            return this;
        }
        
        public Builder isCustom(boolean isCustom) {
            this.isCustom = isCustom;
            return this;
        }
        
        public Builder supportedLanguages(List<String> supportedLanguages) {
            this.supportedLanguages = supportedLanguages;
            return this;
        }
        
        public Builder characteristics(Map<String, Object> characteristics) {
            this.characteristics = characteristics;
            return this;
        }
        
        public VoiceOptions build() {
            return new VoiceOptions(this);
        }
    }
}
