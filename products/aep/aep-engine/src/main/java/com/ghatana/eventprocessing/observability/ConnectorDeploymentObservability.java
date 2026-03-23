package com.ghatana.eventprocessing.observability;

import java.util.List;
import java.util.Map;

/**
 * Reference configuration and documentation for observability dashboards,
 * alerts, and runbooks.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides comprehensive observability reference for connector health and
 * deployment metrics. Includes Grafana dashboard definitions, Prometheus alert
 * rules, runbook guides, and sample queries for operators and SREs to monitor
 * event processing health, diagnose issues, and respond to incidents.
 *
 * <p>
 * <b>Sections</b><br>
 * 1. Dashboard Configuration - Panel definitions and layout 2. Alert Rules -
 * Thresholds and conditions for incident creation 3. Runbook Guides - Diagnosis
 * and remediation procedures 4. Sample Queries - Common PromQL queries for
 * debugging
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Reference for dashboard configuration in monitoring/grafana/
 * // Copy dashboard definitions to Grafana provisioning
 * String dashboardJson = ConnectorDeploymentObservability.CONNECTOR_HEALTH_DASHBOARD;
 *
 * // Reference for alert rules
 * // Copy alert definitions to prometheus/
 * String alertRules = ConnectorDeploymentObservability.CONNECTOR_FAILURE_ALERT;
 *
 * // Use sample queries for debugging in Prometheus/Grafana UI
 * String query = ConnectorDeploymentObservability.CONNECTOR_ERROR_RATE_QUERY;
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Reference guide for connector/deployment observability
 * configuration
 * @doc.layer product
 * @doc.pattern Configuration Reference
 */
public final class ConnectorDeploymentObservability {

    private ConnectorDeploymentObservability() {
        // Utility class - no instantiation
    }

    // ===== CONNECTOR HEALTH METRICS =====
    /**
     * Connector open/closed state tracking.
     *
     * <p>
     * Emitted by ConnectorOperator implementations on lifecycle transitions.
     */
    public static final class ConnectorHealthMetrics {

        public static final String CONNECTOR_OPEN_COUNT = "aep.connector.open.count";
        public static final String CONNECTOR_CLOSED_COUNT = "aep.connector.closed.count";
        public static final String CONNECTOR_ERROR_COUNT = "aep.connector.error.count";
        public static final String CONNECTOR_LATENCY_MS = "aep.connector.operation.latency.ms";

        /**
         * Tags: connector_id, connector_type, tenant_id.
         */
        public static final List<String> STANDARD_TAGS = List.of(
                "connector_id",
                "connector_type",
                "tenant_id");
    }

    // ===== DEPLOYMENT HEALTH METRICS =====
    /**
     * Deployment operation tracking (success/failure, latency).
     *
     * <p>
     * Emitted by DeploymentHandler on deploy/update/undeploy operations.
     */
    public static final class DeploymentHealthMetrics {

        public static final String DEPLOYMENT_SUCCESS_COUNT = "aep.deployment.success.count";
        public static final String DEPLOYMENT_ERROR_COUNT = "aep.deployment.error.count";
        public static final String DEPLOYMENT_LATENCY_MS = "aep.deployment.operation.latency.ms";
        public static final String DEPLOYMENT_ACTIVE_COUNT = "aep.deployment.active.gauge";

        /**
         * Tags: deployment_id, tenant_id, pipeline_id, operation
         * (deploy/update/undeploy), error_type.
         */
        public static final List<String> STANDARD_TAGS = List.of(
                "deployment_id",
                "tenant_id",
                "pipeline_id",
                "operation");
    }

    // ===== GRAFANA DASHBOARD REFERENCES =====
    /**
     * Connector Health Dashboard - Shows per-connector metrics and status.
     *
     * <p>
     * URI: /d/connector-health (to be provisioned via Grafana)
     * <p>
     * Panels: 1. Connector Open/Closed Rate (graph, 5m resolution) 2. Connector
     * Errors by Type (pie chart) 3. Connector Operation Latency P95 (heatmap)
     * 4. Connector Status by Type (stat, current value) 5. Top Error Connectors
     * (table, by error count)
     */
    public static final String CONNECTOR_HEALTH_DASHBOARD_UID = "connector-health";
    public static final String CONNECTOR_HEALTH_DASHBOARD_TITLE = "Event Processing: Connector Health";
    public static final List<String> CONNECTOR_HEALTH_DASHBOARD_TAGS = List.of(
            "event-processing",
            "connectors",
            "health");

