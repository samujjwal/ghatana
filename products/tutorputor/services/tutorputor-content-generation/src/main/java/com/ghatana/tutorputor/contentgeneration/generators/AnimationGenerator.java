package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.AnimationConfig;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Produces domain-aware animation configurations for learning claims.
 *
 * <p>Keyframes and duration are tailored to the domain and grade level so that
 * animations accurately reflect the pedagogical arc of the learning objective — e.g.,
 * showing build-up → peak → resolution for science, or input → transform → output for
 * computer science — rather than the generic "Start → Middle → End" sequence that
 * does not convey meaningful instructional intent.
 *
 * @doc.type class
 * @doc.purpose Generate domain-specific animation configs for claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class AnimationGenerator {

    public Promise<List<AnimationConfig>> generateAnimations(
            List<LearningClaim> claims, ContentGenerationRequest request) {

        return Promise.of(claims.stream()
                .map(claim -> buildConfig(claim, request))
                .toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static AnimationConfig buildConfig(LearningClaim claim, ContentGenerationRequest request) {
        Domain domain = request.getDomain();
        int gradeLevel = request.getGradeLevel();
        List<String> keyframes = keyframesFor(domain, claim.getText(), gradeLevel);
        int durationMs = durationMsFor(domain, gradeLevel);

        return AnimationConfig.builder()
                .id(UUID.randomUUID().toString())
                .title("Animation: " + claim.getText())
                .keyframes(keyframes)
                .durationMs(durationMs)
                .build();
    }

    private static List<String> keyframesFor(Domain domain, String claimText, int gradeLevel) {
        return switch (domain) {
            case MATH -> List.of(
                    "Display problem statement: " + claimText,
                    "Highlight known variables",
                    "Apply rule or formula step-by-step",
                    "Verify result against constraints",
                    "Present final solution");
            case SCIENCE -> List.of(
                    "Present initial conditions",
                    "Show forces / interactions at play",
                    "Simulate state transition",
                    "Reach equilibrium or conclusion",
                    "Reflect on real-world implications");
            case ENGINEERING -> List.of(
                    "Define design constraints",
                    "Sketch concept model",
                    "Apply engineering principles",
                    "Test against failure criteria",
                    "Iterate to optimal solution");
            case COMPUTER_SCIENCE, TECH -> List.of(
                    "Input: initial data set",
                    "Processing: step-by-step algorithm trace",
                    "State change: intermediate results",
                    "Output: final computed result",
                    "Complexity analysis");
            case MEDICINE, HEALTH -> List.of(
                    "Patient baseline assessment",
                    "Symptom / diagnostic phase",
                    "Intervention applied",
                    "Monitoring and response",
                    "Outcome and follow-up");
            case BUSINESS, MANAGEMENT, ECONOMICS -> List.of(
                    "Market or organisational context",
                    "Problem identification",
                    "Strategy formulation",
                    "Implementation simulation",
                    "Evaluate outcomes and KPIs");
            default -> List.of(
                    "Introduce concept: " + claimText,
                    "Explore key components",
                    "Apply in context (grade " + gradeLevel + ")",
                    "Synthesise understanding",
                    "Reflect and extend");
        };
    }

    /** Returns a grade-scaled duration in milliseconds (more complex topics get more time). */
    private static int durationMsFor(Domain domain, int gradeLevel) {
        int base = switch (domain) {
            case MATH, COMPUTER_SCIENCE, TECH -> 6000;
            case SCIENCE, ENGINEERING -> 8000;
            case MEDICINE, HEALTH -> 9000;
            default -> 7000;
        };
        // Add 500 ms per grade level (higher grades → more nuance → longer animations)
        return base + (gradeLevel * 500);
    }
}
