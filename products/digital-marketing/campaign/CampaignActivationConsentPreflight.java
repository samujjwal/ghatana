package com.ghatana.digitalmarketing.campaign;

import com.ghatana.kernel.interaction.ProductInteractionBroker;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;

import io.activej.promise.Promise;

/**
 * Preflight check for campaign activation that verifies PHR consent.
 *
 * <p>This check must pass before a campaign can be activated. It uses the Kernel
 * interaction broker to query PHR consent status through the governed interaction system.</p>
 *
 * @doc.type class
 * @doc.purpose Preflight check for campaign activation consent verification
 * @doc.layer digital-marketing
 * @doc.pattern PreflightCheck
 */
public final class CampaignActivationConsentPreflight {

    private final ProductInteractionBroker interactionBroker;

    public CampaignActivationConsentPreflight(ProductInteractionBroker interactionBroker) {
        this.interactionBroker = interactionBroker;
    }

    /**
     * Performs consent preflight check before campaign activation.
     *
     * @param campaignId the campaign identifier
     * @param customerId the customer identifier
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @return the preflight check result
     */
    public Promise<CampaignActivationPreflightResult> check(
        String campaignId,
        String customerId,
        String tenantId,
        String workspaceId
    ) {
        if (customerId == null || customerId.isBlank()) {
            return Promise.of(CampaignActivationPreflightResult.denied(
                campaignId,
                "customer_id_required",
                "Customer ID is required for campaign activation"
            ));
        }

        // Build interaction request for consent check
        ProductInteractionRequest<?> request = ProductInteractionRequest.builder()
            .contractId("kernel://interactions/phr.consent-status.v1")
            .contractVersion("1.0.0")
            .providerProductId("phr")
            .consumerProductId("digital-marketing")
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .productUnitId("digital-marketing")
            .interactionId("campaign-activation-consent-check")
            .policyContext(buildPolicyContext(campaignId, customerId, tenantId, workspaceId))
            .build();

        return interactionBroker.execute(request)
            .map(outcome -> {
                if (outcome.status() == ProductInteractionStatus.SUCCEEDED) {
                    return CampaignActivationPreflightResult.allowed(
                        campaignId,
                        customerId,
                        "Consent granted for campaign activation"
                    );
                } else {
                    return CampaignActivationPreflightResult.denied(
                        campaignId,
                        outcome.reasonCode(),
                        "Campaign activation denied: " + outcome.reasonCode()
                    );
                }
            });
    }

    private java.util.Map<String, String> buildPolicyContext(
        String campaignId,
        String customerId,
        String tenantId,
        String workspaceId
    ) {
        java.util.Map<String, String> context = new java.util.HashMap<>();
        context.put("purpose", "campaign-activation");
        context.put("actor", "digital-marketing-system");
        context.put("tenantId", tenantId);
        context.put("workspaceId", workspaceId);
        context.put("subjectId", customerId);
        context.put("consentType", "marketing-communications");
        context.put("authorized", "true");
        context.put("campaignId", campaignId);
        return context;
    }

    /**
     * Preflight check result.
     */
    public record CampaignActivationPreflightResult(
        String campaignId,
        boolean allowed,
        String reasonCode,
        String message,
        String customerId
    ) {
        public static CampaignActivationPreflightResult allowed(
            String campaignId,
            String customerId,
            String message
        ) {
            return new CampaignActivationPreflightResult(campaignId, true, null, message, customerId);
        }

        public static CampaignActivationPreflightResult denied(
            String campaignId,
            String reasonCode,
            String message
        ) {
            return new CampaignActivationPreflightResult(campaignId, false, reasonCode, message, null);
        }
    }
}
