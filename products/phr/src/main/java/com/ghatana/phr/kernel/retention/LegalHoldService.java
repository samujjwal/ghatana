package com.ghatana.phr.kernel.retention;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing legal holds on PHR patient data.
 *
 * <p>A legal hold overrides all retention policies and deletion workflows.
 * Any resource covered by an active legal hold must receive
 * {@link DeletionOutcome#RETAIN_UNDER_HOLD} regardless of the retention
 * period elapsed or the patient's deletion request.</p>
 *
 * <h3>Hold lifecycle</h3>
 * <ol>
 *   <li>Hold is raised by an authorised legal officer or automated regulatory trigger</li>
 *   <li>All deletion jobs skip covered resources and record a hold event</li>
 *   <li>Hold is explicitly released when proceedings or investigations conclude</li>
 *   <li>Deletion jobs resume for newly uncovered resources on the next scheduled run</li>
 * </ol>
 *
 * @doc.type interface
 * @doc.purpose Legal hold lifecycle management to gate PHR deletion workflows
 * @doc.layer product
 * @doc.pattern Service
 */
public interface LegalHoldService {

    // =========================================================================
    // Value types
    // =========================================================================

    /**
     * Scope of a legal hold — what data it covers within a tenant.
     */
    enum HoldScope {
        /** Hold covers all records for a specific patient. */
        PATIENT,
        /** Hold covers all records matching a specific resource type. */
        RESOURCE_TYPE,
        /** Hold covers one specific resource. */
        RESOURCE_ID,
        /** Hold covers all data for the tenant. */
        TENANT_WIDE
    }

    /**
     * Status of a legal hold.
     */
    enum HoldStatus { ACTIVE, RELEASED, EXPIRED }

    /**
     * An active or historical legal hold record.
     *
     * @param holdId         unique hold identifier
     * @param tenantId       tenant that owns the covered data
     * @param scope          scope of the hold
     * @param scopeValue     value qualifying the scope (patientId, resourceType, or resourceId)
     * @param raisedBy       identifier of the officer or system that raised the hold
     * @param raisedAt       when the hold was created
     * @param reason         human-readable reason (case reference, regulation citation, etc.)
     * @param status         current status
     * @param releasedAt     when the hold was released; null if still active
     * @param expiresAt      automatic expiry time; null for indefinite holds
     */
    record LegalHold(
            UUID holdId,
            String tenantId,
            HoldScope scope,
            String scopeValue,
            String raisedBy,
            Instant raisedAt,
            String reason,
            HoldStatus status,
            Instant releasedAt,
            Instant expiresAt) {

        public LegalHold {
            if (holdId == null) throw new IllegalArgumentException("holdId must not be null");
            if (tenantId == null || tenantId.isBlank())
                throw new IllegalArgumentException("tenantId must not be blank");
            if (scope == null) throw new IllegalArgumentException("scope must not be null");
            if (scopeValue == null || scopeValue.isBlank())
                throw new IllegalArgumentException("scopeValue must not be blank");
            if (raisedBy == null || raisedBy.isBlank())
                throw new IllegalArgumentException("raisedBy must not be blank");
            if (raisedAt == null) throw new IllegalArgumentException("raisedAt must not be null");
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("reason must not be blank");
            if (status == null) throw new IllegalArgumentException("status must not be null");
        }

        /** True when this hold is currently blocking deletion. */
        public boolean isActive() {
            if (status != HoldStatus.ACTIVE) return false;
            return expiresAt == null || Instant.now().isBefore(expiresAt);
        }
    }

    // =========================================================================
    // Contract methods
    // =========================================================================

    /**
     * Raises a new legal hold.
     *
     * @param tenantId   tenant that owns the covered data
     * @param scope      scope of the hold
     * @param scopeValue qualifying value for the scope
     * @param raisedBy   identifier of the officer or system raising the hold
     * @param reason     human-readable reason for the hold
     * @param expiresAt  optional expiry; pass null for an indefinite hold
     * @return the created {@link LegalHold}
     */
    Promise<LegalHold> raiseHold(
            String tenantId,
            HoldScope scope,
            String scopeValue,
            String raisedBy,
            String reason,
            Instant expiresAt);

    /**
     * Releases an active legal hold by ID.
     *
     * <p>Releasing a hold does not trigger immediate deletion — the next scheduled
     * deletion job will re-evaluate the newly uncovered resources.</p>
     *
     * @param holdId    the hold to release
     * @param releasedBy identifier of the officer or system releasing the hold
     */
    Promise<Void> releaseHold(UUID holdId, String releasedBy);

    /**
     * Returns whether there is any active legal hold covering a specific patient resource.
     *
     * @param tenantId     tenant that owns the record
     * @param patientId    the patient
     * @param resourceType FHIR resource type or domain name
     * @param resourceId   specific resource identifier
     * @return true if a hold is currently active for this resource
     */
    Promise<Boolean> isUnderHold(
            String tenantId, String patientId, String resourceType, String resourceId);

    /**
     * Lists all active holds for a tenant.
     *
     * @param tenantId the tenant
     * @return list of active {@link LegalHold} records
     */
    Promise<List<LegalHold>> listActiveHolds(String tenantId);

    /**
     * Retrieves a legal hold by its ID.
     *
     * @param holdId the hold identifier
     * @return an Optional containing the hold, or empty if not found
     */
    Promise<Optional<LegalHold>> getHold(UUID holdId);
}
