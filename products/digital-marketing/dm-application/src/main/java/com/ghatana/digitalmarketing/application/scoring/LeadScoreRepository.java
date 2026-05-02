package com.ghatana.digitalmarketing.application.scoring;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import io.activej.promise.Promise;

/**
 * Persistence boundary for {@link LeadScore} aggregates.
 *
 * @doc.type class
 * @doc.purpose Storage contract for F1-012 lead scores
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface LeadScoreRepository {

    /**
     * Persists a new lead score, overwriting any previous score for the same workspace.
     *
     * @param score the lead score to save
     * @return the saved score
     */
    Promise<LeadScore> save(LeadScore score);

    /**
     * Retrieves the most recent lead score for a workspace.
     *
     * @param workspaceId the workspace to query
     * @return the latest score, or a failed promise with {@link java.util.NoSuchElementException}
     */
    Promise<LeadScore> findLatestByWorkspace(DmWorkspaceId workspaceId);
}
