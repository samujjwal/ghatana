package com.ghatana.digitalmarketing.application.proposal;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.proposal.Proposal;
import com.ghatana.digitalmarketing.domain.proposal.ProposalDeliverable;
import com.ghatana.digitalmarketing.domain.proposal.PricingOption;
import com.ghatana.digitalmarketing.domain.proposal.ProposalStatus;
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
 * Deterministic proposal generation service implementation.
 *
 * <p>Builds a versioned proposal from an approved strategy, bundling standard
 * deliverables, pricing options, and legal disclaimers sourced from the
 * specified template. Proposals start in {@code DRAFT} status and require
 * human review before client delivery.</p>
 *
 * @doc.type class
 * @doc.purpose Proposal generator for DMOS F1-015
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ProposalServiceImpl implements ProposalService {

    private static final Logger LOG = LoggerFactory.getLogger(ProposalServiceImpl.class);

    /** Model version string stamped into every generated proposal. */
    public static final String MODEL_VERSION = "v1.0";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ProposalRepository repository;

    public ProposalServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ProposalRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<Proposal> generateProposal(DmOperationContext ctx, GenerateProposalCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "proposal", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to generate a proposal"));
                }
                Proposal proposal = buildProposal(ctx, command);
                return repository.save(proposal)
                    .then(saved -> kernelAdapter.recordAudit(ctx, saved.getProposalId(),
                            "proposal-generated",
                            Map.of("strategyId", saved.getStrategyId(),
                                   "templateId", saved.getTemplateId(),
                                   "templateVersion", saved.getTemplateVersion(),
                                   "modelVersion", MODEL_VERSION))
                        .map(__ -> saved));
            });
    }

    @Override
    public Promise<Proposal> getProposal(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "proposal", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to read proposal"));
                }
                return repository.findLatestByWorkspace(ctx.getWorkspaceId())
                    .map(opt -> opt.orElseThrow(
                        () -> new NoSuchElementException(
                            "No proposal found for workspace: "
                            + ctx.getWorkspaceId().getValue())));
            });
    }

    @Override
    public Promise<Proposal> submitForReview(DmOperationContext ctx, String proposalId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(proposalId, "proposalId must not be null");

        return kernelAdapter.isAuthorized(ctx, "proposal", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to submit proposal for review"));
                }
                return repository.findById(proposalId)
                    .then(opt -> {
                        Proposal proposal = opt.orElseThrow(
                            () -> new NoSuchElementException(
                                "Proposal not found: " + proposalId));
                        Proposal submitted = proposal.submitForReview();
                        return repository.save(submitted)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getProposalId(),
                                    "proposal-submitted", Map.of())
                                .map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Proposal> approveProposal(DmOperationContext ctx, String proposalId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(proposalId, "proposalId must not be null");

        return kernelAdapter.isAuthorized(ctx, "proposal", "approve")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to approve proposal"));
                }
                return repository.findById(proposalId)
                    .then(opt -> {
                        Proposal proposal = opt.orElseThrow(
                            () -> new NoSuchElementException(
                                "Proposal not found: " + proposalId));
                        String approver = ctx.getActor().getPrincipalId();
                        Proposal approved = proposal.approve(approver, Instant.now());
                        LOG.info("Proposal approved: id={} by={}", proposalId, approver);
                        return repository.save(approved)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getProposalId(),
                                    "proposal-approved",
                                    Map.of("approvedBy", approver))
                                .map(__ -> saved));
                    });
            });
    }

    // ---- deterministic proposal builder ----

    private static Proposal buildProposal(DmOperationContext ctx, GenerateProposalCommand command) {
        List<ProposalDeliverable> deliverables = buildDeliverables(command.strategyId());
        List<PricingOption> pricingOptions = buildPricingOptions(command.strategyId());

        String disclaimer =
            "Results are estimates based on historical benchmarks and are not guaranteed. "
            + "Actual performance may vary based on market conditions, ad quality, and competition.";
        String exclusions =
            "Excludes creative production, third-party software licensing, stock photography, "
            + "video production, and out-of-scope platform fees.";
        String measurementPlan =
            "Monthly KPI reviews covering impressions, clicks, CTR, conversions, and ROI. "
            + "Weekly optimization check-ins with client stakeholders.";
        String timeline = "30-day onboarding; full campaign active by day 14.";
        String rationale =
            "Proposal generated from strategy " + command.strategyId()
            + " using template " + command.templateId() + " v" + command.templateVersion() + ".";

        return Proposal.builder()
            .proposalId(UUID.randomUUID().toString())
            .workspaceId(ctx.getWorkspaceId())
            .strategyId(command.strategyId())
            .templateId(command.templateId())
            .templateVersion(command.templateVersion())
            .deliverables(deliverables)
            .pricingOptions(pricingOptions)
            .assumptions(command.assumptions().isBlank()
                ? "Standard 30-day campaign period; local service market; MVP channels only."
                : command.assumptions())
            .timeline(timeline)
            .rationale(rationale)
            .disclaimer(disclaimer)
            .exclusions(exclusions)
            .measurementPlan(measurementPlan)
            .modelVersion(MODEL_VERSION)
            .status(ProposalStatus.DRAFT)
            .generatedAt(Instant.now())
            .generatedBy(ctx.getActor().getPrincipalId())
            .build();
    }

    private static List<ProposalDeliverable> buildDeliverables(String strategyId) {
        return List.of(
            new ProposalDeliverable(
                "GOOGLE_SEARCH_CAMPAIGN",
                "Google Search ad campaign targeting high-intent keywords for " + strategyId,
                14, "campaign", 1),
            new ProposalDeliverable(
                "LANDING_PAGE_OPTIMIZATION",
                "Landing page setup and A/B testing for lead conversion",
                10, "page", 2),
            new ProposalDeliverable(
                "EMAIL_FOLLOW_UP_SEQUENCE",
                "Automated 5-step email nurture sequence for captured leads",
                7, "sequence", 1)
        );
    }

    private static List<PricingOption> buildPricingOptions(String strategyId) {
        return List.of(
            new PricingOption(
                "MONTHLY_RETAINER",
                2500.00,
                "USD",
                "Monthly management retainer covering all deliverables for strategy " + strategyId),
            new PricingOption(
                "ONE_TIME_SETUP",
                750.00,
                "USD",
                "One-time onboarding, account setup, and initial campaign configuration fee")
        );
    }
}
