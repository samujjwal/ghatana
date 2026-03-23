package com.ghatana.eventprocessing.observability;

import java.util.Arrays;
import java.util.List;

/**
 * Observability reference configuration for pattern and pipeline registries.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides dashboard, alert rules, runbooks, and sample queries for monitoring
 * registry operations. Documents the observability expectations for pattern
 * registration, pipeline registration, and their related
 * activation/deactivation flows.
 *
 * <p>
 * <b>Dashboards</b><br>
 * - Pattern Registry Dashboard (UID: pattern-registry) - Pattern registration
 * rate (success/failure) by tenant - Pattern registration latency (p50/p99) by
 * tenant - Pattern activation/deactivation metrics - Error breakdown by error
 * type
 *
 * - Pipeline Registry Dashboard (UID: pipeline-registry) - Pipeline
 * registration rate (success/failure) by tenant - Pipeline registration latency
 * (p50/p99) by tenant - Pipeline activation/deactivation metrics - Error
 * breakdown by error type
 *
 * <p>
 * <b>Alert Rules</b><br>
 * - PatternRegistrationFailureRateHigh: Error rate > 5% for 5 minutes -
 * PipelineRegistrationFailureRateHigh: Error rate > 5% for 5 minutes -
 * PatternRegistrationLatencyHigh: P99 latency > 2 seconds for 3 minutes -
 * PipelineRegistrationLatencyHigh: P99 latency > 2 seconds for 3 minutes -
 * RegistryTenantIsolationFailure: Multi-tenant cross-access detected in logs
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Reference the dashboard configurations
 * List<DashboardPanel> patternPanels = RegistryDashboardConfiguration.PATTERN_REGISTRY_PANELS;
 * List<String> alertRules = RegistryDashboardConfiguration.getAllAlertRules();
 * String runbook = RegistryDashboardConfiguration.RUNBOOK_REGISTRATION_FAILURE_DIAGNOSIS;
 * List<String> queries = RegistryDashboardConfiguration.SAMPLE_PROMETHEUS_QUERIES;
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * This is a reference documentation class that guides operators on
 * observability expectations. It's not executed at runtime but consulted during
 * deployment and troubleshooting. Integration with actual Grafana/Prometheus
 * happens via separate deployment configuration.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable class; all methods and constants are thread-safe.
 *
 * @see RegistryObservability
 *
 * @doc.type class
 * @doc.purpose Reference configuration for registry observability (dashboards,
 * alerts, runbooks)
 * @doc.layer product
 * @doc.pattern Reference Configuration
 */
public class RegistryDashboardConfiguration {

    // ==================== Pattern Registry Dashboard ====================
    /**
     * Pattern Registry Dashboard panels documenting key metrics and queries.
     *
     * <p>
     * Panels:<br>
     * 1. Pattern Registration Success Rate (%) by tenant 2. Pattern
     * Registration Latency (ms) - p50, p95, p99 3. Pattern Registration Errors
     * by Error Type 4. Pattern Activation Success Rate (%) by tenant 5. Top
     * Error Patterns (Top 10 by error count)
     */
    public static final List<DashboardPanel> PATTERN_REGISTRY_PANELS
            = Arrays.asList(
                    new DashboardPanel(
                            "Pattern Registration Success Rate",
                            "d/pattern-registry",
                            "sum(rate(aep_registry_pattern_registration_count{result=\"success\"}[5m])) "
                            + "/ sum(rate(aep_registry_pattern_registration_count[5m])) * 100"),
                    new DashboardPanel(
                            "Pattern Registration Latency (p50/p95/p99)",
                            "d/pattern-registry",
                            "histogram_quantile(0.50, "
                            + "sum(rate(aep_registry_pattern_registration_latency_bucket[5m])) by (le)) "
                            + "as p50"),
                    new DashboardPanel(
                            "Pattern Registration Errors by Type",
                            "d/pattern-registry",
                            "sum(rate(aep_registry_pattern_registration_errors[5m])) by (error_type)"),
                    new DashboardPanel(
                            "Pattern Activation Success Rate",
                            "d/pattern-registry",
                            "sum(rate(aep_registry_pattern_activation_count{result=\"success\"}[5m])) "
                            + "/ sum(rate(aep_registry_pattern_activation_count[5m])) * 100"),
                    new DashboardPanel(
                            "Top Error Patterns",
                            "d/pattern-registry",
                            "topk(10, sum by (operationId) "
                            + "(rate(aep_registry_pattern_registration_errors[5m])))")
            );

