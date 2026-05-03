package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.application.content.ContentItemService;
import com.ghatana.digitalmarketing.application.suppression.SuppressionService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import com.ghatana.digitalmarketing.domain.content.EmailSection;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic email follow-up draft generator for DMOS F1-020.
 *
 * <p>Generates all required {@link EmailSection} segments as {@link ContentBlock} entries
 * and stores them as a new {@link ContentVersion} in DRAFT state. The implementation is
 * consent-aware: it checks suppression before generating and enforces
 * {@code marketing-email} consent via the kernel. Subject lines are capped at
 * {@link #SUBJECT_MAX_CHARS} characters. No email is sent — this service only creates
 * the draft for compliance review and subsequent approval.</p>
 *
 * @doc.type class
 * @doc.purpose Deterministic DMOS email follow-up draft generator (F1-020)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class EmailFollowUpDraftServiceImpl implements EmailFollowUpDraftService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailFollowUpDraftServiceImpl.class);

    /** Consent purpose required for marketing email targeting. */
    static final String MARKETING_EMAIL_PURPOSE = "marketing-email";

    /** Model version identifier for traceability. */
    static final String MODEL_VERSION = "email-gen-v1.0";

    /** Prompt version identifier for traceability. */
    static final String PROMPT_VERSION = "email-prompt-v1.0";

    /** Maximum character length for an email subject line. */
    public static final int SUBJECT_MAX_CHARS = 60;

    /** Mandatory unsubscribe notice text appended to all email drafts. */
    static final String UNSUBSCRIBE_PLACEHOLDER =
        "[UNSUBSCRIBE LINK REQUIRED — insert a valid one-click unsubscribe link here "
        + "before sending. Sending without an unsubscribe link violates CAN-SPAM / CASL.]";

    /** Warning emitted in compliance notes when claims are referenced but not yet verified. */
    static final String UNVERIFIED_CLAIM_WARNING =
        "[CLAIM REVIEW REQUIRED — claim ID referenced but no approved evidence on file. "
        + "Remove or replace with an evidence-backed claim before activating this email.]";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContentItemService contentItemService;
    private final SuppressionService suppressionService;

    /**
     * Constructs a new {@code EmailFollowUpDraftServiceImpl}.
     *
     * @param kernelAdapter      platform kernel adapter; must not be null
     * @param contentItemService content version lifecycle service; must not be null
     * @param suppressionService suppression check service; must not be null
     */
    public EmailFollowUpDraftServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContentItemService contentItemService,
            SuppressionService suppressionService) {
        this.kernelAdapter      = Objects.requireNonNull(kernelAdapter,      "kernelAdapter must not be null");
        this.contentItemService = Objects.requireNonNull(contentItemService, "contentItemService must not be null");
        this.suppressionService = Objects.requireNonNull(suppressionService, "suppressionService must not be null");
    }

    @Override
    public Promise<ContentVersion> generateEmailDraft(
            DmOperationContext ctx,
            GenerateEmailDraftCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to write content"));
                }
                return kernelAdapter.verifyConsent(ctx, command.contactId(), MARKETING_EMAIL_PURPOSE);
            })
            .then(hasConsent -> {
                if (!hasConsent) {
                    return Promise.ofException(
                        new SecurityException(
                            "Contact " + command.contactId()
                                + " has not granted marketing-email consent"));
                }
                return suppressionService.isSuppressed(ctx, command.contactId());
            })
            .then(suppressed -> {
                if (suppressed) {
                    return Promise.ofException(
                        new SecurityException(
                            "Contact " + command.contactId() + " is suppressed"));
                }
                List<ContentBlock> blocks = buildBlocks(command);
                List<ClaimReference> claimRefs = buildClaimReferences(command.claimIds());
                List<DisclosureReference> disclosureRefs = buildDisclosureReferences();

                ContentItemService.CreateContentVersionCommand versionCmd =
                    new ContentItemService.CreateContentVersionCommand(
                        command.itemId(),
                        blocks,
                        claimRefs,
                        disclosureRefs,
                        MODEL_VERSION,
                        PROMPT_VERSION,
                        "DETERMINISTIC"
                    );
                return contentItemService.createVersion(ctx, versionCmd);
            })
            .then(version ->
                kernelAdapter.recordAudit(
                    ctx,
                    command.itemId(),
                    "email-follow-up-draft-generated",
                    Map.of(
                        "contactId",  command.contactId(),
                        "strategyId", command.strategyId(),
                        "versionId",  version.getVersionId()))
                .map(ignored -> {
                    LOG.info("Email follow-up draft generated: itemId={} versionId={} workspace={}",
                        command.itemId(), version.getVersionId(),
                        ctx.getWorkspaceId().getValue());
                    return version;
                })
            );
    }

    @Override
    public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (itemId == null || itemId.isBlank()) {
            return Promise.ofException(
                new IllegalArgumentException("itemId must not be blank"));
        }
        return kernelAdapter.isAuthorized(ctx, "content", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to read content"));
                }
                return contentItemService.getLatestApproved(ctx, itemId);
            });
    }

    // -------------------------------------------------------------------------
    // Block builders
    // -------------------------------------------------------------------------

    private List<ContentBlock> buildBlocks(GenerateEmailDraftCommand cmd) {
        List<ContentBlock> blocks = new ArrayList<>();

        String subject = truncate(cmd.brandDisplayName() + " | " + cmd.primaryOffer(),
            SUBJECT_MAX_CHARS);
        blocks.add(new ContentBlock(
            EmailSection.SUBJECT_LINE.name(),
            EmailSection.SUBJECT_LINE.name(),
            subject,
            0));

        blocks.add(new ContentBlock(
            EmailSection.GREETING.name(),
            EmailSection.GREETING.name(),
            "Hello,",
            1));

        String body = "We wanted to follow up with you about "
            + cmd.primaryOffer()
            + " from " + cmd.brandDisplayName()
            + "."
            + (cmd.voiceTone() != null && !cmd.voiceTone().isBlank()
                ? " Our team takes a " + cmd.voiceTone() + " approach to every engagement."
                : "")
            + " Please reply to this email or contact us to learn more.";
        blocks.add(new ContentBlock(
            EmailSection.BODY_COPY.name(),
            EmailSection.BODY_COPY.name(),
            body,
            2));

        blocks.add(new ContentBlock(
            EmailSection.CALL_TO_ACTION.name(),
            EmailSection.CALL_TO_ACTION.name(),
            "Reply now or visit our website to get started.",
            3));

        blocks.add(new ContentBlock(
            EmailSection.UNSUBSCRIBE_NOTICE.name(),
            EmailSection.UNSUBSCRIBE_NOTICE.name(),
            UNSUBSCRIBE_PLACEHOLDER,
            4));

        String compliance = "This email is sent by " + cmd.senderName()
            + ". Reply-to: " + cmd.replyToAddress() + ".";
        if (!cmd.claimIds().isEmpty()) {
            compliance += " " + UNVERIFIED_CLAIM_WARNING;
        }
        blocks.add(new ContentBlock(
            EmailSection.COMPLIANCE_NOTES.name(),
            EmailSection.COMPLIANCE_NOTES.name(),
            compliance,
            5));

        return blocks;
    }

    private List<ClaimReference> buildClaimReferences(List<String> claimIds) {
        List<ClaimReference> refs = new ArrayList<>();
        for (String id : claimIds) {
            refs.add(new ClaimReference(id, "[PENDING REVIEW]", "[SOURCE REQUIRED]"));
        }
        return refs;
    }

    private List<DisclosureReference> buildDisclosureReferences() {
        return List.of(
            new DisclosureReference(
                "disc-email-unsubscribe",
                "One-click unsubscribe link must be present before sending (CAN-SPAM / CASL).",
                "COMPLIANCE")
        );
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Truncates {@code text} to at most {@code maxLen} characters.
     * Returns an empty string when {@code maxLen} is zero or negative.
     */
    static String truncate(String text, int maxLen) {
        if (text == null || maxLen <= 0) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
