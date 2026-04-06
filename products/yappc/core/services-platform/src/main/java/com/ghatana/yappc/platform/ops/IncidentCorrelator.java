package com.ghatana.yappc.platform.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Groups correlated alerts into incidents, infers likely root cause, and routes the incident to the owning team.
 * @doc.layer product
 * @doc.pattern Correlator
 */
public final class IncidentCorrelator {

  private static final Duration DEFAULT_CORRELATION_WINDOW = Duration.ofMinutes(30);

  private final YAPPCAIService aiService;
  private final IncidentStore incidentStore;
  private final OwnershipResolver ownershipResolver;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public IncidentCorrelator(
      YAPPCAIService aiService, IncidentStore incidentStore, OwnershipResolver ownershipResolver) {
    this(aiService, incidentStore, ownershipResolver, new ObjectMapper(), Clock.systemUTC());
  }

  IncidentCorrelator(
      YAPPCAIService aiService,
      IncidentStore incidentStore,
      OwnershipResolver ownershipResolver,
      ObjectMapper objectMapper,
      Clock clock) {
    this.aiService = Objects.requireNonNull(aiService, "aiService");
    this.incidentStore = Objects.requireNonNull(incidentStore, "incidentStore");
    this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Promise<CorrelationResult> correlateAlert(PrometheusAlert alert) {
    Objects.requireNonNull(alert, "alert");
    return incidentStore
        .findRecentOpen(alert.tenantId(), DEFAULT_CORRELATION_WINDOW, alert.firedAt())
        .then(
            openIncidents -> {
              Optional<IncidentRecord> related = findRelatedIncident(alert, openIncidents);
              if (related.isPresent()) {
                IncidentRecord incident = related.get();
                return incidentStore
                    .addAlert(incident.incidentId(), alert)
                    .map(
                        ignored ->
                            new CorrelationResult(
                                incident.incidentId(),
                                false,
                                incident.owningTeam(),
                                "Grouped alert into existing incident."));
              }
              return createNewIncident(alert);
            });
  }

  private Promise<CorrelationResult> createNewIncident(PrometheusAlert alert) {
    return ownershipResolver
        .resolveOwner(alert.affectedModule(), alert.tenantId())
        .then(
            owner -> {
              String owningTeam = owner.filter(value -> !value.isBlank()).orElse("platform-team");
              return aiService
                  .reason(buildPrompt(alert), buildContext(alert, owningTeam))
                  .map(response -> aiSummary(response, alert))
                  .then(
                      summary -> {
                        IncidentRecord incident =
                            new IncidentRecord(
                                null,
                                alert.tenantId(),
                                alert.alertName(),
                                classifySeverity(alert),
                                "OPEN",
                                alert.affectedModule(),
                                alert.environment(),
                                owningTeam,
                                alert.firedAt(),
                                List.of(alert),
                                summary.rootCause(),
                                summary.runbookSuggestion());
                        return incidentStore
                            .create(incident)
                            .map(
                                incidentId ->
                                    new CorrelationResult(
                                        incidentId,
                                        true,
                                        owningTeam,
                                        "Created new incident for alert."));
                      });
            });
  }

  private Optional<IncidentRecord> findRelatedIncident(
      PrometheusAlert alert, List<IncidentRecord> openIncidents) {
    return openIncidents.stream()
        .filter(incident -> matchesEnvironment(alert, incident))
        .filter(incident -> sharesAlertSignature(alert, incident) || sharesModule(alert, incident))
        .findFirst();
  }

  private boolean matchesEnvironment(PrometheusAlert alert, IncidentRecord incident) {
    return Objects.equals(alert.environment(), incident.environment());
  }

  private boolean sharesModule(PrometheusAlert alert, IncidentRecord incident) {
    return !alert.affectedModule().isBlank()
        && alert.affectedModule().equalsIgnoreCase(incident.affectedModule());
  }

  private boolean sharesAlertSignature(PrometheusAlert alert, IncidentRecord incident) {
    if (alert.alertName().equalsIgnoreCase(incident.title())) {
      return true;
    }
    return incident.alerts().stream().anyMatch(existing -> sharedServiceLabel(alert, existing));
  }

  private boolean sharedServiceLabel(PrometheusAlert left, PrometheusAlert right) {
    String leftService = left.labels().getOrDefault("service", "");
    String rightService = right.labels().getOrDefault("service", "");
    return !leftService.isBlank() && leftService.equalsIgnoreCase(rightService);
  }

  private String buildPrompt(PrometheusAlert alert) {
    return "Infer the likely root cause for this production alert and return JSON with rootCause and runbookSuggestion.\n"
        + "Alert name: "
        + alert.alertName()
        + "\nModule: "
        + alert.affectedModule()
        + "\nLabels: "
        + alert.labels()
        + "\nAnnotations: "
        + alert.annotations();
  }

  private Map<String, Object> buildContext(PrometheusAlert alert, String owningTeam) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("tenantId", alert.tenantId());
    context.put("alertName", alert.alertName());
    context.put("module", alert.affectedModule());
    context.put("environment", alert.environment());
    context.put("owningTeam", owningTeam);
    context.put("severity", classifySeverity(alert));
    return context;
  }

