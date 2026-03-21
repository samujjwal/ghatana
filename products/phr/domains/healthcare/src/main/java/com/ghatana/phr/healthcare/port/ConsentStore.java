package com.ghatana.phr.healthcare.port;

import com.ghatana.phr.healthcare.domain.ConsentAction;
import com.ghatana.phr.healthcare.domain.ConsentRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port: Consent record store.
 *
 * <p>All consent records are append-only. Implementations must never update or
 * delete a consent record — revocation creates a new record.
 * Implementations must enforce tenant isolation.</p>
 *
 * @doc.type interface
 * @doc.purpose Consent persistence port — append-only, tenant-isolated
 * @doc.layer domain-pack
 * @doc.pattern PortAdapter
 * @since 1.0.0
 */
public interface ConsentStore {

    /**
     * Appends a new consent record. Must fail if a record with the same consentId already
     * exists (idempotency guard against duplicate event delivery).
     *
     * @param record the consent record to persist
     */
    void append(ConsentRecord record);

    /**
     * Returns the active consent record that authorises {@code granteeId} to perform
     * {@code action} on a patient's C3/C4 resource.
     *
     * @param tenantId   the tenant scope
     * @param patientId  the patient's UUID
     * @param granteeId  the requesting actor/service
     * @param action     the consent action being checked
     * @return the active, non-expired consent if it exists
     */
    Optional<ConsentRecord> findActiveConsent(
            String tenantId, UUID patientId, String granteeId, ConsentAction action);

    /**
     * Returns all consent records for a patient (all statuses, newest first).
     *
     * @param tenantId  the tenant scope
     * @param patientId the patient's UUID
     */
    List<ConsentRecord> findAllForPatient(String tenantId, UUID patientId);

    /**
     * Returns all consent records that a specific grantee holds for any patient within
     * a tenant. Used for grantee audit and offboarding.
     *
     * @param tenantId  the tenant scope
     * @param granteeId the grantee's id
     */
    List<ConsentRecord> findAllForGrantee(String tenantId, String granteeId);
}
