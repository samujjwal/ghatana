package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Application service interface for DMOS campaign use cases.
 *
 * <p>{@code CampaignService} exposes the authoritative API for campaign lifecycle
 * management. All operations carry a {@link DmOperationContext} for tenant isolation,
 * authorization, and audit propagation.</p>
 *
 * <p>The production implementation is {@link CampaignServiceImpl}.</p>
 *
 * @doc.type interface
 * @doc.purpose Application service contract for DMOS campaign lifecycle use cases
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface CampaignService {

    /**
     * Creates a new campaign in DRAFT status.
     *
     * @param ctx        the operation context
     * @param command    the campaign creation command
     * @return promise resolving to the created campaign
     */
    Promise<Campaign> createCampaign(DmOperationContext ctx, CreateCampaignCommand command);

    /**
     * Lists campaigns for a workspace with pagination.
     *
     * @param ctx    the operation context
     * @param limit  maximum number of results (max 100)
     * @param offset pagination offset
     * @return promise resolving to a paginated list of campaigns
     */
    Promise<List<Campaign>> listCampaigns(DmOperationContext ctx, int limit, int offset);

    /**
     * Launches a campaign that is in DRAFT or PAUSED status.
     *
     * <p>Enforces:
     * <ul>
     *   <li>Authorization: {@code campaigns/{id}} → {@code launch}</li>
     *   <li>Compliance preflight: {@link com.ghatana.digitalmarketing.pack.DmComplianceRuleSetIds#DM_CAMPAIGN_PREFLIGHT}</li>
     *   <li>Human approval gate (async) via the kernel bridge</li>
     *   <li>Audit recording on completion</li>
     * </ul>
     *
     * @param ctx        the operation context; must have an idempotency key for write
     * @param campaignId the ID of the campaign to launch
     * @return promise resolving to the launched campaign
     */
    Promise<Campaign> launchCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Pauses a campaign that is in LAUNCHED status.
     *
     * @param ctx        the operation context; must have an idempotency key for write
     * @param campaignId the ID of the campaign to pause
     * @return promise resolving to the paused campaign
     */
    Promise<Campaign> pauseCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Marks a campaign as COMPLETED.
     *
     * <p>P1-005: Extends campaign lifecycle beyond create/launch/pause to include
     * completion, archive, and rollback operations with event publishing.</p>
     *
     * @param ctx        the operation context; must have an idempotency key for write
     * @param campaignId the ID of the campaign to complete
     * @return promise resolving to the completed campaign
     */
    Promise<Campaign> completeCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Archives a campaign that is in COMPLETED status.
     *
     * <p>P1-005: Extends campaign lifecycle beyond create/launch/pause to include
     * completion, archive, and rollback operations with event publishing.</p>
     *
     * @param ctx        the operation context; must have an idempotency key for write
     * @param campaignId the ID of the campaign to archive
     * @return promise resolving to the archived campaign
     */
    Promise<Campaign> archiveCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Rolls back a campaign to DRAFT status.
     *
     * <p>P1-005: Extends campaign lifecycle beyond create/launch/pause to include
     * completion, archive, and rollback operations with event publishing.</p>
     *
     * @param ctx        the operation context; must have an idempotency key for write
     * @param campaignId the ID of the campaign to rollback
     * @return promise resolving to the rolled-back campaign
     */
    Promise<Campaign> rollbackCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Retrieves a campaign by ID within the workspace in the operation context.
     *
     * @param ctx        the operation context
     * @param campaignId the campaign ID
     * @return promise resolving to the campaign
     */
    Promise<Campaign> getCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Command object for campaign creation.
     *
     * @param name the campaign name; must not be blank
     * @param type the campaign channel type
     */
    record CreateCampaignCommand(String name, CampaignType type) {
        public CreateCampaignCommand {
            java.util.Objects.requireNonNull(name, "name must not be null");
            java.util.Objects.requireNonNull(type, "type must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }
}
