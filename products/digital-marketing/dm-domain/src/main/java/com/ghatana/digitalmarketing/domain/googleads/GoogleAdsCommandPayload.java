package com.ghatana.digitalmarketing.domain.googleads;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * P1-023: Google Ads command payloads for outbox execution.
 *
 * <p>Payload types for Google Ads commands executed through the outbox pattern:</p>
 * <ul>
 *   <li>CreateCampaign - Create a new Google Ads campaign</li>
 *   <li>UpdateCampaign - Update an existing Google Ads campaign</li>
 *   <li>PauseCampaign - Pause a Google Ads campaign</li>
 *   <li>DeleteCampaign - Remove a Google Ads campaign</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Google Ads command payloads for durable outbox execution (P1-023)
 * @doc.layer domain
 * @doc.pattern ValueObject, DTO
 */
public sealed interface GoogleAdsCommandPayload {

    /**
     * Payload for creating a Google Ads campaign.
     */
    record CreateCampaign(
        String internalCampaignId,
        String googleAdsCustomerId,
        String campaignName,
        CampaignGoal goal,
        BigDecimal dailyBudget,
        LocalDate startDate,
        LocalDate endDate,
        String[] targetingCriteria,
        String[] adGroupConfigs,
        String landingPageUrl,
        String trackingTemplate
    ) implements GoogleAdsCommandPayload {

        public CreateCampaign {
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(googleAdsCustomerId, "googleAdsCustomerId must not be null");
            Objects.requireNonNull(campaignName, "campaignName must not be null");
            Objects.requireNonNull(goal, "goal must not be null");
            Objects.requireNonNull(dailyBudget, "dailyBudget must not be null");

            if (internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (googleAdsCustomerId.isBlank()) {
                throw new IllegalArgumentException("googleAdsCustomerId must not be blank");
            }
            if (campaignName.isBlank()) {
                throw new IllegalArgumentException("campaignName must not be blank");
            }
            if (dailyBudget.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("dailyBudget must be positive");
            }
        }
    }

    /**
     * Payload for updating a Google Ads campaign.
     */
    record UpdateCampaign(
        String internalCampaignId,
        String googleAdsCampaignId,
        String campaignName,
        BigDecimal dailyBudget,
        LocalDate endDate,
        String[] targetingCriteriaUpdates,
        String landingPageUrl,
        String trackingTemplate
    ) implements GoogleAdsCommandPayload {

        public UpdateCampaign {
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(googleAdsCampaignId, "googleAdsCampaignId must not be null");

            if (internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (googleAdsCampaignId.isBlank()) {
                throw new IllegalArgumentException("googleAdsCampaignId must not be blank");
            }
        }
    }

    /**
     * Payload for pausing a Google Ads campaign.
     */
    record PauseCampaign(
        String internalCampaignId,
        String googleAdsCampaignId,
        String reason
    ) implements GoogleAdsCommandPayload {

        public PauseCampaign {
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(googleAdsCampaignId, "googleAdsCampaignId must not be null");

            if (internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (googleAdsCampaignId.isBlank()) {
                throw new IllegalArgumentException("googleAdsCampaignId must not be blank");
            }
        }
    }

    /**
     * Payload for resuming a paused Google Ads campaign.
     */
    record ResumeCampaign(
        String internalCampaignId,
        String googleAdsCampaignId,
        String reason
    ) implements GoogleAdsCommandPayload {

        public ResumeCampaign {
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(googleAdsCampaignId, "googleAdsCampaignId must not be null");

            if (internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (googleAdsCampaignId.isBlank()) {
                throw new IllegalArgumentException("googleAdsCampaignId must not be blank");
            }
        }
    }

    /**
     * Payload for deleting/removing a Google Ads campaign.
     */
    record DeleteCampaign(
        String internalCampaignId,
        String googleAdsCampaignId,
        boolean permanent,
        String reason
    ) implements GoogleAdsCommandPayload {

        public DeleteCampaign {
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(googleAdsCampaignId, "googleAdsCampaignId must not be null");

            if (internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (googleAdsCampaignId.isBlank()) {
                throw new IllegalArgumentException("googleAdsCampaignId must not be blank");
            }
        }
    }

    /**
     * Campaign goal types for Google Ads.
     */
    enum CampaignGoal {
        SALES,
        LEADS,
        WEBSITE_TRAFFIC,
        PRODUCT_BRAND_CONSIDERATION,
        BRAND_AWARENESS_REACH,
        APP_PROMOTION
    }
}
