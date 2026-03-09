package com.ghatana.datacloud.infrastructure.persistence.storage;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.entity.storage.CollectionStorageProfileRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of CollectionStorageProfileRepository.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides fast, tenant-scoped in-memory storage for collection profiles.
 * Suitable for testing, development, and single-node deployments.
 * Not recommended for production with multiple instances.
 *
 * <p>
 * <b>Storage Strategy</b><br>
 * Uses nested ConcurrentHashMap for tenant→collection→profile mapping:
 * - Key format: "tenantId:collectionName" in outer map
 * - Thread-safe without explicit locking
 * - O(1) lookup by tenant and collection name
 * - O(n) iteration by tenant (requires filtering)
 *
 * <p>
 * <b>Uniqueness Enforcement</b><br>
 * Enforces (tenantId, collectionName) uniqueness by composite key.
 * Save operation automatically updates if key exists.
 *
 * <p>
 * <b>Memory Characteristics</b><br>
 * - Memory usage: ~500 bytes per profile
 * - No automatic eviction (manual cleanup required)
 * - Keep in-memory for < 100k profiles per instance
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * // Create instance
 * CollectionStorageProfileRepository repo = new InMemoryCollectionStorageProfileRepository();
 *
 * // Save profile
 * CollectionStorageProfile profile = CollectionStorageProfile.builder()
 *                 .tenantId("tenant-1")
 *                 .collectionName("products")
 *                 .primaryBackendId("postgres-1")
 *                 .build();
 * CollectionStorageProfile saved = runPromise(() -> repo.save(profile));
 *
 * // Retrieve
 * Optional<CollectionStorageProfile> found = runPromise(() -> repo.findByTenantAndName("tenant-1", "products"));
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * All operations are thread-safe via ConcurrentHashMap.
 * Multiple threads can read/write concurrently.
 *
 * @see CollectionStorageProfileRepository
 * @see CollectionStorageProfile
 * @see java.util.concurrent.ConcurrentHashMap
 * @doc.type class
 * @doc.purpose In-memory collection profile repository implementation
 * @doc.layer infrastructure
 * @doc.pattern Repository Adapter
 */
