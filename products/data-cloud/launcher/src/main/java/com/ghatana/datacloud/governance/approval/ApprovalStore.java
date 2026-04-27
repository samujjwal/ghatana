package com.ghatana.datacloud.governance.approval;

import java.util.List;
import java.util.Optional;

/**
 * Repository for destructive-action approval requests.
 *
 * @doc.type interface
 * @doc.purpose Store and retrieve approval records (P0.4)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ApprovalStore {

    /**
     * Persist a new approval request.
     */
    void save(DestructiveActionApproval approval);

    /**
     * Retrieve an approval by its unique id.
     */
    Optional<DestructiveActionApproval> findById(String approvalId);

    /**
     * List all approvals for a tenant, optionally filtered by status.
     */
    List<DestructiveActionApproval> findByTenant(String tenantId, DestructiveActionApproval.Status status);

    /**
     * Overwrite an existing approval (e.g., after approve/reject).
     */
    void update(DestructiveActionApproval approval);
}
