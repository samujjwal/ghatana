package com.ghatana.yappc.platform.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Accepts alert webhook payloads, normalizes them into Prometheus alerts, and routes them through incident correlation.
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class AlertWebhookHandler {

  private final IncidentCorrelator incidentCorrelator;
  private final WebhookMetrics metrics;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AlertWebhookHandler(IncidentCorrelator incidentCorrelator, WebhookMetrics metrics) {
    this(incidentCorrelator, metrics, new ObjectMapper(), Clock.systemUTC());
  }

  AlertWebhookHandler(
      IncidentCorrelator incidentCorrelator,
      WebhookMetrics metrics,
      ObjectMapper objectMapper,
      Clock clock) {
    this.incidentCorrelator = Objects.requireNonNull(incidentCorrelator, "incidentCorrelator");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Promise<AlertHandlingResult> handle(String payload) {
    Objects.requireNonNull(payload, "payload");

    List<IncidentCorrelator.PrometheusAlert> alerts = parseAlerts(payload);
    metrics.recordAlertsReceived(alerts.size());

    return Promises.toList(alerts.stream().map(incidentCorrelator::correlateAlert))
        .map(
            results -> {
              long createdCount = results.stream().filter(IncidentCorrelator.CorrelationResult::createdNewIncident).count();
              metrics.recordIncidentsCreated((int) createdCount);
              return new AlertHandlingResult(alerts.size(), (int) createdCount, results);
            });
  }

  private List<IncidentCorrelator.PrometheusAlert> parseAlerts(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      JsonNode alertsNode = root.path("alerts");

      if (alertsNode.isArray()) {
        List<IncidentCorrelator.PrometheusAlert> alerts = new ArrayList<>();
        String tenantId = textOrDefault(root.path("tenantId"), "unknown-tenant");
        for (JsonNode alertNode : alertsNode) {
          alerts.add(toAlert(alertNode, tenantId));
        }
        return List.copyOf(alerts);
      }

      return List.of(toAlert(root, textOrDefault(root.path("tenantId"), "unknown-tenant")));
    } catch (IOException exception) {
      throw new IllegalArgumentException("Invalid alert webhook payload", exception);
    }
  }

  private IncidentCorrelator.PrometheusAlert toAlert(JsonNode node, String tenantId) {
    Map<String, String> labels = objectToMap(node.path("labels"));
    Map<String, String> annotations = objectToMap(node.path("annotations"));

    Instant firedAt = node.hasNonNull("firedAt") ? Instant.parse(node.path("firedAt").asText()) : clock.instant();

    String alertName = textOrDefault(node.path("alertName"), textOrDefault(node.path("labels").path("alertname"), "unknown-alert"));
    String affectedModule = textOrDefault(node.path("affectedModule"), labels.getOrDefault("service", ""));

    return new IncidentCorrelator.PrometheusAlert(
        tenantId,
      alertName,
      affectedModule,
        labels,
        annotations,
        firedAt);
  }

  private Map<String, String> objectToMap(JsonNode node) {
    if (!node.isObject()) {
      return Map.of();
    }

    Map<String, String> values = new LinkedHashMap<>();
    node.fields().forEachRemaining(entry -> values.put(entry.getKey(), textOrDefault(entry.getValue(), "")));
    return Map.copyOf(values);
  }

  private String textOrDefault(JsonNode node, String fallback) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    String value = node.asText();
    return value.isBlank() ? fallback : value;
  }

  public interface WebhookMetrics {
    void recordAlertsReceived(int count);

    void recordIncidentsCreated(int count);
  }

  public record AlertHandlingResult(
      int alertCount,
      int createdIncidentCount,
      List<IncidentCorrelator.CorrelationResult> results) {

    public AlertHandlingResult {
      alertCount = Math.max(0, alertCount);
      createdIncidentCount = Math.max(0, createdIncidentCount);
      results = results == null ? List.of() : List.copyOf(results);
    }
  }
}
