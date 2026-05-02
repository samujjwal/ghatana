package com.ghatana.digitalmarketing.application.sow;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.sow.SowDraft;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Application service contract for DMOS SOW draft generation and lifecycle management.
 *
 * <p>All operations require an authorized {@link DmOperationContext} and are executed
 * asynchronously. SOW drafts progress through the
 * {@code DRAFT → PENDING_REVIEW → APPROVED → EXPORTED} lifecycle before
 * they may be delivered to the client.</p>
 *
 * @doc.type class
 * @doc.purpose SOW draft service interface for F1-016
 * @doc.layer product
 * @doc.pattern Service
 */
public interface SowService {

    /**
     * Generates a new SOW draft for the workspace referenced in {@code ctx}.
     *
     * @param ctx     the operation context (tenant, workspace, actor)
     * @param command the generation command carrying proposal and template details
     * @return a promise resolving to the newly created SOW draft
     */
    Promise<SowDraft> generateDraft(DmOperationContext ctx, GenerateSowCommand command);

    /**
     * Retrieves the latest SOW draft for the workspace in {@code ctx}.
     *
     * @param ctx the operation context
     * @return a promise resolving to the latest SOW draft
     * @throws java.util.NoSuchElementException if no draft exists for the workspace
     */
    Promise<SowDraft> getDraft(DmOperationContext ctx);

    /**
     * Submits a SOW draft for human review.
     *
     * @param ctx   the operation context
     * @param sowId the ID of the draft to submit
     * @return a promise resolving to the updated draft in {@code PENDING_REVIEW} status
     */
    Promise<SowDraft> submitForReview(DmOperationContext ctx, String sowId);

    /**
     * Approves a SOW draft that is in {@code PENDING_REVIEW} status.
     *
     * @param ctx   the operation context (the actor becomes the approver)
     * @param sowId the ID of the draft to approve
     * @return a promise resolving to the approved draft
     */
    Promise<SowDraft> approveDraft(DmOperationContext ctx, String sowId);

    /**
     * Exports an approved SOW draft, marking it as ready for client delivery.
     *
     * @param ctx   the operation context
     * @param sowId the ID of the approved draft to export
     * @return a promise resolving to the exported draft
     */
    Promise<SowDraft> exportDraft(DmOperationContext ctx, String sowId);

    /**
     * Command for generating a new SOW draft.
     *
     * @param proposalId      the ID of the approved proposal this SOW references
     * @param templateVersion the versioned SOW template to apply
     * @param assumptions     project assumptions; may be empty but not null
     * @param exclusions      explicit exclusions from scope; may be empty but not null
     */
    record GenerateSowCommand(
            String proposalId,
            String templateVersion,
            String assumptions,
            String exclusions) {

        public GenerateSowCommand {
            Objects.requireNonNull(proposalId, "proposalId must not be null");
            Objects.requireNonNull(templateVersion, "templateVersion must not be null");
            Objects.requireNonNull(assumptions, "assumptions must not be null");
            Objects.requireNonNull(exclusions, "exclusions must not be null");
            if (proposalId.isBlank()) {
                throw new IllegalArgumentException("proposalId must not be blank");
            }
            if (templateVersion.isBlank()) {
                throw new IllegalArgumentException("templateVersion must not be blank");
            }
        }
    }
}
