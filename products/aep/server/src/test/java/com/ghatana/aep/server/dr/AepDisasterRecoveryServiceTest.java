/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepDisasterRecoveryService}.
 *
 * @doc.type class
 * @doc.purpose Tests for DR scheduling, status, recoverability, and retention logic
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepDisasterRecoveryService [GH-90000]")
class AepDisasterRecoveryServiceTest {

    @Mock
    AepBackupRecoveryService backupService;

    ScheduledExecutorService scheduler;
    AepDisasterRecoveryService drService;

    @BeforeEach
    void setUp() { // GH-90000
        // Use a manually controlled scheduler so tests don't wait for real time
        scheduler = Executors.newSingleThreadScheduledExecutor(); // GH-90000
        drService = new AepDisasterRecoveryService( // GH-90000
                backupService, new SimpleMeterRegistry(), scheduler); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        drService.shutdown(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should reject null backupService [GH-90000]")
    void rejectsNullBackupService() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                new AepDisasterRecoveryService(null, new SimpleMeterRegistry(), scheduler)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should reject null MeterRegistry [GH-90000]")
    void rejectsNullMeterRegistry() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                new AepDisasterRecoveryService(backupService, null, scheduler)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DRPolicy Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DRPolicy [GH-90000]")
    class DRPolicyTests {

        @Test
        @DisplayName("should reject zero backupIntervalMinutes [GH-90000]")
        void rejectsZeroInterval() { // GH-90000
            assertThatThrownBy(() -> new DRPolicy(0, 5, 120, BackupMode.FULL)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("backupIntervalMinutes [GH-90000]");
        }

        @Test
        @DisplayName("should reject zero retentionCount [GH-90000]")
        void rejectsZeroRetention() { // GH-90000
            assertThatThrownBy(() -> new DRPolicy(60, 0, 120, BackupMode.FULL)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("retentionCount [GH-90000]");
        }

        @Test
        @DisplayName("standard() policy should be valid [GH-90000]")
        void standardPolicyIsValid() { // GH-90000
            DRPolicy p = DRPolicy.standard(); // GH-90000
            assertThat(p.backupIntervalMinutes()).isEqualTo(60); // GH-90000
            assertThat(p.retentionCount()).isEqualTo(168); // GH-90000
            assertThat(p.targetRPOMinutes()).isEqualTo(120); // GH-90000
            assertThat(p.mode()).isEqualTo(BackupMode.FULL); // GH-90000
        }

        @Test
        @DisplayName("conservative() policy should be valid [GH-90000]")
        void conservativePolicyIsValid() { // GH-90000
            DRPolicy p = DRPolicy.conservative(); // GH-90000
            assertThat(p.backupIntervalMinutes()).isEqualTo(1440); // GH-90000
            assertThat(p.retentionCount()).isEqualTo(30); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Schedule / Stop
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Scheduling [GH-90000]")
    class Scheduling {

        @Test
        @DisplayName("should mark tenant as scheduled after scheduleAutomatedBackups [GH-90000]")
        void tenantIsScheduledAfterCall() { // GH-90000
            drService.scheduleAutomatedBackups("t1", DRPolicy.standard()); // GH-90000

            assertThat(drService.isScheduled("t1 [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("should not mark unscheduled tenant as scheduled [GH-90000]")
        void unscheduledTenantNotScheduled() { // GH-90000
            assertThat(drService.isScheduled("unknown-tenant [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("stopAutomatedBackups should return true when schedule was active [GH-90000]")
        void stopReturnsTrueWhenActive() { // GH-90000
            drService.scheduleAutomatedBackups("t1", DRPolicy.standard()); // GH-90000
            boolean stopped = drService.stopAutomatedBackups("t1 [GH-90000]");

            assertThat(stopped).isTrue(); // GH-90000
            assertThat(drService.isScheduled("t1 [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("stopAutomatedBackups should return false when no schedule exists [GH-90000]")
        void stopReturnsFalseWhenNotScheduled() { // GH-90000
            assertThat(drService.stopAutomatedBackups("no-such-tenant [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("activePolicies should reflect all scheduled tenants [GH-90000]")
        void activePoliciesReflectsScheduled() { // GH-90000
            DRPolicy policy = DRPolicy.standard(); // GH-90000
            drService.scheduleAutomatedBackups("tenant-A", policy); // GH-90000

            assertThat(drService.activePolicies()).containsKey("tenant-A [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DR Status
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DR Status [GH-90000]")
    class DRStatusTests {

        @Test
        @DisplayName("should return no-backup status when backup list is empty [GH-90000]")
        void noBackupStatus() { // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of()));

            DRStatus status = drService.getDRStatus("t1 [GH-90000]").getResult();

            assertThat(status.tenantId()).isEqualTo("t1 [GH-90000]");
            assertThat(status.backupCount()).isEqualTo(0); // GH-90000
            assertThat(status.hasCompleteBackup()).isFalse(); // GH-90000
            assertThat(status.withinRPO()).isFalse(); // GH-90000
            assertThat(status.lastBackupTime()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should indicate within-RPO when last backup is recent [GH-90000]")
        void withinRPOForRecentBackup() { // GH-90000
            Instant recentBackup = Instant.now().minusSeconds(30);  // 30 seconds ago // GH-90000
            BackupMetadata meta = metadataWith("b1", "t1", recentBackup, "COMPLETE"); // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of(meta)));

            // Register a policy with 120-minute RPO (no immediate backup fires since initialDelay = period) // GH-90000
            drService.scheduleAutomatedBackups("t1", new DRPolicy(60, 7, 120, BackupMode.FULL)); // GH-90000

            DRStatus status = drService.getDRStatus("t1 [GH-90000]").getResult();

            assertThat(status.withinRPO()).isTrue(); // GH-90000
            assertThat(status.backupCount()).isEqualTo(1); // GH-90000
            assertThat(status.hasCompleteBackup()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should indicate RPO breach when last backup is older than target [GH-90000]")
        void rpoBreach() { // GH-90000
            // Backup from 200 minutes ago — beyond the 120-minute RPO
            Instant oldBackup = Instant.now().minusSeconds(200 * 60); // GH-90000
            BackupMetadata meta = metadataWith("b1", "t1", oldBackup, "COMPLETE"); // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of(meta)));

            drService.scheduleAutomatedBackups("t1", new DRPolicy(60, 7, 120, BackupMode.FULL)); // GH-90000

            DRStatus status = drService.getDRStatus("t1 [GH-90000]").getResult();

            assertThat(status.withinRPO()).isFalse(); // GH-90000
            assertThat(status.lastBackupTime()).isEqualTo(oldBackup); // GH-90000
        }

        @Test
        @DisplayName("should reflect automation status in DR status [GH-90000]")
        void automationFlagInStatus() { // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of()));
            DRStatus status = drService.getDRStatus("t1 [GH-90000]").getResult();
            assertThat(status.automationActive()).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Recoverability Test
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recoverability Test [GH-90000]")
    class RecoverabilityTests {

        @Test
        @DisplayName("should return not-recoverable when no backup exists [GH-90000]")
        void noBackupMeansNotRecoverable() { // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of()));

            DRTestResult result = drService.testRecoverability("t1 [GH-90000]").getResult();

            assertThat(result.recoverable()).isFalse(); // GH-90000
            assertThat(result.backupId()).isNull(); // GH-90000
            assertThat(result.message()).contains("No completed backup [GH-90000]");
        }

        @Test
        @DisplayName("should return recoverable when latest backup verifies successfully [GH-90000]")
        void recoverableWhenVerificationPasses() { // GH-90000
            BackupMetadata meta = metadataWith("b1", "t1", Instant.now(), "COMPLETE"); // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of(meta)));
            when(backupService.verifyBackup("t1", "b1")) // GH-90000
                    .thenReturn(Promise.of(new VerificationResult("b1", true, 10, 10, null))); // GH-90000

            DRTestResult result = drService.testRecoverability("t1 [GH-90000]").getResult();

            assertThat(result.recoverable()).isTrue(); // GH-90000
            assertThat(result.backupId()).isEqualTo("b1 [GH-90000]");
            assertThat(result.message()).contains("verified [GH-90000]");
        }

        @Test
        @DisplayName("should return not-recoverable when verification fails [GH-90000]")
        void notRecoverableWhenVerificationFails() { // GH-90000
            BackupMetadata meta = metadataWith("b1", "t1", Instant.now(), "COMPLETE"); // GH-90000
            when(backupService.listBackups("t1 [GH-90000]")).thenReturn(Promise.of(List.of(meta)));
            when(backupService.verifyBackup("t1", "b1")) // GH-90000
                    .thenReturn(Promise.of(new VerificationResult("b1", false, 10, 8, "Count mismatch"))); // GH-90000

            DRTestResult result = drService.testRecoverability("t1 [GH-90000]").getResult();

            assertThat(result.recoverable()).isFalse(); // GH-90000
            assertThat(result.message()).isEqualTo("Count mismatch [GH-90000]");
        }

        @Test
        @DisplayName("should skip FAILED backups and use latest COMPLETE backup [GH-90000]")
        void skipsFailedBackupsForVerification() { // GH-90000
            Instant earlier = Instant.now().minusSeconds(3600); // GH-90000
            Instant later   = Instant.now().minusSeconds(1800); // GH-90000
            BackupMetadata failed   = metadataWith("b-fail", "t1", earlier, "FAILED"); // GH-90000
            BackupMetadata complete = metadataWith("b-ok",   "t1", later,   "COMPLETE"); // GH-90000

            when(backupService.listBackups("t1 [GH-90000]"))
                    .thenReturn(Promise.of(List.of(failed, complete))); // GH-90000
            when(backupService.verifyBackup("t1", "b-ok")) // GH-90000
                    .thenReturn(Promise.of(new VerificationResult("b-ok", true, 5, 5, null))); // GH-90000

            DRTestResult result = drService.testRecoverability("t1 [GH-90000]").getResult();

            assertThat(result.backupId()).isEqualTo("b-ok [GH-90000]");
            assertThat(result.recoverable()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Immediate Backup
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("triggerImmediateBackup should delegate to createFullBackup [GH-90000]")
    void immediateBackupDelegatesToFull() { // GH-90000
        BackupResult expected = successfulBackup("b-immediate [GH-90000]");
        when(backupService.createFullBackup("t1 [GH-90000]")).thenReturn(Promise.of(expected));

        BackupResult result = drService.triggerImmediateBackup("t1 [GH-90000]").getResult();

        assertThat(result.backupId()).isEqualTo("b-immediate [GH-90000]");
        verify(backupService).createFullBackup("t1 [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static BackupResult successfulBackup(String backupId) { // GH-90000
        return new BackupResult( // GH-90000
                backupId, "t1", "FULL", 42,
                Instant.now(), Instant.now(), true, List.of()); // GH-90000
    }

    private static BackupMetadata metadataWith(String id, String tenantId, // GH-90000
                                                Instant createdAt, String status) {
        return new BackupMetadata(id, tenantId, "FULL", createdAt, 10, status, "aep_patterns", 1); // GH-90000
    }
}
