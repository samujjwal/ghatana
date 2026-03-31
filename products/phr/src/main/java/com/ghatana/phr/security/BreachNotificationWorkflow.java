package com.ghatana.phr.security;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Automates the 72-hour breach notification pipeline for PHI data breaches
 * as defined in the PHR incident response playbook.
 *
 * <p>Steps:</p>
 * <ol>
 *   <li>Record the breach with severity, scope, and affected patient count</li>
 *   <li>Trigger immediate containment actions (revoke tokens, isolate tenant)</li>
 *   <li>Notify the compliance lead within 1 hour</li>
 *   <li>Assess whether regulatory notification is required</li>
 *   <li>Send regulatory notification within 72 hours if required</li>
 *   <li>Notify affected patients per Nepal Directive 2081</li>
 *   <li>Schedule post-incident review within 5 business days</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Automated 72-hour breach notification workflow per incident response playbook
 * @doc.layer product
 * @doc.pattern Workflow
 */
public class BreachNotificationWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(BreachNotificationWorkflow.class);
    private static final Duration COMPLIANCE_NOTIFICATION_DEADLINE = Duration.ofHours(1);
    private static final Duration REGULATORY_NOTIFICATION_DEADLINE = Duration.ofHours(72);

    private final NotificationSender notificationSender;
    private final BreachAuditLogger breachAuditLogger;
    private final ContainmentService containmentService;

    public BreachNotificationWorkflow(
            NotificationSender notificationSender,
            BreachAuditLogger breachAuditLogger,
            ContainmentService containmentService
    ) {
        this.notificationSender = notificationSender;
        this.breachAuditLogger = breachAuditLogger;
        this.containmentService = containmentService;
    }

    /**
     * Initiates the full breach notification workflow.
     *
     * @param report the breach report describing the incident
     * @return the breach case with assigned tracking ID and status
     */
    public Promise<BreachCase> initiate(BreachReport report) {
        String caseId = "BREACH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();

        logger.error("BREACH INITIATED [{}] severity={} affectedPatients={} tenantId={}",
                caseId, report.severity(), report.affectedPatientCount(), report.tenantId());

        BreachCase breachCase = new BreachCase(
                caseId,
                report,
                now,
                BreachStatus.CONTAINMENT,
                now.plus(COMPLIANCE_NOTIFICATION_DEADLINE),
                now.plus(REGULATORY_NOTIFICATION_DEADLINE)
        );

        return breachAuditLogger.logBreachInitiated(breachCase)
                .then(() -> containmentService.executeContainment(report.tenantId(), report.severity()))
                .then(() -> {
                    logger.info("[{}] Containment executed, notifying compliance lead", caseId);
                    return notificationSender.notifyComplianceLead(breachCase);
                })
                .then(() -> {
                    if (requiresRegulatoryNotification(report)) {
                        logger.warn("[{}] Regulatory notification REQUIRED — deadline: {}",
                                caseId, breachCase.regulatoryDeadline());
                        return notificationSender.scheduleRegulatoryNotification(breachCase)
                                .then(() -> notificationSender.notifyAffectedPatients(breachCase));
                    }
                    logger.info("[{}] Regulatory notification not required for this breach", caseId);
                    return Promise.complete();
                })
                .then(() -> notificationSender.schedulePostIncidentReview(breachCase))
                .map($ -> {
                    logger.info("[{}] Breach workflow initiated successfully, status={}",
                            caseId, BreachStatus.NOTIFICATION_PENDING);
                    return breachCase.withStatus(BreachStatus.NOTIFICATION_PENDING);
                });
    }

    /**
     * Determines whether regulatory notification is required based on:
     * - Severity (SEV-1 always, SEV-2 if >50 patients)
     * - Number of affected patients
     * - Whether PHI was actually exposed
     */
    private boolean requiresRegulatoryNotification(BreachReport report) {
        if (report.severity() == BreachSeverity.SEV_1) {
            return true;
        }
        if (report.severity() == BreachSeverity.SEV_2 && report.affectedPatientCount() > 50) {
            return true;
        }
        return report.phiExposed();
    }

    // ──────────── Domain types ────────────

    public enum BreachSeverity {
        SEV_1, SEV_2, SEV_3
    }

    public enum BreachStatus {
        CONTAINMENT,
        NOTIFICATION_PENDING,
        REGULATORY_NOTIFIED,
        PATIENT_NOTIFIED,
        POST_INCIDENT_REVIEW,
        CLOSED
    }

    public record BreachReport(
            String tenantId,
            BreachSeverity severity,
            String description,
            int affectedPatientCount,
            boolean phiExposed,
            List<String> affectedSystems,
            String reportedBy
    ) {}

    public record BreachCase(
            String caseId,
            BreachReport report,
            Instant initiatedAt,
            BreachStatus status,
            Instant complianceDeadline,
            Instant regulatoryDeadline
    ) {
        public BreachCase withStatus(BreachStatus newStatus) {
            return new BreachCase(caseId, report, initiatedAt, newStatus, complianceDeadline, regulatoryDeadline);
        }
    }

    // ──────────── SPI contracts ────────────

    /**
     * Sends notifications to various stakeholders in the breach workflow.
     */
    public interface NotificationSender {
        Promise<Void> notifyComplianceLead(BreachCase breachCase);
        Promise<Void> scheduleRegulatoryNotification(BreachCase breachCase);
        Promise<Void> notifyAffectedPatients(BreachCase breachCase);
        Promise<Void> schedulePostIncidentReview(BreachCase breachCase);
    }

    /**
     * Logs breach events for audit and forensic purposes.
     */
    public interface BreachAuditLogger {
        Promise<Void> logBreachInitiated(BreachCase breachCase);
    }

    /**
     * Executes containment actions: token revocation, tenant isolation, route disabling.
     */
    public interface ContainmentService {
        Promise<Void> executeContainment(String tenantId, BreachSeverity severity);
    }
}