    // ==================== Pipeline Registry Dashboard ====================
    /**
     * Pipeline Registry Dashboard panels documenting key metrics and queries.
     *
     * <p>
     * Panels:<br>
     * 1. Pipeline Registration Success Rate (%) by tenant 2. Pipeline
     * Registration Latency (ms) - p50, p95, p99 3. Pipeline Registration Errors
     * by Error Type 4. Pipeline Activation Success Rate (%) by tenant 5. Top
     * Error Pipelines (Top 10 by error count)
     */
    public static final List<DashboardPanel> PIPELINE_REGISTRY_PANELS
            = Arrays.asList(
                    new DashboardPanel(
                            "Pipeline Registration Success Rate",
                            "d/pipeline-registry",
                            "sum(rate(aep_registry_pipeline_registration_count{result=\"success\"}[5m])) "
                            + "/ sum(rate(aep_registry_pipeline_registration_count[5m])) * 100"),
                    new DashboardPanel(
                            "Pipeline Registration Latency (p50/p95/p99)",
                            "d/pipeline-registry",
                            "histogram_quantile(0.50, "
                            + "sum(rate(aep_registry_pipeline_registration_latency_bucket[5m])) by (le)) "
                            + "as p50"),
                    new DashboardPanel(
                            "Pipeline Registration Errors by Type",
                            "d/pipeline-registry",
                            "sum(rate(aep_registry_pipeline_registration_errors[5m])) by (error_type)"),
                    new DashboardPanel(
                            "Pipeline Activation Success Rate",
                            "d/pipeline-registry",
                            "sum(rate(aep_registry_pipeline_activation_count{result=\"success\"}[5m])) "
                            + "/ sum(rate(aep_registry_pipeline_activation_count[5m])) * 100"),
                    new DashboardPanel(
                            "Top Error Pipelines",
                            "d/pipeline-registry",
                            "topk(10, sum by (operationId) "
                            + "(rate(aep_registry_pipeline_registration_errors[5m])))")
            );

    // ==================== Alert Rules ====================
    /**
     * Alert: Pattern registration failure rate exceeds 5% for 5 minutes.
     *
     * <p>
     * Severity: warning<br>
     * Threshold: 5% error rate<br>
     * Window: 5 minutes<br>
     * Remediation: Check logs for validation errors, verify pattern specs
     */
    public static final String ALERT_PATTERN_REGISTRATION_FAILURE_RATE_HIGH
            = "- alert: PatternRegistrationFailureRateHigh\n"
            + "  expr: |\n"
            + "    (\n"
            + "      sum(rate(aep_registry_pattern_registration_errors[5m]))\n"
            + "      / sum(rate(aep_registry_pattern_registration_count[5m]))\n"
            + "    ) > 0.05\n"
            + "  for: 5m\n"
            + "  labels:\n"
            + "    severity: warning\n"
            + "    component: pattern-registry\n"
            + "  annotations:\n"
            + "    summary: \"Pattern registration failure rate high\"\n"
            + "    description: \"Pattern registration error rate > 5% for 5 minutes\"\n"
            + "    runbook: See RUNBOOK_REGISTRATION_FAILURE_DIAGNOSIS";

    /**
     * Alert: Pipeline registration failure rate exceeds 5% for 5 minutes.
     *
     * <p>
     * Severity: warning<br>
     * Threshold: 5% error rate<br>
     * Window: 5 minutes<br>
     * Remediation: Check logs for validation errors, verify pipeline specs
     */
    public static final String ALERT_PIPELINE_REGISTRATION_FAILURE_RATE_HIGH
            = "- alert: PipelineRegistrationFailureRateHigh\n"
            + "  expr: |\n"
            + "    (\n"
            + "      sum(rate(aep_registry_pipeline_registration_errors[5m]))\n"
            + "      / sum(rate(aep_registry_pipeline_registration_count[5m]))\n"
            + "    ) > 0.05\n"
            + "  for: 5m\n"
            + "  labels:\n"
            + "    severity: warning\n"
            + "    component: pipeline-registry\n"
            + "  annotations:\n"
            + "    summary: \"Pipeline registration failure rate high\"\n"
            + "    description: \"Pipeline registration error rate > 5% for 5 minutes\"\n"
            + "    runbook: See RUNBOOK_REGISTRATION_FAILURE_DIAGNOSIS";

