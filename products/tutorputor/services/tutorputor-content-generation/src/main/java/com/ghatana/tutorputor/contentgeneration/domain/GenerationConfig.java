package com.ghatana.tutorputor.explorer.model;

public class GenerationConfig {
    private final boolean includeExamples;
    private final boolean includeSimulations;
    private final boolean includeAnimations;
    private final boolean includeAssessments;
    private final double qualityThreshold;
    
    public GenerationConfig(boolean includeExamples, boolean includeSimulations, boolean includeAnimations, boolean includeAssessments, double qualityThreshold) {
        this.includeExamples = includeExamples; this.includeSimulations = includeSimulations;
        this.includeAnimations = includeAnimations; this.includeAssessments = includeAssessments;
        this.qualityThreshold = qualityThreshold;
    }
    
    public boolean isIncludeExamples() { return includeExamples; }
    public boolean isIncludeSimulations() { return includeSimulations; }
    public boolean isIncludeAnimations() { return includeAnimations; }
    public boolean isIncludeAssessments() { return includeAssessments; }
    public double getQualityThreshold() { return qualityThreshold; }
    
    public static Builder builder() { return new Builder(); }
    
    public static GenerationConfig defaultConfig() {
        return GenerationConfig.builder()
            .includeExamples(true).includeSimulations(true)
            .includeAnimations(true).includeAssessments(true)
            .qualityThreshold(0.7).build();
    }
    
    public static class Builder {
        private boolean includeExamples = true;
        private boolean includeSimulations = true;
        private boolean includeAnimations = true;
        private boolean includeAssessments = true;
        private double qualityThreshold = 0.7;
        
        public Builder includeExamples(boolean includeExamples) { this.includeExamples = includeExamples; return this; }
        public Builder includeSimulations(boolean includeSimulations) { this.includeSimulations = includeSimulations; return this; }
        public Builder includeAnimations(boolean includeAnimations) { this.includeAnimations = includeAnimations; return this; }
        public Builder includeAssessments(boolean includeAssessments) { this.includeAssessments = includeAssessments; return this; }
        public Builder qualityThreshold(double qualityThreshold) { this.qualityThreshold = qualityThreshold; return this; }
        public GenerationConfig build() { return new GenerationConfig(includeExamples, includeSimulations, includeAnimations, includeAssessments, qualityThreshold); }
    }
}
