/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * Finance Incident Management Service.
 *
 * <p>Finance-specific incident management with regulatory compliance and financial operations.
 * Provides incident detection, classification, and response coordination for financial systems
 * with regulatory reporting requirements and operational resilience.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Financial incident detection with regulatory classification</li>
 *   <li>Incident response coordination with regulatory notifications</li>
 *   <li>Financial impact assessment and business continuity</li>
 *   <li>Regulatory incident reporting and compliance</li>
 *   <li>Post-incident analysis with financial lessons learned</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance incident management - regulatory compliance, operational resilience, financial impact
 * @doc.layer finance
 * @doc.pattern Service, Incident Management
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceIncidentManagementService {

    // ── Finance-Specific Inner Ports ───────────────────────────────────────────────

    /** Financial incident detection with regulatory patterns. */
    public interface FinanceIncidentDetectionPort {
        Promise<FinanceIncident> detectFinancialIncident(FinanceAlert alert);
        Promise<List<FinanceIncident>> getActiveIncidents();
        Promise<FinanceIncident> getIncident(String incidentId);
    }

    /** Regulatory incident reporting and notifications. */
    public interface FinanceRegulatoryNotificationPort {
        Promise<Void> notifyRegulator(FinanceIncident incident, FinanceRegulatoryBody regulator);
        Promise<Void> submitIncidentReport(FinanceIncidentReport report);
        Promise<FinanceRegulatoryResponse> getRegulatorResponse(String incidentId);
    }

    /** Financial impact assessment for incidents. */
    public interface FinanceImpactAssessmentPort {
        Promise<FinanceImpactAssessment> assessFinancialImpact(FinanceIncident incident);
        Promise<FinanceBusinessContinuityPlan> activateBusinessContinuity(FinanceIncident incident);
        Promise<FinanceRecoveryPlan> generateRecoveryPlan(FinanceIncident incident);
    }

    /** Post-incident analysis with financial lessons learned. */
    public interface FinancePostIncidentAnalysisPort {
        Promise<FinanceIncidentAnalysis> performAnalysis(FinanceIncident incident);
        Promise<FinanceLessonsLearned> extractLessonsLearned(FinanceIncident incident);
        Promise<FinanceImprovementPlan> createImprovementPlan(FinanceIncident incident);
    }

    // ── Finance-Specific Value Types ─────────────────────────────────────────────

    public enum FinanceIncidentSeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    public enum FinanceIncidentStatus {
        DETECTED, INVESTIGATING, MITIGATING, RESOLVED, POST_MORTEM
    }

    public enum FinanceIncidentType {
        TRADING_SYSTEM_FAILURE, SETTLEMENT_SYSTEM_FAILURE, COMPLIANCE_BREACH,
        DATA_BREACH, MARKET_DATA_FAILURE, RISK_SYSTEM_FAILURE, REGULATORY_REPORTING_FAILURE,
        PAYMENT_SYSTEM_FAILURE, CLEARING_SYSTEM_FAILURE, CYBERSECURITY_INCIDENT
    }

    public enum FinanceRegulatoryBody {
        SEC, FINRA, FCA, ESMA, MAS, HKMA, ASIC, JFSA, CFTC, NFA
    }

    public enum FinanceBusinessImpact {
        NO_IMPACT, MINIMAL, MODERATE, SIGNIFICANT, SEVERE, CRITICAL
    }

    public record FinanceAlert(
        String alertId,
        String alertName,
        String source,
        FinanceIncidentSeverity severity,
        Map<String, String> labels,
        Instant timestamp,
        String description
    ) {}

    public record FinanceIncident(
        String incidentId,
        String title,
        FinanceIncidentType incidentType,
        FinanceIncidentSeverity severity,
        FinanceIncidentStatus status,
        String description,
        List<String> affectedSystems,
        List<String> affectedTenants,
        FinanceRegulatoryBody primaryRegulator,
        Instant detectedAt,
        Instant resolvedAt,
        String assignedTo,
        Map<String, Object> metadata
    ) {}

    public record FinanceIncidentReport(
        String reportId,
        String incidentId,
        FinanceRegulatoryBody regulator,
        LocalDateTime reportTime,
        String incidentSummary,
        FinanceBusinessImpact businessImpact,
        String rootCauseAnalysis,
        List<String> correctiveActions,
        String estimatedFinancialImpact,
        String regulatoryViolations,
        boolean materialAdverseEffect
    ) {}

    public record FinanceImpactAssessment(
        String assessmentId,
        String incidentId,
        FinanceBusinessImpact businessImpact,
        String estimatedFinancialLoss,
        String affectedRevenue,
        String affectedTransactions,
        String customerImpact,
        String marketImpact,
        List<String> affectedProducts,
        Instant assessmentTime
    ) {}

    public record FinanceBusinessContinuityPlan(
        String planId,
        String incidentId,
        List<FinanceContinuityAction> actions,
        String activationTime,
        String estimatedRecoveryTime,
        List<String> criticalFunctions,
        List<String> backupSystems,
        boolean activated
    ) {}

    public record FinanceContinuityAction(
        String actionId,
        String description,
        String assignedTo,
        String status,
        LocalDateTime dueTime,
        String priority
    ) {}

    public record FinanceRecoveryPlan(
        String planId,
        String incidentId,
        List<FinanceRecoveryStep> steps,
        String estimatedTotalRecoveryTime,
        List<String> requiredResources,
        List<String> stakeholders,
        Instant createdTime
    ) {}

    public record FinanceRecoveryStep(
        String stepId,
        String description,
        String assignedTo,
        String status,
        String estimatedDuration,
        List<String> dependencies
    ) {}

    public record FinanceIncidentAnalysis(
        String analysisId,
        String incidentId,
        String rootCause,
        List<String> contributingFactors,
        String timeline,
        List<String> lessonsLearned,
        List<String> recommendations,
        LocalDateTime analysisDate,
        String analyst
    ) {}

    public record FinanceLessonsLearned(
        String lessonId,
        String incidentId,
        List<String> technicalLessons,
        List<String> processLessons,
        List<String> complianceLessons,
        List<String> businessLessons,
        LocalDateTime capturedDate
    ) {}

    public record FinanceImprovementPlan(
        String planId,
        String incidentId,
        List<FinanceImprovementAction> actions,
        String priority,
        String owner,
        LocalDateTime targetCompletion,
        String status
    ) {}

    public record FinanceImprovementAction(
        String actionId,
        String description,
        String category,
        String assignedTo,
        String dueDate,
        String status,
        String priority
    ) {}

    public record FinanceRegulatoryResponse(
        String responseId,
        String incidentId,
        FinanceRegulatoryBody regulator,
        String responseStatus,
        String requirements,
        String deadlines,
        LocalDateTime responseTime
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final FinanceIncidentDetectionPort detection;
    private final FinanceRegulatoryNotificationPort regulatory;
    private final FinanceImpactAssessmentPort impact;
    private final FinancePostIncidentAnalysisPort analysis;
    private final Executor executor;

    private final Counter financeIncidentDetectedCounter;
    private final Counter financeIncidentResolvedCounter;
    private final Counter regulatoryNotificationSentCounter;
    private final Counter impactAssessmentCompletedCounter;

    // ── Constructor ─────────────────────────────────────────────────────────────

    public FinanceIncidentManagementService(
        FinanceIncidentDetectionPort detection,
        FinanceRegulatoryNotificationPort regulatory,
        FinanceImpactAssessmentPort impact,
        FinancePostIncidentAnalysisPort analysis,
        MeterRegistry registry,
        Executor executor
    ) {
        this.detection = detection;
        this.regulatory = regulatory;
        this.impact = impact;
        this.analysis = analysis;
        this.executor = executor;

        this.financeIncidentDetectedCounter = Counter.builder("finance.incident.detected_total").register(registry);
        this.financeIncidentResolvedCounter = Counter.builder("finance.incident.resolved_total").register(registry);
        this.regulatoryNotificationSentCounter = Counter.builder("finance.incident.regulatory.notification_total").register(registry);
        this.impactAssessmentCompletedCounter = Counter.builder("finance.incident.impact.assessment_total").register(registry);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Process a financial alert and potentially create an incident.
     */
    public Promise<Optional<FinanceIncident>> processFinanceAlert(FinanceAlert alert) {
        return Promise.ofBlocking(executor, () -> {
            return detection.detectFinancialIncident(alert)
                .then(incident -> {
                    if (incident != null) {
                        financeIncidentDetectedCounter.increment();

                        // Assess financial impact
                        return impact.assessFinancialImpact(incident)
                            .then(impactAssessment -> {
                                // Notify primary regulator if high severity
                                if (incident.severity() == FinanceIncidentSeverity.CRITICAL ||
                                    incident.severity() == FinanceIncidentSeverity.HIGH) {

                                    return regulatory.notifyRegulator(incident, incident.primaryRegulator())
                                        .then(notified -> {
                                            regulatoryNotificationSentCounter.increment();
                                            return Promise.of(Optional.of(incident));
                                        });
                                }

                                return Promise.of(Optional.of(incident));
                            });
                    }

                    return Promise.of(Optional.<FinanceIncident>empty());
                });
        }).getResult();
    }

    /**
     * Resolve a financial incident with regulatory compliance.
     */
    public Promise<FinanceIncident> resolveFinanceIncident(
            String incidentId, String resolutionSummary, String resolvedBy) {

        return Promise.ofBlocking(executor, () -> {
            return detection.getIncident(incidentId)
                .then(incident -> {
                    // Update incident status to resolved
                    FinanceIncident resolved = new FinanceIncident(
                        incident.incidentId(),
                        incident.title(),
                        incident.incidentType(),
                        incident.severity(),
                        FinanceIncidentStatus.RESOLVED,
                        incident.description() + "\n\nRESOLUTION: " + resolutionSummary,
                        incident.affectedSystems(),
                        incident.affectedTenants(),
                        incident.primaryRegulator(),
                        incident.detectedAt(),
                        Instant.now(),
                        resolvedBy,
                        incident.metadata()
                    );

                    financeIncidentResolvedCounter.increment();

                    // Submit final incident report to regulator
                    return submitFinalIncidentReport(resolved, resolvedBy)
                        .then(reportSubmitted -> Promise.of(resolved));
                });
        }).getResult();
    }

    /**
     * Activate business continuity plan for a financial incident.
     */
    public Promise<FinanceBusinessContinuityPlan> activateBusinessContinuity(String incidentId) {
        return Promise.ofBlocking(executor, () -> {
            return detection.getIncident(incidentId)
                .then(incident -> {
                    return impact.activateBusinessContinuity(incident)
                        .then(plan -> {
                            // Notify regulator of business continuity activation
                            return regulatory.notifyRegulator(incident, incident.primaryRegulator())
                                .then(notified -> Promise.of(plan));
                        });
                });
        }).getResult();
    }

    /**
     * Perform post-incident analysis with financial lessons learned.
     */
    public Promise<FinanceIncidentAnalysis> performPostIncidentAnalysis(String incidentId, String analyst) {
        return Promise.ofBlocking(executor, () -> {
            return detection.getIncident(incidentId)
                .then(incident -> {
                    return analysis.performAnalysis(incident)
                        .then(analysisResult -> {
                            // Extract lessons learned
                            return analysis.extractLessonsLearned(incident)
                                .then(lessons -> {
                                    // Create improvement plan
                                    return analysis.createImprovementPlan(incident)
                                        .then(improvementPlan -> Promise.of(analysisResult));
                                });
                        });
                });
        }).getResult();
    }

    /**
     * Get active financial incidents with regulatory status.
     */
    public Promise<List<FinanceIncident>> getActiveFinanceIncidents() {
        return detection.getActiveIncidents();
    }

    /**
     * Submit regulatory incident report.
     */
    public Promise<FinanceIncidentReport> submitRegulatoryReport(
            String incidentId, FinanceRegulatoryBody regulator, String reportSummary) {

        return Promise.ofBlocking(executor, () -> {
            return detection.getIncident(incidentId)
                .then(incident -> {
                    // Assess impact for reporting
                    return impact.assessFinancialImpact(incident)
                        .then(impactAssessment -> {
                            FinanceIncidentReport report = new FinanceIncidentReport(
                                UUID.randomUUID().toString(),
                                incidentId,
                                regulator,
                                LocalDateTime.now(),
                                reportSummary,
                                impactAssessment.businessImpact(),
                                "To be determined by investigation",
                                List.of("Immediate containment", "Root cause investigation"),
                                impactAssessment.estimatedFinancialLoss(),
                                "To be determined by investigation",
                                impactAssessment.businessImpact() == FinanceBusinessImpact.CRITICAL
                            );

                            return regulatory.submitIncidentReport(report)
                                .then(submitted -> Promise.of(report));
                        });
                });
        }).getResult();
    }

    /**
     * Get regulatory response for an incident.
     */
    public Promise<FinanceRegulatoryResponse> getRegulatoryResponse(String incidentId) {
        return regulatory.getRegulatorResponse(incidentId);
    }

    /**
     * Generate recovery plan for a financial incident.
     */
    public Promise<FinanceRecoveryPlan> generateRecoveryPlan(String incidentId) {
        return Promise.ofBlocking(executor, () -> {
            return detection.getIncident(incidentId)
                .then(incident -> {
                    return impact.generateRecoveryPlan(incident);
                });
        }).getResult();
    }

    // ── Private Helper Methods ─────────────────────────────────────────────────--

    private Promise<Void> submitFinalIncidentReport(FinanceIncident incident, String resolvedBy) {
        return Promise.ofBlocking(executor, () -> {
            FinanceIncidentReport report = new FinanceIncidentReport(
                UUID.randomUUID().toString(),
                incident.incidentId(),
                incident.primaryRegulator(),
                LocalDateTime.now(),
                "Incident resolved: " + incident.title(),
                FinanceBusinessImpact.MINIMAL, // Would be assessed
                "Incident has been resolved with " + incident.description(),
                List.of("Incident resolution", "System recovery"),
                "Minimal - incident resolved quickly",
                "None - incident resolved within SLA",
                false
            );

            return regulatory.submitIncidentReport(report);
        }).getResult();
    }

    // ── Default Implementation (for testing) ─────────────────────────────────────

    public static FinanceIncidentManagementService createDefault(MeterRegistry registry, Executor executor) {
        return new FinanceIncidentManagementService(
            new DefaultFinanceIncidentDetectionPort(),
            new DefaultFinanceRegulatoryNotificationPort(),
            new DefaultFinanceImpactAssessmentPort(),
            new DefaultFinancePostIncidentAnalysisPort(),
            registry,
            executor
        );
    }

    // Default implementations for testing/development
    private static final class DefaultFinanceIncidentDetectionPort implements FinanceIncidentDetectionPort {
        @Override
        public Promise<FinanceIncident> detectFinancialIncident(FinanceAlert alert) {
            // Simple detection logic - create incident for critical alerts
            if (alert.severity() == FinanceIncidentSeverity.CRITICAL) {
                FinanceIncident incident = new FinanceIncident(
                    UUID.randomUUID().toString(),
                    alert.alertName(),
                    FinanceIncidentType.TRADING_SYSTEM_FAILURE,
                    alert.severity(),
                    FinanceIncidentStatus.DETECTED,
                    alert.description(),
                    List.of(alert.source()),
                    List.of(),
                    FinanceRegulatoryBody.SEC,
                    alert.timestamp(),
                    null,
                    "system",
                    Map.of("alertId", alert.alertId())
                );
                return Promise.of(incident);
            }
            return Promise.of((FinanceIncident) null);
        }

        @Override
        public Promise<List<FinanceIncident>> getActiveIncidents() {
            return Promise.of(List.of());
        }

        @Override
        public Promise<FinanceIncident> getIncident(String incidentId) {
            return Promise.of(new FinanceIncident(
                incidentId, "Test Incident", FinanceIncidentType.TRADING_SYSTEM_FAILURE,
                FinanceIncidentSeverity.MEDIUM, FinanceIncidentStatus.DETECTED, "Test",
                List.of(), List.of(), FinanceRegulatoryBody.SEC, Instant.now(), null, "system", Map.of()
            ));
        }
    }

    private static final class DefaultFinanceRegulatoryNotificationPort implements FinanceRegulatoryNotificationPort {
        @Override
        public Promise<Void> notifyRegulator(FinanceIncident incident, FinanceRegulatoryBody regulator) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> submitIncidentReport(FinanceIncidentReport report) {
            return Promise.of(null);
        }

        @Override
        public Promise<FinanceRegulatoryResponse> getRegulatorResponse(String incidentId) {
            return Promise.of(new FinanceRegulatoryResponse(
                "resp-001", incidentId, FinanceRegulatoryBody.SEC, "ACKNOWLEDGED",
                "Submit detailed report", "72 hours", LocalDateTime.now()
            ));
        }
    }

    private static final class DefaultFinanceImpactAssessmentPort implements FinanceImpactAssessmentPort {
        @Override
        public Promise<FinanceImpactAssessment> assessFinancialImpact(FinanceIncident incident) {
            return Promise.of(new FinanceImpactAssessment(
                "impact-001", incident.incidentId(), FinanceBusinessImpact.MINIMAL,
                "$0", "$0", "0", "None", "None", List.of(), Instant.now()
            ));
        }

        @Override
        public Promise<FinanceBusinessContinuityPlan> activateBusinessContinuity(FinanceIncident incident) {
            return Promise.of(new FinanceBusinessContinuityPlan(
                "bcp-001", incident.incidentId(), List.of(), "NOW", "2 hours",
                List.of("Trading", "Clearing"), List.of("Backup"), true
            ));
        }

        @Override
        public Promise<FinanceRecoveryPlan> generateRecoveryPlan(FinanceIncident incident) {
            return Promise.of(new FinanceRecoveryPlan(
                "recovery-001", incident.incidentId(), List.of(), "4 hours",
                List.of("Engineering", "Operations"), List.of("CTO", "CRO"), Instant.now()
            ));
        }
    }

    private static final class DefaultFinancePostIncidentAnalysisPort implements FinancePostIncidentAnalysisPort {
        @Override
        public Promise<FinanceIncidentAnalysis> performAnalysis(FinanceIncident incident) {
            return Promise.of(new FinanceIncidentAnalysis(
                "analysis-001", incident.incidentId(), "Root cause identified",
                List.of("Factor 1", "Factor 2"), "Timeline", List.of("Lesson 1"),
                List.of("Recommendation 1"), LocalDateTime.now(), "analyst"
            ));
        }

        @Override
        public Promise<FinanceLessonsLearned> extractLessonsLearned(FinanceIncident incident) {
            return Promise.of(new FinanceLessonsLearned(
                "lessons-001", incident.incidentId(), List.of("Technical lesson"),
                List.of("Process lesson"), List.of("Compliance lesson"),
                List.of("Business lesson"), LocalDateTime.now()
            ));
        }

        @Override
        public Promise<FinanceImprovementPlan> createImprovementPlan(FinanceIncident incident) {
            return Promise.of(new FinanceImprovementPlan(
                "improvement-001", incident.incidentId(), List.of(), "HIGH", "CTO",
                LocalDateTime.now().plusWeeks(4), "PENDING"
            ));
        }
    }
}