    /**
     * Deployment Status Dashboard - Shows deployment operation rates and
     * health.
     *
     * <p>
     * URI: /d/deployment-status (to be provisioned via Grafana)
     * <p>
     * Panels: 1. Deployment Success Rate (stat, % of successful operations) 2.
     * Deployment Failures by Error Type (bar chart) 3. Deployment Operation
     * Latency (heatmap, ms) 4. Active Deployments by Tenant (stat) 5. Recent
     * Failures (table, with timestamps and errors)
     */
    public static final String DEPLOYMENT_STATUS_DASHBOARD_UID = "deployment-status";
    public static final String DEPLOYMENT_STATUS_DASHBOARD_TITLE = "Event Processing: Deployment Status";
    public static final List<String> DEPLOYMENT_STATUS_DASHBOARD_TAGS = List.of(
            "event-processing",
            "deployment",
            "status");

    // ===== PROMETHEUS ALERT RULES =====
    /**
     * Alert: Connector Failure Rate High.
     *
     * <p>
     * Condition: Error count > 5 in 5 minute window
     * <p>
     * Severity: CRITICAL
     * <p>
     * Action: Page on-call engineer, check connector logs
     */
    public static final String CONNECTOR_FAILURE_ALERT = "aep:connector:failure_rate_high";
    public static final String CONNECTOR_FAILURE_THRESHOLD = "5";
    public static final String CONNECTOR_FAILURE_WINDOW = "5m";

    /**
     * Alert: Connector Operation Latency High.
     *
     * <p>
     * Condition: P95 latency > 1000ms in 5 minute window
     * <p>
     * Severity: WARNING
     * <p>
     * Action: Check connector performance, review queue depth
     */
    public static final String CONNECTOR_LATENCY_ALERT = "aep:connector:latency_high";
    public static final String CONNECTOR_LATENCY_THRESHOLD_MS = "1000";
    public static final String CONNECTOR_LATENCY_WINDOW = "5m";

    /**
     * Alert: Deployment Failure Rate High.
     *
     * <p>
     * Condition: Error count > 3 in 5 minute window
     * <p>
     * Severity: CRITICAL
     * <p>
     * Action: Page SRE, investigate deployment handler logs, check PipelineSpec
     * validation
     */
    public static final String DEPLOYMENT_FAILURE_ALERT = "aep:deployment:failure_rate_high";
    public static final String DEPLOYMENT_FAILURE_THRESHOLD = "3";
    public static final String DEPLOYMENT_FAILURE_WINDOW = "5m";

    /**
     * Alert: Deployment Operation Latency High.
     *
     * <p>
     * Condition: P95 latency > 5000ms in 5 minute window
     * <p>
     * Severity: WARNING
     * <p>
     * Action: Check deployment handler performance, review event-processing
     * resources
     */
    public static final String DEPLOYMENT_LATENCY_ALERT = "aep:deployment:latency_high";
    public static final String DEPLOYMENT_LATENCY_THRESHOLD_MS = "5000";
    public static final String DEPLOYMENT_LATENCY_WINDOW = "5m";

    /**
     * Alert: No Active Deployments.
     *
     * <p>
     * Condition: Active deployment count == 0 for 10 minutes
     * <p>
     * Severity: WARNING
     * <p>
     * Action: Check if deployments are intentionally idle or if deployment
     * handler is stuck
     */
    public static final String NO_ACTIVE_DEPLOYMENTS_ALERT = "aep:deployment:no_active";
    public static final String NO_ACTIVE_DEPLOYMENTS_THRESHOLD = "0";
    public static final String NO_ACTIVE_DEPLOYMENTS_WINDOW = "10m";

