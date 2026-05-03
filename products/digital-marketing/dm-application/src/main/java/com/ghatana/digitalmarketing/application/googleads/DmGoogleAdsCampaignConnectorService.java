package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for Google Search campaign creation in Google Ads.
 *
 * @doc.type class
 * @doc.purpose Creates external Google Search campaigns from launched DMOS campaigns (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmGoogleAdsCampaignConnectorService {

    Promise<DmGoogleAdsCampaignLink> createSearchCampaign(
        DmOperationContext ctx,
        CreateSearchCampaignRequest request
    );

    Promise<Optional<DmGoogleAdsCampaignLink>> findByInternalCampaignId(DmOperationContext ctx, String campaignId);

    /**
     * Request to create an external Google Search campaign for an existing DMOS campaign.
     */
    record CreateSearchCampaignRequest(
        String connectorId,
        String internalCampaignId,
        BigDecimal dailyBudget,
        String serviceArea,
        String keywordTheme
    ) {
        public CreateSearchCampaignRequest {
            Objects.requireNonNull(connectorId, "connectorId must not be null");
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(dailyBudget, "dailyBudget must not be null");
            Objects.requireNonNull(serviceArea, "serviceArea must not be null");
            Objects.requireNonNull(keywordTheme, "keywordTheme must not be null");
            if (connectorId.isBlank()) throw new IllegalArgumentException("connectorId must not be blank");
            if (internalCampaignId.isBlank()) throw new IllegalArgumentException("internalCampaignId must not be blank");
            if (dailyBudget.signum() <= 0) throw new IllegalArgumentException("dailyBudget must be > 0");
            if (serviceArea.isBlank()) throw new IllegalArgumentException("serviceArea must not be blank");
            if (keywordTheme.isBlank()) throw new IllegalArgumentException("keywordTheme must not be blank");
        }
    }
}
