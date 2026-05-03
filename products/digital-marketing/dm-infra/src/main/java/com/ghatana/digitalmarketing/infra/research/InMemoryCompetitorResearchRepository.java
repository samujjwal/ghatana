package com.ghatana.digitalmarketing.infra.research;

import com.ghatana.digitalmarketing.application.research.CompetitorResearchRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link CompetitorResearchRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Only one snapshot per workspace is retained (the most recently saved one),
 * consistent with the "latest research" read model in the application layer.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory competitor research snapshot adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryCompetitorResearchRepository implements CompetitorResearchRepository {

    private final ConcurrentHashMap<String, CompetitorResearchSnapshot> store = new ConcurrentHashMap<>();

    @Override
    public Promise<CompetitorResearchSnapshot> save(CompetitorResearchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        store.put(snapshot.getWorkspaceId().getValue(), snapshot);
        return Promise.of(snapshot);
    }

    @Override
    public Promise<Optional<CompetitorResearchSnapshot>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        return Promise.of(Optional.ofNullable(store.get(workspaceId.getValue())));
    }
}
