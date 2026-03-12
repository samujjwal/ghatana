package com.ghatana.tutorputor.contentgeneration.validation;

import com.ghatana.tutorputor.contentgeneration.domain.*;
import com.ghatana.tutorputor.explorer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified content validator combining capabilities from ai-agents and ai-service.
 *
 * <p>Validates all content types for quality, completeness, and appropriateness.
 * Provides confidence scoring and issue detection for educational content.
 *
 * @doc.type class
 * @doc.purpose Unified content validation for educational content
 * @doc.layer domain
 * @doc.pattern Strategy
 */
public class UnifiedContentValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(UnifiedContentValidator.class);
    
    // Validation thresholds
    private static final int MIN_CLAIMS = 1;
    private static final int MIN_CLAIM_TEXT_LENGTH = 20;
    private static final int MIN_EXAMPLES = 1;
    private static final int MIN_EXAMPLE_TEXT_LENGTH = 50;
    private static final int MIN_SIMULATION_ENTITIES = 1;
    private static final int MIN_ANIMATION_KEYFRAMES = 2;
    private static final int MIN_ASSESSMENT_ITEMS = 1;
    
    public UnifiedContentValidator() {}
    
    /**
     * Validates learning claims for completeness and quality.
     */
    public ValidationResult validateClaims(List<LearningClaim> claims) {
        LOG.debug("Validating {} claims", claims.size());
        
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check minimum count
        if (claims.size() < MIN_CLAIMS) {
            issues.add(String.format("Too few claims: %d (minimum %d)", claims.size(), MIN_CLAIMS));
            score -= 0.3;
        }
        
        // Check claim quality
        int validClaims = 0;
        for (LearningClaim claim : claims) {
            if (claim.getText() == null || claim.getText().length() < MIN_CLAIM_TEXT_LENGTH) {
                issues.add(String.format("Claim %s too short: %d characters", 
                                        claim.getId(), claim.getText() != null ? claim.getText().length() : 0));
            } else {
                validClaims++;
            }
            
            // Check domain appropriateness
            if (!isDomainAppropriate(claim.getText(), claim.getDomain())) {
                issues.add(String.format("Claim %s may not be appropriate for domain: %s", 
                                        claim.getId(), claim.getDomain()));
                score -= 0.1;
            }
        }
        
        // Adjust score based on valid claims ratio
        if (!claims.isEmpty()) {
            score *= (double) validClaims / claims.size();
        }
        
        return new ValidationResult(issues.isEmpty(), Math.max(0.0, score), issues);
    }
    
    /**
     * Validates content examples for completeness and quality.
     */
    public ValidationResult validateExamples(List<ContentExample> examples) {
        LOG.debug("Validating {} examples", examples.size());
        
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check minimum count
        if (examples.size() < MIN_EXAMPLES) {
            issues.add(String.format("Too few examples: %d (minimum %d)", examples.size(), MIN_EXAMPLES));
            score -= 0.3;
        }
        
        // Check example quality
        int validExamples = 0;
        for (ContentExample example : examples) {
            if (example.getContent() == null || example.getContent().length() < MIN_EXAMPLE_TEXT_LENGTH) {
                issues.add(String.format("Example %s too short: %d characters", 
                                        example.getId(), example.getContent() != null ? example.getContent().length() : 0));
            } else {
                validExamples++;
            }
            
            // Check example type validity
            if (!isValidExampleType(example.getType())) {
                issues.add(String.format("Example %s has invalid type: %s", 
                                        example.getId(), example.getType()));
                score -= 0.1;
            }
        }
        
        // Adjust score based on valid examples ratio
        if (!examples.isEmpty()) {
            score *= (double) validExamples / examples.size();
        }
        
        return new ValidationResult(issues.isEmpty(), Math.max(0.0, score), issues);
    }
    
    /**
     * Validates simulation manifests for completeness and quality.
     */
    public ValidationResult validateSimulations(List<SimulationManifest> simulations) {
        LOG.debug("Validating {} simulations", simulations.size());
        
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check simulation quality
        for (SimulationManifest simulation : simulations) {
            if (simulation.getManifest() == null || simulation.getManifest().isEmpty()) {
                issues.add(String.format("Simulation %s has empty manifest", simulation.getId()));
                score -= 0.5;
            }
            
            // Check for required entities (simplified check)
            if (!simulation.getManifest().contains("entity") && 
                !simulation.getManifest().contains("parameter")) {
                issues.add(String.format("Simulation %s may lack interactive elements", simulation.getId()));
                score -= 0.2;
            }
        }
        
        return new ValidationResult(issues.isEmpty(), Math.max(0.0, score), issues);
    }
    
    /**
     * Validates animation configurations for completeness and quality.
     */
    public ValidationResult validateAnimations(List<AnimationConfig> animations) {
        LOG.debug("Validating {} animations", animations.size());
        
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check animation quality
        for (AnimationConfig animation : animations) {
            if (animation.getSpecification() == null || animation.getSpecification().isEmpty()) {
                issues.add(String.format("Animation %s has empty specification", animation.getId()));
                score -= 0.5;
            }
            
            // Check for keyframes (simplified check)
            if (!animation.getSpecification().contains("keyframe")) {
                issues.add(String.format("Animation %s may lack keyframes", animation.getId()));
                score -= 0.3;
            }
        }
        
        return new ValidationResult(issues.isEmpty(), Math.max(0.0, score), issues);
    }
    
    /**
     * Validates assessment items for completeness and quality.
     */
    public ValidationResult validateAssessments(List<AssessmentItem> assessments) {
        LOG.debug("Validating {} assessments", assessments.size());
        
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check minimum count
        if (assessments.size() < MIN_ASSESSMENT_ITEMS) {
            issues.add(String.format("Too few assessments: %d (minimum %d)", assessments.size(), MIN_ASSESSMENT_ITEMS));
            score -= 0.3;
        }
        
        // Check assessment quality
        int validAssessments = 0;
        for (AssessmentItem assessment : assessments) {
            if (assessment.getQuestion() == null || assessment.getQuestion().isEmpty()) {
                issues.add(String.format("Assessment %s has empty question", assessment.getId()));
            } else {
                validAssessments++;
            }
            
            // Check options for multiple choice
            if ("MULTIPLE_CHOICE".equals(assessment.getType()) && 
                (assessment.getOptions() == null || assessment.getOptions().size() < 2)) {
                issues.add(String.format("Assessment %s has insufficient options", assessment.getId()));
                score -= 0.2;
            }
            
            // Check correct answer
            if (assessment.getCorrectAnswer() == null || assessment.getCorrectAnswer().isEmpty()) {
                issues.add(String.format("Assessment %s missing correct answer", assessment.getId()));
                score -= 0.1;
            }
        }
        
        // Adjust score based on valid assessments ratio
        if (!assessments.isEmpty()) {
            score *= (double) validAssessments / assessments.size();
        }
        
        return new ValidationResult(issues.isEmpty(), Math.max(0.0, score), issues);
    }
    
    /**
     * Validates complete content package for overall quality.
     */
    public ValidationResult validateCompletePackage(CompleteContentPackage package_) {
        LOG.debug("Validating complete content package");
        
        List<String> issues = new ArrayList<>();
        double totalScore = 0.0;
        int componentCount = 0;
        
        // Validate each component
        ValidationResult claimsResult = validateClaims(package_.claims());
        issues.addAll(claimsResult.issues());
        totalScore += claimsResult.confidence();
        componentCount++;
        
        ValidationResult examplesResult = validateExamples(package_.examples());
        issues.addAll(examplesResult.issues());
        totalScore += examplesResult.confidence();
        componentCount++;
        
        ValidationResult simulationsResult = validateSimulations(package_.simulations());
        issues.addAll(simulationsResult.issues());
        totalScore += simulationsResult.confidence();
        componentCount++;
        
        ValidationResult animationsResult = validateAnimations(package_.animations());
        issues.addAll(animationsResult.issues());
        totalScore += animationsResult.confidence();
        componentCount++;
        
        ValidationResult assessmentsResult = validateAssessments(package_.assessments());
        issues.addAll(assessmentsResult.issues());
        totalScore += assessmentsResult.confidence();
        componentCount++;
        
        // Calculate overall score
        double overallScore = componentCount > 0 ? totalScore / componentCount : 0.0;
        
        // Check for content diversity
        if (package_.claims().isEmpty() && package_.examples().isEmpty()) {
            issues.add("Package lacks both claims and examples");
            overallScore -= 0.3;
        }
        
        return new ValidationResult(issues.isEmpty(), Math.max(0.0, overallScore), issues);
    }
    
    // Helper methods
    private boolean isDomainAppropriate(String content, String domain) {
        // Simplified domain appropriateness check
        // In real implementation would use more sophisticated analysis
        return content != null && !content.isEmpty();
    }
    
    private boolean isValidExampleType(String type) {
        return type != null && List.of("WORKED_EXAMPLE", "VISUAL_EXAMPLE", "CONCRETE_EXAMPLE").contains(type);
    }
}
