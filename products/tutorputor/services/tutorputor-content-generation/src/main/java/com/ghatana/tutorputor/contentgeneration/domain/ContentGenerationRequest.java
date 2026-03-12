package com.ghatana.tutorputor.explorer.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @doc.type class
 * @doc.purpose Model for content generation request
 * @doc.layer product
 */
@Builder
@Getter
public class ContentGenerationRequest {
    private final String topic;
    private final String gradeLevel;
    private final Domain domain;
    private final String tenantId;
    private final GenerationConfig config;
    
    @Builder.Default
    private final int maxClaims = 10;
    
    @Builder.Default
    private final int maxExamples = 5;
    
    @Builder.Default
    private final int maxSimulations = 3;
    
    @Builder.Default
    private final int maxAnimations = 3;
    
    @Builder.Default
    private final int maxAssessments = 10;
}
