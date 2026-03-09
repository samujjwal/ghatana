package com.ghatana.datacloud.infrastructure.persistence.version;

import com.ghatana.datacloud.application.version.VersionComparator;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.version.*;
import io.activej.promise.Promise;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of VersionRecord for testing and development.
 *
 * <p><b>Purpose</b><br>
 * Provides fast, in-memory version storage without database dependency.
 * Useful for unit tests, integration tests, and local development.
 * Thread-safe using ConcurrentHashMap.
 *
 * <p><b>Storage Strategy</b><br>
 * - Key: "tenant:{tenantId}:entity:{entityId}"
 * - Value: List of EntityVersions indexed by version number
 * - Thread-safe via ConcurrentHashMap
 * - Supports concurrent reads and writes
 *
 * <p><b>Limitations</b><br>
 * - Data not persisted (lost on shutdown)
 * - Suitable for tests only, not production
 * - Unlimited growth (no cleanup)
 *
 * @doc.type class
 * @doc.purpose In-memory version repository (test implementation)
 * @doc.layer infrastructure
 * @doc.pattern Adapter (Hexagonal Architecture)
 */
public class InMemoryVersionRecord implements VersionRecord {

    private final Map<String, List<EntityVersion>> versionStore;
    private final VersionComparator comparator;

    /**
     * Creates an InMemoryVersionRecord.
     */
    public InMemoryVersionRecord() {
        this.versionStore = new ConcurrentHashMap<>();
        this.comparator = new VersionComparator();
    }

    @Override
    public Promise<EntityVersion> saveVersion(
            String tenantId,
            Entity entity,
            VersionMetadata metadata) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(metadata, "Metadata must not be null");

        String key = buildKey(tenantId, entity.getId());
        List<EntityVersion> versions = versionStore.computeIfAbsent(key, k -> new ArrayList<>());

        // Determine next version number
        Integer nextVersion = versions.isEmpty() ? 1 : versions.size() + 1;

        // Create and store version
        EntityVersion version = EntityVersion.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .entityId(entity.getId())
            .entitySnapshot(entity)
            .versionNumber(nextVersion)
            .metadata(metadata)
            .build();

        versions.add(version);
        return Promise.of(version);
    }

    @Override
    public Promise<List<EntityVersion>> getVersionHistory(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        String key = buildKey(tenantId, entityId);
        List<EntityVersion> versions = versionStore.getOrDefault(key, Collections.emptyList());
        return Promise.of(new ArrayList<>(versions));
    }

    @Override
    public Promise<EntityVersion> getVersion(
            String tenantId,
            UUID entityId,
            Integer versionNumber) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");
        Objects.requireNonNull(versionNumber, "Version number must not be null");

        if (versionNumber < 1) {
            return Promise.ofException(
                new IllegalArgumentException("Version number must be >= 1"));
        }

        String key = buildKey(tenantId, entityId);
        List<EntityVersion> versions = versionStore.getOrDefault(key, Collections.emptyList());

        return versions.stream()
            .filter(v -> v.getVersionNumber().equals(versionNumber))
            .findFirst()
            .map(Promise::of)
            .orElse(Promise.ofException(
                new NoSuchElementException("Version not found: " + versionNumber)));
    }

    @Override
    public Promise<EntityVersion> getLatestVersion(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        String key = buildKey(tenantId, entityId);
        List<EntityVersion> versions = versionStore.getOrDefault(key, Collections.emptyList());

        return versions.isEmpty()
            ? Promise.ofException(new NoSuchElementException("No versions found"))
            : Promise.of(versions.get(versions.size() - 1));
    }

    @Override
    public Promise<VersionDiff> computeDiff(
            String tenantId,
            UUID entityId,
            Integer versionNumberFrom,
            Integer versionNumberTo) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");
        Objects.requireNonNull(versionNumberFrom, "From version must not be null");
        Objects.requireNonNull(versionNumberTo, "To version must not be null");

        String key = buildKey(tenantId, entityId);
        List<EntityVersion> versions = versionStore.getOrDefault(key, Collections.emptyList());

        EntityVersion fromVersion = versions.stream()
            .filter(v -> v.getVersionNumber().equals(versionNumberFrom))
            .findFirst()
            .orElse(null);

        EntityVersion toVersion = versions.stream()
            .filter(v -> v.getVersionNumber().equals(versionNumberTo))
            .findFirst()
            .orElse(null);

        if (fromVersion == null || toVersion == null) {
            return Promise.ofException(
                new NoSuchElementException("One or both versions not found"));
        }

        VersionDiff diff = comparator.compare(
            fromVersion.getEntitySnapshot(),
            toVersion.getEntitySnapshot());

        return Promise.of(diff);
    }

    @Override
    public Promise<Integer> countVersions(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        String key = buildKey(tenantId, entityId);
        List<EntityVersion> versions = versionStore.getOrDefault(key, Collections.emptyList());
        return Promise.of(versions.size());
    }

    @Override
    public Promise<Integer> deleteVersionHistory(String tenantId, UUID entityId) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");

        String key = buildKey(tenantId, entityId);
        List<EntityVersion> removed = versionStore.remove(key);
        int count = removed == null ? 0 : removed.size();
        return Promise.of(count);
    }

    /**
     * Clears all stored versions (useful for testing).
     *
     * @return Promise of void
     */
    public Promise<Void> clear() {
        versionStore.clear();
        return Promise.of(null);
    }

    /**
     * Gets count of entities with versions (for testing).
     *
     * @return count
     */
    public int getEntityCount() {
        return versionStore.size();
    }

    /**
     * Builds the storage key for an entity version list.
     *
     * @param tenantId tenant ID
     * @param entityId entity ID
     * @return storage key
     */
    private String buildKey(String tenantId, UUID entityId) {
        return String.format("tenant:%s:entity:%s", tenantId, entityId);
    }
}
