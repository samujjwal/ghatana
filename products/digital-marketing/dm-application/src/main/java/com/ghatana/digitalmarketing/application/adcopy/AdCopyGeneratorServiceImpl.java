package com.ghatana.digitalmarketing.application.adcopy;

import com.ghatana.digitalmarketing.application.content.ContentItemService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import com.ghatana.digitalmarketing.domain.content.GoogleAdCopySection;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic Google Search responsive search ad copy draft generator for DMOS F1-019.
 *
 * <p>Generates all required segments ({@link GoogleAdCopySection}) as
 * {@link ContentBlock} entries and stores them as a new {@link ContentVersion}.
 * Character limits are enforced: headlines ≤30 chars, descriptions ≤90 chars.
 * Claims requiring evidence are flagged explicitly — no fabricated testimonials.</p>
 *
 * @doc.type class
 * @doc.purpose Deterministic DMOS Google Search ad copy draft generator
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AdCopyGeneratorServiceImpl implements AdCopyGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(AdCopyGeneratorServiceImpl.class);

    /** Model version identifier for traceability. */
    static final String MODEL_VERSION = "ad-gen-v1.0";

    /** Prompt version identifier for traceability. */
    static final String PROMPT_VERSION = "ad-prompt-v1.0";

    /** Maximum character length for a Google Search ad headline. */
    public static final int HEADLINE_MAX_CHARS = 30;

    /** Maximum character length for a Google Search ad description. */
    public static final int DESCRIPTION_MAX_CHARS = 90;

    /** Warning emitted in compliance notes when a claim has no attached evidence. */
    static final String UNVERIFIED_CLAIM_WARNING =
        "[CLAIM REVIEW REQUIRED — claim ID referenced but no approved evidence on file. "
        + "Remove or replace with an evidence-backed claim before activating this ad.]";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContentItemService contentItemService;

    /**
     * Constructs a new {@code AdCopyGeneratorServiceImpl}.
     *
     * @param kernelAdapter      platform kernel adapter; must not be null
     * @param contentItemService content version lifecycle service; must not be null
     */
    public AdCopyGeneratorServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContentItemService contentItemService) {
        this.kernelAdapter      = Objects.requireNonNull(kernelAdapter,      "kernelAdapter must not be null");
        this.contentItemService = Objects.requireNonNull(contentItemService, "contentItemService must not be null");
    }

    @Override
    public Promise<ContentVersion> generateAdCopyDraft(
            DmOperationContext ctx,
            GenerateAdCopyCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorised to generate ad copy content"));
                }

                List<ContentBlock>        blocks      = buildContentBlocks(command);
                List<ClaimReference>      claims      = buildClaimReferences(command);
                List<DisclosureReference> disclosures = List.of();

                ContentItemService.CreateContentVersionCommand cmd =
                    new ContentItemService.CreateContentVersionCommand(
                        command.itemId(),
                        blocks,
                        claims,
                        disclosures,
                        MODEL_VERSION,
                        PROMPT_VERSION,
                        "strategy:" + command.strategyId()
                    );

                return contentItemService.createVersion(ctx, cmd)
                    .then(version ->
                        kernelAdapter.recordAudit(
                            ctx,
                            version.getVersionId(),
                            "ad-copy-draft-generated",
                            Map.of(
                                "itemId",      command.itemId(),
                                "strategyId",  command.strategyId(),
                                "modelVersion", MODEL_VERSION
                            )
                        ).map(__ -> version)
                    );
            });
    }

    @Override
    public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        if (itemId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("itemId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "content", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorised to read ad copy content"));
                }
                return contentItemService.getLatestApproved(ctx, itemId);
            });
    }

    // -------------------------------------------------------------------------
    // Block builders — deterministic, no AI hallucination
    // -------------------------------------------------------------------------

    private List<ContentBlock> buildContentBlocks(GenerateAdCopyCommand cmd) {
        List<ContentBlock> blocks = new ArrayList<>();
        int order = 0;

        blocks.add(new ContentBlock(
            GoogleAdCopySection.HEADLINES.name(),
            GoogleAdCopySection.HEADLINES.name(),
            buildHeadlinesText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            GoogleAdCopySection.DESCRIPTIONS.name(),
            GoogleAdCopySection.DESCRIPTIONS.name(),
            buildDescriptionsText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            GoogleAdCopySection.KEYWORD_THEMES.name(),
            GoogleAdCopySection.KEYWORD_THEMES.name(),
            buildKeywordThemesText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            GoogleAdCopySection.NEGATIVE_KEYWORDS.name(),
            GoogleAdCopySection.NEGATIVE_KEYWORDS.name(),
            buildNegativeKeywordsText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            GoogleAdCopySection.CALL_TO_ACTION.name(),
            GoogleAdCopySection.CALL_TO_ACTION.name(),
            buildCallToActionText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            GoogleAdCopySection.COMPLIANCE_NOTES.name(),
            GoogleAdCopySection.COMPLIANCE_NOTES.name(),
            buildComplianceNotesText(cmd),
            order
        ));

        return List.copyOf(blocks);
    }

    /**
     * Builds 3 headline variants that fit within Google's 30-character limit.
     * Headlines are deterministic templates populated from command fields.
     */
    private String buildHeadlinesText(GenerateAdCopyCommand cmd) {
        String brand  = truncate(cmd.brandDisplayName(), HEADLINE_MAX_CHARS);
        String offer  = truncate(cmd.primaryOffer(), HEADLINE_MAX_CHARS);
        String area   = truncate(cmd.serviceArea(), HEADLINE_MAX_CHARS);
        String h2     = truncate(offer + " " + area, HEADLINE_MAX_CHARS);
        String h3     = truncate("Contact Us " + area, HEADLINE_MAX_CHARS);
        return String.join(" | ", brand, h2, h3);
    }

    /**
     * Builds 2 description variants within Google's 90-character limit.
     */
    private String buildDescriptionsText(GenerateAdCopyCommand cmd) {
        String base = cmd.brandDisplayName() + " offers " + cmd.primaryOffer()
            + " in " + cmd.serviceArea() + ".";
        String desc1 = truncate(base + " Contact us today.", DESCRIPTION_MAX_CHARS);
        String desc2 = truncate("Serving " + cmd.serviceArea() + " — "
            + cmd.primaryOffer() + ". Fast, reliable service.", DESCRIPTION_MAX_CHARS);
        return desc1 + "\n" + desc2;
    }

    private String buildKeywordThemesText(GenerateAdCopyCommand cmd) {
        if (cmd.keywordThemes().isEmpty()) {
            return "[" + cmd.primaryOffer() + " near me] | "
                + "[" + cmd.primaryOffer() + " " + cmd.serviceArea() + "] | "
                + "[best " + cmd.primaryOffer() + "]";
        }
        return String.join(" | ", cmd.keywordThemes().stream()
            .map(k -> "[" + k + "]")
            .toList());
    }

    private String buildNegativeKeywordsText(GenerateAdCopyCommand cmd) {
        if (cmd.negativeKeywords().isEmpty()) {
            return "[free] | [DIY] | [training] | [jobs]";
        }
        return String.join(" | ", cmd.negativeKeywords().stream()
            .map(k -> "[" + k + "]")
            .toList());
    }

    private String buildCallToActionText(GenerateAdCopyCommand cmd) {
        String url = cmd.landingPageUrl();
        return "Call Now or visit " + url + " for " + cmd.primaryOffer() + " in " + cmd.serviceArea() + ".";
    }

    private String buildComplianceNotesText(GenerateAdCopyCommand cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("LANDING PAGE ALIGNMENT: Verify ad copy matches offer and CTA at ").append(cmd.landingPageUrl()).append(".\n");
        sb.append("CHARACTER LIMITS: All headlines truncated to ≤").append(HEADLINE_MAX_CHARS)
            .append(" chars; descriptions ≤").append(DESCRIPTION_MAX_CHARS).append(" chars.\n");
        if (!cmd.claimIds().isEmpty()) {
            sb.append(UNVERIFIED_CLAIM_WARNING).append("\n");
            sb.append("Claim IDs referenced: ").append(String.join(", ", cmd.claimIds())).append("\n");
        }
        sb.append("AD APPROVAL REQUIRED: This draft must be reviewed and approved before connector command creation.");
        return sb.toString();
    }

    private List<ClaimReference> buildClaimReferences(GenerateAdCopyCommand cmd) {
        List<ClaimReference> refs = new ArrayList<>();
        for (String claimId : cmd.claimIds()) {
            refs.add(new ClaimReference(claimId, "[Claim text pending review]", "approved-claims"));
        }
        return List.copyOf(refs);
    }

    /**
     * Truncates {@code text} to at most {@code maxLen} characters. Returns the text unchanged
     * if shorter than or equal to {@code maxLen}.
     */
    static String truncate(String text, int maxLen) {
        if (maxLen <= 0) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
