/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.dr;

import com.ghatana.aep.server.backup.AepBackupRecoveryService;
import com.ghatana.aep.server.backup.AepBackupRecoveryService.BackupMetadata;
import com.ghatana.aep.server.backup.AepBackupRecoveryService.BackupResult;
import com.ghatana.aep.server.backup.AepBackupRecoveryService.VerificationResult;
import com.ghatana.aep.server.dr.AepDisasterRecoveryService.BackupMode;
import com.ghatana.aep.server.dr.AepDisasterRecoveryService.DRPolicy;
import com.ghatana.aep.server.dr.AepDisasterRecoveryService.DRStatus;
import com.ghatana.aep.server.dr.AepDisasterRecoveryService.DRTestResult;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepDisasterRecoveryService}.
 *
 * @doc.type class
 * @doc.purpose Tests for DR scheduling, status, recoverability, and retention logic
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepDisasterRecoveryService")
class AepDisasterRecoveryServiceTest {

    @Mock
    AepBackupRecoveryService backupService;

    ScheduledExecutorService scheduler;
    AepDisasterRecoveryService drService;

    @BeforeEach
    void setUp() {
        // Use a manually controlled scheduler so tests don't wait for real time
        scheduler = Executors.newSingleThreadScheduledExecutor();
        drService = new AepDisasterRecoveryService(
                backupService, new SimpleMeterRegistry(), scheduler);
    }

