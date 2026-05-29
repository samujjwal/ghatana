package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.healthcare.port.PatientStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Production-grade tests for RollbackSafetyCheckService.
 * Tests verify rollback safety checks for PHR healthcare deployments.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rollback Safety Check Service Tests")
class RollbackSafetyCheckServiceTest extends EventloopTestBase {

    @Mock
    private PatientStore patientStore;

    private RollbackSafetyCheckService service;
    private Path deploymentHistoryPath;
    private java.util.concurrent.Executor executor;

    @BeforeEach
    void setUp() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        deploymentHistoryPath = Files.createTempFile("deployment-history", ".json");
        
        // Write sample deployment history
        String sampleHistory = """
            {
              "schemaVersion": "1.0.0",
              "productId": "phr",
              "history": [
                {
                  "deploymentId": "phr-deploy-2026-05-23-001",
                  "artifactDigest": "sha256:abc123",
                  "status": "success"
                }
              ]
            }
            """;
        Files.writeString(deploymentHistoryPath, sampleHistory);

        service = new RollbackSafetyCheckService(patientStore, executor, deploymentHistoryPath);

        // Lenient stubs for methods not used in all tests
        lenient().when(patientStore.findByTenant(anyString(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Should allow rollback when all checks pass")
    void shouldAllowRollbackWhenAllChecksPass() {
        RollbackSafetyCheckService.RollbackSafetyCheck check = new RollbackSafetyCheckService.RollbackSafetyCheck(
            "tenant-123",
            "current-artifact-001",
            "phr-deploy-2026-05-23-001",
            Instant.now(),
            "staging"
        );

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isTrue();
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    @DisplayName("Should allow rollback when target artifact matches recorded digest")
    void shouldAllowRollbackWhenTargetArtifactMatchesRecordedDigest() {
        RollbackSafetyCheckService.RollbackSafetyCheck check = new RollbackSafetyCheckService.RollbackSafetyCheck(
            "tenant-123",
            "current-artifact-001",
            "sha256:abc123",
            Instant.now(),
            "staging"
        );

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isTrue();
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    @DisplayName("Should block rollback when target artifact is unknown")
    void shouldBlockRollbackWhenTargetArtifactUnknown() {
        RollbackSafetyCheckService.RollbackSafetyCheck check = new RollbackSafetyCheckService.RollbackSafetyCheck(
            "tenant-123",
            "current-artifact-001",
            "unknown-artifact-999",
            Instant.now(),
            "staging"
        );

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).contains("Target artifact is not in deployment history: unknown-artifact-999");
    }

    @Test
    @DisplayName("Should block rollback when artifact ID is blank")
    void shouldBlockRollbackWhenArtifactIdBlank() {
        RollbackSafetyCheckService.RollbackSafetyCheck check = new RollbackSafetyCheckService.RollbackSafetyCheck(
            "tenant-123",
            "current-artifact-001",
            "",
            Instant.now(),
            "staging"
        );

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).contains("Target artifact is not in deployment history: ");
    }

    @Test
    @DisplayName("Should block rollback when deployment history file does not exist")
    void shouldBlockRollbackWhenHistoryFileMissing() throws Exception {
        // Delete the temp file
        Files.deleteIfExists(deploymentHistoryPath);
        
        RollbackSafetyCheckService serviceNoHistory = new RollbackSafetyCheckService(patientStore, executor, deploymentHistoryPath);

        RollbackSafetyCheckService.RollbackSafetyCheck check = new RollbackSafetyCheckService.RollbackSafetyCheck(
            "tenant-123",
            "current-artifact-001",
            "phr-deploy-2026-05-23-001",
            Instant.now(),
            "staging"
        );

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> serviceNoHistory.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).contains("Target artifact is not in deployment history: phr-deploy-2026-05-23-001");
    }

    @Test
    @DisplayName("Should block rollback when patient data is inconsistent")
    void shouldBlockRollbackWhenPatientDataIsInconsistent() {
        when(patientStore.findByTenant(anyString(), anyInt(), anyInt())).thenReturn(List.of(
            patient("other-tenant", "NHS-12345", Instant.now().minusSeconds(3_600))
        ));
        RollbackSafetyCheckService.RollbackSafetyCheck check = validCheck();

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).contains("Patient data consistency check failed - data integrity issues detected");
    }

    @Test
    @DisplayName("Should block rollback when clinical activity is active")
    void shouldBlockRollbackWhenClinicalActivityIsActive() {
        when(patientStore.findByTenant(anyString(), anyInt(), anyInt())).thenReturn(List.of(
            patient("tenant-123", "NHS-12345", Instant.now())
        ));
        RollbackSafetyCheckService.RollbackSafetyCheck check = validCheck();

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).contains("Active treatments in progress - rollback would disrupt patient care");
    }

    @Test
    @DisplayName("Should warn when audit history is malformed")
    void shouldWarnWhenAuditHistoryIsMalformed() throws Exception {
        Files.writeString(deploymentHistoryPath, """
            {
              "schemaVersion": "1.0.0",
              "history": [
                {
                  "artifactDigest": "sha256:abc123",
                  "status": "success"
                }
              ]
            }
            """);
        RollbackSafetyCheckService.RollbackSafetyCheck check = validCheck("sha256:abc123");

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isTrue();
        assertThat(result.warnings()).contains("Audit trail integrity check shows potential issues - review recommended");
    }

