package com.ghatana.kernel.document;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Kernel-owned document and OCR lifecycle policy plugin.
 *
 * <p>Products keep their own DTOs, persistence, audit event names, and domain
 * authorization. The Kernel owns reusable upload policy validation,
 * provenance shape checks, malware attestation requirements, and deterministic
 * OCR review state transitions.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel document upload and OCR review lifecycle validation plugin
 * @doc.layer core
 * @doc.pattern Plugin
 */
public final class KernelDocumentOcrPlugin {

    private static final Set<String> ALLOWED_RESIDENCIES = Set.of("NP", "REGIONAL", "US");
    private static final Set<String> ALLOWED_RETENTIONS = Set.of("25years", "permanent");
    private static final Set<String> ALLOWED_ENCRYPTION_MODES = Set.of("managed-kms", "tenant-kms");
    private static final Set<String> ALLOWED_MALWARE_STATUSES = Set.of("clean");
    private static final Set<String> TERMINAL_REVIEW_STATUSES = Set.of("CONFIRMED", "REJECTED");

    public StoragePolicy validateStoragePolicy(StoragePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("storagePolicy is required");
        }
        requireAllowed(policy.residency(), "storagePolicy.residency", ALLOWED_RESIDENCIES);
        requireAllowed(policy.retention(), "storagePolicy.retention", ALLOWED_RETENTIONS);
        requireAllowed(policy.encryption(), "storagePolicy.encryption", ALLOWED_ENCRYPTION_MODES);
        return policy;
    }

    public MalwareScanAttestation validateMalwareScan(MalwareScanAttestation attestation) {
        if (attestation == null) {
            throw new IllegalArgumentException("malwareScan is required");
        }
        requireAllowed(attestation.status(), "malwareScan.status", ALLOWED_MALWARE_STATUSES);
        requireNonBlank(attestation.engine(), "malwareScan.engine");
        if (attestation.scannedAt() == null) {
            throw new IllegalArgumentException("malwareScan.scannedAt is required");
        }
        return attestation;
    }

    public UploadProvenance validateUploadProvenance(UploadProvenance provenance, String expectedSubjectId) {
        if (provenance == null) {
            throw new IllegalArgumentException("provenance is required");
        }
        requireNonBlank(expectedSubjectId, "expectedSubjectId");
        requireNonBlank(provenance.source(), "provenance.source");
        requireNonBlank(provenance.uploadedBy(), "provenance.uploadedBy");
        requireNonBlank(provenance.subjectId(), "provenance.subjectId");
        if (!expectedSubjectId.equals(provenance.subjectId())) {
            throw new IllegalArgumentException("provenance.subjectId must match expectedSubjectId");
        }
        return provenance;
    }

    public ReviewTransition validateReviewTransition(
        String currentStatus,
        String existingIdempotencyKey,
        String requestedStatus,
        String requestedIdempotencyKey
    ) {
        requireAllowed(requestedStatus, "requestedStatus", TERMINAL_REVIEW_STATUSES);
        String normalizedCurrentStatus = currentStatus == null || currentStatus.isBlank()
            ? "PENDING_REVIEW"
            : currentStatus;
        if ("PENDING_REVIEW".equals(normalizedCurrentStatus)) {
            return ReviewTransition.APPLY;
        }
        if (Objects.equals(existingIdempotencyKey, requestedIdempotencyKey)) {
            return ReviewTransition.RETURN_EXISTING;
        }
        throw new IllegalStateException("OCR document is already " + normalizedCurrentStatus);
    }

    private static void requireAllowed(String value, String fieldName, Set<String> allowedValues) {
        requireNonBlank(value, fieldName);
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(fieldName + " is not supported");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    public enum ReviewTransition {
        APPLY,
        RETURN_EXISTING
    }

    public record StoragePolicy(String residency, String retention, String encryption) {}

    public record UploadProvenance(String source, String uploadedBy, String subjectId) {}

    public record MalwareScanAttestation(String status, String engine, Instant scannedAt) {}
}
