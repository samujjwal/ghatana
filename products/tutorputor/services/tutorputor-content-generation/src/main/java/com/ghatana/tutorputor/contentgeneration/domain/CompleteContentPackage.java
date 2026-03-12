package com.ghatana.tutorputor.explorer.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @doc.type class
 * @doc.purpose Model for complete content package
 * @doc.layer product
 */
@Builder
@Getter
public class CompleteContentPackage {
    private final List<LearningClaim> claims;
    private final List<LearningEvidence> evidence;
    private final List<ContentExample> examples;
    private final List<SimulationManifest> simulations;
    private final List<AnimationConfig> animations;
    private final List<AssessmentItem> assessments;
    private final QualityReport qualityReport;
    private final long generationDurationMs;
}