    // ===== RUNBOOK GUIDES =====
    /**
     * Runbook: Diagnose High Connector Error Rate.
     *
     * <p>
     * Steps: 1. Identify affected connector_id from alert 2. Query connector
     * logs: grep -i "ERROR\|EXCEPTION" connector-{connector_id}.log 3. Check
     * connector configuration: validate endpoint, credentials, QoS settings 4.
     * Check downstream resource availability (EventCloud, queue, HTTP endpoint)
     * 5. If transient, restart connector: deployment-handler undeploy
     * {deployment_id} && deploy 6. If persistent, escalate to architecture team
     */
    public static final String RUNBOOK_CONNECTOR_ERROR_DIAGNOSIS
            = """
        ## Connector Error Rate High - Runbook

        ### Alert: aep:connector:failure_rate_high

        **Severity**: CRITICAL
        **SLA**: Respond within 5 minutes

        ### Diagnosis Steps

        1. **Identify affected connector**:
           - Query: `label_values(aep_connector_error_count, connector_id)`
           - Check alert payload for connector_id

        2. **Review recent error logs**:
           ```
           kubectl logs -n event-processing pod-name | grep -A5 "connector_id={id}"
           ```

        3. **Check connector configuration**:
           - Verify endpoint URL is correct and reachable
           - Verify authentication credentials (API key, mTLS cert)
           - Verify QoS settings (batch size, timeout)

        4. **Check downstream resources**:
           - For EventCloud source: `curl http://eventcloud:8080/health`
           - For queue connector: Check queue broker health (Kafka, RabbitMQ)
           - For HTTP egress: Verify target endpoint is responding

        5. **Determine scope**:
           - Is error rate increasing? (escalation)
           - Is error rate stable? (may self-recover)
           - Is specific tenant affected? (tenant isolation)

        ### Recovery Actions

        - **For transient issues**: Wait 2 minutes, monitor recovery
        - **For persistent issues**: 
          ```
          curl -X POST http://deployment-handler:8080/api/v1/deployments/{deployment_id}/undeploy
          (wait 30 seconds)
          curl -X POST http://deployment-handler:8080/api/v1/deployments/{deployment_id}/deploy
          ```
        - **For configuration issues**: Update connector config, restart deployment
        - **For critical issues**: Escalate to on-call architect

        ### Prevention

        - Monitor connector health continuously (5m sliding window)
        - Implement circuit breaker for failing endpoints
        - Set up log aggregation alerts for error patterns
        """;

    /**
     * Runbook: Diagnose High Deployment Operation Latency.
     *
     * <p>
     * Steps: 1. Check deployment handler resource usage (CPU, memory) 2. Check
     * event-processing pipeline depth (queue length) 3. Check PipelineSpec
     * complexity (stage count, operator count) 4. Analyze latency distribution:
     * P50, P95, P99 5. If resource-constrained, scale event-processing pods
     */
    public static final String RUNBOOK_DEPLOYMENT_LATENCY_DIAGNOSIS
            = """
        ## Deployment Operation Latency High - Runbook

        ### Alert: aep:deployment:latency_high

        **Severity**: WARNING
        **SLA**: Respond within 15 minutes

        ### Diagnosis Steps

        1. **Check deployment handler health**:
           ```
           kubectl describe pod deployment-handler | grep -A10 "Status:"
           ```

        2. **Monitor handler resource usage**:
           ```
           kubectl top pod deployment-handler
           (Check CPU% and Memory% - if >80%, need scaling)
           ```

        3. **Check event-processing pipeline depth**:
           ```
           curl http://event-processing:8080/metrics | grep aep_pipeline_queue_depth
           (Check if queue is backed up)
           ```

        4. **Analyze latency percentiles**:
           - Query: `histogram_quantile(0.95, aep_deployment_operation_latency_ms)`
           - Check if P95 > threshold (1000ms for deployment, 5000ms for operations)

        5. **Check PipelineSpec complexity**:
           - Review recent deployments: stage count, operator count
           - Complex specs take longer to materialize

        ### Recovery Actions

        - **For handler resource constraints**:
          ```
          kubectl scale deployment deployment-handler --replicas=3
          ```

        - **For event-processing pipeline backlog**:
          ```
          kubectl scale deployment event-processing --replicas=5
          ```

        - **For complex PipelineSpecs**: Optimize spec (reduce stages/operators) or accept higher latency

        ### Prevention

        - Set resource requests/limits appropriately for handler pods
        - Implement auto-scaling based on queue depth
        - Establish SLO for deployment operations (<2000ms P95)
        - Review and optimize complex pipeline specs before deployment
        """;

    // ===== SAMPLE QUERIES =====
    /**
     * PromQL: Connector Error Rate (per minute).
     */
    public static final String CONNECTOR_ERROR_RATE_QUERY
            = "rate(aep_connector_error_count[1m])";

    /**
     * PromQL: Deployment Success Rate (% of successful deployments in last
     * hour).
     */
    public static final String DEPLOYMENT_SUCCESS_RATE_QUERY
            = "sum(rate(aep_deployment_success_count[1h])) / (sum(rate(aep_deployment_success_count[1h])) + sum(rate(aep_deployment_error_count[1h]))) * 100";

    /**
     * PromQL: Connector Operation Latency P95.
     */
    public static final String CONNECTOR_LATENCY_P95_QUERY
            = "histogram_quantile(0.95, rate(aep_connector_operation_latency_ms_bucket[5m]))";

    /**
     * PromQL: Deployment Operation Latency P99.
     */
    public static final String DEPLOYMENT_LATENCY_P99_QUERY
            = "histogram_quantile(0.99, rate(aep_deployment_operation_latency_ms_bucket[5m]))";

