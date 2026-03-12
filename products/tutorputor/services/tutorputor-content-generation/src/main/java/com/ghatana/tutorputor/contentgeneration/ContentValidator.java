package com.ghatana.tutorputor.contentgeneration.validation;

import com.ghatana.tutorputor.contentgeneration.contracts.v1.GenerateAnimationResponse;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.GenerateClaimsResponse;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.GenerateExamplesResponse;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.GenerateSimulationResponse;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.ValidationResult;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.ValidationIssue;
import com.ghatana.tutorputor.contentgeneration.contracts.v1.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Content validator for LLM-generated educational content.
 *
 * <p>Validates generation responses for correctness, completeness,
 * concreteness, and conciseness. Issues {@link ValidationIssue}s at
 * {@code WARNING} or {@code ERROR} severity and derives a confidence
 * score in [0, 1] based on the fraction of checks that pass.
 *
 * <p>Supported response types:
 * <ul>
 *   <li>{@link GenerateClaimsResponse} — claims completeness and Bloom diversity</li>
 *   <li>{@link GenerateExamplesResponse} — example completeness per type</li>
 *   <li>{@link GenerateSimulationResponse} — manifest entity/goal presence</li>
 *   <li>{@link GenerateAnimationResponse} — keyframe count and coverage</li>
 *   <li>Unknown types — accepted with reduced confidence</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validate LLM-generated educational content for quality and completeness
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class ContentValidator {

    /** Minimum claims expected in a response before we flag an error. */
    private static final int MIN_CLAIMS = 1;
    /** Minimum characters for a claim text to be considered non-trivial. */
    private static final int MIN_CLAIM_TEXT_LENGTH = 20;
    /** Minimum keyframes for a valid animation spec. */
    private static final int MIN_KEYFRAMES = 2;

    public ContentValidator() {}

    /**
     * Validates the given content response object.
     *
     * <p>Dispatches to a type-specific validation path and aggregates issues
     * into a {@link ValidationResult}. The confidence score is computed as
     * {@code (passedChecks / totalChecks)} where each check contributes 1 point.
     *
     * @param content a proto response (e.g., {@link GenerateClaimsResponse})
     * @return a {@link ValidationResult} with validity flag, confidence score, and any issues
     */
    public ValidationResult validate(Object content) {
        if (content == null) {
            return invalid("Content object is null", 0.0f);
        }

        if (content instanceof GenerateClaimsResponse response) {
            return validateClaims(response);
        }
        if (content instanceof GenerateExamplesResponse response) {
            return validateExamples(response);
        }
        if (content instanceof GenerateSimulationResponse response) {
            return validateSimulation(response);
        }
        if (content instanceof GenerateAnimationResponse response) {
            return validateAnimation(response);
        }

        // Unknown type — accept with reduced confidence to avoid false-blocking
        return ValidationResult.newBuilder()
                .setValid(true)
                .setConfidenceScore(0.6f)
                .addSuggestions("No type-specific validation rule found for: " + content.getClass().getSimpleName())
                .build();
    }

    // ── Type-specific validators ─────────────────────────────────────────────

    private ValidationResult validateClaims(GenerateClaimsResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        // Check: at least MIN_CLAIMS claims present
        totalChecks++;
        if (response.getClaimsCount() < MIN_CLAIMS) {
            issues.add(issue(Severity.ERROR, "claims",
                    "Response contains fewer than " + MIN_CLAIMS + " claims (got " + response.getClaimsCount() + ")"));
        } else {
            passedChecks++;
        }

        // Check: each claim has non-trivial text
        for (int i = 0; i < response.getClaimsCount(); i++) {
            totalChecks++;
            String text = response.getClaims(i).getText();
            if (text == null || text.trim().length() < MIN_CLAIM_TEXT_LENGTH) {
                issues.add(issue(Severity.WARNING, "claims[" + i + "].text",
                        "Claim text is too short or empty (min " + MIN_CLAIM_TEXT_LENGTH + " chars)"));
            } else {
                passedChecks++;
            }
        }

        // Check: at least two distinct Bloom levels across all claims (diversity)
        totalChecks++;
        long distinctBloomLevels = response.getClaimsList().stream()
                .map(c -> c.getBloomLevel())
                .distinct()
                .count();
        if (response.getClaimsCount() > 2 && distinctBloomLevels < 2) {
            issues.add(issue(Severity.WARNING, "claims",
                    "All claims are at the same Bloom level — consider a wider range of cognitive objectives"));
        } else {
            passedChecks++;
        }

        return buildResult(issues, totalChecks, passedChecks);
    }

    private ValidationResult validateExamples(GenerateExamplesResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        // Check: at least one example present
        totalChecks++;
        if (response.getExamplesCount() == 0) {
            issues.add(issue(Severity.ERROR, "examples", "No examples were generated"));
        } else {
            passedChecks++;
        }

        // Check: each example has title and description
        for (int i = 0; i < response.getExamplesCount(); i++) {
            var example = response.getExamples(i);
            totalChecks++;
            if (example.getTitle().isBlank()) {
                issues.add(issue(Severity.WARNING, "examples[" + i + "].title", "Example title is empty"));
            } else {
                passedChecks++;
            }
            totalChecks++;
            if (example.getDescription().isBlank()) {
                issues.add(issue(Severity.WARNING, "examples[" + i + "].description", "Example description is empty"));
            } else {
                passedChecks++;
            }
        }

        return buildResult(issues, totalChecks, passedChecks);
    }

    private ValidationResult validateSimulation(GenerateSimulationResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        // Check: manifest present
        totalChecks++;
        if (!response.hasManifest()) {
            issues.add(issue(Severity.ERROR, "manifest", "Simulation manifest is missing"));
            return buildResult(issues, totalChecks, passedChecks); // can't check further
        }
        passedChecks++;

        var manifest = response.getManifest();

        // Check: at least one entity
        totalChecks++;
        if (manifest.getEntitiesCount() == 0) {
            issues.add(issue(Severity.WARNING, "manifest.entities",
                    "Simulation has no entities — add at least one physical or conceptual object"));
        } else {
            passedChecks++;
        }

        // Check: at least one goal
        totalChecks++;
        if (manifest.getGoalsCount() == 0) {
            issues.add(issue(Severity.WARNING, "manifest.goals",
                    "Simulation has no learning goals — define at least one measurable success criterion"));
        } else {
            passedChecks++;
        }

        // Check: manifest has a name
        totalChecks++;
        if (manifest.getName().isBlank()) {
            issues.add(issue(Severity.WARNING, "manifest.name", "Simulation name is empty"));
        } else {
            passedChecks++;
        }

        return buildResult(issues, totalChecks, passedChecks);
    }

    private ValidationResult validateAnimation(GenerateAnimationResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        // Check: animation spec present
        totalChecks++;
        if (!response.hasAnimation()) {
            issues.add(issue(Severity.ERROR, "animation", "Animation spec is missing"));
            return buildResult(issues, totalChecks, passedChecks);
        }
        passedChecks++;

        var anim = response.getAnimation();

        // Check: minimum keyframes
        totalChecks++;
        if (anim.getKeyframesCount() < MIN_KEYFRAMES) {
            issues.add(issue(Severity.ERROR, "animation.keyframes",
                    "Animation must have at least " + MIN_KEYFRAMES + " keyframes (got " + anim.getKeyframesCount() + ")"));
        } else {
            passedChecks++;
        }

        // Check: duration is positive
        totalChecks++;
        if (anim.getDurationSeconds() <= 0) {
            issues.add(issue(Severity.WARNING, "animation.duration_seconds",
                    "Animation duration must be a positive number of seconds"));
        } else {
            passedChecks++;
        }

        // Check: title is present
        totalChecks++;
        if (anim.getTitle().isBlank()) {
            issues.add(issue(Severity.WARNING, "animation.title", "Animation title is empty"));
        } else {
            passedChecks++;
        }

        return buildResult(issues, totalChecks, passedChecks);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ValidationResult buildResult(List<ValidationIssue> issues, int totalChecks, int passedChecks) {
        float confidence = totalChecks == 0 ? 1.0f : (float) passedChecks / totalChecks;
        boolean hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == Severity.ERROR);
        boolean valid = !hasErrors && confidence >= 0.5f;

        var builder = ValidationResult.newBuilder()
                .setValid(valid)
                .setConfidenceScore(confidence)
                .addAllIssues(issues);

        if (!valid) {
            builder.addSuggestions("Review the flagged ERROR issues and regenerate if necessary.");
        }
        if (confidence < 0.8f) {
            builder.addSuggestions("Confidence is below 0.8 — consider human review before publishing.");
        }

        return builder.build();
    }

    private ValidationResult invalid(String message, float confidence) {
        return ValidationResult.newBuilder()
                .setValid(false)
                .setConfidenceScore(confidence)
                .addIssues(issue(Severity.ERROR, "root", message))
                .build();
    }

    private ValidationIssue issue(Severity severity, String field, String message) {
        return ValidationIssue.newBuilder()
                .setSeverity(severity)
                .setField(field)
                .setMessage(message)
                .build();
    }
}