    /**
     * Alert: Pattern registration latency (p99) exceeds 2 seconds for 3
     * minutes.
     *
     * <p>
     * Severity: info<br>
     * Threshold: p99 latency > 2000ms<br>
     * Window: 3 minutes<br>
     * Remediation: Check database performance, spec validation complexity
     */
    public static final String ALERT_PATTERN_REGISTRATION_LATENCY_HIGH
            = "- alert: PatternRegistrationLatencyHigh\n"
            + "  expr: |\n"
            + "    histogram_quantile(0.99,\n"
            + "      sum(rate(aep_registry_pattern_registration_latency_bucket[5m])) by (le)\n"
            + "    ) > 2000\n"
            + "  for: 3m\n"
            + "  labels:\n"
            + "    severity: info\n"
            + "    component: pattern-registry\n"
            + "  annotations:\n"
            + "    summary: \"Pattern registration latency high\"\n"
            + "    description: \"Pattern registration p99 latency > 2 seconds for 3 minutes\"";

    /**
     * Alert: Pipeline registration latency (p99) exceeds 2 seconds for 3
     * minutes.
     *
     * <p>
     * Severity: info<br>
     * Threshold: p99 latency > 2000ms<br>
     * Window: 3 minutes<br>
     * Remediation: Check database performance, DAG validation complexity
     */
    public static final String ALERT_PIPELINE_REGISTRATION_LATENCY_HIGH
            = "- alert: PipelineRegistrationLatencyHigh\n"
            + "  expr: |\n"
            + "    histogram_quantile(0.99,\n"
            + "      sum(rate(aep_registry_pipeline_registration_latency_bucket[5m])) by (le)\n"
            + "    ) > 2000\n"
            + "  for: 3m\n"
            + "  labels:\n"
            + "    severity: info\n"
            + "    component: pipeline-registry\n"
            + "  annotations:\n"
            + "    summary: \"Pipeline registration latency high\"\n"
            + "    description: \"Pipeline registration p99 latency > 2 seconds for 3 minutes\"";

    /**
     * Alert: No pattern registrations in the last 5 minutes.
     *
     * <p>
     * Severity: info<br>
     * Threshold: 0 registrations in 5 minutes<br>
     * Window: 5 minutes<br>
     * Remediation: Expected if no patterns being registered; not a failure
     */
    public static final String ALERT_NO_PATTERN_REGISTRATIONS
            = "- alert: NoPatternRegistrationsRecent\n"
            + "  expr: |\n"
            + "    increase(aep_registry_pattern_registration_count[5m]) == 0\n"
            + "  for: 5m\n"
            + "  labels:\n"
            + "    severity: info\n"
            + "    component: pattern-registry\n"
            + "  annotations:\n"
            + "    summary: \"No pattern registrations in 5 minutes\"\n"
            + "    description: \"No pattern registration activity detected (may be normal)\"";

    /**
     * Alert: No pipeline registrations in the last 5 minutes.
     *
     * <p>
     * Severity: info<br>
     * Threshold: 0 registrations in 5 minutes<br>
     * Window: 5 minutes<br>
     * Remediation: Expected if no pipelines being registered; not a failure
     */
    public static final String ALERT_NO_PIPELINE_REGISTRATIONS
            = "- alert: NoPipelineRegistrationsRecent\n"
            + "  expr: |\n"
            + "    increase(aep_registry_pipeline_registration_count[5m]) == 0\n"
            + "  for: 5m\n"
            + "  labels:\n"
            + "    severity: info\n"
            + "    component: pipeline-registry\n"
            + "  annotations:\n"
            + "    summary: \"No pipeline registrations in 5 minutes\"\n"
            + "    description: \"No pipeline registration activity detected (may be normal)\"";

