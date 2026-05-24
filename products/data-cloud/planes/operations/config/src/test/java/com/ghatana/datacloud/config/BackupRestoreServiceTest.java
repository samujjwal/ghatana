/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Phase 3: Contract tests for BackupRestoreService.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Backup snapshot creation</li>
 *   <li>Restore from backup</li>
 *   <li>Backup management (list, delete, get)</li>
 * </ul>
 */
@DisplayName("Backup Restore Service Tests (Phase 3)")
class BackupRestoreServiceTest {

    private final RuntimeTruthService runtimeTruthService = new RuntimeTruthService();
    private final BackupRestoreService backupRestoreService = new BackupRestoreService(runtimeTruthService);

    // =========================================================================
    //  Backup Creation
    // =========================================================================

    @Nested
    @DisplayName("Backup Creation")
    class BackupCreationTests {

        @Test
        @DisplayName("creates backup with unique ID")
        void createsBackupWithUniqueId() {
            BackupRestoreService.BackupResult result1 = backupRestoreService.createBackup();
            BackupRestoreService.BackupResult result2 = backupRestoreService.createBackup();

            assertThat(result1.success()).isTrue();
            assertThat(result2.success()).isTrue();
            assertThat(result1.backupId()).isNotEqualTo(result2.backupId());
        }

