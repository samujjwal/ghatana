package com.ghatana.tutorputor.contentgeneration.domain;

import io.activej.promise.Promise;
import java.util.*;

/**
 * Unified content generation interface combining capabilities from ai-service and content-explorer.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for generating educational content including claims, examples, 
 * simulations, animations, and comprehensive learning packages. Supports streaming 
 * responses, batch generation, and model configuration using platform LLMGateway.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * UnifiedContentGenerator generator = new PlatformContentGenerator(llmGateway, validator);
 * ContentGenerationRequest request = ContentGenerationRequest.builder()
 *     .topic("Newton's Laws")
 *     .gradeLevel("HIGH_SCHOOL")
 *     .domain("PHYSICS")
 *     .build();
 * 
 * Promise<CompleteContentPackage> result = generator.generateCompletePackage(request);
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Claims generation with Bloom's taxonomy diversity
 * - Example generation (worked examples, visual examples)
 * - Simulation manifest generation
 * - Animation keyframe generation
 * - Assessment item generation
 * - Quality validation and scoring
 * - Multi-provider LLM routing
 * - Batch processing capabilities
 *
 * @doc.type interface
 * @doc.purpose Unified content generation for educational content
 * @doc.layer domain
 * @doc.pattern Port, Strategy
 */
public interface UnifiedContentGenerator {

    /**
     * Generates a complete content package with all content types.
     *
     * <p>Generates claims, evidence, examples, simulations, animations, and assessments
     * in parallel, validates quality, and returns a comprehensive package.
     *
     * @param request the content generation request (non-null)
     * @return Promise of complete content package
     * @throws NullPointerException if request null
     */
    Promise<CompleteContentPackage> generateCompletePackage(ContentGenerationRequest request);

    /**
     * Generates educational claims for a given topic.
     *
     * @param request the claims generation request (non-null)
     * @return Promise of claims response
     */
    Promise<ClaimsResponse> generateClaims(ClaimsRequest request);

    /**
     * Generates worked examples and visual examples for claims.
     *
     * @param request the examples generation request (non-null)
     * @return Promise of examples response
     */
    Promise<ExamplesResponse> generateExamples(ExamplesRequest request);

    /**
     * Generates simulation manifests for interactive content.
     *
     * @param request the simulation generation request (non-null)
     * @return Promise of simulation response
     */
    Promise<SimulationResponse> generateSimulation(SimulationRequest request);

    /**
     * Generates animation specifications with keyframes.
     *
     * @param request the animation generation request (non-null)
     * @return Promise of animation response
     */
    Promise<AnimationResponse> generateAnimation(AnimationRequest request);

    /**
     * Generates assessment items for content evaluation.
     *
     * @param request the assessment generation request (non-null)
     * @return Promise of assessment response
     */
    Promise<AssessmentResponse> generateAssessment(AssessmentRequest request);

    /**
     * Validates generated content for quality and completeness.
     *
     * @param content the content to validate (non-null)
     * @param contentType type of content being validated (non-null)
     * @return Promise of validation result with confidence score
     */
    Promise<ValidationResult> validateContent(Object content, ContentType contentType);

    /**
     * Updates configuration for content generation.
     *
     * @param tenantId tenant identifier (non-null)
     * @param configuration configuration key-value pairs (non-null)
     * @return Promise of void (completes when configuration updated)
     */
    Promise<Void> updateConfiguration(String tenantId, Map<String, Object> configuration);

    /**
     * Gets current configuration for tenant.
     *
     * @param tenantId tenant identifier (non-null)
     * @return Promise of configuration map
     */
    Promise<Map<String, Object>> getConfiguration(String tenantId);

    /**
     * Gets supported generation models.
     *
     * @return Promise of list of model names
     */
    Promise<List<String>> getSupportedModels();

    // Result classes
    record CompleteContentPackage(
        List<LearningClaim> claims,
        List<LearningEvidence> evidence,
        List<ContentExample> examples,
        List<SimulationManifest> simulations,
        List<AnimationConfig> animations,
        List<AssessmentItem> assessments,
        QualityReport qualityReport
    ) {}

    record ClaimsRequest(
        String topic,
        String gradeLevel,
        String domain,
        int maxClaims
    ) {}

    record ClaimsResponse(
        List<LearningClaim> claims,
        ValidationResult validation
    ) {}

    record ExamplesRequest(
        List<LearningClaim> claims,
        String gradeLevel,
        String domain,
        int maxExamples
    ) {}

    record ExamplesResponse(
        List<ContentExample> examples,
        ValidationResult validation
    ) {}

    record SimulationRequest(
        List<LearningClaim> claims,
        String gradeLevel,
        String domain,
        int maxSimulations
    ) {}

    record SimulationResponse(
        List<SimulationManifest> simulations,
        ValidationResult validation
    ) {}

    record AnimationRequest(
        List<LearningClaim> claims,
        String gradeLevel,
        String domain,
        int maxAnimations
    ) {}

    record AnimationResponse(
        List<AnimationConfig> animations,
        ValidationResult validation
    ) {}

    record AssessmentRequest(
        List<LearningClaim> claims,
        String gradeLevel,
        String domain,
        int maxAssessments
    ) {}

    record AssessmentResponse(
        List<AssessmentItem> assessments,
        ValidationResult validation
    ) {}

    record ValidationResult(
        boolean passed,
        double confidence,
        List<String> issues
    ) {}

    enum ContentType {
        CLAIMS, EXAMPLES, SIMULATION, ANIMATION, ASSESSMENT, COMPLETE_PACKAGE
    }
}