public class InMemoryCollectionStorageProfileRepository
                implements CollectionStorageProfileRepository {

        private static final Logger logger = LoggerFactory.getLogger(
                        InMemoryCollectionStorageProfileRepository.class);

        /**
         * Storage map: "tenantId:collectionName" → CollectionStorageProfile
         * Uses composite key to enforce (tenant, collection) uniqueness
         */
        private final ConcurrentHashMap<String, CollectionStorageProfile> storage;

        /**
         * Creates new in-memory repository with empty storage.
         */
        public InMemoryCollectionStorageProfileRepository() {
                this.storage = new ConcurrentHashMap<>();
        }

        @Override
        public Promise<CollectionStorageProfile> save(CollectionStorageProfile profile) {
                if (profile == null) {
                        logger.error("Cannot save null profile");
                        return Promise.ofException(
                                        new IllegalArgumentException("Profile cannot be null"));
                }

                if (profile.getTenantId() == null || profile.getTenantId().isBlank()) {
                        logger.error("Cannot save profile without tenantId");
                        return Promise.ofException(
                                        new IllegalArgumentException("Profile must have non-blank tenantId"));
                }

                if (profile.getCollectionName() == null || profile.getCollectionName().isBlank()) {
                        logger.error("Cannot save profile without collectionName");
                        return Promise.ofException(
                                        new IllegalArgumentException("Profile must have non-blank collectionName"));
                }

                // Generate ID if not set: rebuild via builder using existing properties.
                if (profile.getId() == null) {
                        profile = CollectionStorageProfile.builder()
                                        .tenantId(profile.getTenantId())
                                        .collectionName(profile.getCollectionName())
                                        .storageProfileId(profile.getStorageProfileId())
                                        .primaryBackendId(profile.getPrimaryBackendId())
                                        .fallbackBackendIds(profile.getFallbackBackendIds())
                                        .backendConfig(profile.getBackendConfig())
                                        .isActive(profile.getIsActive())
                                        .priorityOrder(profile.getPriorityOrder())
                                        .createdAt(profile.getCreatedAt())
                                        .updatedAt(profile.getUpdatedAt())
                                        .build();
                }

                String key = makeKey(profile.getTenantId(), profile.getCollectionName());
                storage.put(key, profile);

                logger.debug(
                                "Saved collection storage profile [tenant={}, collection={}]",
                                profile.getTenantId(),
                                profile.getCollectionName());

                return Promise.of(profile);
        }

        @Override
        public Promise<Optional<CollectionStorageProfile>> findByTenantAndName(
                        String tenantId,
                        String collectionName) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                if (collectionName == null || collectionName.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("collectionName cannot be null or blank"));
                }

                String key = makeKey(tenantId, collectionName);
                CollectionStorageProfile profile = storage.get(key);

                logger.debug(
                                "Retrieved collection storage profile [tenant={}, collection={}, found={}]",
                                tenantId,
                                collectionName,
                                profile != null);

                return Promise.of(Optional.ofNullable(profile));
        }

        @Override
        public Promise<Optional<CollectionStorageProfile>> findByTenantAndId(
                        String tenantId,
                        UUID profileId) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                if (profileId == null) {
                        return Promise.ofException(
                                        new IllegalArgumentException("profileId cannot be null"));
                }

                // Linear search through all entries (inefficient, but necessary for ID lookup)
                CollectionStorageProfile found = storage.values()
                                .stream()
                                .filter(p -> p.getTenantId().equals(tenantId)
                                                && p.getId().equals(profileId.toString()))
                                .findFirst()
                                .orElse(null);

                logger.debug(
                                "Retrieved collection storage profile by ID [tenant={}, id={}, found={}]",
                                tenantId,
                                profileId,
                                found != null);

                return Promise.of(Optional.ofNullable(found));
        }

        @Override
        public Promise<List<CollectionStorageProfile>> findAllByTenant(String tenantId) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                List<CollectionStorageProfile> profiles = storage.values()
                                .stream()
                                .filter(p -> p.getTenantId().equals(tenantId))
                                .collect(Collectors.toUnmodifiableList());

                logger.debug(
                                "Listed all collection storage profiles [tenant={}, count={}]",
                                tenantId,
                                profiles.size());

                return Promise.of(profiles);
        }

        @Override
        public Promise<List<CollectionStorageProfile>> findByTenantAndFailoverSupport(
                        String tenantId,
                        boolean hasFailover) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                List<CollectionStorageProfile> profiles = storage.values()
                                .stream()
                                .filter(p -> p.getTenantId().equals(tenantId))
                                .filter(p -> p.hasFailoverSupport() == hasFailover)
                                .collect(Collectors.toUnmodifiableList());

                logger.debug(
                                "Listed collection storage profiles with failover [tenant={}, hasFailover={}, count={}]",
                                tenantId,
                                hasFailover,
                                profiles.size());

                return Promise.of(profiles);
        }

        @Override
        public Promise<Void> deleteByTenantAndName(String tenantId, String collectionName) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                if (collectionName == null || collectionName.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("collectionName cannot be null or blank"));
                }

                String key = makeKey(tenantId, collectionName);
                storage.remove(key);

                logger.debug(
                                "Deleted collection storage profile [tenant={}, collection={}]",
                                tenantId,
                                collectionName);

                return Promise.complete();
        }

        @Override
        public Promise<Void> deleteByTenantAndId(String tenantId, UUID profileId) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                if (profileId == null) {
                        return Promise.ofException(
                                        new IllegalArgumentException("profileId cannot be null"));
                }

                storage.values()
                                .removeIf(p -> p.getTenantId().equals(tenantId)
                                                && p.getId().equals(profileId.toString()));

                logger.debug(
                                "Deleted collection storage profile by ID [tenant={}, id={}]",
                                tenantId,
                                profileId);

                return Promise.complete();
        }

        @Override
        public Promise<Long> countByTenant(String tenantId) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                long count = storage.values()
                                .stream()
                                .filter(p -> p.getTenantId().equals(tenantId))
                                .count();

                logger.debug("Counted collection storage profiles [tenant={}, count={}]", tenantId, count);

                return Promise.of(count);
        }

        @Override
        public Promise<Boolean> existsByTenantAndName(String tenantId, String collectionName) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                if (collectionName == null || collectionName.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("collectionName cannot be null or blank"));
                }

                String key = makeKey(tenantId, collectionName);
                boolean exists = storage.containsKey(key);

                logger.debug(
                                "Checked existence of collection storage profile [tenant={}, collection={}, exists={}]",
                                tenantId,
                                collectionName,
                                exists);

                return Promise.of(exists);
        }

        @Override
        public Promise<Long> deleteAllByTenant(String tenantId) {
                if (tenantId == null || tenantId.isBlank()) {
                        return Promise.ofException(
                                        new IllegalArgumentException("tenantId cannot be null or blank"));
                }

                List<String> keysToDelete = storage.keySet()
                                .stream()
                                .filter(key -> key.startsWith(tenantId + ":"))
                                .collect(Collectors.toList());

                keysToDelete.forEach(storage::remove);

                logger.warn(
                                "Deleted all collection storage profiles for tenant [tenant={}, count={}]",
                                tenantId,
                                keysToDelete.size());

                return Promise.of((long) keysToDelete.size());
        }

        /**
         * Creates composite key from tenant ID and collection name.
         *
         * @param tenantId       tenant identifier
         * @param collectionName collection name
         * @return composite key "tenantId:collectionName"
         */
        private String makeKey(String tenantId, String collectionName) {
                return tenantId + ":" + collectionName;
        }

        /**
         * Clears all stored profiles (test/debug only).
         */
        public void clear() {
                storage.clear();
                logger.info("Cleared all collection storage profiles from in-memory repository");
        }

        /**
         * Gets current repository size (test/debug only).
         */
        public int size() {
                return storage.size();
        }
}
