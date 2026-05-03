package com.ghatana.digitalmarketing.application.approval;

import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Storage SPI for DMOS approval snapshots.
 *
 * @doc.type interface
 * @doc.purpose DMOS F1-022 approval snapshot persistence port
 * @doc.layer product
 * @doc.pattern Repository, SPI
 */
public interface ApprovalSnapshotRepository {

    /**
     * Saves a new approval snapshot.
     *
     * @param workspaceId workspace owning the snapshot
     * @param snapshot    the snapshot to persist
     * @return Promise resolving to the saved snapshot
     */
    Promise<ApprovalSnapshot> save(String workspaceId, ApprovalSnapshot snapshot);

    /**
     * Finds an approval snapshot by request ID.
     *
     * @param workspaceId workspace owning the snapshot
     * @param requestId   the approval request identifier
     * @return Promise resolving to the snapshot if found
     */
    Promise<Optional<ApprovalSnapshot>> findByRequestId(String workspaceId, String requestId);
}
