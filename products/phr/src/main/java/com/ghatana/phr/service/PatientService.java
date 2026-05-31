package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.phr.kernel.consent.ConsentAccessDeniedException;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.phr.kernel.service.PhrInputSanitizationUtils;
import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.repository.PatientRecordRepository;
import com.ghatana.phr.security.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic service for PatientService.
 *
 * <p>All reads that touch patient records must pass through the consent gate
 * before the repository is accessed. Access without a valid consent decision
 * throws {@link ConsentAccessDeniedException}.</p>
 *
 * @doc.type class
 * @doc.purpose Business logic service for patient record access with mandatory consent enforcement
 * @doc.layer product
 * @doc.pattern Service
 */
public class PatientService {
    private static final Logger logger = LoggerFactory.getLogger(PatientService.class);

    private final KernelTelemetryManager telemetry;
    private final AuditTrailService auditTrail;
    private final PatientRecordRepository recordsRepository;
    private final ConsentService consentService;

    public PatientService(KernelTelemetryManager telemetry,
                         AuditTrailService auditTrail,
                         PatientRecordRepository recordsRepository,
                         ConsentService consentService) {
        this.telemetry = telemetry;
        this.auditTrail = auditTrail;
        this.recordsRepository = recordsRepository;
        this.consentService = consentService;
    }

    public PatientRecords getRecords(String patientId) {
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        enforceConsentForPatientRead(sanitizedPatientId);
        KernelTelemetryManager.Timer timer = telemetry.startTimer(
            "phr.patient.records.fetch",
            "resource_type", "patient-record"
        );

        try {
            PatientRecords records = recordsRepository.findByPatientId(sanitizedPatientId);

            telemetry.recordMetric(
                "phr.patient.records.count",
                records.size(),
                "resource_type", "patient-record"
            );

            AuditTrailService.AuditTrailEvent event = AuditTrailService.AuditTrailEvent.builder()
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
            "resource_type", "patient-record"
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
                "resource_type", "patient-record"
            );

            AuditTrailService.AuditTrailEvent event = AuditTrailService.AuditTrailEvent.builder()
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
                "resource_type", "patient-record"
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

    /**
     * Enforces consent for a PATIENT_READ action before the repository is accessed.
     * Throws {@link ConsentAccessDeniedException} if access is denied or the consent
     * service returns a deny decision.
     *
     * <p>When no consent service is wired, access is allowed with a warning log
     * that does not include patient identifiers.</p>
     */
    private void enforceConsentForPatientRead(String patientId) {
        if (consentService == null) {
            logger.warn("[SECURITY] PatientService.getRecords called without ConsentService; consent gate bypassed");
            return;
        }
        String tenantId = getCurrentTenantId();
        String actorId = getCurrentUserId();
        ConsentService.ActorContext actor = new ConsentService.ActorContext(
                actorId, ConsentService.ActorType.PROVIDER, null, actorId, null, Set.of());
        ConsentService.TargetResource target = new ConsentService.TargetResource(
                patientId, "PatientRecord", null, PhrDataClassification.C3);
        ConsentService.ConsentCheckRequest req = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(), tenantId, actor, target,
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.CARE_DELIVERY, null);
        ConsentService.ConsentAccessDecision decision = consentService.checkAccess(req).toCompletableFuture().join();
        if (!decision.allowed()) {
            logger.warn("[SECURITY] Consent denied for patient record access; reasonCode={}",
                    decision.reasonCode());
            throw new ConsentAccessDeniedException(
                    req.requestId(), tenantId, actorId, patientId, decision);
        }
    }
}
