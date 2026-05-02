package com.ghatana.digitalmarketing.application.proposal;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.proposal.Proposal;
import io.activej.promise.Promise;

/**
 * Application service for DMOS proposal generation and approval workflows (F1-015).
 *
 * <p>Orchestrates proposal creation from approved strategies, human review routing,
 * and approval. All operations enforce authorization, audit, and consent checks
 * through the kernel adapter.</p>
 *
 * @doc.type interface
 * @doc.purpose Proposal generation and approval service contract for F1-015
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ProposalService {

    /**
     * Generates a proposal draft from an approved strategy using the specified template.
     *
     * @param ctx     the operation context carrying tenant and actor identity; must not be null
     * @param command the generation command; must not be null
     * @return promise resolving to the new {@link Proposal} in {@code DRAFT} status
     * @throws SecurityException        if the actor is not authorized
     * @throws IllegalArgumentException if command fields are invalid
     */
    Promise<Proposal> generateProposal(DmOperationContext ctx, GenerateProposalCommand command);

    /**
     * Returns the most recently generated proposal for the workspace.
     *
     * @param ctx the operation context; must not be null
     * @return promise resolving to the latest {@link Proposal}
     * @throws SecurityException       if the actor is not authorized
     * @throws java.util.NoSuchElementException if no proposal exists for the workspace
     */
    Promise<Proposal> getProposal(DmOperationContext ctx);

    /**
     * Transitions a proposal from {@code DRAFT} to {@code PENDING_REVIEW}.
     *
     * @param ctx        the operation context; must not be null
     * @param proposalId the proposal to submit; must not be null or blank
     * @return promise resolving to the updated {@link Proposal} in {@code PENDING_REVIEW} status
     * @throws SecurityException              if the actor is not authorized
     * @throws java.util.NoSuchElementException if the proposal is not found
     * @throws IllegalStateException          if the proposal is not in {@code DRAFT} status
     */
    Promise<Proposal> submitForReview(DmOperationContext ctx, String proposalId);

    /**
     * Transitions a proposal from {@code PENDING_REVIEW} to {@code APPROVED}.
     *
     * @param ctx        the operation context; must not be null
     * @param proposalId the proposal to approve; must not be null or blank
     * @return promise resolving to the updated {@link Proposal} in {@code APPROVED} status
     * @throws SecurityException              if the actor is not authorized
     * @throws java.util.NoSuchElementException if the proposal is not found
     * @throws IllegalStateException          if the proposal is not in {@code PENDING_REVIEW} status
     */
    Promise<Proposal> approveProposal(DmOperationContext ctx, String proposalId);

    /**
     * Command record for generating a proposal draft.
     *
     * @param strategyId      the approved strategy ID to base the proposal on
     * @param templateId      the versioned template identifier to use
     * @param templateVersion the specific template version to pin this proposal to
     * @param assumptions     free-text assumptions to include in the proposal body
     *
     * @doc.type class
     * @doc.purpose Generate proposal command value object
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    record GenerateProposalCommand(
            String strategyId,
            String templateId,
            String templateVersion,
            String assumptions) {

        /**
         * Compact constructor — validates all fields.
         */
        public GenerateProposalCommand {
            if (strategyId == null || strategyId.isBlank()) {
                throw new IllegalArgumentException("strategyId must not be blank");
            }
            if (templateId == null || templateId.isBlank()) {
                throw new IllegalArgumentException("templateId must not be blank");
            }
            if (templateVersion == null || templateVersion.isBlank()) {
                throw new IllegalArgumentException("templateVersion must not be blank");
            }
            if (assumptions == null) {
                throw new IllegalArgumentException("assumptions must not be null");
            }
        }
    }
}
