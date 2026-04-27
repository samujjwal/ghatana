package com.ghatana.datacloud.governance.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link ApprovalStore} for local / test use.
 *
 * @doc.type class
 * @doc.purpose In-memory approval store (P0.4)
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryApprovalStore implements ApprovalStore {

    private final Map<String, DestructiveActionApproval> approvals = new ConcurrentHashMap<>();

    @Override
    public void save(DestructiveActionApproval approval) {
        approvals.put(approval.approvalId(), approval);
    }

    @Override
    public Optional<DestructiveActionApproval> findById(String approvalId) {
        return Optional.ofNullable(approvals.get(approvalId));
    }

    @Override
    public List<DestructiveActionApproval> findByTenant(String tenantId, DestructiveActionApproval.Status status) {
        return approvals.values().stream()
            .filter(a -> a.tenantId().equals(tenantId))
            .filter(a -> status == null || a.status() == status)
            .collect(Collectors.toList());
    }

    @Override
    public void update(DestructiveActionApproval approval) {
        approvals.put(approval.approvalId(), approval);
    }
}
