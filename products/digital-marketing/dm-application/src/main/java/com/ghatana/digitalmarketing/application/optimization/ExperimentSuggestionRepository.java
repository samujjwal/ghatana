package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestion;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for experiment suggestion persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for experiment suggestions (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ExperimentSuggestionRepository {

    /**
     * Save an experiment suggestion.
     *
     * @param suggestion the suggestion to save
     * @return Promise containing the saved suggestion
     */
    Promise<ExperimentSuggestion> save(ExperimentSuggestion suggestion);

    /**
     * Update an experiment suggestion.
     *
     * @param suggestion the suggestion to update
     * @return Promise containing the updated suggestion
     */
    Promise<ExperimentSuggestion> update(ExperimentSuggestion suggestion);

    /**
     * Find a suggestion by ID.
     *
     * @param id the suggestion ID
     * @return Promise containing optional suggestion
     */
    Promise<Optional<ExperimentSuggestion>> findById(String id);

    /**
     * List suggestions by tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise containing list of suggestions
     */
    Promise<List<ExperimentSuggestion>> listByTenant(String tenantId);

    /**
     * List suggestions by workspace.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @return Promise containing list of suggestions
     */
    Promise<List<ExperimentSuggestion>> listByWorkspace(String tenantId, String workspaceId);

    /**
     * List suggestions by campaign.
     *
     * @param tenantId the tenant ID
     * @param campaignId the campaign ID
     * @return Promise containing list of suggestions
     */
    Promise<List<ExperimentSuggestion>> listByCampaign(String tenantId, String campaignId);

    /**
     * List suggestions by status.
     *
     * @param tenantId the tenant ID
     * @param status the status to filter by
     * @return Promise containing list of suggestions
     */
    Promise<List<ExperimentSuggestion>> listByStatus(String tenantId, ExperimentSuggestionStatus status);
}
