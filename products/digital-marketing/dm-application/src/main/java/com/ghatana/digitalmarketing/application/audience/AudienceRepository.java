package com.ghatana.digitalmarketing.application.audience;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audience.Audience;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository interface for DMOS audience segment persistence.
 *
 * @doc.type interface
 * @doc.purpose Audience segment persistence contract for DMOS application services
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AudienceRepository {

    /**
     * Saves an audience (insert or update by ID within the workspace).
     *
     * @param audience the audience to save; must not be null
     * @return promise resolving to the saved audience
     */
    Promise<Audience> save(Audience audience);

    /**
     * Finds an audience by ID within the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param audienceId  the audience ID; must not be null
     * @return promise resolving to an optional audience
     */
    Promise<Optional<Audience>> findById(DmWorkspaceId workspaceId, String audienceId);

    /**
     * Finds the primary audience segment associated with a campaign.
     *
     * <p>A campaign may have a single primary audience or multiple. This method returns
     * the first audience whose ID is associated with the given campaign, if any.</p>
     *
     * @param workspaceId the owning workspace; must not be null
     * @param campaignId  the campaign ID; must not be null
     * @return promise resolving to an optional audience
     */
    Promise<Optional<Audience>> findByCampaign(DmWorkspaceId workspaceId, String campaignId);

    /**
     * Finds all audiences that contain a specific contact ID.
     *
     * <p>This is used for consent revocation workflows - when a patient revokes consent,
     * all audiences containing that patient's contact ID must be disabled.</p>
     *
     * @param contactId the contact ID to search for; must not be null
     * @return promise resolving to a list of audiences containing the contact ID
     */
    Promise<java.util.List<Audience>> findByContactId(String contactId);

    /**
     * Disables an audience with a specified reason.
     *
     * <p>Disabled audiences cannot be used for campaign targeting. This is used when
     * consent is revoked or when compliance rules require audience deactivation.</p>
     *
     * @param audienceId the audience ID to disable; must not be null
     * @param reason the reason for disabling; must not be null
     * @return promise completing when the audience is disabled
     */
    Promise<Void> disableAudience(String audienceId, String reason);
}
