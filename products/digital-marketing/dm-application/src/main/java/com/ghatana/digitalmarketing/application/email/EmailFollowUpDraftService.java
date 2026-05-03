package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Application service for generating email follow-up draft sequences for DMOS leads.
 *
 * <p>All generated drafts are consent-aware: if the target contact is suppressed
 * or has not granted the required marketing-email consent the operation fails with
 * a {@link SecurityException}. The generated draft is stored as a {@link ContentVersion}
 * and must pass compliance review before any send step is executed.</p>
 *
 * @doc.type interface
 * @doc.purpose Email follow-up draft generation application service (F1-020)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface EmailFollowUpDraftService {

    /**
     * Generates a new email follow-up draft sequence for the given lead / contact.
     *
     * <p>Checks suppression and marketing-email consent before generating. The
     * draft content is persisted as a {@link ContentVersion} in DRAFT state and
     * returned for review.</p>
     *
     * @param ctx     operation context (workspace, actor, tenant)
     * @param command generation parameters
     * @return promise resolving to the saved content version draft
     * @throws SecurityException    if the actor is not authorised or the contact is
     *                              suppressed / has not consented
     * @throws java.util.NoSuchElementException if the contact or strategy is not found
     * @throws IllegalArgumentException if command validation fails
     */
    Promise<ContentVersion> generateEmailDraft(DmOperationContext ctx,
                                               GenerateEmailDraftCommand command);

    /**
     * Returns the latest approved email version for a content item.
     *
     * @param ctx    operation context
     * @param itemId the content item identifier; must not be blank
     * @return promise resolving to the approved version
     * @throws SecurityException    if the actor is not authorised
     * @throws java.util.NoSuchElementException if no approved version exists
     */
    Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId);

    // -------------------------------------------------------------------------
    // Command
    // -------------------------------------------------------------------------

    /**
     * Command for generating an email follow-up draft.
     *
     * @param itemId            the content item ID that will hold this version; must not be blank
     * @param contactId         the target contact ID for personalisation; must not be blank
     * @param strategyId        the marketing strategy this email belongs to; must not be blank
     * @param brandDisplayName  the brand name shown in the email body; must not be blank
     * @param primaryOffer      the core offer or value proposition; must not be blank
     * @param senderName        display name for the From field; must not be blank
     * @param replyToAddress    reply-to address; must not be blank
     * @param voiceTone         optional tone hint (e.g. "friendly", "professional"); may be null
     * @param claimIds          optional list of approved claim IDs to reference; may be null
     */
    record GenerateEmailDraftCommand(
            String itemId,
            String contactId,
            String strategyId,
            String brandDisplayName,
            String primaryOffer,
            String senderName,
            String replyToAddress,
            String voiceTone,
            List<String> claimIds) {

        public GenerateEmailDraftCommand {
            Objects.requireNonNull(itemId,           "itemId must not be null");
            Objects.requireNonNull(contactId,        "contactId must not be null");
            Objects.requireNonNull(strategyId,       "strategyId must not be null");
            Objects.requireNonNull(brandDisplayName, "brandDisplayName must not be null");
            Objects.requireNonNull(primaryOffer,     "primaryOffer must not be null");
            Objects.requireNonNull(senderName,       "senderName must not be null");
            Objects.requireNonNull(replyToAddress,   "replyToAddress must not be null");
            if (itemId.isBlank())           throw new IllegalArgumentException("itemId must not be blank");
            if (contactId.isBlank())        throw new IllegalArgumentException("contactId must not be blank");
            if (strategyId.isBlank())       throw new IllegalArgumentException("strategyId must not be blank");
            if (brandDisplayName.isBlank()) throw new IllegalArgumentException("brandDisplayName must not be blank");
            if (primaryOffer.isBlank())     throw new IllegalArgumentException("primaryOffer must not be blank");
            if (senderName.isBlank())       throw new IllegalArgumentException("senderName must not be blank");
            if (replyToAddress.isBlank())   throw new IllegalArgumentException("replyToAddress must not be blank");
            claimIds = (claimIds == null) ? List.of() : List.copyOf(claimIds);
        }
    }
}
