/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.expert;

import io.activej.promise.Promise;

/**
 * Expert interface for pipeline design assistance.
 * Provides AI-powered recommendations for pipeline construction.
 */
public interface PipelineDesignAssistant {
    
    /**
     * Get recommendations for pipeline design based on input requirements.
     * @param requirements natural language description of requirements
     * @return Promise of design recommendations
     */
    Promise<String> getRecommendations(String requirements);
    
    /**
     * Validate a proposed pipeline design.
     * @param pipelineDesign the pipeline design to validate
     * @return Promise of validation result
     */
    Promise<ValidationResult> validateDesign(String pipelineDesign);
    
    /**
     * Suggest optimizations for an existing pipeline.
     * @param pipelineId the pipeline to optimize
     * @return Promise of optimization suggestions
     */
    Promise<String> suggestOptimizations(String pipelineId);
    
    /**
     * Result of pipeline validation.
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String[] suggestions;
        
        public ValidationResult(boolean valid, String message, String[] suggestions) {
            this.valid = valid;
            this.message = message;
            this.suggestions = suggestions;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String[] getSuggestions() {
            return suggestions;
        }
    }
}
