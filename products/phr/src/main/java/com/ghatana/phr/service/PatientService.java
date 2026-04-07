package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.phr.kernel.service.PhrInputSanitizationUtils;
import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.repository.PatientRecordRepository;
import com.ghatana.phr.security.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic service for PatientService
 *
 * @doc.type record
 * @doc.purpose Business logic service for PatientService
 * @doc.layer product
 * @doc.pattern Service
 */
public class PatientService {
    private static final Logger logger = LoggerFactory.getLogger(PatientService.class);

    private final KernelTelemetryManager telemetry;
    private final AuditTrailService auditTrail;
    private final PatientRecordRepository recordsRepository;

    public PatientService(KernelTelemetryManager telemetry,
                         AuditTrailService auditTrail,
                         PatientRecordRepository recordsRepository) {
        this.telemetry = telemetry;
        this.auditTrail = auditTrail;
        this.recordsRepository = recordsRepository;
    }

    public PatientRecords getRecords(String patientId) {
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        KernelTelemetryManager.Timer timer = telemetry.startTimer(
            "phr.patient.records.fetch",
            "patient_id", sanitizedPatientId
        );
        
        try {
            PatientRecords records = recordsRepository.findByPatientId(sanitizedPatientId);
            
            telemetry.recordMetric(
                "phr.patient.records.count",
                records.size(),
                "patient_id", sanitizedPatientId
            );
            
            AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("patient.records.accessed")
                .entityId(sanitizedPatientId)
                .userId(getCurrentUserId())
                .tenantId(getCurrentTenantId())
                .action("read")
                .data(Map.of("record_count", records.size()))
                .build();
            
            auditTrail.recordAuditEvent(event);
            
            return records;
            
        } finally {
            timer.stop();
        }
    }

    public void createRecord(String patientId, Map<String, Object> recordData) {
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        Map<String, Object> sanitizedRecordData = PhrInputSanitizationUtils.sanitizeStructuredData(recordData, "recordData");
        KernelTelemetryManager.Timer timer = telemetry.startTimer(
            "phr.patient.records.create",
            "patient_id", sanitizedPatientId
        );
        
        try {
            PatientRecord record = new PatientRecord();
            record.setRecordId(UUID.randomUUID().toString());
            record.setPatientId(sanitizedPatientId);
            record.setTenantId(getCurrentTenantId());
            record.setData(sanitizedRecordData);
            record.setCreatedBy(getCurrentUserId());
            record.setCreatedAt(Instant.now());
            
            recordsRepository.save(record);
            
            telemetry.incrementCounter(
                "phr.patient.records.created",
                1,
                "patient_id", sanitizedPatientId
            );
            
            AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("patient.records.created")
                .entityId(sanitizedPatientId)
                .userId(getCurrentUserId())
                .tenantId(getCurrentTenantId())
                .action("create")
                .data(Map.of("record_id", record.getRecordId()))
                .build();

            try {
                auditTrail.recordAuditEvent(event);
            } catch (RuntimeException exception) {
                rollbackCreatedRecord(record, exception);
            }
            
        } finally {
            timer.stop();
        }
    }

    private void rollbackCreatedRecord(PatientRecord record, RuntimeException auditException) {
        String patientId = record.getPatientId();
        try {
            recordsRepository.delete(record.getRecordId());
            telemetry.incrementCounter(
                "phr.patient.records.rollback",
                1,
                "patient_id", patientId
            );
        } catch (RuntimeException rollbackException) {
            auditException.addSuppressed(rollbackException);
            logger.error("Failed to rollback patient record {} after audit failure", record.getRecordId(), rollbackException);
            throw new IllegalStateException(
                "Failed to create patient record and rollback the partial write",
                auditException
            );
        }

        throw new IllegalStateException(
            "Failed to create patient record because audit logging failed",
            auditException
        );
    }

    private String getCurrentUserId() {
        return SecurityContextHolder.getContext() != null 
            ? SecurityContextHolder.getContext().getUserId() 
            : "system";
    }

    private String getCurrentTenantId() {
        return SecurityContextHolder.getContext() != null 
            ? SecurityContextHolder.getContext().getTenantId() 
            : "default";
    }
}
