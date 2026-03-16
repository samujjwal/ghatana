/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.backup;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-grade backup and recovery service for AEP entity data.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Full backups</b> — snapshots all entities across configured collections
 *       for a given tenant into a dedicated {@value #BACKUP_COLLECTION} collection in
 *       Data-Cloud. Each backup is tagged with an ID, tenant ID, timestamp, and
 *       schema version for reliable restore identification.</li>
 *   <li><b>Incremental backups</b> — snapshots only entities updated since a
 *       supplied {@code sinceInstant} watermark, keeping backup bandwidth low for
 *       frequently-changed tenants.</li>
 *   <li><b>Point-in-time restore</b> — replays backup entities back into their
 *       source collections from a specific backup snapshot.</li>
 *   <li><b>Backup inventory</b> — lists all backup metadata for a tenant,
 *       enabling automated retention management.</li>
 *   <li><b>Backup verification</b> — validates that a backup's recorded entity
 *       count matches the actual stored snapshot size.</li>
 *   <li><b>Observability</b> — Micrometer counters and timers for every backup
 *       run, restore, verification, and error event.</li>
 * </ul>
 *
 * <h3>Backup Entity Layout in Data-Cloud</h3>
 * <pre>
 * Collection: aep_backups
 * Entity ID : backupId (UUID string)
 * Fields:
 *   backupId        : String  — UUID of this backup
 *   tenantId        : String  — owning tenant
 *   backupType      : String  — "FULL" or "INCREMENTAL"
 *   collections     : String  — comma-separated list of backed-up collections
 *   entityCount     : int     — total entities stored in this backup
 *   snapshotStart   : String  — ISO-8601 backup window start (null for FULL)
 *   createdAt       : String  — ISO-8601 creation timestamp
 *   schemaVersion   : int     — backup schema version (currently: 1)
 *   status          : String  — "COMPLETE" or "FAILED"
 *   backupPrefix    : String  — collection prefix used for data entities
 * </pre>
 *
 * <h3>Data Collection Naming</h3>
 * <p>Backup entity data is stored in a collection named
 * {@code aep_backup_<backupId>_<sourceCollection>}. This allows restoring
 * individual collections without touching others.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepBackupRecoveryService backup = injector.getInstance(AepBackupRecoveryService.class);
 *
 * // Full backup
 * BackupResult result = backup.createFullBackup("tenant-acme").await();
 *
 * // Incremental backup since last night
 * BackupResult result = backup.createIncrementalBackup("tenant-acme", Instant.now().minus(Duration.ofHours(24))).await();
 *
 * // Restore from a backup
 * RestoreResult restore = backup.restoreFromBackup("tenant-acme", result.backupId()).await();
 *
 * // List all backups
 * List<BackupMetadata> backups = backup.listBackups("tenant-acme").await();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Production-grade backup and point-in-time recovery service for AEP entity data
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepBackupRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AepBackupRecoveryService.class);

    /** Data-Cloud collection that stores backup metadata records. */
    public static final String BACKUP_COLLECTION = "aep_backups";

    /** Current backup schema version — increment when the backup format changes. */
    public static final int SCHEMA_VERSION = 1;

    /** Collections backed up by default on a full backup (order matters for restore). */
    public static final List<String> DEFAULT_COLLECTIONS = List.of(
            "aep_patterns", "aep_pipelines", "aep_agents");

    private final DataCloudClient client;
    private final List<String> collectionsToCopy;

    // Micrometer instruments
    private final Counter backupsCreated;
    private final Counter backupsFailed;
    private final Counter restoresCompleted;
    private final Counter restoresFailed;
    private final Counter verificationsPassed;
    private final Counter verificationsFailed;
    private final Timer backupTimer;
    private final Timer restoreTimer;

    /**
     * Creates a backup/recovery service with the default collection list.
     *
     * @param client        Data-Cloud client
     * @param meterRegistry Micrometer registry
     */
    public AepBackupRecoveryService(DataCloudClient client,
                                    MeterRegistry meterRegistry) {
        this(client, meterRegistry, DEFAULT_COLLECTIONS);
    }

    /**
     * Creates a backup/recovery service with a custom set of collections.
     *
     * @param client            Data-Cloud client
     * @param meterRegistry     Micrometer registry
     * @param collectionsToCopy ordered list of Data-Cloud collections to back up
     */
    public AepBackupRecoveryService(DataCloudClient client,
                                    MeterRegistry meterRegistry,
                                    List<String> collectionsToCopy) {
        this.client            = Objects.requireNonNull(client, "client");
        this.collectionsToCopy = List.copyOf(Objects.requireNonNull(collectionsToCopy, "collectionsToCopy"));

        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.backupsCreated        = Counter.builder("aep.backup.created").register(meterRegistry);
        this.backupsFailed         = Counter.builder("aep.backup.failed").register(meterRegistry);
        this.restoresCompleted     = Counter.builder("aep.restore.completed").register(meterRegistry);
        this.restoresFailed        = Counter.builder("aep.restore.failed").register(meterRegistry);
        this.verificationsPassed   = Counter.builder("aep.backup.verification.passed").register(meterRegistry);
        this.verificationsFailed   = Counter.builder("aep.backup.verification.failed").register(meterRegistry);
        this.backupTimer  = Timer.builder("aep.backup.duration").register(meterRegistry);
        this.restoreTimer = Timer.builder("aep.restore.duration").register(meterRegistry);
    }

    // =========================================================================
    //  Public API — Backup creation
    // =========================================================================

    /**
     * Creates a full backup of all configured collections for the specified tenant.
     *
     * <p>All entities in each configured collection are copied to a dedicated
     * backup collection prefixed with the new backup ID.
     *
     * @param tenantId tenant to back up
     * @return Promise of the backup result
     */
    public Promise<BackupResult> createFullBackup(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        String backupId = UUID.randomUUID().toString();
        log.info("Starting FULL backup for tenant '{}' — backupId={}", tenantId, backupId);
        return runBackup(tenantId, backupId, "FULL", null, Instant.now());
    }

    /**
     * Creates an incremental backup of entities updated after {@code sinceInstant}.
     *
     * <p>Useful for frequent backup schedules where copying the entire dataset
     * each time would be prohibitively slow.
     *
     * @param tenantId     tenant to back up
     * @param sinceInstant earliest update timestamp to include (exclusive)
     * @return Promise of the backup result
     */
    public Promise<BackupResult> createIncrementalBackup(String tenantId, Instant sinceInstant) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sinceInstant, "sinceInstant");
        String backupId = UUID.randomUUID().toString();
        log.info("Starting INCREMENTAL backup for tenant '{}' since {} — backupId={}",
                tenantId, sinceInstant, backupId);
        return runBackup(tenantId, backupId, "INCREMENTAL", sinceInstant, Instant.now());
    }

    // =========================================================================
    //  Public API — Restore
    // =========================================================================

    /**
     * Restores all backed-up collections for a tenant from the specified backup.
     *
     * <p><b>Warning</b>: this overwrites existing entities with the same IDs in the
     * target collections. Ensure that fresh backups of the current state exist before
     * calling this in production.
     *
     * @param tenantId tenant to restore
     * @param backupId ID of the backup to restore from
     * @return Promise of the restore result
     */
    public Promise<RestoreResult> restoreFromBackup(String tenantId, String backupId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(backupId, "backupId");
        Instant startTime = Instant.now();

        log.info("Starting RESTORE for tenant '{}' from backupId={}", tenantId, backupId);

        return client.findById(tenantId, BACKUP_COLLECTION, backupId)
                .then(optMeta -> {
                    if (optMeta.isEmpty()) {
                        log.error("Backup '{}' not found for tenant '{}'", backupId, tenantId);
                        return Promise.of(new RestoreResult(backupId, tenantId, 0,
                                startTime, Instant.now(), false, List.of("Backup not found: " + backupId)));
                    }

                    Entity meta = optMeta.get();
                    String collectionsStr = (String) meta.data().get("collections");
                    List<String> targetCollections = collectionsStr != null && !collectionsStr.isBlank()
                            ? List.of(collectionsStr.split(","))
                            : collectionsToCopy;

                    AtomicInteger totalRestored = new AtomicInteger(0);
                    List<String> errors = Collections.synchronizedList(new ArrayList<>());

                    List<Promise<Void>> collectionRestores = new ArrayList<>();
                    for (String collection : targetCollections) {
                        String bkColl = backupDataCollection(backupId, collection);
                        Promise<Void> restoreOne = queryAll(tenantId, bkColl)
                                .then(entities -> {
                                    List<Promise<Void>> saves = new ArrayList<>();
                                    for (Entity entity : entities) {
                                        Map<String, Object> restoredData = new HashMap<>(entity.data());
                                        restoredData.remove("backupId");
                                        restoredData.remove("backedUpAt");
                                        restoredData.remove("sourceCollection");
                                        restoredData.remove("originalId");
                                        saves.add(client.save(tenantId, collection, restoredData)
                                                .map(e -> {
                                                    totalRestored.incrementAndGet();
                                                    return (Void) null;
                                                }));
                                    }
                                    return saves.isEmpty()
                                            ? Promise.complete()
                                            : Promises.all(saves).map(v -> (Void) null);
                                })
                                .whenException(e -> {
                                    log.error("Restore failed for collection '{}': {}",
                                            collection, e.getMessage(), e);
                                    errors.add(collection + ": " + e.getMessage());
                                });
                        collectionRestores.add(restoreOne);
                    }
                    return Promises.all(collectionRestores).map(ignored -> {
                        boolean success = errors.isEmpty();
                        restoreTimer.record(Duration.between(startTime, Instant.now()));
                        if (success) { restoresCompleted.increment(); }
                        else         { restoresFailed.increment(); }
                        log.info("RESTORE {} tenant='{}' backupId={} entities={}",
                                success ? "COMPLETE" : "PARTIAL",
                                tenantId, backupId, totalRestored.get());
                        return new RestoreResult(backupId, tenantId, totalRestored.get(),
                                startTime, Instant.now(), success, List.copyOf(errors));
                    });
                });
    }

    // =========================================================================
    //  Public API — Inventory & Verification
    // =========================================================================

    /**
     * Lists all backup metadata records for the specified tenant.
     *
     * <p>Results are returned in reverse-chronological order (newest first).
     *
     * @param tenantId tenant to list backups for
     * @return Promise of backup metadata list
     */
    public Promise<List<BackupMetadata>> listBackups(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        Query query = Query.builder()
                .filter(Filter.eq("tenantId", tenantId))
                .limit(1000)
                .build();

        return client.query(tenantId, BACKUP_COLLECTION, query)
                .map(entities -> {
                    List<BackupMetadata> result = new ArrayList<>();
                    for (Entity entity : entities) {
                        try {
                            result.add(entityToBackupMetadata(entity));
                        } catch (Exception e) {
                            log.warn("Failed to parse backup metadata for entity {}: {}",
                                    entity.id(), e.getMessage());
                        }
                    }
                    // Sort newest first
                    result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
                    return Collections.unmodifiableList(result);
                });
    }

    /**
     * Verifies that a backup's recorded entity count matches the actual stored
     * snapshot size. Useful for detecting partial or corrupted backups.
     *
     * @param tenantId tenant
     * @param backupId backup to verify
     * @return Promise of verification result
     */
    public Promise<VerificationResult> verifyBackup(String tenantId, String backupId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(backupId, "backupId");

        return client.findById(tenantId, BACKUP_COLLECTION, backupId)
                .then(optMeta -> {
                    if (optMeta.isEmpty()) {
                        return Promise.of(new VerificationResult(backupId, false,
                                0, 0, "Backup metadata not found"));
                    }

                    Entity meta = optMeta.get();
                    int expectedCount = ((Number) meta.data().getOrDefault("entityCount", 0)).intValue();
                    String collectionsStr = (String) meta.data().get("collections");
                    List<String> verifyCollections = collectionsStr != null && !collectionsStr.isBlank()
                            ? List.of(collectionsStr.split(","))
                            : collectionsToCopy;

                    List<Promise<List<Entity>>> countQueries = new ArrayList<>();
                    for (String collection : verifyCollections) {
                        countQueries.add(
                                queryAll(tenantId, backupDataCollection(backupId, collection)));
                    }
                    return Promises.toList(countQueries).map(allEntities -> {
                        int actualCount = allEntities.stream().mapToInt(List::size).sum();
                        boolean valid = actualCount == expectedCount;
                        if (valid) { verificationsPassed.increment(); }
                        else       { verificationsFailed.increment(); }
                        log.info("Backup {} verification: expected={} actual={} valid={}",
                                backupId, expectedCount, actualCount, valid);
                        return new VerificationResult(backupId, valid, expectedCount, actualCount,
                                valid ? null : "Entity count mismatch: expected "
                                        + expectedCount + ", found " + actualCount);
                    });
                });
    }

    /**
     * Deletes a backup and all its associated entity data from Data-Cloud.
     *
     * @param tenantId tenant
     * @param backupId backup to delete
     * @return Promise completing when backup is fully deleted
     */
    public Promise<Void> deleteBackup(String tenantId, String backupId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(backupId, "backupId");

        return client.findById(tenantId, BACKUP_COLLECTION, backupId)
                .then(optMeta -> {
                    List<String> backupCollections;
                    if (optMeta.isPresent()) {
                        String collectionsStr = (String) optMeta.get().data().get("collections");
                        backupCollections = collectionsStr != null && !collectionsStr.isBlank()
                                ? List.of(collectionsStr.split(","))
                                : collectionsToCopy;
                    } else {
                        backupCollections = List.of();
                    }
                    List<Promise<Void>> deleteOps = new ArrayList<>();
                    for (String collection : backupCollections) {
                        String bkColl = backupDataCollection(backupId, collection);
                        deleteOps.add(queryAll(tenantId, bkColl)
                                .then(entities -> {
                                    List<Promise<Void>> deletes = entities.stream()
                                            .map(e -> client.delete(tenantId, bkColl, e.id()))
                                            .toList();
                                    return deletes.isEmpty()
                                            ? Promise.complete()
                                            : Promises.all(deletes).map(v -> (Void) null);
                                })
                                .whenException(e -> log.warn(
                                        "Failed to delete backup data '{}': {}",
                                        bkColl, e.getMessage())));
                    }
                    Promise<Void> deleteData = deleteOps.isEmpty()
                            ? Promise.complete()
                            : Promises.all(deleteOps).map(v -> (Void) null);
                    return deleteData
                            .then(ignored -> client.delete(tenantId, BACKUP_COLLECTION, backupId))
                            .whenResult(() -> log.info("Backup '{}' deleted for tenant '{}'",
                                    backupId, tenantId));
                });
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    /**
     * Core backup loop: fetch entities per collection, copy to backup collections
     * in parallel, then persist backup metadata.
     */
    private Promise<BackupResult> runBackup(String tenantId, String backupId,
                                            String backupType, Instant sinceInstant,
                                            Instant startTime) {
        AtomicInteger totalEntities = new AtomicInteger(0);
        List<String> failedCollections = Collections.synchronizedList(new ArrayList<>());
        List<Promise<Void>> collectionBackups = new ArrayList<>();

        for (String collection : collectionsToCopy) {
            Promise<List<Entity>> fetchOp = sinceInstant == null
                    ? queryAll(tenantId, collection)
                    : queryUpdatedSince(tenantId, collection, sinceInstant);

            Promise<Void> backupOne = fetchOp
                    .then(entities -> {
                        List<Promise<Void>> saves = new ArrayList<>();
                        for (Entity entity : entities) {
                            Map<String, Object> bkData = new HashMap<>(entity.data());
                            bkData.put("backupId", backupId);
                            bkData.put("sourceCollection", collection);
                            bkData.put("backedUpAt", Instant.now().toString());
                            bkData.put("originalId", entity.id());
                            saves.add(client.save(tenantId,
                                    backupDataCollection(backupId, collection), bkData)
                                    .map(e -> (Void) null));
                        }
                        totalEntities.addAndGet(entities.size());
                        return saves.isEmpty()
                                ? Promise.complete()
                                : Promises.all(saves).map(v -> (Void) null);
                    })
                    .whenException(e -> {
                        log.error("Failed to back up collection '{}': {}",
                                collection, e.getMessage(), e);
                        failedCollections.add(collection);
                    })
                    .then(v -> Promise.<Void>complete(), e -> Promise.<Void>complete()); // absorb failure so Promises.all() still completes
            collectionBackups.add(backupOne);
        }

        return Promises.all(collectionBackups).then(ignored -> {
            boolean success = failedCollections.isEmpty();
            int count = totalEntities.get();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", backupId);
            metadata.put("backupId", backupId);
            metadata.put("tenantId", tenantId);
            metadata.put("backupType", backupType);
            metadata.put("collections", String.join(",", collectionsToCopy));
            metadata.put("entityCount", count);
            if (sinceInstant != null) {
                metadata.put("snapshotStart", sinceInstant.toString());
            }
            metadata.put("createdAt", Instant.now().toString());
            metadata.put("schemaVersion", SCHEMA_VERSION);
            metadata.put("status", success ? "COMPLETE" : "PARTIAL");
            metadata.put("backupPrefix", "aep_backup_" + backupId + "_");
            return client.save(tenantId, BACKUP_COLLECTION, metadata).map(e -> {
                backupTimer.record(Duration.between(startTime, Instant.now()));
                if (success) { backupsCreated.increment(); }
                else         { backupsFailed.increment(); }
                log.info("{} backup {} tenant='{}' backupId={} entities={}",
                        backupType, success ? "COMPLETE" : "PARTIAL",
                        tenantId, backupId, count);
                return new BackupResult(backupId, tenantId, backupType, count,
                        startTime, Instant.now(), success, List.copyOf(failedCollections));
            });
        });
    }

    private Promise<List<Entity>> queryAll(String tenantId, String collection) {
        return client.query(tenantId, collection, Query.builder().limit(10_000).build());
    }

    private Promise<List<Entity>> queryUpdatedSince(String tenantId, String collection,
                                                     Instant since) {
        return client.query(tenantId, collection,
                Query.builder()
                        .filter(Filter.gte("updatedAt", since.toString()))
                        .limit(10_000)
                        .build());
    }

    private BackupMetadata entityToBackupMetadata(Entity entity) {
        Map<String, Object> d = entity.data();
        String createdAtStr = (String) d.get("createdAt");
        return new BackupMetadata(
                (String) d.get("backupId"),
                (String) d.get("tenantId"),
                (String) d.get("backupType"),
                createdAtStr != null ? Instant.parse(createdAtStr) : Instant.EPOCH,
                ((Number) d.getOrDefault("entityCount", 0)).intValue(),
                (String) d.get("status"),
                (String) d.get("collections"),
                ((Number) d.getOrDefault("schemaVersion", SCHEMA_VERSION)).intValue()
        );
    }

    /**
     * Returns the Data-Cloud collection name for backup entity data.
     *
     * @param backupId         UUID of the backup
     * @param sourceCollection source collection name
     * @return deterministic backup collection name
     */
    public static String backupDataCollection(String backupId, String sourceCollection) {
        return "aep_backup_" + backupId + "_" + sourceCollection;
    }

    // =========================================================================
    //  Result types
    // =========================================================================

    /**
     * Result of a backup creation operation.
     *
     * @param backupId          unique identifier of the created backup
     * @param tenantId          owning tenant
     * @param backupType        "FULL" or "INCREMENTAL"
     * @param entityCount       total entities included in the backup
     * @param startedAt         when the backup started
     * @param completedAt       when the backup finished
     * @param success           true when all collections backed up without error
     * @param failedCollections collections that could not be backed up (empty on full success)
     *
     * @doc.type record
     * @doc.purpose Immutable backup operation result
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record BackupResult(
            String backupId,
            String tenantId,
            String backupType,
            int entityCount,
            Instant startedAt,
            Instant completedAt,
            boolean success,
            List<String> failedCollections
    ) {}

    /**
     * Result of a restore operation.
     *
     * @param backupId      source backup ID
     * @param tenantId      owning tenant
     * @param entityCount   total entities restored
     * @param startedAt     when the restore started
     * @param completedAt   when the restore finished
     * @param success       true when all entities were restored without error
     * @param errors        per-collection error messages (empty on full success)
     *
     * @doc.type record
     * @doc.purpose Immutable restore operation result
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record RestoreResult(
            String backupId,
            String tenantId,
            int entityCount,
            Instant startedAt,
            Instant completedAt,
            boolean success,
            List<String> errors
    ) {}

    /**
     * Metadata record for a single backup snapshot.
     *
     * @param backupId      unique backup ID
     * @param tenantId      owning tenant
     * @param backupType    "FULL" or "INCREMENTAL"
     * @param createdAt     when the backup was created
     * @param entityCount   number of entities in the backup
     * @param status        "COMPLETE", "PARTIAL", or "FAILED"
     * @param collections   comma-separated list of backed-up collections
     * @param schemaVersion backup format version
     *
     * @doc.type record
     * @doc.purpose Immutable backup metadata for inventory and restore operations
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record BackupMetadata(
            String backupId,
            String tenantId,
            String backupType,
            Instant createdAt,
            int entityCount,
            String status,
            String collections,
            int schemaVersion
    ) {}

    /**
     * Result of a backup verification operation.
     *
     * @param backupId      backup that was verified
     * @param valid         true when entity count matches
     * @param expectedCount entity count from backup metadata
     * @param actualCount   actual count found in backup data collections
     * @param errorMessage  description of the verification failure (null when valid)
     *
     * @doc.type record
     * @doc.purpose Immutable backup verification result
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record VerificationResult(
            String backupId,
            boolean valid,
            int expectedCount,
            int actualCount,
            String errorMessage
    ) {}
}
