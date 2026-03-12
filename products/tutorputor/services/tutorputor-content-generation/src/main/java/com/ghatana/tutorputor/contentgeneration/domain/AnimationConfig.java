package com.ghatana.tutorputor.explorer.model;

import java.util.List;

public class AnimationConfig {
    private final String id;
    private final String title;
    private final List<String> keyframes;
    private final long durationMs;
    
    public AnimationConfig(String id, String title, List<String> keyframes, long durationMs) {
        this.id = id; this.title = title; this.keyframes = keyframes; this.durationMs = durationMs;
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<String> getKeyframes() { return keyframes; }
    public long getDurationMs() { return durationMs; }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private String id, title;
        private List<String> keyframes;
        private long durationMs;
        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder keyframes(List<String> keyframes) { this.keyframes = keyframes; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public AnimationConfig build() { return new AnimationConfig(id, title, keyframes, durationMs); }
    }
}
