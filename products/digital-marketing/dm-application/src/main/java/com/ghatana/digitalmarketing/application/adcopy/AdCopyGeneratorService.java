package com.ghatana.digitalmarketing.application.adcopy;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Generates Google Search responsive search ad copy drafts and retrieves approved versions.
 *
 * <p>Implementations must be deterministic — no AI hallucination of claims or testimonials.
 * Character-limit rules (headlines ≤30 chars, descriptions ≤90 chars) are enforced at generation time.</p>
 *
 * @doc.type class
 * @doc.purpose Defines the contract for generating and retrieving Google Search ad copy drafts.
 * @doc.layer application
 * @doc.pattern Service
 */
public interface AdCopyGeneratorService {

    /**
     * Generates a draft {@link ContentVersion} containing Google Search ad copy segments.
     *
     * @param ctx     the operation context (tenant, workspace, actor); must not be null
     * @param command the generation parameters; must not be null
     * @return a promise resolving to the newly created DRAFT content version
     */
    Promise<ContentVersion> generateAdCopyDraft(DmOperationContext ctx, GenerateAdCopyCommand command);

    /**
     * Returns the latest approved ad copy {@link ContentVersion} for the given content item.
     *
     * @param ctx    the operation context; must not be null
     * @param itemId the content item ID; must not be blank
     * @return a promise resolving to the approved version
     */
    Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId);

    /**
     * Encapsulates parameters for generating a Google Search ad copy draft.
     *
     * <p>All string fields are validated not-null; required fields must not be blank.</p>
     *
     * @param itemId            the content item to version under; must not be blank
     * @param strategyId        the strategy this ad serves; must not be blank
     * @param brandDisplayName  the brand name used in copy; must not be blank
     * @param primaryOffer      the main service or product offered; must not be blank
     * @param serviceArea       the geographic service area; must not be blank
     * @param landingPageUrl    the destination URL for click-through alignment; must not be blank
     * @param voiceTone         optional brand voice modifier (e.g. "professional", "friendly")
     * @param keywordThemes     seed keyword themes; may be empty
     * @param negativeKeywords  seed negative keywords; may be empty
     * @param claimIds          IDs of approved claims to reference; may be empty
     */
    record GenerateAdCopyCommand(
            String itemId,
            String strategyId,
            String brandDisplayName,
            String primaryOffer,
            String serviceArea,
            String landingPageUrl,
            String voiceTone,
            List<String> keywordThemes,
            List<String> negativeKeywords,
            List<String> claimIds) {

        /**
         * Compact constructor — validates required fields and copies mutable lists.
         */
        public GenerateAdCopyCommand {
            Objects.requireNonNull(itemId, "itemId must not be null");
            Objects.requireNonNull(strategyId, "strategyId must not be null");
            Objects.requireNonNull(brandDisplayName, "brandDisplayName must not be null");
            Objects.requireNonNull(primaryOffer, "primaryOffer must not be null");
            Objects.requireNonNull(serviceArea, "serviceArea must not be null");
            Objects.requireNonNull(landingPageUrl, "landingPageUrl must not be null");
            Objects.requireNonNull(voiceTone, "voiceTone must not be null");
            Objects.requireNonNull(keywordThemes, "keywordThemes must not be null");
            Objects.requireNonNull(negativeKeywords, "negativeKeywords must not be null");
            Objects.requireNonNull(claimIds, "claimIds must not be null");

            if (itemId.isBlank()) throw new IllegalArgumentException("itemId must not be blank");
            if (strategyId.isBlank()) throw new IllegalArgumentException("strategyId must not be blank");
            if (brandDisplayName.isBlank()) throw new IllegalArgumentException("brandDisplayName must not be blank");
            if (primaryOffer.isBlank()) throw new IllegalArgumentException("primaryOffer must not be blank");
            if (serviceArea.isBlank()) throw new IllegalArgumentException("serviceArea must not be blank");
            if (landingPageUrl.isBlank()) throw new IllegalArgumentException("landingPageUrl must not be blank");

            keywordThemes    = List.copyOf(keywordThemes);
            negativeKeywords = List.copyOf(negativeKeywords);
            claimIds         = List.copyOf(claimIds);
        }
    }
}
