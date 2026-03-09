package com.ghatana.yappc.sdlc.ops;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Ops Phase - Step 3: Monitor Production.
 *
 * <p>Continuous monitoring of production environment with alerting and anomaly detection. Tracks
 * SLIs, SLOs, and triggers alerts based on threshold violations.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Collect metrics (latency, error rate, throughput, saturation)
 *   <li>Track SLIs and SLO compliance
 *   <li>Detect anomalies and degradation
 *   <li>Trigger alerts for threshold violations
 *   <li>Persist monitoring data
 *   <li>Emit workflow events
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Monitors production environment with alerting
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MonitorStep implements WorkflowStep {

  private static final String COLLECTION_MONITORING_DATA = "monitoring_data";
  private static final String COLLECTION_ALERTS = "alerts";
  private static final String EVENT_TOPIC = "ops.workflow";

  // SLO thresholds
  private static final double SLO_AVAILABILITY = 99.9; // 99.9%
  private static final double SLO_LATENCY_P99 = 500.0; // 500ms
  private static final double SLO_ERROR_RATE = 1.0; // 1%

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public MonitorStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "ops.monitor";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String monitoringId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> collectMetrics(tenantId, monitoringId))
        .then(metrics -> evaluateSlos(metrics, tenantId, monitoringId))
        .then(evaluation -> detectAnomalies(evaluation, tenantId, monitoringId))
        .then(evaluation -> generateAlerts(evaluation, tenantId, monitoringId))
        .then(monitoring -> persistMonitoring(monitoring, tenantId, monitoringId))
        .then(monitoring -> publishEvents(monitoring, tenantId, monitoringId))
        .then(monitoring -> buildOutputContext(context, monitoring, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, monitoringId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> collectMetrics(String tenantId, String monitoringId) {
    // Simulate metrics collection from production

    Map<String, Object> metrics = new LinkedHashMap<>();

    // Golden signals
    metrics.put(
        "latency",
        Map.of(
            "p50", 85.0,
            "p95", 195.0,
            "p99", 420.0,
            "max", 1200.0));

    metrics.put(
        "traffic",
        Map.of("requestsPerSecond", 1250.0, "activeConnections", 340, "bandwidth", "125 MB/s"));

    metrics.put(
        "errors",
        Map.of(
            "errorRate", 0.45,
            "5xxCount", 12,
            "4xxCount", 105,
            "timeoutCount", 3));

    metrics.put(
        "saturation",
        Map.of(
            "cpu", "62%",
            "memory", "74%",
            "disk", "48%",
            "network", "35%"));

    metrics.put("availability", Map.of("uptime", 99.95, "healthyInstances", "10/10"));

    metrics.put("collectedAt", Instant.now().toString());

    return Promise.of(metrics);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> evaluateSlos(
      Map<String, Object> metrics, String tenantId, String monitoringId) {

    Map<String, Object> latency = (Map<String, Object>) metrics.get("latency");
    Map<String, Object> errors = (Map<String, Object>) metrics.get("errors");
    Map<String, Object> availability = (Map<String, Object>) metrics.get("availability");

    double latencyP99 = (double) latency.get("p99");
    double errorRate = (double) errors.get("errorRate");
    double uptime = (double) availability.get("uptime");

    Map<String, Object> sloEvaluation = new LinkedHashMap<>();

    // Latency SLO
    boolean latencySloMet = latencyP99 <= SLO_LATENCY_P99;
    sloEvaluation.put(
        "latencySlo",
        Map.of(
            "target", SLO_LATENCY_P99,
            "actual", latencyP99,
            "met", latencySloMet,
            "budget", latencySloMet ? (SLO_LATENCY_P99 - latencyP99) : 0));

    // Error rate SLO
    boolean errorSloMet = errorRate <= SLO_ERROR_RATE;
    sloEvaluation.put(
        "errorSlo",
        Map.of(
            "target", SLO_ERROR_RATE,
            "actual", errorRate,
            "met", errorSloMet,
            "budget", errorSloMet ? (SLO_ERROR_RATE - errorRate) : 0));

    // Availability SLO
    boolean availabilitySloMet = uptime >= SLO_AVAILABILITY;
    sloEvaluation.put(
        "availabilitySlo",
        Map.of(
            "target", SLO_AVAILABILITY,
            "actual", uptime,
            "met", availabilitySloMet,
            "budget", availabilitySloMet ? (uptime - SLO_AVAILABILITY) : 0));

    boolean allSlosMet = latencySloMet && errorSloMet && availabilitySloMet;
    sloEvaluation.put(
        "overall", Map.of("status", allSlosMet ? "HEALTHY" : "DEGRADED", "slosMet", allSlosMet));

    Map<String, Object> evaluation = new LinkedHashMap<>();
    evaluation.put("metrics", metrics);
    evaluation.put("sloEvaluation", sloEvaluation);
    evaluation.put("evaluatedAt", Instant.now().toString());

    return Promise.of(evaluation);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> detectAnomalies(
      Map<String, Object> evaluation, String tenantId, String monitoringId) {

    Map<String, Object> metrics = (Map<String, Object>) evaluation.get("metrics");

    List<Map<String, Object>> anomalies = new ArrayList<>();

    // Check for sudden traffic spike
    Map<String, Object> traffic = (Map<String, Object>) metrics.get("traffic");
    double rps = (double) traffic.get("requestsPerSecond");
    if (rps > 2000) { // 60% above baseline of 1250
      anomalies.add(
          Map.of(
              "type", "TRAFFIC_SPIKE",
              "severity", "WARNING",
              "description",
                  String.format("Request rate %.0f rps significantly above baseline", rps)));
    }

    // Check for error rate spike
    Map<String, Object> errors = (Map<String, Object>) metrics.get("errors");
    double errorRate = (double) errors.get("errorRate");
    if (errorRate > 2.0) {
      anomalies.add(
          Map.of(
              "type", "ERROR_SPIKE",
              "severity", "CRITICAL",
              "description",
                  String.format("Error rate %.2f%% exceeds critical threshold", errorRate)));
    }

    // Check for resource saturation
    Map<String, Object> saturation = (Map<String, Object>) metrics.get("saturation");
    String cpuUsage = (String) saturation.get("cpu");
    int cpu = Integer.parseInt(cpuUsage.replace("%", ""));
    if (cpu > 80) {
      anomalies.add(
          Map.of(
              "type", "RESOURCE_SATURATION",
              "severity", "WARNING",
              "description", String.format("CPU usage %d%% approaching limits", cpu)));
    }

    evaluation.put("anomalies", anomalies);
    evaluation.put("anomalyCount", anomalies.size());

    return Promise.of(evaluation);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> generateAlerts(
      Map<String, Object> evaluation, String tenantId, String monitoringId) {

    Map<String, Object> sloEvaluation = (Map<String, Object>) evaluation.get("sloEvaluation");
    Map<String, Object> overall = (Map<String, Object>) sloEvaluation.get("overall");
    List<Map<String, Object>> anomalies = (List<Map<String, Object>>) evaluation.get("anomalies");

    List<Map<String, Object>> alerts = new ArrayList<>();

    // SLO violation alerts
    boolean slosMet = (boolean) overall.get("slosMet");
    if (!slosMet) {
      alerts.add(
          createAlert(tenantId, "SLO_VIOLATION", "WARNING", "One or more SLOs are not being met"));
    }

    // Anomaly alerts
    for (Map<String, Object> anomaly : anomalies) {
      String severity = (String) anomaly.get("severity");
      if ("CRITICAL".equals(severity)) {
        alerts.add(
            createAlert(
                tenantId,
                (String) anomaly.get("type"),
                severity,
                (String) anomaly.get("description")));
      }
    }

    // Persist alerts
    List<Promise<Void>> alertPromises =
        alerts.stream().map(alert -> dbClient.insert(COLLECTION_ALERTS, alert)).toList();

    return io.activej.promise.Promises.all(alertPromises)
        .map(
            $ -> {
              evaluation.put("alerts", alerts);
              evaluation.put("alertCount", alerts.size());
              return evaluation;
            });
  }

  private Map<String, Object> createAlert(
      String tenantId, String type, String severity, String description) {
    Map<String, Object> alert = new LinkedHashMap<>();
    alert.put("alertId", UUID.randomUUID().toString());
    alert.put("tenantId", tenantId);
    alert.put("type", type);
    alert.put("severity", severity);
    alert.put("description", description);
    alert.put("status", "OPEN");
    alert.put("createdAt", Instant.now().toString());
    return alert;
  }

  private Promise<Map<String, Object>> persistMonitoring(
      Map<String, Object> monitoring, String tenantId, String monitoringId) {

    Map<String, Object> monitoringRecord = new LinkedHashMap<>();
    monitoringRecord.put("monitoringId", monitoringId);
    monitoringRecord.put("tenantId", tenantId);
    monitoringRecord.putAll(monitoring);

    return dbClient.insert(COLLECTION_MONITORING_DATA, monitoringRecord).map($ -> monitoring);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> monitoring, String tenantId, String monitoringId) {

    Map<String, Object> sloEvaluation = (Map<String, Object>) monitoring.get("sloEvaluation");
    Map<String, Object> overall = (Map<String, Object>) sloEvaluation.get("overall");

    Map<String, Object> eventPayload =
        Map.of(
            "eventType",
            "MONITORING_COMPLETED",
            "monitoringId",
            monitoringId,
            "status",
            overall.get("status"),
            "alertCount",
            monitoring.get("alertCount"));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> monitoring);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> monitoring, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Monitor");
    output.put("monitoring", monitoring);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex,
      WorkflowContext context,
      String tenantId,
      String monitoringId,
      Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "eventType",
            "MONITORING_FAILED",
            "monitoringId",
            monitoringId,
            "error",
            ex.getMessage(),
            "timestamp",
            Instant.now().toString());

    return eventClient
        .publish(EVENT_TOPIC, tenantId, errorPayload)
        .then(
            $ -> {
              WorkflowContext errorContext = context.copy();
              errorContext.put("status", "FAILED");
              errorContext.put("error", ex.getMessage());
              errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
              return Promise.of(errorContext);
            });
  }
}
