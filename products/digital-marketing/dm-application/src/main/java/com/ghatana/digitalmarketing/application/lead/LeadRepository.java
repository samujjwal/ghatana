package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.lead.Lead;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DMOS CRM-lite lead persistence.
 *
 * @doc.type interface
 * @doc.purpose Lead capture persistence contract for DMOS application services
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface LeadRepository {

    /**
     * Saves a lead (insert or update by ID within the workspace).
     *
     * @param lead the lead to save; must not be null
     * @return promise resolving to the saved lead
     */
    Promise<Lead> save(Lead lead);

    /**
     * Finds a lead by ID within the given workspace.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param leadId      the lead ID; must not be null
     * @return promise resolving to an optional lead
     */
    Promise<Optional<Lead>> findById(DmWorkspaceId workspaceId, String leadId);

    /**
     * Lists all leads captured for a given campaign.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param campaignId  the campaign ID; must not be null
     * @return promise resolving to a list of leads; never null
     */
    Promise<List<Lead>> findByCampaign(DmWorkspaceId workspaceId, String campaignId);

    /**
     * Checks whether a lead with the given email already exists in the campaign.
     *
     * @param workspaceId the owning workspace; must not be null
     * @param campaignId  the campaign ID; must not be null
     * @param email       the email address to check; must not be null
     * @return promise resolving to true if a lead already exists for this email+campaign
     */
    Promise<Boolean> existsByEmail(DmWorkspaceId workspaceId, String campaignId, String email);
}
