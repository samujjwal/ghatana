package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import io.activej.promise.Promise;

/**
 * Loads preflight evidence used for campaign launch compliance checks.
 *
 * <p>The provider returns product-owned, tenant/workspace-scoped data required by
 * {@code DM_CAMPAIGN_PREFLIGHT} rules, such as approved budget and audience/content readiness.
 * Implementations may aggregate data from budget, audience, content, and spend stores.</p>
 *
 * @doc.type interface
 * @doc.purpose Resolve campaign preflight evidence for DMOS compliance evaluation
 * @doc.layer product
 * @doc.pattern Port
 */
public interface CampaignPreflightDataProvider {

    /**
     * Resolves immutable preflight data for a campaign launch decision.
     *
     * @param ctx operation context carrying tenant/workspace and actor identity
     * @param campaign campaign being evaluated for launch readiness
     * @return promise resolving to preflight data used by compliance checks
     */
    Promise<CampaignPreflightData> resolve(DmOperationContext ctx, Campaign campaign);

    /**
     * Immutable preflight evidence consumed by campaign preflight compliance rules.
     *
     * @param budgetApproved whether a valid budget has been approved for this campaign
     * @param targetAudienceCount number of target audience segments assigned to the campaign
     * @param approvedContentCount number of approved content assets assigned to the campaign
     * @param totalSpend accumulated spend for the campaign
     * @param approvedBudget approved spend ceiling for the campaign
     */
    record CampaignPreflightData(
        boolean budgetApproved,
        int targetAudienceCount,
        int approvedContentCount,
        double totalSpend,
        double approvedBudget
    ) {
        /**
         * Validates numeric preflight invariants at construction time.
         */
        public CampaignPreflightData {
            if (targetAudienceCount < 0) {
                throw new IllegalArgumentException("targetAudienceCount must be >= 0");
            }
            if (approvedContentCount < 0) {
                throw new IllegalArgumentException("approvedContentCount must be >= 0");
            }
            if (totalSpend < 0) {
                throw new IllegalArgumentException("totalSpend must be >= 0");
            }
            if (approvedBudget < 0) {
                throw new IllegalArgumentException("approvedBudget must be >= 0");
            }
        }
    }
}
