/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for backing up and restoring Data-Cloud plane states.
 *
 * <p>This service provides backup and restore capabilities for plane states,
 * including snapshot creation, restoration, and backup management.
 *
 * @doc.type class
 * @doc.purpose Provides backup and restore capabilities for Data-Cloud plane states
 * @doc.layer product
 * @doc.pattern BackupRestore
 */
public final class BackupRestoreService {

    /**
     * Represents a backup snapshot of plane states.
     *
     * @param backupId unique backup identifier
     * @param timestamp when the backup was created
     * @param planeStates snapshot of all plane states
     * @param systemStatus snapshot of system status
     */
    public record BackupSnapshot(
            String backupId,
            Instant timestamp,
            Map<String, RuntimeTruthService.PlaneState> planeStates,
            RuntimeTruthService.PlaneStatus systemStatus) {

        public BackupSnapshot {
            Objects.requireNonNull(backupId, "backupId must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
            Objects.requireNonNull(planeStates, "planeStates must not be null");
            Objects.requireNonNull(systemStatus, "systemStatus must not be null");
        }
    }

    /**
     * Result of a backup operation.
     *
     * @param success whether the backup succeeded
     * @param backupId the backup ID (if successful)
     * @param error error message (if failed)
     */
    public record BackupResult(boolean success, String backupId, String error) {
        public static BackupResult success(String backupId) {
            return new BackupResult(true, backupId, null);
        }

        public static BackupResult failure(String error) {
            return new BackupResult(false, null, error);
        }
    }

    /**
     * Result of a restore operation.
     *
     * @param success whether the restore succeeded
     * @param restoredPlanes number of planes restored
     * @param error error message (if failed)
     */
    public record RestoreResult(boolean success, int restoredPlanes, String error) {
        public static RestoreResult success(int restoredPlanes) {
            return new RestoreResult(true, restoredPlanes, null);
        }

        public static RestoreResult failure(String error) {
            return new RestoreResult(false, 0, error);
        }
    }

    private final RuntimeTruthService runtimeTruthService;
    private final Map<String, BackupSnapshot> backupStorage;

    public BackupRestoreService(RuntimeTruthService runtimeTruthService) {
        this.runtimeTruthService = Objects.requireNonNull(runtimeTruthService, "runtimeTruthService must not be null");
        this.backupStorage = new java.util.concurrent.ConcurrentHashMap<>();
    }

    /**
     * Creates a backup snapshot of all plane states.
     *
     * @return the backup result
     */
    public BackupResult createBackup() {
        try {
            String backupId = UUID.randomUUID().toString();
            Instant timestamp = Instant.now();

            RuntimeTruthService.RuntimeTruth truth = runtimeTruthService.getRuntimeTruth();

            // Create snapshot of all plane states
            Map<String, RuntimeTruthService.PlaneState> planeStatesSnapshot = new java.util.LinkedHashMap<>();
            truth.planeStates().forEach((name, state) -> {
                planeStatesSnapshot.put(name, new RuntimeTruthService.PlaneState(
                    state.planeName(),
                    state.status(),
                    new java.util.LinkedHashMap<>(state.metadata()),
                    state.lastUpdated()
                ));
            });

            BackupSnapshot snapshot = new BackupSnapshot(
                backupId,
                timestamp,
                planeStatesSnapshot,
                truth.systemStatus()
            );

            backupStorage.put(backupId, snapshot);

            return BackupResult.success(backupId);
        } catch (Exception e) {
            return BackupResult.failure("Backup failed: " + e.getMessage());
        }
    }

    /**
     * Restores plane states from a backup snapshot.
     *
     * @param backupId the backup ID to restore
     * @return the restore result
     */
    public RestoreResult restoreBackup(String backupId) {
        Objects.requireNonNull(backupId, "backupId must not be null");

        BackupSnapshot snapshot = backupStorage.get(backupId);
        if (snapshot == null) {
            return RestoreResult.failure("Backup not found: " + backupId);
        }

        try {
            // Clear current state
            runtimeTruthService.clear();

            // Restore plane states
            int restoredCount = 0;
            for (RuntimeTruthService.PlaneState state : snapshot.planeStates().values()) {
                runtimeTruthService.updatePlaneState(
                    state.planeName(),
                    state.status(),
                    state.metadata()
                );
                restoredCount++;
            }

            return RestoreResult.success(restoredCount);
        } catch (Exception e) {
            return RestoreResult.failure("Restore failed: " + e.getMessage());
        }
    }

    /**
     * Gets a backup snapshot by ID.
     *
     * @param backupId the backup ID
     * @return the backup snapshot, or null if not found
     */
    public BackupSnapshot getBackup(String backupId) {
        Objects.requireNonNull(backupId, "backupId must not be null");
        return backupStorage.get(backupId);
    }

    /**
     * Lists all available backup IDs.
     *
     * @return list of backup IDs
     */
    public java.util.List<String> listBackups() {
        return new java.util.ArrayList<>(backupStorage.keySet());
    }

    /**
     * Deletes a backup snapshot.
     *
     * @param backupId the backup ID to delete
     * @return true if the backup was deleted, false if not found
     */
    public boolean deleteBackup(String backupId) {
        Objects.requireNonNull(backupId, "backupId must not be null");
        return backupStorage.remove(backupId) != null;
    }

    /**
     * Gets the number of stored backups.
     *
     * @return backup count
     */
    public int getBackupCount() {
        return backupStorage.size();
    }
}
