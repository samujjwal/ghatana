package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.budget.BudgetChannelAllocation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendationStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic budget recommendation service implementation.
 *
 * <p>Allocates the total monthly cap across three MVP channels using a fixed 70/20/10
 * split and derives per-channel daily caps from a 30-day month. Guardrails include a
 * configurable re-approval threshold and hard cap enforcement enforced at command time.</p>
 *
 * @doc.type class
 * @doc.purpose Deterministic budget recommendation generator for DMOS F1-014
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BudgetRecommendationServiceImpl implements BudgetRecommendationService {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetRecommendationServiceImpl.class);

    static final String MODEL_VERSION = "v1.0";
    private static final double MAX_BUDGET_RECOMMENDATION_RISK_SCORE = 0.85d;
    private static final String BUDGET_RECOMMENDATION_RISK_MODEL = "DM_BUDGET_RECOMMENDATION";

    /** MVP channel split percentages. */
    private static final double SEARCH_PCT = 0.70;
    private static final double LANDING_PCT = 0.20;
    private static final double EMAIL_PCT = 0.10;

    /** Days in the recommendation period used to derive daily caps. */
    private static final int PERIOD_DAYS = 30;

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final BudgetRecommendationRepository repository;

    public BudgetRecommendationServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            BudgetRecommendationRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<BudgetRecommendation> recommendBudget(
            DmOperationContext ctx,
            BudgetRecommendationService.GenerateBudgetCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-recommendation", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to generate a budget recommendation"));
                }
                BudgetRecommendation rec = buildRecommendation(ctx, command);
                return kernelAdapter.evaluateRisk(
                        ctx,
                        rec.getRecommendationId(),
                        BUDGET_RECOMMENDATION_RISK_MODEL,
                        Map.of(
                            "totalMonthlyCap", rec.getTotalMonthlyCap(),
                            "changeThresholdPct", rec.getChangeThresholdPct()
                        )
                    )
                    .then(score -> {
                        if (score > MAX_BUDGET_RECOMMENDATION_RISK_SCORE) {
                            return Promise.ofException(new SecurityException(
                                "Budget recommendation risk score " + score
                                    + " exceeds threshold " + MAX_BUDGET_RECOMMENDATION_RISK_SCORE
                            ));
                        }

                        return repository.save(rec)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getRecommendationId(),
                                    "budget-recommendation-generated",
                                    Map.of(
                                        "totalMonthlyCap", Double.toString(saved.getTotalMonthlyCap()),
                                        "modelVersion", MODEL_VERSION,
                                        "riskModel", BUDGET_RECOMMENDATION_RISK_MODEL,
                                        "riskScore", Double.toString(score)
                                    ))
                                .map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<BudgetRecommendation> getLatestRecommendation(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-recommendation", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to read budget recommendation"));
                }
                return repository.findLatestByWorkspace(ctx.getWorkspaceId())
                    .map(opt -> opt.orElseThrow(
                        () -> new NoSuchElementException(
                            "No budget recommendation found for workspace: "
                            + ctx.getWorkspaceId().getValue())));
            });
    }

    @Override
    public Promise<BudgetRecommendation> submitForApproval(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-recommendation", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to submit budget recommendation"));
                }
                return repository.findById(recommendationId)
                    .then(opt -> {
                        BudgetRecommendation rec = opt.orElseThrow(
                            () -> new NoSuchElementException(
                                "Budget recommendation not found: " + recommendationId));
                        BudgetRecommendation pending = rec.submitForApproval();
                        return repository.save(pending)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getRecommendationId(),
                                    "budget-recommendation-submitted", Map.of())
                                .map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<BudgetRecommendation> approveRecommendation(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-recommendation", "approve")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to approve budget recommendation"));
                }
                return repository.findById(recommendationId)
                    .then(opt -> {
                        BudgetRecommendation rec = opt.orElseThrow(
                            () -> new NoSuchElementException(
                                "Budget recommendation not found: " + recommendationId));
                        String approver = ctx.getActor().getPrincipalId();
                        BudgetRecommendation approved = rec.approve(approver, Instant.now());
                        LOG.info("Budget recommendation approved: id={} by={}", recommendationId, approver);
                        return repository.save(approved)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getRecommendationId(),
                                    "budget-recommendation-approved",
                                    Map.of("approvedBy", approver))
                                .map(__ -> saved));
                    });
            });
    }

    // ---- deterministic recommendation builder ----

    private static BudgetRecommendation buildRecommendation(
            DmOperationContext ctx,
            BudgetRecommendationService.GenerateBudgetCommand command) {
        double cap = command.totalMonthlyCap();
        double searchAmt  = Math.round(cap * SEARCH_PCT  * 100.0) / 100.0;
        double landingAmt = Math.round(cap * LANDING_PCT * 100.0) / 100.0;
        double emailAmt   = cap - searchAmt - landingAmt;

        double searchDaily  = Math.round(searchAmt  / PERIOD_DAYS * 100.0) / 100.0;
        double landingDaily = Math.round(landingAmt / PERIOD_DAYS * 100.0) / 100.0;
        double emailDaily   = Math.round(emailAmt   / PERIOD_DAYS * 100.0) / 100.0;

        List<BudgetChannelAllocation> allocations = List.of(
            new BudgetChannelAllocation(
                "GOOGLE_SEARCH", searchAmt, searchDaily,
                "70% of total cap allocated to paid search for lead acquisition"),
            new BudgetChannelAllocation(
                "LANDING_PAGE", landingAmt, landingDaily,
                "20% of total cap for landing page hosting, optimization, and testing"),
            new BudgetChannelAllocation(
                "EMAIL_FOLLOW_UP", emailAmt, emailDaily,
                "10% of total cap for email sequence delivery and list management")
        );

        String rationale = cap > 0
            ? "Recommended $" + cap + "/month based on strategy budget cap; 70/20/10 channel split for MVP."
            : "Zero-budget recommendation: no spend authorized until budget increased.";

        return BudgetRecommendation.builder()
            .recommendationId(UUID.randomUUID().toString())
            .workspaceId(ctx.getWorkspaceId())
            .strategyId(command.strategyId())
            .totalMonthlyCap(cap)
            .changeThresholdPct(command.changeThreshold())
            .channelAllocations(allocations)
            .rationale(rationale)
            .assumptions("30-day period; local service market; MVP channels only.")
            .modelVersion(MODEL_VERSION)
            .status(BudgetRecommendationStatus.DRAFT)
            .generatedAt(Instant.now())
            .generatedBy(ctx.getActor().getPrincipalId())
            .build();
    }
}