    /**
     * PromQL: Active Deployments by Tenant.
     */
    public static final String ACTIVE_DEPLOYMENTS_BY_TENANT_QUERY
            = "sum(aep_deployment_active_gauge) by (tenant_id)";

    /**
     * PromQL: Top 5 Error Types by Count.
     */
    public static final String TOP_ERROR_TYPES_QUERY
            = "topk(5, sum(rate(aep_connector_error_count[5m])) by (error_type))";

    /**
     * PromQL: Deployment Failures by Operation Type.
     */
    public static final String DEPLOYMENT_FAILURES_BY_OPERATION_QUERY
            = "sum(rate(aep_deployment_error_count[5m])) by (operation)";

    // ===== TRACE CONFIGURATION =====
    /**
     * Jaeger Tracing Configuration for connector/deployment operations.
     */
    public static final class JaegerTracing {

        public static final String SERVICE_NAME = "aep-event-processing";
        public static final String CONNECTOR_TRACE_SPAN = "aep:connector:operation";
        public static final String DEPLOYMENT_TRACE_SPAN = "aep:deployment:operation";

        /**
         * Example trace hierarchy:
         * <pre>
         * aep:deployment:operation (root)
         *   ├── aep:deployment:validate-spec
         *   ├── aep:connector:materialize
         *   │   ├── aep:connector:create-source
         *   │   ├── aep:connector:create-sink
         *   │   └── aep:connector:wire-stages
         *   └── aep:pipeline:start
         * </pre>
         */
        public static final String TRACE_HIERARCHY_DOCUMENTATION
                = """
            ### Trace Hierarchy

            Root: aep:deployment:operation
            - duration: wall-clock time from request to completion

            Children:
            1. aep:deployment:validate-spec (PipelineSpecValidator)
               - Validates DAG structure, stages, connectors
            2. aep:connector:materialize (ConnectorFactory)
               - Creates ConnectorOperator instances
            3. aep:pipeline:start (EventPipeline)
               - Starts pipeline execution
               - Parent spans: aep:connector:create-source, aep:connector:create-sink, aep:connector:wire-stages

            Tags (on all spans):
            - deployment_id: Deployment identifier
            - tenant_id: Tenant context
            - pipeline_id: Pipeline identifier
            - error: true if exception occurred
            """;
    }

    // ===== ALERT RULES AS YAML =====
    /**
     * Prometheus alert rules configuration (YAML format).
     *
     * <p>
     * To use: Copy content to prometheus/rules/aep-alerts.yml
     */
    public static final String PROMETHEUS_ALERT_RULES_YAML
            = """
        groups:
        - name: aep_connector_deployment_alerts
          interval: 30s
          rules:

          - alert: ConnectorFailureRateHigh
            expr: rate(aep_connector_error_count[5m]) > 1
            for: 5m
            labels:
              severity: critical
              service: event-processing
            annotations:
              summary: "High connector failure rate for {{ $labels.connector_id }}"
              description: "Connector {{ $labels.connector_id }} error rate is {{ $value }} errors/sec"
              runbook: "https://wiki.company.com/runbooks/connector-errors"

          - alert: ConnectorLatencyHigh
            expr: histogram_quantile(0.95, rate(aep_connector_operation_latency_ms_bucket[5m])) > 1000
            for: 10m
            labels:
              severity: warning
              service: event-processing
            annotations:
              summary: "High connector operation latency for {{ $labels.connector_id }}"
              description: "P95 latency is {{ $value }}ms (threshold: 1000ms)"

          - alert: DeploymentFailureRateHigh
            expr: rate(aep_deployment_error_count[5m]) > 0.6
            for: 5m
            labels:
              severity: critical
              service: event-processing
            annotations:
              summary: "High deployment failure rate"
              description: "Deployment error rate is {{ $value }} errors/sec"
              runbook: "https://wiki.company.com/runbooks/deployment-errors"

          - alert: DeploymentLatencyHigh
            expr: histogram_quantile(0.95, rate(aep_deployment_operation_latency_ms_bucket[5m])) > 5000
            for: 10m
            labels:
              severity: warning
              service: event-processing
            annotations:
              summary: "High deployment operation latency"
              description: "P95 latency is {{ $value }}ms (threshold: 5000ms)"

          - alert: NoActiveDeployments
            expr: aep_deployment_active_gauge == 0
            for: 10m
            labels:
              severity: warning
              service: event-processing
            annotations:
              summary: "No active deployments"
              description: "Zero active deployments for 10 minutes"
        """;
}
