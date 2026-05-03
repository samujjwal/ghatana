package com.ghatana.digitalmarketing.application.suppression;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository interface for suppression and DNC records.
 *
 * @doc.type interface
 * @doc.purpose Persistence contract for workspace-scoped suppression records
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SuppressionRepository {

    Promise<SuppressionEntry> save(SuppressionEntry entry);

    Promise<Optional<SuppressionEntry>> findActiveByContactPointHash(DmWorkspaceId workspaceId, String contactPointHash);
}
