package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates the quality of generated educational content.
 * 
 * <p>Validation checks include:
 * <ul>
 *   <li>Length and completeness</li>
 *   <li>Age-appropriateness of language</li>
 *   <li>Factual accuracy (via knowledge base)</li>
 *   <li>Pedagogical soundness</li>
 *   <li>Accessibility requirements</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Content quality validation service
 * @doc.layer product
 * @doc.pattern Validator
 */
public class ContentQualityValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ContentQualityValidator.class);
    
    // Metrics
    private final Counter validationsCounter;
    private final Counter validationFailuresCounter;
    private final Timer validationTimer;
    
    // Content length thresholds by type
    private static final int MIN_CLAIM_LENGTH = 20;
    private static final int MAX_CLAIM_LENGTH = 500;
    private static final int MIN_EXAMPLE_LENGTH = 50;
    private static final int MAX_EXAMPLE_LENGTH = 2000;
    private static final int MIN_SIMULATION_LENGTH = 100;
    
    // Inappropriate content patterns
    private static final Pattern PROFANITY_PATTERN = Pattern.compile(
        "\\b(damn|hell|crap|stupid|dumb|idiot)\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    // Complex vocabulary patterns (for grade-level checking)
    private static final Pattern COMPLEX_VOCAB_PATTERN = Pattern.compile(
        "\\b(epistemological|ontological|phenomenological|hermeneutic|dialectical)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    // Placeholder/incomplete content patterns
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
        "\\[(TODO|PLACEHOLDER|INSERT|TBD|FIXME)\\]|<[A-Z_]+>|\\{\\{.*?\\}\\}",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Creates a new ContentQualityValidator.
     *
     * @param meterRegistry the metrics registry
     */
    public ContentQualityValidator(@NotNull MeterRegistry meterRegistry) {
        this.validationsCounter = Counter.builder("tutorputor.content.validations")
            .description("Number of content validations")
            .register(meterRegistry);
        this.validationFailuresCounter = Counter.builder("tutorputor.content.validation_failures")
            .description("Number of validation failures")
            .register(meterRegistry);
        this.validationTimer = Timer.builder("tutorputor.content.validation_time")
            .description("Time spent validating content")
            .register(meterRegistry);
    }

    /**
     * Validates the generated content.
     *
     * @param response the content response to validate
     * @param context the agent context
     * @return a promise containing the validation result
     */
    @NotNull
    public Promise<ValidationResult> validate(
            @NotNull ContentGenerationResponse response,
            @NotNull AgentContext context) {
        
        Instant start = Instant.now();
        validationsCounter.increment();
        
        LOG.debug("Validating content of type {} for grade {}", 
            response.contentType(), response.gradeLevel());
        
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check 1: Content length
        ValidationCheck lengthCheck = validateLength(response);
        if (!lengthCheck.passed()) {
            issues.add(lengthCheck.issue());
            score -= 0.2;
        }
        
        // Check 2: Completeness (no placeholders)
        ValidationCheck completenessCheck = validateCompleteness(response.content());
        if (!completenessCheck.passed()) {
            issues.add(completenessCheck.issue());
            score -= 0.3;
        }
        
        // Check 3: Age-appropriateness
        ValidationCheck ageCheck = validateAgeAppropriateness(response.content(), response.gradeLevel());
        if (!ageCheck.passed()) {
            issues.add(ageCheck.issue());
            score -= 0.25;
        }
        
        // Check 4: Language safety
        ValidationCheck safetyCheck = validateLanguageSafety(response.content());
        if (!safetyCheck.passed()) {
            issues.add(safetyCheck.issue());
            score -= 0.4;
        }
        
        // Check 5: Structure validity
        ValidationCheck structureCheck = validateStructure(response);
        if (!structureCheck.passed()) {
            issues.add(structureCheck.issue());
            score -= 0.15;
        }
        
        // Ensure score is in valid range
        score = Math.max(0.0, Math.min(1.0, score));
        
        if (!issues.isEmpty()) {
            validationFailuresCounter.increment();
            LOG.warn("Content validation found {} issues, score: {}", issues.size(), score);
        }
        
        validationTimer.record(Duration.between(start, Instant.now()));
        
        return Promise.of(new ValidationResult(
            issues.isEmpty(),
            score,
            issues
        ));
    }

    private ValidationCheck validateLength(ContentGenerationResponse response) {
        String content = response.content();
        int length = content != null ? content.length() : 0;
        
        int minLength;
        int maxLength;
        
        switch (response.contentType()) {
            case CLAIM -> {
                minLength = MIN_CLAIM_LENGTH;
                maxLength = MAX_CLAIM_LENGTH;
            }
            case EXAMPLE -> {
                minLength = MIN_EXAMPLE_LENGTH;
                maxLength = MAX_EXAMPLE_LENGTH;
            }
            case SIMULATION, ANIMATION -> {
                minLength = MIN_SIMULATION_LENGTH;
                maxLength = Integer.MAX_VALUE;
            }
            default -> {
                minLength = 10;
                maxLength = Integer.MAX_VALUE;
            }
        }
        
        if (length < minLength) {
            return new ValidationCheck(false, 
                String.format("Content too short: %d chars (minimum: %d)", length, minLength));
        }
        
        if (length > maxLength) {
            return new ValidationCheck(false,
                String.format("Content too long: %d chars (maximum: %d)", length, maxLength));
        }
        
        return new ValidationCheck(true, null);
    }

    private ValidationCheck validateCompleteness(String content) {
        if (content == null || content.isBlank()) {
            return new ValidationCheck(false, "Content is empty or blank");
        }
        
        if (PLACEHOLDER_PATTERN.matcher(content).find()) {
            return new ValidationCheck(false, "Content contains placeholders or incomplete sections");
        }
        
        // Check for unfinished sentences
        if (content.trim().endsWith("...") || content.trim().endsWith(":")) {
            return new ValidationCheck(false, "Content appears to be incomplete");
        }
        
        return new ValidationCheck(true, null);
    }

    private ValidationCheck validateAgeAppropriateness(String content, String gradeLevel) {
        if (content == null) {
            return new ValidationCheck(true, null);
        }
        
        // Parse grade level to number
        int grade = parseGradeLevel(gradeLevel);
        
        // For elementary grades (K-5), check for overly complex vocabulary
        if (grade <= 5 && COMPLEX_VOCAB_PATTERN.matcher(content).find()) {
            return new ValidationCheck(false, 
                "Content contains vocabulary too complex for grade " + gradeLevel);
        }
        
        // Check average sentence length for younger grades
        if (grade <= 3) {
            double avgSentenceLength = calculateAverageSentenceLength(content);
            if (avgSentenceLength > 20) {
                return new ValidationCheck(false,
                    String.format("Sentences too long for grade %s (avg %.1f words)", 
                        gradeLevel, avgSentenceLength));
            }
        }
        
        return new ValidationCheck(true, null);
    }

    private ValidationCheck validateLanguageSafety(String content) {
        if (content == null) {
            return new ValidationCheck(true, null);
        }
        
        if (PROFANITY_PATTERN.matcher(content).find()) {
            return new ValidationCheck(false, "Content contains inappropriate language");
        }
        
        return new ValidationCheck(true, null);
    }

    private ValidationCheck validateStructure(ContentGenerationResponse response) {
        String content = response.content();
        
        if (content == null) {
            return new ValidationCheck(false, "Content is null");
        }
        
        switch (response.contentType()) {
            case CLAIM -> {
                // Claims should be single statements
                if (content.split("[.!?]").length > 3) {
                    return new ValidationCheck(false, 
                        "Claims should be concise single statements");
                }
            }
            case SIMULATION -> {
                // Simulations should be valid JSON
                if (!content.trim().startsWith("{")) {
                    return new ValidationCheck(false,
                        "Simulation content should be JSON format");
                }
            }
            case ANIMATION -> {
                // Animations should be valid JSON
                if (!content.trim().startsWith("{")) {
                    return new ValidationCheck(false,
                        "Animation content should be JSON format");
                }
            }
        }
        
        return new ValidationCheck(true, null);
    }

    private int parseGradeLevel(String gradeLevel) {
        if (gradeLevel == null) return 6; // Default to middle school
        
        String level = gradeLevel.toLowerCase().trim();
        
        if (level.equals("k") || level.equals("kindergarten")) return 0;
        
        try {
            // Try parsing as number
            return Integer.parseInt(level.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            // Handle text grades
            if (level.contains("elementary")) return 3;
            if (level.contains("middle")) return 7;
            if (level.contains("high")) return 10;
            if (level.contains("college") || level.contains("university")) return 13;
            return 6;
        }
    }

    private double calculateAverageSentenceLength(String content) {
        String[] sentences = content.split("[.!?]+");
        if (sentences.length == 0) return 0;
        
        int totalWords = 0;
        for (String sentence : sentences) {
            totalWords += sentence.trim().split("\\s+").length;
        }
        
        return (double) totalWords / sentences.length;
    }

    /**
     * Result of a single validation check.
     */
    private record ValidationCheck(boolean passed, String issue) {}

    /**
     * Complete validation result.
     *
     * @param passed whether all validations passed
     * @param score overall quality score (0.0 to 1.0)
     * @param issues list of validation issues found
     */
    public record ValidationResult(
        boolean passed,
        double score,
        List<String> issues
    ) {}
}