    @AfterEach
    void tearDown() {
        drService.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null backupService")
    void rejectsNullBackupService() {
        assertThatThrownBy(() ->
                new AepDisasterRecoveryService(null, new SimpleMeterRegistry(), scheduler))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void rejectsNullMeterRegistry() {
        assertThatThrownBy(() ->
                new AepDisasterRecoveryService(backupService, null, scheduler))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DRPolicy Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DRPolicy")
    class DRPolicyTests {

        @Test
        @DisplayName("should reject zero backupIntervalMinutes")
        void rejectsZeroInterval() {
            assertThatThrownBy(() -> new DRPolicy(0, 5, 120, BackupMode.FULL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("backupIntervalMinutes");
        }

        @Test
        @DisplayName("should reject zero retentionCount")
        void rejectsZeroRetention() {
            assertThatThrownBy(() -> new DRPolicy(60, 0, 120, BackupMode.FULL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retentionCount");
        }

        @Test
        @DisplayName("standard() policy should be valid")
        void standardPolicyIsValid() {
            DRPolicy p = DRPolicy.standard();
            assertThat(p.backupIntervalMinutes()).isEqualTo(60);
            assertThat(p.retentionCount()).isEqualTo(168);
            assertThat(p.targetRPOMinutes()).isEqualTo(120);
            assertThat(p.mode()).isEqualTo(BackupMode.FULL);
        }

        @Test
        @DisplayName("conservative() policy should be valid")
        void conservativePolicyIsValid() {
            DRPolicy p = DRPolicy.conservative();
            assertThat(p.backupIntervalMinutes()).isEqualTo(1440);
            assertThat(p.retentionCount()).isEqualTo(30);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Schedule / Stop
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Scheduling")
    class Scheduling {

        @Test
        @DisplayName("should mark tenant as scheduled after scheduleAutomatedBackups")
        void tenantIsScheduledAfterCall() {
            drService.scheduleAutomatedBackups("t1", DRPolicy.standard());

            assertThat(drService.isScheduled("t1")).isTrue();
        }

        @Test
        @DisplayName("should not mark unscheduled tenant as scheduled")
        void unscheduledTenantNotScheduled() {
            assertThat(drService.isScheduled("unknown-tenant")).isFalse();
        }

        @Test
        @DisplayName("stopAutomatedBackups should return true when schedule was active")
        void stopReturnsTrueWhenActive() {
            drService.scheduleAutomatedBackups("t1", DRPolicy.standard());
            boolean stopped = drService.stopAutomatedBackups("t1");

            assertThat(stopped).isTrue();
            assertThat(drService.isScheduled("t1")).isFalse();
        }

        @Test
        @DisplayName("stopAutomatedBackups should return false when no schedule exists")
        void stopReturnsFalseWhenNotScheduled() {
            assertThat(drService.stopAutomatedBackups("no-such-tenant")).isFalse();
        }

        @Test
        @DisplayName("activePolicies should reflect all scheduled tenants")
        void activePoliciesReflectsScheduled() {
            DRPolicy policy = DRPolicy.standard();
            drService.scheduleAutomatedBackups("tenant-A", policy);

            assertThat(drService.activePolicies()).containsKey("tenant-A");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DR Status
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DR Status")
    class DRStatusTests {

        @Test
        @DisplayName("should return no-backup status when backup list is empty")
        void noBackupStatus() {
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of()));

            DRStatus status = drService.getDRStatus("t1").getResult();

            assertThat(status.tenantId()).isEqualTo("t1");
            assertThat(status.backupCount()).isEqualTo(0);
            assertThat(status.hasCompleteBackup()).isFalse();
            assertThat(status.withinRPO()).isFalse();
            assertThat(status.lastBackupTime()).isNull();
        }

        @Test
        @DisplayName("should indicate within-RPO when last backup is recent")
        void withinRPOForRecentBackup() {
            Instant recentBackup = Instant.now().minusSeconds(30);  // 30 seconds ago
            BackupMetadata meta = metadataWith("b1", "t1", recentBackup, "COMPLETE");
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of(meta)));

            // Register a policy with 120-minute RPO (no immediate backup fires since initialDelay = period)
            drService.scheduleAutomatedBackups("t1", new DRPolicy(60, 7, 120, BackupMode.FULL));

            DRStatus status = drService.getDRStatus("t1").getResult();

            assertThat(status.withinRPO()).isTrue();
            assertThat(status.backupCount()).isEqualTo(1);
            assertThat(status.hasCompleteBackup()).isTrue();
        }

        @Test
        @DisplayName("should indicate RPO breach when last backup is older than target")
        void rpoBreach() {
            // Backup from 200 minutes ago — beyond the 120-minute RPO
            Instant oldBackup = Instant.now().minusSeconds(200 * 60);
            BackupMetadata meta = metadataWith("b1", "t1", oldBackup, "COMPLETE");
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of(meta)));

            drService.scheduleAutomatedBackups("t1", new DRPolicy(60, 7, 120, BackupMode.FULL));

            DRStatus status = drService.getDRStatus("t1").getResult();

            assertThat(status.withinRPO()).isFalse();
            assertThat(status.lastBackupTime()).isEqualTo(oldBackup);
        }

        @Test
        @DisplayName("should reflect automation status in DR status")
        void automationFlagInStatus() {
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of()));
            DRStatus status = drService.getDRStatus("t1").getResult();
            assertThat(status.automationActive()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Recoverability Test
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recoverability Test")
    class RecoverabilityTests {

        @Test
        @DisplayName("should return not-recoverable when no backup exists")
        void noBackupMeansNotRecoverable() {
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of()));

            DRTestResult result = drService.testRecoverability("t1").getResult();

            assertThat(result.recoverable()).isFalse();
            assertThat(result.backupId()).isNull();
            assertThat(result.message()).contains("No completed backup");
        }

        @Test
        @DisplayName("should return recoverable when latest backup verifies successfully")
        void recoverableWhenVerificationPasses() {
            BackupMetadata meta = metadataWith("b1", "t1", Instant.now(), "COMPLETE");
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of(meta)));
            when(backupService.verifyBackup("t1", "b1"))
                    .thenReturn(Promise.of(new VerificationResult("b1", true, 10, 10, null)));

            DRTestResult result = drService.testRecoverability("t1").getResult();

            assertThat(result.recoverable()).isTrue();
            assertThat(result.backupId()).isEqualTo("b1");
            assertThat(result.message()).contains("verified");
        }

        @Test
        @DisplayName("should return not-recoverable when verification fails")
        void notRecoverableWhenVerificationFails() {
            BackupMetadata meta = metadataWith("b1", "t1", Instant.now(), "COMPLETE");
            when(backupService.listBackups("t1")).thenReturn(Promise.of(List.of(meta)));
            when(backupService.verifyBackup("t1", "b1"))
                    .thenReturn(Promise.of(new VerificationResult("b1", false, 10, 8, "Count mismatch")));

            DRTestResult result = drService.testRecoverability("t1").getResult();

            assertThat(result.recoverable()).isFalse();
            assertThat(result.message()).isEqualTo("Count mismatch");
        }

        @Test
        @DisplayName("should skip FAILED backups and use latest COMPLETE backup")
        void skipsFailedBackupsForVerification() {
            Instant earlier = Instant.now().minusSeconds(3600);
            Instant later   = Instant.now().minusSeconds(1800);
            BackupMetadata failed   = metadataWith("b-fail", "t1", earlier, "FAILED");
            BackupMetadata complete = metadataWith("b-ok",   "t1", later,   "COMPLETE");

            when(backupService.listBackups("t1"))
                    .thenReturn(Promise.of(List.of(failed, complete)));
            when(backupService.verifyBackup("t1", "b-ok"))
                    .thenReturn(Promise.of(new VerificationResult("b-ok", true, 5, 5, null)));

            DRTestResult result = drService.testRecoverability("t1").getResult();

            assertThat(result.backupId()).isEqualTo("b-ok");
            assertThat(result.recoverable()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Immediate Backup
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("triggerImmediateBackup should delegate to createFullBackup")
    void immediateBackupDelegatesToFull() {
        BackupResult expected = successfulBackup("b-immediate");
        when(backupService.createFullBackup("t1")).thenReturn(Promise.of(expected));

        BackupResult result = drService.triggerImmediateBackup("t1").getResult();

        assertThat(result.backupId()).isEqualTo("b-immediate");
        verify(backupService).createFullBackup("t1");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static BackupResult successfulBackup(String backupId) {
        return new BackupResult(
                backupId, "t1", "FULL", 42,
                Instant.now(), Instant.now(), true, List.of());
    }

    private static BackupMetadata metadataWith(String id, String tenantId,
                                                Instant createdAt, String status) {
        return new BackupMetadata(id, tenantId, "FULL", createdAt, 10, status, "aep_patterns", 1);
    }
}