    @Test
    @DisplayName("Should block rollback when tenant has duplicate NHS IDs")
    void shouldBlockRollbackWhenTenantHasDuplicateNhsIds() {
        when(patientStore.findByTenant(anyString(), anyInt(), anyInt())).thenReturn(List.of(
            patient("tenant-123", "NHS-12345", Instant.now().minusSeconds(3_600)),
            patient("tenant-123", "NHS-12345", Instant.now().minusSeconds(7_200))
        ));
        RollbackSafetyCheckService.RollbackSafetyCheck check = validCheck();

        RollbackSafetyCheckService.RollbackSafetyResult result = runPromise(() -> service.checkRollbackSafety(check));

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).contains("Consent record consistency check failed - consent state may be invalid after rollback");
    }

    @Test
    @DisplayName("Should require non-null tenant ID")
    void shouldRequireNonNullTenantId() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService.RollbackSafetyCheck(
                null,
                "current-artifact-001",
                "phr-deploy-2026-05-23-001",
                Instant.now(),
                "staging"
            )
        );
        assertThat(e.getMessage()).contains("tenantId must not be null");
    }

    @Test
    @DisplayName("Should require non-null current artifact ID")
    void shouldRequireNonNullCurrentArtifactId() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                null,
                "phr-deploy-2026-05-23-001",
                Instant.now(),
                "staging"
            )
        );
        assertThat(e.getMessage()).contains("currentArtifactId must not be null");
    }

    @Test
    @DisplayName("Should require non-null target artifact ID")
    void shouldRequireNonNullTargetArtifactId() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                "current-artifact-001",
                null,
                Instant.now(),
                "staging"
            )
        );
        assertThat(e.getMessage()).contains("targetArtifactId must not be null");
    }

    @Test
    @DisplayName("Should require non-null check timestamp")
    void shouldRequireNonNullCheckTimestamp() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                "current-artifact-001",
                "phr-deploy-2026-05-23-001",
                null,
                "staging"
            )
        );
        assertThat(e.getMessage()).contains("checkTimestamp must not be null");
    }

    @Test
    @DisplayName("Should require non-null environment")
    void shouldRequireNonNullEnvironment() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                "current-artifact-001",
                "phr-deploy-2026-05-23-001",
                Instant.now(),
                null
            )
        );
        assertThat(e.getMessage()).contains("environment must not be null");
    }

    @Test
    @DisplayName("Should require non-null patient store in constructor")
    void shouldRequireNonNullPatientStore() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService(null, executor, deploymentHistoryPath)
        );
        assertThat(e.getMessage()).contains("patientStore must not be null");
    }

    @Test
    @DisplayName("Should require non-null executor in constructor")
    void shouldRequireNonNullExecutor() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService(patientStore, null, deploymentHistoryPath)
        );
        assertThat(e.getMessage()).contains("executor must not be null");
    }

    @Test
    @DisplayName("Should require non-null deployment history path in constructor")
    void shouldRequireNonNullDeploymentHistoryPath() {
        NullPointerException e = org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> new RollbackSafetyCheckService(patientStore, executor, null)
        );
        assertThat(e.getMessage()).contains("deploymentHistoryPath must not be null");
    }

    @Test
    @DisplayName("Should create allowed result with warnings")
    void shouldCreateAllowedResultWithWarnings() {
        List<String> warnings = List.of("Warning 1", "Warning 2");
        RollbackSafetyCheckService.RollbackSafetyResult result = RollbackSafetyCheckService.RollbackSafetyResult.allowed(warnings);

        assertThat(result.safeToRollback()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).containsExactly("Warning 1", "Warning 2");
    }

    @Test
    @DisplayName("Should create blocked result with single reason")
    void shouldCreateBlockedResultWithSingleReason() {
        RollbackSafetyCheckService.RollbackSafetyResult result = RollbackSafetyCheckService.RollbackSafetyResult.blocked("Blocker reason");

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).containsExactly("Blocker reason");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Should create blocked result with multiple reasons")
    void shouldCreateBlockedResultWithMultipleReasons() {
        List<String> reasons = List.of("Blocker 1", "Blocker 2");
        RollbackSafetyCheckService.RollbackSafetyResult result = RollbackSafetyCheckService.RollbackSafetyResult.blocked(reasons);

        assertThat(result.safeToRollback()).isFalse();
        assertThat(result.blockers()).containsExactly("Blocker 1", "Blocker 2");
        assertThat(result.warnings()).isEmpty();
    }

    private static RollbackSafetyCheckService.RollbackSafetyCheck validCheck() {
        return validCheck("phr-deploy-2026-05-23-001");
    }

    private static RollbackSafetyCheckService.RollbackSafetyCheck validCheck(String targetArtifactId) {
        return new RollbackSafetyCheckService.RollbackSafetyCheck(
            "tenant-123",
            "current-artifact-001",
            targetArtifactId,
            Instant.now(),
            "staging"
        );
    }

    private static Patient patient(String tenantId, String nhsId, Instant lastClinicalActivityAt) {
        return new Patient(
            UUID.randomUUID(),
            tenantId,
            nhsId,
            "Maya",
            "Shrestha",
            LocalDate.of(1990, 2, 3),
            "female",
            null,
            null,
            null,
            null,
            "3",
            DataClassification.C2,
            "clinician-1",
            Instant.now().minusSeconds(86_400),
            lastClinicalActivityAt,
            true
        );
    }
}
