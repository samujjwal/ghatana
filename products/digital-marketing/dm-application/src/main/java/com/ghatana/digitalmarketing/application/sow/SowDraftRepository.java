package com.ghatana.digitalmarketing.application.sow;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.sow.SowDraft;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for DMOS SOW draft persistence.
 *
 * @doc.type class
 * @doc.purpose SOW draft repository interface for F1-016
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SowDraftRepository {

    /**
     * Saves or updates a SOW draft.
     *
     * @param draft the draft to persist
     * @return a promise resolving to the persisted draft
     */
    Promise<SowDraft> save(SowDraft draft);

    /**
     * Finds the most recent SOW draft for the given workspace.
     *
     * @param workspaceId the workspace to query
     * @return a promise resolving to an optional containing the latest draft
     */
    Promise<Optional<SowDraft>> findLatestByWorkspace(DmWorkspaceId workspaceId);

    /**
     * Finds a SOW draft by its unique ID.
     *
     * @param sowId the draft ID
     * @return a promise resolving to an optional containing the draft if found
     */
    Promise<Optional<SowDraft>> findById(String sowId);
}
