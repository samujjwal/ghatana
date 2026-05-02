package com.ghatana.digitalmarketing.application.sow;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.sow.SowClause;
import com.ghatana.digitalmarketing.domain.sow.SowDraft;
import com.ghatana.digitalmarketing.domain.sow.SowRiskFlag;
import com.ghatana.digitalmarketing.domain.sow.SowRiskType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic SOW draft generation service implementation.
 *
 * <p>Assembles a versioned Statement of Work from approved clause library entries.
 * Risk flags are generated automatically based on clause content analysis and
 * scope completeness. All drafts carry the mandatory legal disclaimer and require
 * human approval before export.</p>
 *
 * @doc.type class
 * @doc.purpose SOW draft generator for DMOS F1-016
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SowServiceImpl implements SowService {

    private static final Logger LOG = LoggerFactory.getLogger(SowServiceImpl.class);

    /** Model version stamped into every generated SOW draft. */
    public static final String MODEL_VERSION = "v1.0";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final SowClauseRepository clauseRepository;
    private final SowDraftRepository draftRepository;

    public SowServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            SowClauseRepository clauseRepository,
            SowDraftRepository draftRepository) {
        this.kernelAdapter   = Objects.requireNonNull(kernelAdapter,   "kernelAdapter must not be null");
        this.clauseRepository = Objects.requireNonNull(clauseRepository, "clauseRepository must not be null");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository must not be null");
    }

    @Override
    public Promise<SowDraft> generateDraft(DmOperationContext ctx, GenerateSowCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "sow", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                            new SecurityException("Actor not authorized to generate a SOW draft"));
                }
                return clauseRepository.findApprovedClauses()
                    .then(clauses -> {
                        List<SowRiskFlag> flags = generateRiskFlags(clauses, command);
                        SowDraft draft = buildDraft(ctx, command, clauses, flags);
                        return draftRepository.save(draft)
                            .then(saved -> kernelAdapter.recordAudit(
                                    ctx, saved.getSowId(), "sow-generated",
                                    Map.of("proposalId", command.proposalId(),
                                           "templateVersion", command.templateVersion()))
                                .map(auditId -> {
                                    LOG.info("[DMOS] SOW draft generated sowId={} auditId={}",
                                             saved.getSowId(), auditId);
                                    return saved;
                                }));
                    });
            });
    }

    @Override
    public Promise<SowDraft> getDraft(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "sow", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                            new SecurityException("Actor not authorized to read SOW drafts"));
                }
                return draftRepository.findLatestByWorkspace(ctx.getWorkspaceId())
                    .map(opt -> opt.orElseThrow(() -> new NoSuchElementException(
                            "No SOW draft found for workspace " + ctx.getWorkspaceId().getValue())));
            });
    }

    @Override
    public Promise<SowDraft> submitForReview(DmOperationContext ctx, String sowId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(sowId, "sowId must not be null");

        return kernelAdapter.isAuthorized(ctx, "sow", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                            new SecurityException("Actor not authorized to submit SOW for review"));
                }
                return draftRepository.findById(sowId)
                    .then(opt -> {
                        SowDraft draft = opt.orElseThrow(() ->
                                new NoSuchElementException("SOW draft not found: " + sowId));
                        SowDraft submitted = draft.submitForReview();
                        return draftRepository.save(submitted)
                            .then(saved -> kernelAdapter.recordAudit(
                                    ctx, saved.getSowId(), "sow-submitted", Map.of())
                                .map(auditId -> saved));
                    });
            });
    }

    @Override
    public Promise<SowDraft> approveDraft(DmOperationContext ctx, String sowId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(sowId, "sowId must not be null");

        return kernelAdapter.isAuthorized(ctx, "sow", "approve")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                            new SecurityException("Actor not authorized to approve SOW drafts"));
                }
                return draftRepository.findById(sowId)
                    .then(opt -> {
                        SowDraft draft = opt.orElseThrow(() ->
                                new NoSuchElementException("SOW draft not found: " + sowId));
                        SowDraft approved = draft.approve(
                                ctx.getActor().getPrincipalId(), Instant.now());
                        return draftRepository.save(approved)
                            .then(saved -> kernelAdapter.recordAudit(
                                    ctx, saved.getSowId(), "sow-approved", Map.of())
                                .map(auditId -> saved));
                    });
            });
    }

    @Override
    public Promise<SowDraft> exportDraft(DmOperationContext ctx, String sowId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(sowId, "sowId must not be null");

        return kernelAdapter.isAuthorized(ctx, "sow", "approve")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                            new SecurityException("Actor not authorized to export SOW drafts"));
                }
                return draftRepository.findById(sowId)
                    .then(opt -> {
                        SowDraft draft = opt.orElseThrow(() ->
                                new NoSuchElementException("SOW draft not found: " + sowId));
                        SowDraft exported = draft.export();
                        return draftRepository.save(exported)
                            .then(saved -> kernelAdapter.recordAudit(
                                    ctx, saved.getSowId(), "sow-exported", Map.of())
                                .map(auditId -> saved));
                    });
            });
    }

    // ---- risk flag generation ----

    private List<SowRiskFlag> generateRiskFlags(List<SowClause> clauses, GenerateSowCommand command) {
        List<SowRiskFlag> flags = new ArrayList<>();

        for (SowClause clause : clauses) {
            if (!clause.isApproved()) {
                flags.add(new SowRiskFlag(
                        SowRiskType.MISSING_APPROVAL,
                        "Clause '" + clause.clauseId() + "' (type=" + clause.clauseType()
                                + ") has not been formally approved.",
                        "BLOCKER"));
            }
            String lower = clause.content().toLowerCase();
            if (lower.contains("guarantee") || lower.contains("warrant")) {
                flags.add(new SowRiskFlag(
                        SowRiskType.UNSUPPORTED_GUARANTEE,
                        "Clause '" + clause.clauseId() + "' contains guarantee or warranty language.",
                        "WARNING"));
            }
            if (lower.contains("personal data") || lower.contains("pii")) {
                flags.add(new SowRiskFlag(
                        SowRiskType.PRIVACY_ISSUE,
                        "Clause '" + clause.clauseId() + "' references personal data or PII.",
                        "WARNING"));
            }
        }

        String assumptionText = command.assumptions().trim();
        if (assumptionText.isEmpty()) {
            flags.add(new SowRiskFlag(
                    SowRiskType.AMBIGUOUS_DELIVERABLE,
                    "No project assumptions were provided. Deliverable scope may be ambiguous.",
                    "WARNING"));
        }

        return flags;
    }

    // ---- draft assembly ----

    private SowDraft buildDraft(
            DmOperationContext ctx,
            GenerateSowCommand command,
            List<SowClause> clauses,
            List<SowRiskFlag> flags) {

        return SowDraft.builder()
                .sowId(UUID.randomUUID().toString())
                .workspaceId(ctx.getWorkspaceId())
                .proposalId(command.proposalId())
                .templateVersion(command.templateVersion())
                .selectedClauses(clauses)
                .riskFlags(flags)
                .assumptions(command.assumptions().isBlank()
                        ? "Standard 30-day campaign; local service market; MVP channels only."
                        : command.assumptions())
                .exclusions(command.exclusions().isBlank()
                        ? "Paid media buying, creative production, and third-party tool licensing."
                        : command.exclusions())
                .disclaimer(SowDraft.LEGAL_DISCLAIMER)
                .modelVersion(MODEL_VERSION)
                .status(com.ghatana.digitalmarketing.domain.sow.SowStatus.DRAFT)
                .createdAt(Instant.now())
                .build();
    }
}
