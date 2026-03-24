package com.ghatana.phr.healthcare.port;

import com.ghatana.phr.healthcare.domain.Patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port: Patient data store.
 *
 * <p>Implementations must enforce tenant isolation: all operations carry an explicit
 * {@code tenantId} and must reject cross-tenant access. Adapters are expected to
 * back this port with a PostgreSQL RLS-enabled table.</p>
 *
 * @doc.type interface
 * @doc.purpose Patient persistence port — healthcare domain pack
 * @doc.layer domain-pack
 * @doc.pattern PortAdapter
 * @since 1.0.0
 */
public interface PatientStore {

    /**
     * Persists a new patient record.
     *
     * @param patient the patient to save; must have a non-null tenantId  and patientId
     */
    void save(Patient patient);

    /**
     * Returns the patient if it belongs to the given tenant.
     *
     * @param tenantId the tenant scope (required)
     * @param patientId the patient's UUID
     * @return optional patient
     */
    Optional<Patient> findById(String tenantId, UUID patientId);

    /**
     * Returns a page of patients for the given tenant, ordered by registeredAt DESC.
     *
     * @param tenantId the tenant scope
     * @param limit max results per page
     * @param offset 0-based offset
     */
    List<Patient> findByTenant(String tenantId, int limit, int offset);

    /**
     * Marks a patient as inactive (soft-delete).
     * Right-to-erasure requests use {@code PatientDeletionService} which calls this after
     * eligibility checks and audit logging.
     *
     * @param tenantId the tenant scope
     * @param patientId the patient's UUID
     */
    void deactivate(String tenantId, UUID patientId);

    /**
     * Permanently removes patient data. MUST only be called by the retention/erasure workflow
     * after all eligibility checks, legal holds, and audit records are preserved.
     *
     * @param tenantId the tenant scope
     * @param patientId the patient's UUID
     */
    void hardDelete(String tenantId, UUID patientId);

    /**
     * Returns the patient matching the NHS ID within the given tenant, if any.
     *
     * <p>Used to enforce NHS ID uniqueness within a tenant during registration.
     *
     * @param tenantId the tenant scope
     * @param nhsId    the NHS identifier
     * @return optional patient
     */
    Optional<Patient> findByNhsId(String tenantId, String nhsId);
}
