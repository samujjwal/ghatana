package com.ghatana.tutorputor.contentgeneration.domain;

// Reuse existing models from content-explorer
import com.ghatana.tutorputor.explorer.model.*;

// Additional models needed for unified service
import java.util.List;
import java.util.Objects;

/**
 * Model classes for unified content generation service.
 * These reuse existing content-explorer models and add any missing ones.
 */

// Re-export existing models for convenience
public final class Models {
    private Models() {} // Utility class
    
    // Re-export existing models
    public static final Class<LearningClaim> LearningClaim = com.ghatana.tutorputor.explorer.model.LearningClaim.class;
    public static final Class<LearningEvidence> LearningEvidence = com.ghatana.tutorputor.explorer.model.LearningEvidence.class;
    public static final Class<ContentExample> ContentExample = com.ghatana.tutorputor.explorer.model.ContentExample.class;
    public static final Class<SimulationManifest> SimulationManifest = com.ghatana.tutorputor.explorer.model.SimulationManifest.class;
    public static final Class<AnimationConfig> AnimationConfig = com.ghatana.tutorputor.explorer.model.AnimationConfig.class;
    public static final Class<AssessmentItem> AssessmentItem = com.ghatana.tutorputor.explorer.model.AssessmentItem.class;
    public static final Class<QualityReport> QualityReport = com.ghatana.tutorputor.explorer.model.QualityReport.class;
    public static final Class<Domain> Domain = com.ghatana.tutorputor.explorer.model.Domain.class;
    public static final Class<ContentType> ContentType = com.ghatana.tutorputor.explorer.model.ContentType.class;
    public static final Class<ContentGenerationResponse> ContentGenerationResponse = com.ghatana.tutorputor.explorer.model.ContentGenerationResponse.class;
    public static final Class<GenerationConfig> GenerationConfig = com.ghatana.tutorputor.explorer.model.GenerationConfig.class;
}
