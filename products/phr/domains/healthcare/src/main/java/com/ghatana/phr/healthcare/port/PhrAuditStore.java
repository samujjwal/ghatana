package com.ghatana.phr.healthcare.port;

import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.platform.audit.AuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port: PHR healthcare audit store for immutable access history.
 *
 * <p>This port provides healthcare-specific audit operations ensuring immutable
 * access history for patient data. All access events are recorded and queryable
 * for compliance and audit purposes.</p>
 *
 * <p>Retention: 25 years per Nepal Health Records Act 2081.</p>
 *
 * @doc.type interface
 * @doc.purpose Healthcare-specific audit store for immutable access history
 * @doc.layer domain-pack
 * @doc.pattern PortAdapter
 */
public interface PhrAuditStore {

    /**
     * Records a patient data access event.
     *
     * @param event the audit event to record
     */
    void recordAccess(AuditEvent event);

    /**
     * Records a consent grant event.
     *
     * @param event the audit event to record
     */
    void recordConsentGrant(AuditEvent event);

    /**
     * Records a consent revocation event.
     *
     * @param event the audit event to record
     */
    void recordConsentRevocation(AuditEvent event);

    /**
     * Returns all access events for a specific patient.
     *
     * @param tenantId the tenant scope
     * @param patientId the patient's UUID
     * @return list of access events for the patient
     */
    List<AuditEvent> findAccessByPatient(String tenantId, UUID patientId);

    /**
     * Returns all access events for a specific actor.
     *
     * @param tenantId the tenant scope
     * @param actorId the actor's id
     * @return list of access events for the actor
     */
    List<AuditEvent> findAccessByActor(String tenantId, String actorId);

    /**
     * Returns all access events for a specific data classification level.
     *
     * @param tenantId the tenant scope
     * @param classification the data classification level
     * @return list of access events for the classification level
     */
    List<AuditEvent> findAccessByClassification(String tenantId, DataClassification classification);

    /**
     * Returns all access events within a time range.
     *
     * @param tenantId the tenant scope
     * @param startDate start of time range (inclusive)
     * @param endDate end of time range (inclusive)
     * @return list of access events within the time range
     */
    List<AuditEvent> findAccessByTimeRange(String tenantId, Instant startDate, Instant endDate);

    /**
     * Returns all consent events for a specific patient.
     *
     * @param tenantId the tenant scope
     * @param patientId the patient's UUID
     * @return list of consent events for the patient
     */
    List<AuditEvent> findConsentByPatient(String tenantId, UUID patientId);

    /**
     * Returns the total count of access events for a patient.
     *
     * @param tenantId the tenant scope
     * @param patientId the patient's UUID
     * @return total count of access events
     */
    long countAccessByPatient(String tenantId, UUID patientId);
}
