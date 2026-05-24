package com.ghatana.digitalmarketing.infra.approval;

import com.ghatana.digitalmarketing.application.approval.ApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ApprovalSnapshotRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Keys are composed as {@code "<workspaceId>:<requestId>"} to scope snapshots within
 * their workspace and ensure distinct per-request storage.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory approval snapshot persistence adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class EphemeralApprovalSnapshotRepository implements ApprovalSnapshotRepository {

    private final ConcurrentHashMap<String, ApprovalSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public Promise<ApprovalSnapshot> save(String workspaceId, ApprovalSnapshot snapshot) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        store.put(key(workspaceId, snapshot.requestId()), snapshot);
        return Promise.of(snapshot);
    }

    @Override
    public Promise<Optional<ApprovalSnapshot>> findByRequestId(String workspaceId, String requestId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        return Promise.of(Optional.ofNullable(store.get(key(workspaceId, requestId))));
    }

    private static String key(String workspaceId, String requestId) {
        return workspaceId + ":" + requestId;
    }
}