    // ==================== Runbooks ====================
    public static final String RUNBOOK_REGISTRATION_FAILURE_DIAGNOSIS
            = "RUNBOOK: Registry Registration Failure Diagnosis\n"
            + "=========================================\n\n"
            + "Issue: Pattern or pipeline registration failures detected\n\n"
            + "Symptoms:\n"
            + "  - aep.registry.pattern.registration.errors counter increasing\n"
            + "  - aep.registry.pipeline.registration.errors counter increasing\n"
            + "  - Increased latency in registrations (p99 > 2s)\n"
            + "  - User reports unable to create patterns/pipelines\n\n"
            + "Diagnosis Steps:\n"
            + "  1. Check registration error rate:\n"
            + "     Query: sum(rate(aep_registry_pattern_registration_errors[5m])) / "
            + "sum(rate(aep_registry_pattern_registration_count[5m]))\n"
            + "  2. Identify error types:\n"
            + "     Query: sum by (error_type) (rate(aep_registry_pattern_registration_errors[5m]))\n"
            + "  3. Check database connectivity:\n"
            + "     - Verify database connection pool status in logs\n"
            + "     - Look for \"Connection timeout\" errors\n"
            + "     - Check database query performance (p99 latency)\n"
            + "  4. Review spec validation logs:\n"
            + "     - Check for validation errors in application logs\n"
            + "     - Filter by MDC: tenantId, operation=registration\n"
            + "     - Look for ValidationException or SyntaxException\n"
            + "  5. Check tenant isolation:\n"
            + "     - Verify MDC tenantId in logs matches expected tenant\n"
            + "     - Check for cross-tenant access attempts\n\n"
            + "Root Causes:\n"
            + "  - Database connectivity issue → connection pool exhausted\n"
            + "  - Malformed spec → validation failures\n"
            + "  - Resource exhaustion → query timeouts\n"
            + "  - Tenant mismatch → isolation enforcement rejection\n"
            + "  - Version incompatibility → schema mismatch errors\n\n"
            + "Recovery Actions:\n"
            + "  1. Immediate: Check application logs (grep 'tenantId=X operation=registration')\n"
            + "  2. Check database: SELECT COUNT(*) FROM patterns WHERE creation_time > NOW() - INTERVAL 5 MINUTE;\n"
            + "  3. Verify connectivity: telnet db-host db-port\n"
            + "  4. If pool exhausted: Bounce database connections or scale database pool\n"
            + "  5. Escalate to database team if connectivity issue persists\n"
            + "  6. Rollback recent spec changes if validation error spike detected\n\n"
            + "Prevention:\n"
            + "  - Monitor error rate continuously; alert at >5% for >5 minutes\n"
            + "  - Test spec validation in CI/CD before deployment\n"
            + "  - Load test registration flows with expected tenant count\n"
            + "  - Maintain database connection pool headroom (80% capacity max)\n";

    public static final String RUNBOOK_REGISTRATION_LATENCY_DIAGNOSIS
            = "RUNBOOK: Registry Registration Latency Diagnosis\n"
            + "==========================================\n\n"
            + "Issue: Pattern or pipeline registration latency exceeding SLO (>2s p99)\n\n"
            + "Symptoms:\n"
            + "  - histogram_quantile(0.99, aep.registry.pattern.registration.latency) > 2000ms\n"
            + "  - Users experiencing slow UI when registering patterns/pipelines\n"
            + "  - PipelineRegistrationLatencyHigh alert firing\n\n"
            + "Diagnosis Steps:\n"
            + "  1. Check current latency distribution:\n"
            + "     Query p50/p95/p99: histogram_quantile(0.50|0.95|0.99, ...)\n"
            + "  2. Identify slow operations:\n"
            + "     Query: topk(10, aep.registry.pattern.registration.latency) by (operationId)\n"
            + "  3. Check database query performance:\n"
            + "     - Enable query logging: SET log_statement = 'all';\n"
            + "     - Check slow query log: SELECT * FROM pg_stat_statements ORDER BY mean_exec_time DESC;\n"
            + "  4. Review spec complexity:\n"
            + "     - Patterns: Check pattern_specification field size\n"
            + "     - Pipelines: Check stage count, DAG validation time\n"
            + "  5. Check resource utilization:\n"
            + "     - Database CPU, memory, disk I/O\n"
            + "     - Application memory usage\n"
            + "     - Network latency to database\n\n"
            + "Root Causes:\n"
            + "  - Database not optimized (missing indexes, table bloat)\n"
            + "  - Complex specs requiring expensive validation\n"
            + "  - Resource contention (high CPU/memory on database)\n"
            + "  - Network latency to database\n"
            + "  - Connection pool exhaustion (queued requests)\n\n"
            + "Recovery Actions:\n"
            + "  1. Identify slow queries in database slow log\n"
            + "  2. Add missing indexes (particularly on tenant_id, created_at)\n"
            + "  3. Analyze table bloat: VACUUM ANALYZE aep_patterns;\n"
            + "  4. Optimize spec validation logic (consider caching)\n"
            + "  5. Scale database resources (CPU, memory, storage)\n"
            + "  6. Consider connection pooling layer (pgBouncer, PgPool)\n\n"
            + "Escalation:\n"
            + "  - If database query time > 1s: Escalate to database team\n"
            + "  - If network latency > 100ms: Check network infrastructure\n"
            + "  - If validation time > 500ms: Review spec complexity constraints\n";

