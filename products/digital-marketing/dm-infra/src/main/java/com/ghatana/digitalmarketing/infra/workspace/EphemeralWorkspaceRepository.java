package com.ghatana.digitalmarketing.infra.workspace;

import com.ghatana.digitalmarketing.application.workspace.WorkspaceRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link WorkspaceRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Keys are composed as {@code "<tenantId>:<workspaceId>"} to enforce tenant isolation
 * structurally. This adapter is suitable for local development, integration tests, and
 * single-instance deployments where durability across restarts is not required.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory workspace persistence adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class EphemeralWorkspaceRepository implements WorkspaceRepository {

    private final ConcurrentHashMap<String, Workspace> store = new ConcurrentHashMap<>();

    @Override
    public Promise<Workspace> save(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace must not be null");
        store.put(key(workspace.getTenantId(), workspace.getId()), workspace);
        return Promise.of(workspace);
    }

    @Override
    public Promise<Optional<Workspace>> findById(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        return Promise.of(Optional.ofNullable(store.get(key(tenantId, workspaceId))));
    }

    @Override
    public Promise<List<Workspace>> listByTenant(DmTenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String prefix = tenantId.getValue() + ":";
        List<Workspace> result = new ArrayList<>();
        for (var entry : store.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        return Promise.of(result);
    }

    private static String key(DmTenantId tenantId, DmWorkspaceId workspaceId) {
        return tenantId.getValue() + ":" + workspaceId.getValue();
    }
}
