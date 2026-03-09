package com.ghatana.requirements.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**

 * @doc.type class

 * @doc.purpose Handles requirement generation response operations

 * @doc.layer core

 * @doc.pattern DTO

 */

public class RequirementGenerationResponse {
    private List<String> requirements;
    private double confidence;
    private long generatedAt;

    private RequirementGenerationResponse() {}

    public List<String> getRequirements() { return requirements; }
    public double getConfidence() { return confidence; }
    public long getGeneratedAt() { return generatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RequirementGenerationResponse resp = new RequirementGenerationResponse();
        public Builder requirements(List<String> r) { resp.requirements = r; return this; }
        public Builder confidence(double c) { resp.confidence = c; return this; }
        public Builder generatedAt(long t) { resp.generatedAt = t; return this; }
        public RequirementGenerationResponse build() { return resp; }
    }
}