    // ==================== Sample Prometheus Queries ====================
    public static final List<String> SAMPLE_PROMETHEUS_QUERIES
            = Arrays.asList(
                    "# Pattern registration success rate (%) by tenant\n"
                    + "sum(rate(aep_registry_pattern_registration_count{result=\"success\"}[5m]))\n"
                    + "/ sum(rate(aep_registry_pattern_registration_count[5m])) * 100",
                    "\n# Pattern registration latency p99 by tenant\n"
                    + "histogram_quantile(0.99,\n"
                    + "  sum(rate(aep_registry_pattern_registration_latency_bucket[5m])) by (le, tenant_id))",
                    "\n# Pattern registration errors by type\n"
                    + "sum(rate(aep_registry_pattern_registration_errors[5m])) by (error_type)",
                    "\n# Pipeline registration throughput (registrations/sec)\n"
                    + "sum(rate(aep_registry_pipeline_registration_count[1m]))",
                    "\n# Top 10 patterns by registration error count\n"
                    + "topk(10, sum by (operationId) "
                    + "(rate(aep_registry_pattern_registration_errors[5m])))",
                    "\n# Tenant-specific pattern registration success rate\n"
                    + "sum(rate(aep_registry_pattern_registration_count{tenant_id=\"TENANT_ID\", "
                    + "result=\"success\"}[5m]))\n"
                    + "/ sum(rate(aep_registry_pattern_registration_count{tenant_id=\"TENANT_ID\"}[5m])) * 100",
                    "\n# Combined registry success rate (patterns + pipelines)\n"
                    + "(sum(rate(aep_registry_pattern_registration_count{result=\"success\"}[5m]))\n"
                    + "+ sum(rate(aep_registry_pipeline_registration_count{result=\"success\"}[5m])))\n"
                    + "/ (sum(rate(aep_registry_pattern_registration_count[5m]))\n"
                    + "+ sum(rate(aep_registry_pipeline_registration_count[5m]))) * 100"
            );

    // ==================== Helper Classes ====================
    /**
     * Value object representing a dashboard panel definition.
     *
     * @doc.type record
     * @doc.purpose Dashboard panel reference configuration
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public static class DashboardPanel {

        private final String panelName;
        private final String dashboardUri;
        private final String promqlQuery;

        /**
         * Constructs a dashboard panel reference.
         *
         * @param panelName the human-readable panel name
         * @param dashboardUri the Grafana dashboard URI (e.g.,
         * "d/pattern-registry")
         * @param promqlQuery the PromQL query for data source
         */
        public DashboardPanel(String panelName, String dashboardUri, String promqlQuery) {
            this.panelName = panelName;
            this.dashboardUri = dashboardUri;
            this.promqlQuery = promqlQuery;
        }

        public String getPanelName() {
            return panelName;
        }

        public String getDashboardUri() {
            return dashboardUri;
        }

        public String getPromqlQuery() {
            return promqlQuery;
        }
    }

    /**
     * Returns all alert rules as a list.
     *
     * @return list of alert rule YAML strings
     */
    public static List<String> getAllAlertRules() {
        return Arrays.asList(
                ALERT_PATTERN_REGISTRATION_FAILURE_RATE_HIGH,
                ALERT_PIPELINE_REGISTRATION_FAILURE_RATE_HIGH,
                ALERT_PATTERN_REGISTRATION_LATENCY_HIGH,
                ALERT_PIPELINE_REGISTRATION_LATENCY_HIGH,
                ALERT_NO_PATTERN_REGISTRATIONS,
                ALERT_NO_PIPELINE_REGISTRATIONS
        );
    }

    /**
     * Returns all runbooks as a list.
     *
     * @return list of runbook strings
     */
    public static List<String> getAllRunbooks() {
        return Arrays.asList(
                RUNBOOK_REGISTRATION_FAILURE_DIAGNOSIS,
                RUNBOOK_REGISTRATION_LATENCY_DIAGNOSIS
        );
    }

    private RegistryDashboardConfiguration() {
        // Utility class, not instantiable
    }
}
