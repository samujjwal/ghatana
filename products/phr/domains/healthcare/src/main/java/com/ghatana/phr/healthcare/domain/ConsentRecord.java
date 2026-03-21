package com.ghatana.phr.healthcare.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FHIR R4-aligned consent record.
 *
 * <p>Represents a patient's persisted consent decision that authorises (or denies)
 * one or more {@link ConsentAction}s on a specific patient resource classification
 * within a tenant. Consent records are append-only: revocation creates a new record
 * in {@link ConsentStatus#REVOKED} state — existing records must never be mutated.</p>
 *
 * <p>Lifecycle:
 * {@code DRAFT → PROPOSED → ACTIVE}  (normal grant flow)<br>
 * {@code ACTIVE → REVOKED}            (patient or provider revokes)<br>
 * {@code ACTIVE → EXPIRED}            (ttlSeconds elapsed)</p>
 *
 * <p>Retention: 25 years per Nepal Health Records Act 2081 — same as patient records.</p>
 *
 * @doc.type record
 * @doc.purpose Healthcare consent record — FHIR-R4-aligned, append-only
 * @doc.layer domain-pack
 * @doc.pattern EventSourced, ValueObject
 * @since 1.0.0
 */
public record ConsentRecord(
    UUID consentId,
    String tenantId,
    UUID patientId,

    /** Actor who granted or requested the consent (patient, caregiver, provider). */
    String grantorId,
    String grantorType,       // PATIENT | PROVIDER | CAREGIVER | ADMIN

    /** Actor who is the beneficiary of the consent (provider, facility, product). */
    String granteeId,
    String granteeType,       // PROVIDER | FACILITY | SERVICE

    List<ConsentAction> grantedActions,
    DataClassification applicableClassification,

    ConsentStatus status,
    String purposeOfUse,     // SELF_SERVICE | CARE_DELIVERY | EMERGENCY | AUDIT_REVIEW

    Instant grantedAt,
    Instant expiresAt,        // null = no expiry
    Instant revokedAt,        // null unless status = REVOKED
    String revocationReason,  // null unless status = REVOKED

    String createdBy
) {

    public ConsentRecord {
        Objects.requireNonNull(consentId, "consentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(grantedActions, "grantedActions must not be null");
        if (grantedActions.isEmpty()) {
            throw new IllegalArgumentException("A consent record must grant at least one action");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(purposeOfUse, "purposeOfUse must not be null");
        Objects.requireNonNull(grantedAt, "grantedAt must not be null");
    }

    /**
     * Factory: create a new active consent grant.
     */
    public static ConsentRecord newGrant(
            String tenantId, UUID patientId,
            String grantorId, String grantorType,
            String granteeId, String granteeType,
            List<ConsentAction> actions,
            DataClassification classification,
            String purposeOfUse,
            Instant expiresAt,
            String createdBy) {
        return new ConsentRecord(
            UUID.randomUUID(), tenantId, patientId,
            grantorId, grantorType, granteeId, granteeType,
            List.copyOf(actions), classification,
            ConsentStatus.ACTIVE, purposeOfUse,
            Instant.now(), expiresAt, null, null, createdBy
        );
    }

    /**
     * Returns whether this consent record currently authorises the given action.
     * A record authorises if: status is ACTIVE, not expired, and the action is in grantedActions.
     */
    public boolean authorises(ConsentAction action, Instant now) {
        if (status != ConsentStatus.ACTIVE) return false;
        if (expiresAt != null && now.isAfter(expiresAt)) return false;
        return grantedActions.contains(action);
    }

    /**
     * Returns a revoked copy of this record (append-only mutation model).
     */
    public ConsentRecord revoked(String reason, Instant revokedAt) {
        return new ConsentRecord(
            consentId, tenantId, patientId,
            grantorId, grantorType, granteeId, granteeType,
            grantedActions, applicableClassification,
            ConsentStatus.REVOKED, purposeOfUse,
            grantedAt, expiresAt, revokedAt, reason, createdBy
        );
    }
}
