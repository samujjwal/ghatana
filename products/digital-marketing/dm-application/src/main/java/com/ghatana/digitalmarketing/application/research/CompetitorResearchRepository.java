package com.ghatana.digitalmarketing.application.research;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for storing and retrieving competitor research snapshots.
 *
 * @doc.type interface
 * @doc.purpose DMOS competitor research persistence contract for F1-011
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CompetitorResearchRepository {

    /**
     * Persists a new competitor research snapshot.
     *
     * @param snapshot the snapshot to persist
     * @return promise resolving to the saved snapshot
     */
    Promise<CompetitorResearchSnapshot> save(CompetitorResearchSnapshot snapshot);

    /**
     * Retrieves the most recent research snapshot for the given workspace.
     *
     * @param workspaceId the target workspace
     * @return promise resolving to an optional snapshot
     */
    Promise<Optional<CompetitorResearchSnapshot>> findLatestByWorkspace(DmWorkspaceId workspaceId);
}
