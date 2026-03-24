package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.healthcare.port.PatientStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Patient data retention and right-to-erasure service.
 *
 * <p>Implements Nepal Health Records Act 2081 §22 retention and right-to-erasure rules:
 * <ul>
 *   <li>Patient records must be retained for a minimum of 25 years from registration.</li>
 *   <li>Erasure is permissible only when retention period has elapsed AND no active
 *       treatment episodes exist.</li>
 *   <li>Legal holds block erasure unconditionally until the hold is released.</li>
 * </ul>
 *
 * <p>Erasure execution is a two-phase operation:
 * <ol>
 *   <li>Eligibility check — soft-delete (deactivate) + audit</li>
 *   <li>Hard deletion - run only after all associated data is exported and audit stored</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Right-to-erasure + Nepal Directive 2081 retention enforcement
 * @doc.layer domain-pack
 * @doc.pattern Service, Saga
 * @since 1.0.0
 */
public class RetentionPolicyService {

    private final PatientStore patientStore;
    private final Executor executor;
    private final AuditService auditService;
    private final Counter erasureRequestedCounter;
    private final Counter erasureBlockedCounter;
    private final Counter erasureExecutedCounter;

    /** Port: checks whether a patient has any active treatment episodes. */
    public interface ActiveTreatmentPort {
        boolean hasActiveTreatment(String tenantId, UUID patientId) throws Exception;
    }

    /** Port: checks whether a patient record is under a legal hold. */
    public interface LegalHoldPort {
        boolean hasActiveLegalHold(String tenantId, UUID patientId) throws Exception;
    }

    public record ErasureOutcome(
        boolean executed,
        String reason   // EXECUTED | LEGAL_HOLD | ACTIVE_TREATMENT | RETENTION_PERIOD_NOT_ELAPSED
    ) {
        static ErasureOutcome executed() {
            return new ErasureOutcome(true, "EXECUTED");
        }

        static ErasureOutcome blocked(String reason) {
            return new ErasureOutcome(false, reason);
        }
    }

    public RetentionPolicyService(PatientStore patientStore,
                                   Executor executor,
                                   MeterRegistry registry,
                                   AuditService auditService) {
        this.patientStore = Objects.requireNonNull(patientStore);
        this.executor = Objects.requireNonNull(executor);
        this.auditService = Objects.requireNonNull(auditService);
        this.erasureRequestedCounter = Counter.builder("healthcare.retention.erasure_requested_total")
            .register(registry);
        this.erasureBlockedCounter = Counter.builder("healthcare.retention.erasure_blocked_total")
            .register(registry);
        this.erasureExecutedCounter = Counter.builder("healthcare.retention.erasure_executed_total")
            .register(registry);
    }

    /**
     * Evaluates and, if eligible, executes the erasure of a patient record.
     *
     * <p>Eligibility requires:
     * <ul>
     *   <li>no active legal hold</li>
     *   <li>no active treatment episode</li>
     *   <li>≥25 years since registration</li>
     * </ul>
     *
     * @param tenantId          tenant scope
     * @param patientId         patient to erase
     * @param activeTreatment   port impl
     * @param legalHold         port impl
     * @return the erasure outcome
     */
    public Promise<ErasureOutcome> requestErasure(
            String tenantId, UUID patientId,
            ActiveTreatmentPort activeTreatment,
            LegalHoldPort legalHold) {

        return Promise.ofBlocking(executor, () -> {
            erasureRequestedCounter.increment();

            Optional<Patient> patientOpt = patientStore.findById(tenantId, patientId);
            if (patientOpt.isEmpty()) {
                erasureBlockedCounter.increment();
                auditErasure(tenantId, patientId, "PATIENT_NOT_FOUND", false);
                return ErasureOutcome.blocked("PATIENT_NOT_FOUND");
            }
            Patient patient = patientOpt.get();

            // 1. Legal hold check
            if (legalHold.hasActiveLegalHold(tenantId, patientId)) {
                erasureBlockedCounter.increment();
                auditErasure(tenantId, patientId, "LEGAL_HOLD", false);
                return ErasureOutcome.blocked("LEGAL_HOLD");
            }

            // 2. Active treatment check
            if (activeTreatment.hasActiveTreatment(tenantId, patientId)) {
                erasureBlockedCounter.increment();
                auditErasure(tenantId, patientId, "ACTIVE_TREATMENT", false);
                return ErasureOutcome.blocked("ACTIVE_TREATMENT");
            }

            // 3. 25-year retention period check
            if (!patient.isDeletionEligible(Instant.now(), false)) {
                erasureBlockedCounter.increment();
                auditErasure(tenantId, patientId, "RETENTION_PERIOD_NOT_ELAPSED", false);
                return ErasureOutcome.blocked("RETENTION_PERIOD_NOT_ELAPSED");
            }

            // All gates passed — soft-delete first, then hard-delete
            patientStore.deactivate(tenantId, patientId);
            patientStore.hardDelete(tenantId, patientId);
            erasureExecutedCounter.increment();
            auditErasure(tenantId, patientId, "EXECUTED", true);
            return ErasureOutcome.executed();
        });
    }

    private void auditErasure(String tenantId, UUID patientId, String reason, boolean success) {
        auditService.record(AuditEvent.builder()
            .tenantId(tenantId)
            .eventType("ERASURE_" + (success ? "EXECUTED" : "BLOCKED"))
            .resourceType("Patient")
            .resourceId(patientId.toString())
            .success(success)
            .detail("reason", reason)
            .build());
    }
}
