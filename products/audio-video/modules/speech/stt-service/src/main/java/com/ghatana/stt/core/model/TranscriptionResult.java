package com.ghatana.stt.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of speech transcription processing.
 * 
 * @doc.type model
 * @doc.purpose Transcription result representation
 */
public class TranscriptionResult {
    private final String text;
    private final String language;
    private final double confidence;
    private final List<WordTiming> wordTimings;
    private final List<Alternative> alternatives;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final ProcessingTime processingTime;
    
    private TranscriptionResult(Builder builder) {
        this.text = builder.text;
        this.language = builder.language;
        this.confidence = builder.confidence;
        this.wordTimings = builder.wordTimings;
        this.alternatives = builder.alternatives;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp;
        this.processingTime = builder.processingTime;
    }
    
    public String text() {
        return text;
    }
    
    public String language() {
        return language;
    }
    
    public double confidence() {
        return confidence;
    }
    
    public List<WordTiming> wordTimings() {
        return wordTimings;
    }
    
    public List<Alternative> alternatives() {
        return alternatives;
    }
    
    public Map<String, Object> metadata() {
        return metadata;
    }
    
    public Instant timestamp() {
        return timestamp;
    }
    
    public ProcessingTime processingTime() {
        return processingTime;
    }
    
    public TranscriptionResult withText(String newText) {
        return new Builder(this).text(newText).build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String text;
        private String language = "en-US";
        private double confidence = 0.0;
        private List<WordTiming> wordTimings = List.of();
        private List<Alternative> alternatives = List.of();
        private Map<String, Object> metadata = Map.of();
        private Instant timestamp = Instant.now();
        private ProcessingTime processingTime;
        
        public Builder() {}
        
        public Builder(TranscriptionResult existing) {
            this.text = existing.text;
            this.language = existing.language;
            this.confidence = existing.confidence;
            this.wordTimings = existing.wordTimings;
            this.alternatives = existing.alternatives;
            this.metadata = existing.metadata;
            this.timestamp = existing.timestamp;
            this.processingTime = existing.processingTime;
        }
        
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        public Builder wordTimings(List<WordTiming> wordTimings) {
            this.wordTimings = wordTimings;
            return this;
        }
        
        public Builder alternatives(List<Alternative> alternatives) {
            this.alternatives = alternatives;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder processingTime(ProcessingTime processingTime) {
            this.processingTime = processingTime;
            return this;
        }
        
        public TranscriptionResult build() {
            return new TranscriptionResult(this);
        }
    }
    
    public static class WordTiming {
        private final String word;
        private final double startTime;
        private final double endTime;
        private final double confidence;
        
        public WordTiming(String word, double startTime, double endTime, double confidence) {
            this.word = word;
            this.startTime = startTime;
            this.endTime = endTime;
            this.confidence = confidence;
        }
        
        public String word() {
            return word;
        }
        
        public double startTime() {
            return startTime;
        }
        
        public double endTime() {
            return endTime;
        }
        
        public double confidence() {
            return confidence;
        }
    }
    
    public static class Alternative {
        private final String text;
        private final double confidence;
        
        public Alternative(String text, double confidence) {
            this.text = text;
            this.confidence = confidence;
        }
        
        public String text() {
            return text;
        }
        
        public double confidence() {
            return confidence;
        }
    }
    
    public static class ProcessingTime {
        private final long totalMs;
        private final long preprocessingMs;
        private final long inferenceMs;
        private final long postprocessingMs;
        
        public ProcessingTime(long totalMs, long preprocessingMs, long inferenceMs, long postprocessingMs) {
            this.totalMs = totalMs;
            this.preprocessingMs = preprocessingMs;
            this.inferenceMs = inferenceMs;
            this.postprocessingMs = postprocessingMs;
        }
        
        public long totalMs() {
            return totalMs;
        }
        
        public long preprocessingMs() {
            return preprocessingMs;
        }
        
        public long inferenceMs() {
            return inferenceMs;
        }
        
        public long postprocessingMs() {
            return postprocessingMs;
        }
    }
}
