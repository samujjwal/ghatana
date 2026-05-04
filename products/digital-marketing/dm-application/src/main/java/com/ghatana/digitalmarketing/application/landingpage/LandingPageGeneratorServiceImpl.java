package com.ghatana.digitalmarketing.application.landingpage;

import com.ghatana.digitalmarketing.application.content.ContentItemService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import com.ghatana.digitalmarketing.domain.content.LandingPageSection;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic landing page draft generator for DMOS F1-018.
 *
 * <p>Generates all required conversion sections ({@link LandingPageSection}) as
 * {@link ContentBlock} entries and stores them as a new {@link ContentVersion} via
 * {@link ContentItemService}. Claims requiring evidence are flagged with a warning
 * text instead of hallucinated proof. No fake testimonials are ever generated.</p>
 *
 * @doc.type class
 * @doc.purpose Deterministic DMOS landing page draft generator — creates structured content versions
 * @doc.layer product
 * @doc.pattern Service
 */
public final class LandingPageGeneratorServiceImpl implements LandingPageGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(LandingPageGeneratorServiceImpl.class);

    static final String MODEL_VERSION = "lp-gen-v1.0";
    static final String PROMPT_VERSION = "lp-prompt-v1.0";
    static final String PROOF_PENDING =
        "Customer success stories and evidence-backed testimonials will be added "
        + "during the content review process before publishing.";
    static final String DISCLOSURE_PENDING =
        "Required legal and regulatory disclosures will be confirmed during the "
        + "compliance review process before publishing.";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContentItemService contentItemService;

    /**
     * Constructs a new {@code LandingPageGeneratorServiceImpl}.
     *
     * @param kernelAdapter      platform kernel adapter; must not be null
     * @param contentItemService content version lifecycle service; must not be null
     */
    public LandingPageGeneratorServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContentItemService contentItemService) {
        this.kernelAdapter      = Objects.requireNonNull(kernelAdapter,      "kernelAdapter must not be null");
        this.contentItemService = Objects.requireNonNull(contentItemService, "contentItemService must not be null");
    }

    @Override
    public Promise<ContentVersion> generateDraft(
            DmOperationContext ctx,
            GenerateLandingPageCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorised to generate landing page content"));
                }

                List<ContentBlock>        blocks       = buildContentBlocks(command);
                List<ClaimReference>      claims       = buildClaimReferences(command);
                List<DisclosureReference> disclosures  = buildDisclosureReferences(command);

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
                            "landing-page-draft-generated",
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
                        new SecurityException("Actor not authorised to read landing page content"));
                }
                return contentItemService.getLatestApproved(ctx, itemId);
            });
    }

    // -------------------------------------------------------------------------
    // Section builders — deterministic, no AI hallucination
    // -------------------------------------------------------------------------

    private List<ContentBlock> buildContentBlocks(GenerateLandingPageCommand cmd) {
        List<ContentBlock> blocks = new ArrayList<>();
        int order = 0;

        blocks.add(new ContentBlock(
            LandingPageSection.HERO.name(),
            LandingPageSection.HERO.name(),
            buildHeroText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            LandingPageSection.PROBLEM.name(),
            LandingPageSection.PROBLEM.name(),
            buildProblemText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            LandingPageSection.OFFER.name(),
            LandingPageSection.OFFER.name(),
            buildOfferText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            LandingPageSection.PROOF.name(),
            LandingPageSection.PROOF.name(),
            buildProofText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            LandingPageSection.CTA.name(),
            LandingPageSection.CTA.name(),
            buildCtaText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            LandingPageSection.FAQ.name(),
            LandingPageSection.FAQ.name(),
            buildFaqText(cmd),
            order++
        ));
        blocks.add(new ContentBlock(
            LandingPageSection.DISCLAIMER.name(),
            LandingPageSection.DISCLAIMER.name(),
            buildDisclaimerText(cmd),
            order
        ));

        return blocks;
    }

    private String buildHeroText(GenerateLandingPageCommand cmd) {
        String tone = cmd.voiceTone().isBlank() ? "" : " (" + cmd.voiceTone() + " tone)";
        return String.format(
            "Headline: %s — Trusted %s Specialists%s%n"
            + "Sub-headline: Get results-driven %s for your business in %s.%n"
            + "CTA: Request a Free Consultation",
            cmd.brandDisplayName(), cmd.primaryOffer(), tone,
            cmd.primaryOffer(), cmd.serviceArea()
        );
    }

    private String buildProblemText(GenerateLandingPageCommand cmd) {
        return String.format(
            "Are you struggling to grow your %s business in %s? "
            + "Without the right marketing strategy, potential customers are choosing your competitors. "
            + "We understand the challenges local businesses face.",
            cmd.primaryOffer(), cmd.serviceArea()
        );
    }

    private String buildOfferText(GenerateLandingPageCommand cmd) {
        String extended = cmd.offerDescription().isBlank()
            ? ""
            : " " + cmd.offerDescription();
        return String.format(
            "Our %s service delivers measurable results.%s "
            + "All performance claims will be verified against evidence during content review.",
            cmd.primaryOffer(), extended
        );
    }

    private String buildProofText(GenerateLandingPageCommand cmd) {
        if (cmd.proofPoints().isEmpty()) {
            LOG.warn("[DMOS] Landing page generated without proof points for item={}; marking for review",
                cmd.itemId());
            return PROOF_PENDING;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.proofPoints().size(); i++) {
            sb.append(i + 1).append(". ").append(cmd.proofPoints().get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildCtaText(GenerateLandingPageCommand cmd) {
        return String.format(
            "Ready to grow your %s business in %s?%n"
            + "Call us today or fill in the form below to request your free strategy session.",
            cmd.primaryOffer(), cmd.serviceArea()
        );
    }

    private String buildFaqText(GenerateLandingPageCommand cmd) {
        return String.format(
            "Q: How long does it take to see results from %s marketing?%n"
            + "A: Most clients see measurable improvements within 30 to 90 days.%n%n"
            + "Q: Do you serve businesses in %s?%n"
            + "A: Yes, we specialise in campaigns for %s.%n%n"
            + "Q: What makes %s different?%n"
            + "A: We combine data-driven strategy with local market expertise.%n%n"
            + "Q: Is there a contract?%n"
            + "A: Contact us to discuss flexible engagement options tailored to your needs.%n%n"
            + "Q: How much does it cost?%n"
            + "A: Pricing is customised based on scope and goals. Request a free consultation for a detailed quote.",
            cmd.primaryOffer(), cmd.serviceArea(), cmd.serviceArea(), cmd.brandDisplayName()
        );
    }

    private String buildDisclaimerText(GenerateLandingPageCommand cmd) {
        if (cmd.disclosureTexts().isEmpty()) {
            return DISCLOSURE_PENDING;
        }
        return String.join("\n\n", cmd.disclosureTexts());
    }

    private List<ClaimReference> buildClaimReferences(GenerateLandingPageCommand cmd) {
        List<ClaimReference> refs = new ArrayList<>();
        for (String claimId : cmd.claimIds()) {
            refs.add(new ClaimReference(claimId, "pending-evidence-review", "approved-claims"));
        }
        return refs;
    }

    private List<DisclosureReference> buildDisclosureReferences(GenerateLandingPageCommand cmd) {
        List<DisclosureReference> refs = new ArrayList<>();
        for (int i = 0; i < cmd.disclosureTexts().size(); i++) {
            refs.add(new DisclosureReference(
                "disc-" + (i + 1),
                cmd.disclosureTexts().get(i),
                "legal"
            ));
        }
        return refs;
    }
}