  private AiSummary aiSummary(String response, PrometheusAlert alert) {
    if (response == null || response.isBlank()) {
      return fallbackSummary(alert, "AI response was empty");
    }

    try {
      JsonNode root = objectMapper.readTree(response);
      if (root.isObject()) {
        String rootCause = root.path("rootCause").asText();
        String runbookSuggestion = root.path("runbookSuggestion").asText();
        return new AiSummary(
            rootCause.isBlank() ? fallbackRootCause(alert) : rootCause,
            runbookSuggestion.isBlank() ? defaultRunbookSuggestion(alert) : runbookSuggestion);
      }
      return new AiSummary(response, defaultRunbookSuggestion(alert));
    } catch (IOException exception) {
      return new AiSummary(response, defaultRunbookSuggestion(alert));
    }
  }

  private AiSummary fallbackSummary(PrometheusAlert alert, String reason) {
    return new AiSummary(fallbackRootCause(alert), reason + ". " + defaultRunbookSuggestion(alert));
  }

  private String fallbackRootCause(PrometheusAlert alert) {
    return String.format(
        Locale.ROOT,
        "Likely impact in module %s for alert %s.",
        alert.affectedModule().isBlank() ? "unknown-module" : alert.affectedModule(),
        alert.alertName());
  }

  private String defaultRunbookSuggestion(PrometheusAlert alert) {
    return "Inspect recent deployments, logs, and traces for "
        + (alert.affectedModule().isBlank() ? alert.alertName() : alert.affectedModule())
        + ".";
  }

  private String classifySeverity(PrometheusAlert alert) {
    String severityLabel = alert.labels().getOrDefault("severity", "").trim();
    if (!severityLabel.isBlank()) {
      String normalized = severityLabel.toUpperCase(Locale.ROOT);
      if (normalized.equals("CRITICAL") || normalized.equals("HIGH")) {
        return normalized;
      }
      if (normalized.equals("MEDIUM") || normalized.equals("WARNING")) {
        return "MEDIUM";
      }
      if (normalized.equals("LOW") || normalized.equals("INFO")) {
        return "LOW";
      }
    }
    return alert.annotations().containsKey("pager") ? "HIGH" : "MEDIUM";
  }

  public interface IncidentStore {
    Promise<List<IncidentRecord>> findRecentOpen(String tenantId, Duration correlationWindow, Instant asOf);

    Promise<Void> addAlert(String incidentId, PrometheusAlert alert);

    Promise<String> create(IncidentRecord incident);
  }

  public interface OwnershipResolver {
    Promise<Optional<String>> resolveOwner(String affectedModule, String tenantId);
  }

  record AiSummary(String rootCause, String runbookSuggestion) {
    AiSummary {
      rootCause = Objects.requireNonNullElse(rootCause, "");
      runbookSuggestion = Objects.requireNonNullElse(runbookSuggestion, "");
    }
  }

  public record PrometheusAlert(
      String tenantId,
      String alertName,
      String affectedModule,
      Map<String, String> labels,
      Map<String, String> annotations,
      Instant firedAt) {

    public PrometheusAlert {
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      alertName = Objects.requireNonNullElse(alertName, "unknown-alert");
      affectedModule = Objects.requireNonNullElse(affectedModule, "");
      labels = labels == null ? Map.of() : Map.copyOf(labels);
      annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
      firedAt = firedAt == null ? Instant.now(Clock.systemUTC()) : firedAt;
    }

    public String environment() {
      return labels.getOrDefault("environment", "production");
    }
  }

  public record IncidentRecord(
      String incidentId,
      String tenantId,
      String title,
      String severity,
      String status,
      String affectedModule,
      String environment,
      String owningTeam,
      Instant openedAt,
      List<PrometheusAlert> alerts,
      String aiRootCause,
      String runbookSuggestion) {

    public IncidentRecord {
      incidentId = Objects.requireNonNullElse(incidentId, "pending-incident");
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      title = Objects.requireNonNullElse(title, "Untitled incident");
      severity = Objects.requireNonNullElse(severity, "MEDIUM");
      status = Objects.requireNonNullElse(status, "OPEN");
      affectedModule = Objects.requireNonNullElse(affectedModule, "");
      environment = Objects.requireNonNullElse(environment, "production");
      owningTeam = Objects.requireNonNullElse(owningTeam, "platform-team");
      openedAt = openedAt == null ? Instant.now(Clock.systemUTC()) : openedAt;
      alerts = alerts == null ? List.of() : List.copyOf(alerts);
      aiRootCause = Objects.requireNonNullElse(aiRootCause, "");
      runbookSuggestion = Objects.requireNonNullElse(runbookSuggestion, "");
    }
  }

  public record CorrelationResult(
      String incidentId, boolean createdNewIncident, String owningTeam, String summary) {

    public CorrelationResult {
      incidentId = Objects.requireNonNullElse(incidentId, "unknown-incident");
      owningTeam = Objects.requireNonNullElse(owningTeam, "platform-team");
      summary = Objects.requireNonNullElse(summary, "");
    }
  }
}