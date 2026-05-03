package com.ghatana.digitalmarketing.application.landingpage;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Application service for deterministic landing page draft generation (F1-018).
 *
 * <p>Generates conversion-focused landing page drafts from approved strategy context,
 * brand profile, offer, proof points, service area, and compliance constraints.
 * Claims requiring evidence are flagged for approval routing. No fake testimonials,
 * guarantees, or proof points are generated.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS landing page draft generator service contract for F1-018
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface LandingPageGeneratorService {

    /**
     * Generates a landing page draft and stores it as a new {@link ContentVersion}.
     *
     * <p>The draft is stored in {@code DRAFT} status and must be reviewed and approved
     * before campaign launch. Claims without evidence are flagged but not blocked.</p>
     *
     * @param ctx     the operation context; must not be null
     * @param command the generation command; must not be null
     * @return the persisted draft {@link ContentVersion} in {@code DRAFT} status
     * @throws SecurityException if the actor is not authorised
     */
    Promise<ContentVersion> generateDraft(DmOperationContext ctx, GenerateLandingPageCommand command);

    /**
     * Returns the latest approved landing page version for a content item.
     *
     * @param ctx    the operation context; must not be null
     * @param itemId the content item ID; must not be null or blank
     * @return the latest {@code APPROVED} {@link ContentVersion}
     * @throws java.util.NoSuchElementException if no approved version exists
     */
    Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId);

    /**
     * Command carrying all input signals needed to generate a landing page draft.
     *
     * @param itemId             existing content item ID to version under; must not be blank
     * @param strategyId         approved strategy ID driving the campaign; must not be blank
     * @param brandDisplayName   brand display name for voice/tone instructions; must not be blank
     * @param voiceTone          brand voice tone guidance; may be blank (treated as neutral)
     * @param primaryOffer       non-blank offer name/description
     * @param offerDescription   optional extended offer description; may be blank
     * @param serviceArea        service geography; must not be blank
     * @param proofPoints        optional list of approved proof-point summaries; may be empty
     * @param disclosureTexts    optional legal disclosure texts; may be empty
     * @param claimIds           IDs of approved claims to reference; may be empty
     */
    record GenerateLandingPageCommand(
            String itemId,
            String strategyId,
            String brandDisplayName,
            String voiceTone,
            String primaryOffer,
            String offerDescription,
            String serviceArea,
            List<String> proofPoints,
            List<String> disclosureTexts,
            List<String> claimIds) {

        /** Compact constructor with validation. */
        public GenerateLandingPageCommand {
            Objects.requireNonNull(itemId,           "itemId must not be null");
            Objects.requireNonNull(strategyId,       "strategyId must not be null");
            Objects.requireNonNull(brandDisplayName, "brandDisplayName must not be null");
            Objects.requireNonNull(voiceTone,        "voiceTone must not be null");
            Objects.requireNonNull(primaryOffer,     "primaryOffer must not be null");
            Objects.requireNonNull(offerDescription, "offerDescription must not be null");
            Objects.requireNonNull(serviceArea,      "serviceArea must not be null");
            Objects.requireNonNull(proofPoints,      "proofPoints must not be null");
            Objects.requireNonNull(disclosureTexts,  "disclosureTexts must not be null");
            Objects.requireNonNull(claimIds,         "claimIds must not be null");

            if (itemId.isBlank())           throw new IllegalArgumentException("itemId must not be blank");
            if (strategyId.isBlank())       throw new IllegalArgumentException("strategyId must not be blank");
            if (brandDisplayName.isBlank()) throw new IllegalArgumentException("brandDisplayName must not be blank");
            if (primaryOffer.isBlank())     throw new IllegalArgumentException("primaryOffer must not be blank");
            if (serviceArea.isBlank())      throw new IllegalArgumentException("serviceArea must not be blank");

            proofPoints      = List.copyOf(proofPoints);
            disclosureTexts  = List.copyOf(disclosureTexts);
            claimIds         = List.copyOf(claimIds);
        }
    }
}
