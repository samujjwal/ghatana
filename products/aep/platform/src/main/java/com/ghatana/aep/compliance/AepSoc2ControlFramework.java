/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SOC 2 Type II control framework for the Agentic Event Processor.
 *
 * <p>Implements the five Trust Services Criteria (TSC) relevant to AEP's
 * role as a data-processing service:
 * <ul>
 *   <li><strong>CC6</strong> — Logical and Physical Access Controls</li>
 *   <li><strong>CC7</strong> — System Operations</li>
 *   <li><strong>CC8</strong> — Change Management</li>
 *   <li><strong>A1</strong> — Availability</li>
 *   <li><strong>C1</strong> — Confidentiality</li>
 * </ul>
 *
 * <p>Each control is represented as a {@link Soc2Control} instance describing
 * the control ID, description, implementation status, and evidence artefacts.
 * The {@link #generateReport} method produces a structured audit report.
 *
 * @doc.type class
 * @doc.purpose SOC 2 Type II control framework for AEP
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepSoc2ControlFramework {

    private static final Logger log = LoggerFactory.getLogger(AepSoc2ControlFramework.class);

    /** Represents an individual SOC 2 control. */
    public record Soc2Control(
            /** Control ID as defined by the AICPA TSC framework (e.g., {@code CC6.1}). */
            String controlId,
            /** TSC category: CC6, CC7, CC8, A1, or C1. */
            String category,
            /** Human-readable control objective. */
            String description,
            /** Whether the control is currently implemented. */
            boolean implemented,
            /** Artefacts that provide evidence of implementation. */
            List<String> evidence,
            /** Notes on deviations or compensating controls. */
            String notes
    ) {}

    /** Structured SOC 2 audit report. */
    public record Soc2Report(
            String service,
            String version,
            Instant generatedAt,
            int totalControls,
            int implementedControls,
            double complianceRate,
            List<Soc2Control> controls,
            List<String> exceptions
    ) {}

    /**
     * Returns the full set of SOC 2 controls implemented by AEP.
     * Each control maps to a verifiable implementation artefact in the codebase or infrastructure.
     */
    public List<Soc2Control> getControls() {
        return List.of(
            // ── CC6 — Logical and Physical Access Controls ────────────────────────
            new Soc2Control(
                "CC6.1", "CC6",
                "Logical access security software, infrastructure, and architectures are implemented.",
                true,
                List.of(
                    "AepSecurityFilter — OWASP headers, rate limiting, payload size enforcement",
                    "IngressAuthValidator — Bearer/API-key/Basic auth at HTTP ingress",
                    "ServerAuthInterceptor — gRPC tenant auth interceptor",
                    "k8s/network-policy.yaml — NetworkPolicy restricts inbound to HTTP/gRPC only",
                    "k8s/rbac.yaml — RBAC restricts pod service-account permissions"
                ),
                null
            ),
            new Soc2Control(
                "CC6.2", "CC6",
                "Prior to issuing system credentials and granting access, user identity is verified.",
                true,
                List.of(
                    "IngressAuthValidator — validates bearer tokens and API keys at ingress",
                    "AgentSpecValidator — validates agent identity before registration",
                    "TenantContextPropagator — propagates and validates tenant context"
                ),
                null
            ),
            new Soc2Control(
                "CC6.3", "CC6",
                "Role-based access control is implemented.",
                true,
                List.of(
                    "IngressAuthValidator.AuthType — NONE/BEARER/API_KEY/BASIC roles",
                    "DataCloudAgentRegistryClient — agent registry access controlled by tenant scope",
                    "k8s/rbac.yaml — Kubernetes RBAC for service accounts"
                ),
                null
            ),
            new Soc2Control(
                "CC6.6", "CC6",
                "Logical access security measures are reviewed and updated to address new threats.",
                true,
                List.of(
                    "aep-ci.yml — OWASP Dependency-Check on every build (failBuildOnCVSS=7.0)",
                    "aep-ci.yml — Trivy container image CVE scan",
                    "aep-ci.yml — SpotBugs SAST (effort=MAX)",
                    "config/owasp-suppressions.xml — documented suppressions with rationale"
                ),
                null
            ),
            new Soc2Control(
                "CC6.7", "CC6",
                "Transmission of confidential information is restricted to authorised parties.",
                true,
                List.of(
                    "k8s/ingress.yaml — TLS termination at ingress (cert-manager)",
                    "k8s/istio-mesh.yaml — mTLS STRICT PeerAuthentication within mesh",
                    "AepSecurityFilter — HSTS header (max-age=31536000)",
                    "DataCloudPipelineRegistryClientImpl — HTTPS with timeout enforcement"
                ),
                null
            ),

            // ── CC7 — System Operations ───────────────────────────────────────────
            new Soc2Control(
                "CC7.1", "CC7",
                "Monitoring tools are implemented to detect anomalies and security events.",
                true,
                List.of(
                    "monitoring/prometheus/rules/aep.yml — 11 Prometheus alert rules",
                    "monitoring/grafana/dashboards/aep-platform-001.json — 23-panel Grafana dashboard",
                    "monitoring/alertmanager/alertmanager.yml — Alertmanager routing to PagerDuty + Slack",
                    "AepObservabilityModule — Micrometer metrics on all subsystems"
                ),
                null
            ),
            new Soc2Control(
                "CC7.2", "CC7",
                "The entity monitors system components for anomalies.",
                true,
                List.of(
                    "RealTimeAnomalyDetectionEngine — in-process anomaly detection",
                    "IntelligentPredictiveAlerting — predictive alert generation",
                    "AepEngine.detectAnomalies — public AEP anomaly detection API"
                ),
                null
            ),
            new Soc2Control(
                "CC7.3", "CC7",
                "Security events are evaluated and responded to.",
                true,
                List.of(
                    "AuditService + JdbcPersistentAuditServiceTest — immutable audit trail",
                    "AgentRegistryAuditLogger — audit logging for agent lifecycle events",
                    "CompilationAuditLogger — audit trail for pattern compilation",
                    "monitoring/alertmanager/alertmanager.yml — on-call routing with inhibition"
                ),
                null
            ),

            // ── CC8 — Change Management ───────────────────────────────────────────
            new Soc2Control(
                "CC8.1", "CC8",
                "Changes to infrastructure, data, and software are authorized and tested before deployment.",
                true,
                List.of(
                    ".gitea/workflows/aep-ci.yml — automated build, test, SAST on every push",
                    ".gitea/workflows/aep-cd.yml — production deployment requires manual approval gate",
                    "PipelineValidator — validates pipeline DAG before deployment",
                    "PatternRegistryService — validates patterns before activation"
                ),
                null
            ),
            new Soc2Control(
                "CC8.2", "CC8",
                "Changes to system components are tracked in a version control system.",
                true,
                List.of(
                    "Git version control on all AEP source code and infrastructure",
                    "Pipeline.version — monotonically increasing version field",
                    "PatternMetadata.version — pattern version tracking",
                    "ConsolidationScheduler — policy versioning in learning loop"
                ),
                null
            ),

            // ── A1 — Availability ─────────────────────────────────────────────────
            new Soc2Control(
                "A1.1", "A1",
                "Current processing capacity is maintained and monitored.",
                true,
                List.of(
                    "k8s/hpa.yaml — HPA scales 2→10 replicas on CPU 70% / memory 80%",
                    "k8s/pdb.yaml — PodDisruptionBudget maintains minimum availability",
                    "monitoring/prometheus/rules/aep.yml — AepHighCpuUsage/AepHighMemoryUsage alerts",
                    "AepMetricsCollector — real-time capacity metrics via Micrometer"
                ),
                null
            ),
            new Soc2Control(
                "A1.2", "A1",
                "Environmental protections, software, data backup processes, and recovery infrastructure are authorized, designed, developed, implemented, operated, approved, maintained, and monitored.",
                true,
                List.of(
                    "k8s/deployment.yaml — liveness + readiness + startup probes",
                    "PostgresqlCheckpointStore — durable checkpoint storage for pipeline recovery",
                    "EventLogStoreBackedEventCloud — event log for event replay on restart",
                    "CheckpointAwareExecutionQueue — checkpoint-aware queue for crash recovery"
                ),
                null
            ),

            // ── C1 — Confidentiality ──────────────────────────────────────────────
            new Soc2Control(
                "C1.1", "C1",
                "Confidential information is protected during processing.",
                true,
                List.of(
                    "AepInputValidator — input validation prevents injection attacks",
                    "AepSecurityFilter — CSP, X-Frame-Options, X-Content-Type-Options headers",
                    "DataCloudPatternStore / DataCloudPipelineStore — tenant-isolated storage",
                    "PipelineTenantIsolationTest — verified tenant isolation"
                ),
                null
            ),
            new Soc2Control(
                "C1.2", "C1",
                "Confidential information is disposed of when no longer needed.",
                true,
                List.of(
                    "AepComplianceService.deletionRequest — GDPR/CCPA erasure across all AEP collections",
                    "AepComplianceService.ccpaOptOut — CCPA opt-out records",
                    "RetentionPolicy in EventLogStore — configurable event retention enforcement"
                ),
                null
            )
        );
    }

    /**
     * Generates a complete SOC 2 compliance report for AEP.
     *
     * @return structured audit report
     */
    public Soc2Report generateReport() {
        List<Soc2Control> controls = getControls();
        long implemented = controls.stream().filter(Soc2Control::implemented).count();
        double rate = controls.isEmpty() ? 0.0 : (double) implemented / controls.size();

        List<String> exceptions = controls.stream()
                .filter(c -> !c.implemented())
                .map(c -> c.controlId() + ": " + c.description())
                .toList();

        log.info("[soc2] Generated compliance report: {}/{} controls implemented ({:.0f}%)",
                implemented, controls.size(), rate * 100);

        return new Soc2Report(
                "aep",
                "1.0.0-SNAPSHOT",
                Instant.now(),
                controls.size(),
                (int) implemented,
                rate,
                controls,
                exceptions
        );
    }

    /**
     * Checks whether AEP meets the SOC 2 compliance threshold (all controls implemented).
     *
     * @return {@code true} if all controls are implemented without exceptions
     */
    public boolean isCompliant() {
        return getControls().stream().allMatch(Soc2Control::implemented);
    }
}
