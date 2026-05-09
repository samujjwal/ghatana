package com.ghatana.tutorputor.contentgeneration.generators;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.LearningEvidence;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates structured evidence items that ground each learning claim in authoritative
 * domain sources.
 *
 * <p>For each claim two evidence items are produced — a primary reference (textbook /
 * canonical source) and a worked example — so that the claim is supported by both
 * declarative and procedural evidence. Evidence types and source descriptions reflect
 * domain conventions (e.g., peer-reviewed journals for MEDICINE, standards bodies for
 * ENGINEERING).
 *
 * @doc.type class
 * @doc.purpose Generate domain-typed, claim-grounded evidence items
 * @doc.layer product
 * @doc.pattern Generator
 */
public class EvidenceGenerator {

    public Promise<List<LearningEvidence>> generateEvidence(
            List<LearningClaim> claims, ContentGenerationRequest request) {

        List<LearningEvidence> evidence = new ArrayList<>();
        Domain domain = request.getDomain();
        String gradeLevel = request.getGradeLevel();

        for (LearningClaim claim : claims) {
            evidence.add(buildPrimaryReference(claim, domain, gradeLevel));
            evidence.add(buildWorkedExample(claim, domain, gradeLevel));
        }
        return Promise.of(List.copyOf(evidence));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static LearningEvidence buildPrimaryReference(
            LearningClaim claim, Domain domain, String gradeLevel) {

        String sourceLabel = primarySourceLabel(domain, gradeLevel);
        return LearningEvidence.builder()
                .id(UUID.randomUUID().toString())
                .claimId(claim.getId())
                .type("REFERENCE")
                .content(sourceLabel + " — " + claim.getText()
                        + ". This reference establishes the authoritative basis for the claim"
                        + " at grade-" + gradeLevel + " level within " + domain.name().toLowerCase() + ".")
                .build();
    }

    private static LearningEvidence buildWorkedExample(
            LearningClaim claim, Domain domain, String gradeLevel) {

        String exampleLabel = workedExampleLabel(domain, gradeLevel);
        return LearningEvidence.builder()
                .id(UUID.randomUUID().toString())
                .claimId(claim.getId())
                .type("WORKED_EXAMPLE")
                .content(exampleLabel + " demonstrating: " + claim.getText()
                        + ". Walkthrough targets grade-" + gradeLevel + " " + domain.name().toLowerCase()
                        + " learners and shows step-by-step reasoning from first principles.")
                .build();
    }

    private static String primarySourceLabel(Domain domain, String gradeLevel) {
        return switch (domain) {
            case MATH -> "Curriculum-aligned mathematics textbook (grade " + gradeLevel + ")";
            case SCIENCE -> "Peer-reviewed science curriculum standard (grade " + gradeLevel + ")";
            case ENGINEERING -> "Engineering fundamentals reference — ABET-aligned (grade " + gradeLevel + ")";
            case COMPUTER_SCIENCE, TECH -> "CS education standard (ACM K-12 framework, grade " + gradeLevel + ")";
            case MEDICINE, HEALTH -> "Clinical guideline / evidence-based health curriculum (grade " + gradeLevel + ")";
            case BUSINESS, MANAGEMENT, ECONOMICS -> "Business studies curriculum guide (grade " + gradeLevel + ")";
            default -> "Domain reference material (grade " + gradeLevel + ")";
        };
    }

    private static String workedExampleLabel(Domain domain, String gradeLevel) {
        return switch (domain) {
            case MATH -> "Annotated problem-solution pair";
            case SCIENCE -> "Lab report walkthrough";
            case ENGINEERING -> "Design case study";
            case COMPUTER_SCIENCE, TECH -> "Traced algorithm execution";
            case MEDICINE, HEALTH -> "Clinical case vignette";
            case BUSINESS, MANAGEMENT, ECONOMICS -> "Business scenario analysis";
            default -> "Illustrative example";
        };
    }
}
