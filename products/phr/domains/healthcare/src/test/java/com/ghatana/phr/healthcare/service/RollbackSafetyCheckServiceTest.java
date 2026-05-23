package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.healthcare.port.PatientStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        lenient().when(patientStore.findByTenantId(anyString())).thenReturn(Promise.of(List.of()));
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
    @DisplayName("Should require non-null tenant ID")
    void shouldRequireNonNullTenantId() {
        try {
            new RollbackSafetyCheckService.RollbackSafetyCheck(
                null,
                "current-artifact-001",
                "phr-deploy-2026-05-23-001",
                Instant.now(),
                "staging"
            );
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("tenantId must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null current artifact ID")
    void shouldRequireNonNullCurrentArtifactId() {
        try {
            new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                null,
                "phr-deploy-2026-05-23-001",
                Instant.now(),
                "staging"
            );
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("currentArtifactId must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null target artifact ID")
    void shouldRequireNonNullTargetArtifactId() {
        try {
            new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                "current-artifact-001",
                null,
                Instant.now(),
                "staging"
            );
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("targetArtifactId must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null check timestamp")
    void shouldRequireNonNullCheckTimestamp() {
        try {
            new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                "current-artifact-001",
                "phr-deploy-2026-05-23-001",
                null,
                "staging"
            );
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("checkTimestamp must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null environment")
    void shouldRequireNonNullEnvironment() {
        try {
            new RollbackSafetyCheckService.RollbackSafetyCheck(
                "tenant-123",
                "current-artifact-001",
                "phr-deploy-2026-05-23-001",
                Instant.now(),
                null
            );
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("environment must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null patient store in constructor")
    void shouldRequireNonNullPatientStore() {
        try {
            new RollbackSafetyCheckService(null, executor, deploymentHistoryPath);
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("patientStore must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null executor in constructor")
    void shouldRequireNonNullExecutor() {
        try {
            new RollbackSafetyCheckService(patientStore, null, deploymentHistoryPath);
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("executor must not be null");
        }
    }

    @Test
    @DisplayName("Should require non-null deployment history path in constructor")
    void shouldRequireNonNullDeploymentHistoryPath() {
        try {
            new RollbackSafetyCheckService(patientStore, executor, null);
            org.junit.jupiter.api.Assertions.fail("Should have thrown NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("deploymentHistoryPath must not be null");
        }
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
}
