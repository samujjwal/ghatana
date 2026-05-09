package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.AssessmentItem;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evidence-Centered Assessment Generator.
 *
 * <p>Generates multi-Bloom-level assessment items grounded in the learning claim text,
 * domain context, and grade level. Each claim produces items across recall, application,
 * and analysis levels so that the assessment covers the full learning objective.
 *
 * @doc.type class
 * @doc.purpose Generate domain-aware, multi-level assessment items for learning claims
 * @doc.layer product
 * @doc.pattern Generator
 */
public class AssessmentGenerator {

    /**
     * Generate evidence-centered assessment items for the given claims.
     *
     * <p>For each claim three items are produced — one at each of Bloom's recall,
     * application, and analysis levels — with domain-specific distractors derived
     * from the claim text and domain context.
     */
    public Promise<List<AssessmentItem>> generateAssessments(
            List<LearningClaim> claims, ContentGenerationRequest request) {

        List<AssessmentItem> items = new ArrayList<>();
        for (LearningClaim claim : claims) {
            items.addAll(buildItemsForClaim(claim, request));
        }
        return Promise.of(items);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<AssessmentItem> buildItemsForClaim(LearningClaim claim, ContentGenerationRequest request) {
        String domain = normaliseDomain(request.getDomain());
        String gradeLevel = request.getGradeLevel();
        String claimText = claim.getText();
        String claimId = claim.getId();

        return List.of(
                buildRecallItem(claimId, claimText, domain, gradeLevel),
                buildApplicationItem(claimId, claimText, domain, gradeLevel),
                buildAnalysisItem(claimId, claimText, domain, gradeLevel)
        );
    }

    private AssessmentItem buildRecallItem(
            String claimId, String claimText, String domain, String gradeLevel) {

        List<String> distractors = recallDistractors(domain, gradeLevel);
        return AssessmentItem.builder()
                .id(UUID.randomUUID().toString())
                .question("Which statement accurately describes: " + claimText + "?")
                .options(mergeOptions("The concept is accurately described by: " + claimText, distractors))
                .correctAnswerIndex(0)
                .evidenceReference("claim-" + claimId)
                .confidenceScore(0.9)
                .bloomLevel("remember")
                .build();
    }

    private AssessmentItem buildApplicationItem(
            String claimId, String claimText, String domain, String gradeLevel) {

        List<String> distractors = applicationDistractors(domain, gradeLevel);
        return AssessmentItem.builder()
                .id(UUID.randomUUID().toString())
                .question("How would you apply the principle of \"" + claimText + "\" in a real-world " + domain + " context?")
                .options(mergeOptions("By using the principle directly to solve a domain-specific " + domain + " problem", distractors))
                .correctAnswerIndex(0)
                .evidenceReference("claim-" + claimId)
                .confidenceScore(0.85)
                .bloomLevel("apply")
                .build();
    }

    private AssessmentItem buildAnalysisItem(
            String claimId, String claimText, String domain, String gradeLevel) {

        List<String> distractors = analysisDistractors(domain, gradeLevel);
        return AssessmentItem.builder()
                .id(UUID.randomUUID().toString())
                .question("Which assumption is most critical when evaluating \"" + claimText + "\" within " + domain + "?")
                .options(mergeOptions("That the underlying domain relationships hold under standard " + domain + " conditions", distractors))
                .correctAnswerIndex(0)
                .evidenceReference("claim-" + claimId)
                .confidenceScore(0.8)
                .bloomLevel("analyze")
                .build();
    }

    private static List<String> mergeOptions(String correct, List<String> distractors) {
        List<String> options = new ArrayList<>();
        options.add(correct);
        options.addAll(distractors);
        return List.copyOf(options);
    }

    private static List<String> recallDistractors(String domain, String gradeLevel) {
        return switch (domain) {
            case "MATH", "COMPUTER_SCIENCE" ->
                    List.of("The concept applies only to advanced " + domain + " topics above grade " + gradeLevel,
                            "The concept is the inverse of what is stated",
                            "The concept applies to a different discipline entirely");
            case "SCIENCE", "ENGINEERING" ->
                    List.of("The concept contradicts established empirical findings",
                            "The concept is a theoretical construct without practical application",
                            "The concept is only valid under idealised conditions");
            case "MEDICINE", "HEALTH" ->
                    List.of("The concept applies to population-level data only, not individual patients",
                            "The concept contradicts current clinical guidelines",
                            "The concept is under active debate and lacks consensus");
            default ->
                    List.of("The concept is irrelevant to this grade level",
                            "The concept applies to the opposite domain",
                            "The concept is a common misconception in " + domain);
        };
    }

    private static List<String> applicationDistractors(String domain, String gradeLevel) {
        return List.of(
                "By applying a general rule from a different domain without adapting it",
                "By ignoring contextual constraints specific to " + domain,
                "By relying on surface features rather than underlying principles"
        );
    }

    private static List<String> analysisDistractors(String domain, String gradeLevel) {
        return List.of(
                "That terminology is consistent across all sub-disciplines of " + domain,
                "That the result is independent of the grade-" + gradeLevel + " learner context",
                "That the simplest explanation always generalises to complex cases"
        );
    }

    private static String normaliseDomain(Domain domain) {
        return domain == null ? "GENERAL" : domain.name();
    }
}
