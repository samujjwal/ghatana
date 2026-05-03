package com.ghatana.digitalmarketing.infra.transparency;

import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AiActionLogRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Entries are keyed by {@code "<workspaceId>:<actionId>"}. The workspace-scoped list
 * query performs a linear scan and applies the provided filters for correlation ID,
 * related entity ID, and limit.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory AI action log persistence adapter for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryAiActionLogRepository implements AiActionLogRepository {

    private final ConcurrentHashMap<String, AiActionLogEntry> store = new ConcurrentHashMap<>();

    @Override
    public Promise<AiActionLogEntry> save(AiActionLogEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        store.put(key(entry.workspaceId(), entry.actionId()), entry);
        return Promise.of(entry);
    }

    @Override
    public Promise<Optional<AiActionLogEntry>> findById(String workspaceId, String actionId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(actionId, "actionId must not be null");
        return Promise.of(Optional.ofNullable(store.get(key(workspaceId, actionId))));
    }

    @Override
    public Promise<List<AiActionLogEntry>> findByWorkspace(
            String workspaceId,
            String correlationId,
            String relatedEntityId,
            int limit) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        List<AiActionLogEntry> result = new ArrayList<>();
        for (AiActionLogEntry entry : store.values()) {
            if (!entry.workspaceId().equals(workspaceId)) {
                continue;
            }
            if (correlationId != null && !correlationId.equals(entry.correlationId())) {
                continue;
            }
            if (relatedEntityId != null && !relatedEntityId.equals(entry.relatedEntityId())) {
                continue;
            }
            result.add(entry);
            if (limit > 0 && result.size() >= limit) {
                break;
            }
        }
        return Promise.of(result);
    }

    private static String key(String workspaceId, String actionId) {
        return workspaceId + ":" + actionId;
    }
}
