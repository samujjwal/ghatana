package com.ghatana.digitalmarketing.application.proposal;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.proposal.Proposal;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for persisting and retrieving {@link Proposal} aggregates.
 *
 * <p>All operations are non-blocking and return ActiveJ {@link Promise}.</p>
 *
 * @doc.type interface
 * @doc.purpose Persistence port for DMOS proposal aggregates (F1-015)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ProposalRepository {

    /**
     * Persists or updates the given proposal.
     *
     * @param proposal the proposal to save; must not be null
     * @return promise resolving to the saved proposal
     */
    Promise<Proposal> save(Proposal proposal);

    /**
     * Returns the most recently generated proposal for the given workspace.
     *
     * @param workspaceId the workspace to query; must not be null
     * @return promise resolving to an {@link Optional} containing the latest proposal,
     *         or {@link Optional#empty()} if none exists
     */
    Promise<Optional<Proposal>> findLatestByWorkspace(DmWorkspaceId workspaceId);

    /**
     * Finds a proposal by its unique identifier.
     *
     * @param proposalId the proposal ID; must not be null or blank
     * @return promise resolving to an {@link Optional} containing the proposal,
     *         or {@link Optional#empty()} if not found
     */
    Promise<Optional<Proposal>> findById(String proposalId);
}
