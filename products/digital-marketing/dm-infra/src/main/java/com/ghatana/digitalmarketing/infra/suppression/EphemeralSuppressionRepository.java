package com.ghatana.digitalmarketing.infra.suppression;

import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory suppression repository for development and tests.
 *
 * @doc.type class
 * @doc.purpose Development adapter for suppression and do-not-contact records
 * @doc.layer infra
 * @doc.pattern Repository
 */
public final class EphemeralSuppressionRepository implements SuppressionRepository {

    private final Map<String, SuppressionEntry> byId = new ConcurrentHashMap<>();
    private final Map<String, String> activeByWorkspaceAndHash = new ConcurrentHashMap<>();

    @Override
    public Promise<SuppressionEntry> save(SuppressionEntry entry) {
        byId.put(entry.getId(), entry);
        String lookupKey = lookupKey(entry.getWorkspaceId(), entry.getContactPointHash());
        if (entry.isActive()) {
            activeByWorkspaceAndHash.put(lookupKey, entry.getId());
        } else {
            activeByWorkspaceAndHash.remove(lookupKey);
        }
        return Promise.of(entry);
    }

    @Override
    public Promise<Optional<SuppressionEntry>> findActiveByContactPointHash(
            DmWorkspaceId workspaceId,
            String contactPointHash) {
        String id = activeByWorkspaceAndHash.get(lookupKey(workspaceId, contactPointHash));
        return Promise.of(Optional.ofNullable(id).map(byId::get).filter(SuppressionEntry::isActive));
    }

    private static String lookupKey(DmWorkspaceId workspaceId, String contactPointHash) {
        return workspaceId.getValue() + ":" + contactPointHash;
    }
}
