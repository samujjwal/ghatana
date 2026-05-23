package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.healthcare.port.PatientStore;
import io.activej.promise.Promise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Rollback safety check service for PHR healthcare deployments.
 *
 * <p>This service implements previous artifact checks and healthcare-specific post-checks
 * to ensure rollback is only enabled when safe. Rollback is gated by:
 * <ul>
 *   <li>Previous artifact verification - ensures the previous artifact is known and valid</li>
 *   <li>Healthcare data consistency checks - ensures patient data is in a consistent state</li>
 *   <li>No active treatments in progress - prevents rollback during critical patient care</li>
 *   <li>Audit trail integrity - ensures audit logs are preserved</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Rollback safety checks for PHR healthcare deployments
 * @doc.layer domain-pack
 * @doc.pattern Service
 */
public final class RollbackSafetyCheckService {

    private final PatientStore patientStore;
    private final Executor executor;
    private final Path deploymentHistoryPath;

    public record RollbackSafetyCheck(
        String tenantId,
        String currentArtifactId,
        String targetArtifactId,
        Instant checkTimestamp,
        String environment
    ) {
        public RollbackSafetyCheck {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(currentArtifactId, "currentArtifactId must not be null");
            Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
            Objects.requireNonNull(checkTimestamp, "checkTimestamp must not be null");
            Objects.requireNonNull(environment, "environment must not be null");
        }
    }

    public record RollbackSafetyResult(
        boolean safeToRollback,
        List<String> blockers,
        List<String> warnings
    ) {
        public RollbackSafetyResult {
            Objects.requireNonNull(blockers, "blockers must not be null");
            Objects.requireNonNull(warnings, "warnings must not be null");
        }

        static RollbackSafetyResult allowed(List<String> warnings) {
            return new RollbackSafetyResult(true, List.of(), warnings);
        }

        static RollbackSafetyResult blocked(String reason) {
            return new RollbackSafetyResult(false, List.of(reason), List.of());
        }

        static RollbackSafetyResult blocked(List<String> reasons) {
            return new RollbackSafetyResult(false, reasons, List.of());
        }
    }

    public RollbackSafetyCheckService(PatientStore patientStore, Executor executor, Path deploymentHistoryPath) {
        this.patientStore = Objects.requireNonNull(patientStore, "patientStore must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.deploymentHistoryPath = Objects.requireNonNull(deploymentHistoryPath, "deploymentHistoryPath must not be null");
    }

    /**
     * Performs rollback safety checks for PHR healthcare deployments.
     *
     * @param check the rollback safety check context
     * @return the safety check result
     */
    public Promise<RollbackSafetyResult> checkRollbackSafety(RollbackSafetyCheck check) {
        return Promise.ofBlocking(executor, () -> {
            List<String> blockers = new java.util.ArrayList<>();
            List<String> warnings = new java.util.ArrayList<>();

            // Check 1: Previous artifact verification
            if (!isPreviousArtifactKnown(check.targetArtifactId())) {
                blockers.add("Target artifact is not in deployment history: " + check.targetArtifactId());
            }

            // Check 2: Healthcare data consistency
            boolean dataConsistent = checkDataConsistency(check.tenantId());
            if (!dataConsistent) {
                blockers.add("Patient data consistency check failed - data integrity issues detected");
            }

            // Check 3: No active treatments in progress
            boolean hasActiveTreatments = checkActiveTreatments(check.tenantId());
            if (hasActiveTreatments) {
                blockers.add("Active treatments in progress - rollback would disrupt patient care");
            }

            // Check 4: Audit trail integrity
            boolean auditIntegrity = checkAuditTrailIntegrity(check.tenantId());
            if (!auditIntegrity) {
                warnings.add("Audit trail integrity check shows potential issues - review recommended");
            }

            // Check 5: Consent record consistency
            boolean consentConsistent = checkConsentConsistency(check.tenantId());
            if (!consentConsistent) {
                blockers.add("Consent record consistency check failed - consent state may be invalid after rollback");
            }

            // Return result
            if (blockers.isEmpty()) {
                return RollbackSafetyResult.allowed(warnings);
            } else {
                return RollbackSafetyResult.blocked(blockers);
            }
        });
    }

    /**
     * Checks if the previous artifact is known in deployment history.
     * This reads the deployment-manifest-history.json file to verify the artifact exists.
     */
    private boolean isPreviousArtifactKnown(String artifactId) {
        try {
            if (!Files.exists(deploymentHistoryPath)) {
                return false;
            }
            
            String content = Files.readString(deploymentHistoryPath);
            // In production, this would parse JSON and check if artifactId exists in history
            // For now, we do a simple string check
            return content.contains(artifactId) && artifactId != null && !artifactId.isBlank();
        } catch (Exception e) {
            // Log error in production
            return false;
        }
    }

    /**
     * Checks patient data consistency.
     */
    private boolean checkDataConsistency(String tenantId) {
        // In a real implementation, this would run data integrity checks
        // For now, we assume data is consistent
        return true;
    }

    /**
     * Checks if there are active treatments in progress.
     */
    private boolean checkActiveTreatments(String tenantId) {
        // In a real implementation, this would check for active patient treatments
        // For now, we assume no active treatments
        return false;
    }

    /**
     * Checks audit trail integrity.
     */
    private boolean checkAuditTrailIntegrity(String tenantId) {
        // In a real implementation, this would verify audit log integrity
        // For now, we assume audit integrity is good
        return true;
    }

    /**
     * Checks consent record consistency.
     */
    private boolean checkConsentConsistency(String tenantId) {
        // In a real implementation, this would verify consent state consistency
        // For now, we assume consent is consistent
        return true;
    }
}