        @Test
        @DisplayName("backup includes all plane states")
        void backupIncludesAllPlaneStates() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of("entities", 1000));
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of("events", 5000));

            BackupRestoreService.BackupResult result = backupRestoreService.createBackup();

            assertThat(result.success()).isTrue();

            BackupRestoreService.BackupSnapshot snapshot = backupRestoreService.getBackup(result.backupId());
            assertThat(snapshot.planeStates()).hasSize(2);
            assertThat(snapshot.planeStates()).containsKey("data-plane");
            assertThat(snapshot.planeStates()).containsKey("event-plane");
        }

        @Test
        @DisplayName("backup includes system status")
        void backupIncludesSystemStatus() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            BackupRestoreService.BackupResult result = backupRestoreService.createBackup();

            assertThat(result.success()).isTrue();

            BackupRestoreService.BackupSnapshot snapshot = backupRestoreService.getBackup(result.backupId());
            assertThat(snapshot.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.UP);
        }

        @Test
        @DisplayName("backup includes timestamp")
        void backupIncludesTimestamp() {
            BackupRestoreService.BackupResult result = backupRestoreService.createBackup();

            assertThat(result.success()).isTrue();

            BackupRestoreService.BackupSnapshot snapshot = backupRestoreService.getBackup(result.backupId());
            assertThat(snapshot.timestamp()).isNotNull();
            assertThat(snapshot.timestamp()).isBefore(Instant.now().plusSeconds(1));
        }
    }

    // =========================================================================
    //  Restore Operations
    // =========================================================================

    @Nested
    @DisplayName("Restore Operations")
    class RestoreTests {

        @Test
        @DisplayName("restores plane states from backup")
        void restoresPlaneStatesFromBackup() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of("entities", 1000));
            BackupRestoreService.BackupResult backupResult = backupRestoreService.createBackup();

            // Clear and modify state
            runtimeTruthService.clear();
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            // Restore
            BackupRestoreService.RestoreResult restoreResult = backupRestoreService.restoreBackup(backupResult.backupId());

            assertThat(restoreResult.success()).isTrue();
            assertThat(restoreResult.restoredPlanes()).isEqualTo(1);

            RuntimeTruthService.PlaneState restoredState = runtimeTruthService.getPlaneState("data-plane");
            assertThat(restoredState).isNotNull();
            assertThat(restoredState.status()).isEqualTo(RuntimeTruthService.PlaneStatus.UP);
        }

        @Test
        @DisplayName("returns failure for missing backup")
        void returnsFailureForMissingBackup() {
            BackupRestoreService.RestoreResult result = backupRestoreService.restoreBackup("non-existent");

            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("not found");
        }

        @Test
        @DisplayName("requires non-null backup ID")
        void requiresNonNullBackupId() {
            assertThatNullPointerException()
                .isThrownBy(() -> backupRestoreService.restoreBackup(null))
                .withMessageContaining("backupId must not be null");
        }

        @Test
        @DisplayName("clears current state before restore")
        void clearsCurrentStateBeforeRestore() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            BackupRestoreService.BackupResult backupResult = backupRestoreService.createBackup();

            // Add more state
            runtimeTruthService.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            // Restore
            backupRestoreService.restoreBackup(backupResult.backupId());

            // Only backed-up planes should remain
            assertThat(runtimeTruthService.getPlaneState("governance-plane")).isNull();
            assertThat(runtimeTruthService.getPlaneState("data-plane")).isNotNull();
            assertThat(runtimeTruthService.getPlaneState("event-plane")).isNotNull();
        }
    }

    // =========================================================================
    //  Backup Management
    // =========================================================================

    @Nested
    @DisplayName("Backup Management")
    class ManagementTests {

        @Test
        @DisplayName("lists all backup IDs")
        void listsAllBackupIds() {
            BackupRestoreService.BackupResult result1 = backupRestoreService.createBackup();
            BackupRestoreService.BackupResult result2 = backupRestoreService.createBackup();

            java.util.List<String> backupIds = backupRestoreService.listBackups();

            assertThat(backupIds).hasSize(2);
            assertThat(backupIds).contains(result1.backupId());
            assertThat(backupIds).contains(result2.backupId());
        }

        @Test
        @DisplayName("gets backup by ID")
        void getsBackupById() {
            BackupRestoreService.BackupResult result = backupRestoreService.createBackup();

            BackupRestoreService.BackupSnapshot snapshot = backupRestoreService.getBackup(result.backupId());

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.backupId()).isEqualTo(result.backupId());
        }

        @Test
        @DisplayName("returns null for missing backup")
        void returnsNullForMissingBackup() {
            BackupRestoreService.BackupSnapshot snapshot = backupRestoreService.getBackup("non-existent");

            assertThat(snapshot).isNull();
        }

        @Test
        @DisplayName("deletes backup by ID")
        void deletesBackupById() {
            BackupRestoreService.BackupResult result = backupRestoreService.createBackup();

            boolean deleted = backupRestoreService.deleteBackup(result.backupId());

            assertThat(deleted).isTrue();
            assertThat(backupRestoreService.getBackup(result.backupId())).isNull();
        }

        @Test
        @DisplayName("delete returns false for missing backup")
        void deleteReturnsFalseForMissingBackup() {
            boolean deleted = backupRestoreService.deleteBackup("non-existent");

            assertThat(deleted).isFalse();
        }

        @Test
        @DisplayName("requires non-null backup ID for get")
        void requiresNonNullBackupIdForGet() {
            assertThatNullPointerException()
                .isThrownBy(() -> backupRestoreService.getBackup(null))
                .withMessageContaining("backupId must not be null");
        }

        @Test
        @DisplayName("requires non-null backup ID for delete")
        void requiresNonNullBackupIdForDelete() {
            assertThatNullPointerException()
                .isThrownBy(() -> backupRestoreService.deleteBackup(null))
                .withMessageContaining("backupId must not be null");
        }

        @Test
        @DisplayName("tracks backup count")
        void tracksBackupCount() {
            assertThat(backupRestoreService.getBackupCount()).isEqualTo(0);

            backupRestoreService.createBackup();
            assertThat(backupRestoreService.getBackupCount()).isEqualTo(1);

            backupRestoreService.createBackup();
            assertThat(backupRestoreService.getBackupCount()).isEqualTo(2);
        }
    }

    // =========================================================================
    //  Result Records
    // =========================================================================

    @Nested
    @DisplayName("Result Records")
    class ResultRecordTests {

        @Test
        @DisplayName("backup success result contains backup ID")
        void backupSuccessResultContainsBackupId() {
            BackupRestoreService.BackupResult result = BackupRestoreService.BackupResult.success("backup-123");

            assertThat(result.success()).isTrue();
            assertThat(result.backupId()).isEqualTo("backup-123");
            assertThat(result.error()).isNull();
        }

        @Test
        @DisplayName("backup failure result contains error")
        void backupFailureResultContainsError() {
            BackupRestoreService.BackupResult result = BackupRestoreService.BackupResult.failure("Disk full");

            assertThat(result.success()).isFalse();
            assertThat(result.backupId()).isNull();
            assertThat(result.error()).isEqualTo("Disk full");
        }

        @Test
        @DisplayName("restore success result contains restored count")
        void restoreSuccessResultContainsRestoredCount() {
            BackupRestoreService.RestoreResult result = BackupRestoreService.RestoreResult.success(5);

            assertThat(result.success()).isTrue();
            assertThat(result.restoredPlanes()).isEqualTo(5);
            assertThat(result.error()).isNull();
        }

        @Test
        @DisplayName("restore failure result contains error")
        void restoreFailureResultContainsError() {
            BackupRestoreService.RestoreResult result = BackupRestoreService.RestoreResult.failure("Corrupt backup");

            assertThat(result.success()).isFalse();
            assertThat(result.restoredPlanes()).isEqualTo(0);
            assertThat(result.error()).isEqualTo("Corrupt backup");
        }
    }
}
