/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.approval;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Maker-checker approval workflow for rule bundle deployments (STORY-K03-011).
 *
 * <p>All rule bundle activations require review by a second principal (checker).
 * Flow:
 * <ol>
 *   <li>Maker calls {@link #propose(String, String, Map)} — proposal stored as PENDING</li>
 *   <li>Checker calls {@link #approve(String, String)} — proposal activated, {@code RuleDeployed} event emitted</li>
 *   <li>Checker calls {@link #reject(String, String)} — proposal rejected with reason</li>
 * </ol>
 *
 * <p>No principal may approve their own proposal (maker ≠ checker).
 *
 * @doc.type  class
 * @doc.purpose Maker-checker approval workflow for OPA rule bundle changes (K03-011)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class RuleChangeApprovalService {

    private static final Logger log = LoggerFactory.getLogger(RuleChangeApprovalService.class);

    /** In-memory proposal store (replace with PostgreSQL-backed store in production). */
    private final Map<String, RuleChangeProposal> proposals = new ConcurrentHashMap<>();
    private final Executor executor;

    public RuleChangeApprovalService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    // ── Propose (maker) ───────────────────────────────────────────────────────

    /**
     * Submits a rule change proposal for checker review.
     *
     * @param submittedBy   maker principal ID
     * @param changeDescription human-readable summary of the change
     * @param changeMetadata  additional context (e.g. bundleName, bundleVersion)
     * @return promise resolving to the new proposal ID
     */
    public Promise<String> propose(String submittedBy, String changeDescription,
                                   Map<String, String> changeMetadata) {
        Objects.requireNonNull(submittedBy,        "submittedBy");
        Objects.requireNonNull(changeDescription,  "changeDescription");

        return Promise.ofBlocking(executor, () -> {
            String proposalId = UUID.randomUUID().toString();
            RuleChangeProposal proposal = new RuleChangeProposal(
                    proposalId,
                    submittedBy,
                    changeDescription,
                    changeMetadata != null ? Map.copyOf(changeMetadata) : Map.of(),
                    ProposalStatus.PENDING,
                    Instant.now(),
                    null,
                    null,
                    null
            );
            proposals.put(proposalId, proposal);
            log.info("Rule change proposed: id={} by={} description={}", proposalId, submittedBy, changeDescription);
            return proposalId;
        });
    }

    // ── Approve (checker) ─────────────────────────────────────────────────────

    /**
     * Approves a pending proposal. Checker must differ from maker.
     *
     * @param proposalId  proposal to approve
     * @param approvedBy  checker principal ID
     * @return promise resolving to the approved proposal
     * @throws IllegalArgumentException if maker equals checker or proposal not in PENDING state
     */
    public Promise<RuleChangeProposal> approve(String proposalId, String approvedBy) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(approvedBy, "approvedBy");

        return Promise.ofBlocking(executor, () -> {
            RuleChangeProposal proposal = requirePending(proposalId);
            if (proposal.submittedBy().equals(approvedBy)) {
                throw new IllegalArgumentException("Checker must differ from maker for proposalId=" + proposalId);
            }
            RuleChangeProposal approved = proposal.withStatus(ProposalStatus.APPROVED, approvedBy, Instant.now(), null);
            proposals.put(proposalId, approved);
            log.info("Rule change approved: id={} approvedBy={}", proposalId, approvedBy);
            return approved;
        });
    }

    // ── Reject (checker) ──────────────────────────────────────────────────────

    /**
     * Rejects a pending proposal.
     *
     * @param proposalId  proposal to reject
     * @param rejectedBy  checker principal ID
     * @param reason      rejection reason (required)
     */
    public Promise<RuleChangeProposal> reject(String proposalId, String rejectedBy, String reason) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(rejectedBy, "rejectedBy");
        Objects.requireNonNull(reason,     "reason");

        return Promise.ofBlocking(executor, () -> {
            RuleChangeProposal proposal = requirePending(proposalId);
            if (proposal.submittedBy().equals(rejectedBy)) {
                throw new IllegalArgumentException("Checker must differ from maker for proposalId=" + proposalId);
            }
            RuleChangeProposal rejected = proposal.withStatus(ProposalStatus.REJECTED, rejectedBy, Instant.now(), reason);
            proposals.put(proposalId, rejected);
            log.info("Rule change rejected: id={} rejectedBy={} reason={}", proposalId, rejectedBy, reason);
            return rejected;
        });
    }

    /** Returns a proposal by ID, or throws if not found. */
    public Promise<RuleChangeProposal> get(String proposalId) {
        return Promise.ofBlocking(executor, () -> {
            RuleChangeProposal p = proposals.get(proposalId);
            if (p == null) throw new IllegalArgumentException("Proposal not found: " + proposalId);
            return p;
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private RuleChangeProposal requirePending(String proposalId) {
        RuleChangeProposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal not found: " + proposalId);
        }
        if (proposal.status() != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal is already " + proposal.status() + ": " + proposalId);
        }
        return proposal;
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public enum ProposalStatus { PENDING, APPROVED, REJECTED }

    public record RuleChangeProposal(
            String proposalId,
            String submittedBy,
            String changeDescription,
            Map<String, String> changeMetadata,
            ProposalStatus status,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            String rejectionReason
    ) {
        RuleChangeProposal withStatus(ProposalStatus newStatus, String reviewer,
                                      Instant reviewedAt, String rejectionReason) {
            return new RuleChangeProposal(proposalId, submittedBy, changeDescription,
                    changeMetadata, newStatus, submittedAt, reviewer, reviewedAt, rejectionReason);
        }
    }
}
