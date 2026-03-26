package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.repository.PatientRecordRepository;
import com.ghatana.phr.security.SecurityContextHolder;

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
        KernelTelemetryManager.Timer timer = telemetry.startTimer(
            "phr.patient.records.fetch",
            "patient_id", patientId
        );
        
        try {
            PatientRecords records = recordsRepository.findByPatientId(patientId);
            
            telemetry.recordMetric(
                "phr.patient.records.count",
                records.size(),
                "patient_id", patientId
            );
            
            AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("patient.records.accessed")
                .entityId(patientId)
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
        KernelTelemetryManager.Timer timer = telemetry.startTimer(
            "phr.patient.records.create",
            "patient_id", patientId
        );
        
        try {
            PatientRecord record = new PatientRecord();
            record.setRecordId(UUID.randomUUID().toString());
            record.setPatientId(patientId);
            record.setTenantId(getCurrentTenantId());
            record.setData(recordData);
            record.setCreatedBy(getCurrentUserId());
            record.setCreatedAt(Instant.now());
            
            recordsRepository.save(record);
            
            telemetry.incrementCounter(
                "phr.patient.records.created",
                1,
                "patient_id", patientId
            );
            
            AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("patient.records.created")
                .entityId(patientId)
                .userId(getCurrentUserId())
                .tenantId(getCurrentTenantId())
                .action("create")
                .data(Map.of("record_id", record.getRecordId()))
                .build();
            
            auditTrail.recordAuditEvent(event);
            
        } finally {
            timer.stop();
        }
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
