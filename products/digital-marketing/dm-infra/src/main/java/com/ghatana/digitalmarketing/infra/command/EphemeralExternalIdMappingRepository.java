package com.ghatana.digitalmarketing.infra.command;

import com.ghatana.digitalmarketing.application.command.ExternalIdMappingRepository;
import com.ghatana.digitalmarketing.application.command.ExternalIdMappingRepository.ExternalIdMapping;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ExternalIdMappingRepository} for local and test execution.
 *
 * @doc.type class
 * @doc.purpose In-memory external-id mapping persistence adapter for DMOS tests
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class EphemeralExternalIdMappingRepository implements ExternalIdMappingRepository {

    private final Map<String, ExternalIdMapping> byInternal = new ConcurrentHashMap<>();
    private final Map<String, ExternalIdMapping> byExternal = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> save(DmOperationContext ctx, ExternalIdMapping mapping) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(mapping, "mapping must not be null");

        byInternal.put(internalKey(mapping.internalId(), mapping.externalSystem()), mapping);
        byExternal.put(externalKey(mapping.externalId(), mapping.externalSystem()), mapping);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<String>> findExternalId(DmOperationContext ctx, String internalId, String externalSystem) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(internalId, "internalId must not be null");
        Objects.requireNonNull(externalSystem, "externalSystem must not be null");

        ExternalIdMapping mapping = byInternal.get(internalKey(internalId, externalSystem));
        return Promise.of(Optional.ofNullable(mapping).map(ExternalIdMapping::externalId));
    }

    @Override
    public Promise<Optional<String>> findInternalId(DmOperationContext ctx, String externalId, String externalSystem) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(externalId, "externalId must not be null");
        Objects.requireNonNull(externalSystem, "externalSystem must not be null");

        ExternalIdMapping mapping = byExternal.get(externalKey(externalId, externalSystem));
        return Promise.of(Optional.ofNullable(mapping).map(ExternalIdMapping::internalId));
    }

    @Override
    public Promise<Void> delete(DmOperationContext ctx, String internalId, String externalSystem) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(internalId, "internalId must not be null");
        Objects.requireNonNull(externalSystem, "externalSystem must not be null");

        ExternalIdMapping removed = byInternal.remove(internalKey(internalId, externalSystem));
        if (removed != null) {
            byExternal.remove(externalKey(removed.externalId(), removed.externalSystem()));
        }
        return Promise.complete();
    }

    private static String internalKey(String internalId, String externalSystem) {
        return externalSystem + ":" + internalId;
    }

    private static String externalKey(String externalId, String externalSystem) {
        return externalSystem + ":" + externalId;
    }
}
