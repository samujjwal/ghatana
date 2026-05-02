package com.ghatana.digitalmarketing.application.scoring;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.scoring.LeadGrade;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import com.ghatana.digitalmarketing.domain.scoring.ScoreDimension;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link LeadScoringService}.
 *
 * <p>Scoring is fully deterministic based on observable, non-protected attributes:
 * intake completion, audit finding severity, tracking gaps, keyword opportunities, and budget.
 * No protected attributes (demographics, location bias, etc.) are used.
 *
 * @doc.type class
 * @doc.purpose DMOS deterministic lead scoring service for F1-012
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class LeadScoringServiceImpl implements LeadScoringService {

    /** Current model version — increment when scoring logic changes. */
    static final String MODEL_VERSION = "v1.0";

    /** Confidence threshold below which human review is required. */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final LeadScoreRepository repository;

    public LeadScoringServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            LeadScoreRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<LeadScore> generateScore(DmOperationContext ctx, GenerateLeadScoreCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "lead-score/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to generate lead score"));
                }

                List<ScoreDimension> dimensions = buildDimensions(command);
                int totalScore = dimensions.stream().mapToInt(ScoreDimension::points).sum();
                int cappedScore = Math.min(totalScore, 100);
                LeadGrade grade = deriveGrade(cappedScore);
                double confidence = deriveConfidence(command);
                boolean needsReview = confidence < LOW_CONFIDENCE_THRESHOLD;
                String nextAction = recommendNextAction(grade, needsReview);

                LeadScore score = LeadScore.builder()
                    .scoreId(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .score(cappedScore)
                    .grade(grade)
                    .dimensions(dimensions)
                    .confidence(confidence)
                    .requiresHumanReview(needsReview)
                    .recommendedNextAction(nextAction)
                    .modelVersion(MODEL_VERSION)
                    .scoredAt(Instant.now())
                    .scoredBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(score)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "lead-score/" + saved.getScoreId(),
                        "lead-score-generated",
                        Map.of(
                            "score", Integer.toString(saved.getScore()),
                            "grade", saved.getGrade().name(),
                            "modelVersion", saved.getModelVersion(),
                            "requiresHumanReview", Boolean.toString(saved.isRequiresHumanReview())
                        )
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<LeadScore> getLatestScore(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "lead-score/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read lead score"));
                }
                return repository.findLatestByWorkspace(ctx.getWorkspaceId());
            });
    }

    // ---- deterministic scoring helpers ----

    private static List<ScoreDimension> buildDimensions(GenerateLeadScoreCommand cmd) {
        List<ScoreDimension> dims = new ArrayList<>();

        // Dimension 1: intake completeness (max 30 pts)
        int intakePts = (int) Math.round(cmd.intakeCompletionPct() * 0.30);
        dims.add(new ScoreDimension(
                "intake-completeness",
                intakePts,
                "Intake questionnaire " + cmd.intakeCompletionPct() + "% complete"));

        // Dimension 2: audit findings (max 20 pts — more findings means stronger need)
        int auditPts = Math.min(cmd.auditFindingCount() * 4, 20);
        dims.add(new ScoreDimension(
                "audit-finding-urgency",
                auditPts,
                cmd.auditFindingCount() + " website audit finding(s) indicate digital improvement need"));

        // Dimension 3: tracking gaps (10 pts if gaps detected)
        int trackingPts = cmd.trackingGapsDetected() ? 10 : 0;
        dims.add(new ScoreDimension(
                "tracking-gaps",
                trackingPts,
                cmd.trackingGapsDetected() ? "Tracking gaps detected — immediate fix opportunity" : "No tracking gaps"));

        // Dimension 4: keyword opportunity (max 20 pts)
        int kwPts = Math.min(cmd.keywordOpportunityCount() * 4, 20);
        dims.add(new ScoreDimension(
                "keyword-opportunity",
                kwPts,
                cmd.keywordOpportunityCount() + " keyword opportunit(ies) identified"));

        // Dimension 5: budget fit (max 20 pts)
        int budgetPts = deriveBudgetPoints(cmd.monthlyBudgetHint());
        dims.add(new ScoreDimension(
                "budget-fit",
                budgetPts,
                "Monthly budget hint $" + cmd.monthlyBudgetHint()));

        return dims;
    }

    private static int deriveBudgetPoints(int monthlyBudget) {
        if (monthlyBudget >= 3000) {
            return 20;
        }
        if (monthlyBudget >= 1500) {
            return 14;
        }
        if (monthlyBudget >= 500) {
            return 8;
        }
        return 2;
    }

    private static LeadGrade deriveGrade(int score) {
        if (score >= 80) {
            return LeadGrade.A;
        }
        if (score >= 60) {
            return LeadGrade.B;
        }
        if (score >= 40) {
            return LeadGrade.C;
        }
        return LeadGrade.D;
    }

    private static double deriveConfidence(GenerateLeadScoreCommand cmd) {
        // Confidence is proportional to data completeness
        int signals = 0;
        if (cmd.intakeCompletionPct() >= 80) {
            signals++;
        }
        if (cmd.auditFindingCount() > 0) {
            signals++;
        }
        if (cmd.keywordOpportunityCount() > 0) {
            signals++;
        }
        if (cmd.monthlyBudgetHint() > 0) {
            signals++;
        }
        return signals / 4.0;
    }

    private static String recommendNextAction(LeadGrade grade, boolean needsReview) {
        if (needsReview) {
            return "Escalate for human review — insufficient data for confident scoring";
        }
        return switch (grade) {
            case A -> "Generate proposal immediately";
            case B -> "Send personalised outreach and proposal outline";
            case C -> "Add to nurture sequence";
            case D -> "Deprioritise — does not meet current fit threshold";
        };
    }
}
