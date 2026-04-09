package com.ghatana.yappc.platform.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentCorrelator Tests")
class IncidentCorrelatorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("correlateAlert groups related alerts into an open incident")
  void correlateAlertGroupsRelatedAlertsIntoAnOpenIncident() {
    RecordingIncidentStore store = new RecordingIncidentStore();
    store.openIncidents.add(
        new IncidentCorrelator.IncidentRecord(
            "inc-1",
            "tenant-a",
            "HighErrorRate",
            "HIGH",
            "OPEN",
            "payments",
            "production",
            "payments-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "LatencySpike", "payments", Map.of("service", "payments"), Map.of())),
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator = correlator(store, (module, tenantId) -> Promise.of(Optional.of("ignored")));

    IncidentCorrelator.CorrelationResult result =
        runPromise(
            () ->
                correlator.correlateAlert(
                    alert(
                        "tenant-a",
                        "QueueDepthHigh",
                        "payments",
                        Map.of("service", "payments", "environment", "production"),
                        Map.of())));

    assertThat(result.createdNewIncident()).isFalse();
    assertThat(result.incidentId()).isEqualTo("inc-1");
    assertThat(store.addedToIncidentIds).containsExactly("inc-1");
    verifyNoInteractions(aiService);
  }

  @Test
  @DisplayName("correlateAlert groups incidents when the alert title already matches")
  void correlateAlertGroupsIncidentsWhenAlertTitleMatches() {
    RecordingIncidentStore store = new RecordingIncidentStore();
    store.openIncidents.add(
        new IncidentCorrelator.IncidentRecord(
            "inc-2",
            "tenant-a",
            "CpuSaturation",
            "HIGH",
            "OPEN",
            "compute",
            "production",
            "compute-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "OlderAlert", "storage", Map.of("service", "storage"), Map.of())),
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator = correlator(store, (module, tenantId) -> Promise.of(Optional.of("ignored")));

    IncidentCorrelator.CorrelationResult result =
        runPromise(
            () ->
                correlator.correlateAlert(
                    alert("tenant-a", "CpuSaturation", "api", Map.of("environment", "production"), Map.of())));

    assertThat(result.createdNewIncident()).isFalse();
    assertThat(result.incidentId()).isEqualTo("inc-2");
  }

  @Test
  @DisplayName("correlateAlert groups incidents when module matches but signatures do not")
  void correlateAlertGroupsIncidentsWhenModuleMatchesButSignaturesDoNot() {
    RecordingIncidentStore store = new RecordingIncidentStore();
    store.openIncidents.add(
        new IncidentCorrelator.IncidentRecord(
            "inc-3",
            "tenant-a",
            "StoragePressure",
            "MEDIUM",
            "OPEN",
            "billing",
            "production",
            "billing-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "OlderAlert", "storage", Map.of("service", "storage"), Map.of())),
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator = correlator(store, (module, tenantId) -> Promise.of(Optional.of("ignored")));

    IncidentCorrelator.CorrelationResult result =
        runPromise(
            () ->
                correlator.correlateAlert(
                    alert(
                        "tenant-a",
                        "QueueBacklog",
                        "billing",
                        Map.of("service", "queue", "environment", "production"),
                        Map.of())));

    assertThat(result.createdNewIncident()).isFalse();
    assertThat(result.incidentId()).isEqualTo("inc-3");
  }

  @Test
  @DisplayName("correlateAlert creates a new incident with routed owner and AI summary")
  void correlateAlertCreatesNewIncidentWithRoutedOwnerAndAiSummary() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(
            Promise.of(
                "{\"rootCause\":\"Database saturation\",\"runbookSuggestion\":\"Scale the writer pool and inspect slow queries\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("database-team")));

    IncidentCorrelator.CorrelationResult result =
        runPromise(
            () ->
                correlator.correlateAlert(
                    alert(
                        "tenant-a",
                        "DatabaseConnectionsHigh",
                        "database",
                        Map.of("severity", "critical", "environment", "production"),
                        Map.of("pager", "true"))));

    assertThat(result.createdNewIncident()).isTrue();
    assertThat(result.incidentId()).isEqualTo("inc-created-1");
    assertThat(result.owningTeam()).isEqualTo("database-team");
    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("CRITICAL");
      assertThat(incident.owningTeam()).isEqualTo("database-team");
      assertThat(incident.aiRootCause()).isEqualTo("Database saturation");
      assertThat(incident.runbookSuggestion()).contains("Scale the writer pool");
    });
  }

  @Test
  @DisplayName("correlateAlert defaults owner and summary when AI response is blank")
  void correlateAlertDefaultsOwnerAndSummaryWhenAiResponseIsBlank() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.empty()));

    IncidentCorrelator.CorrelationResult result =
        runPromise(
            () ->
                correlator.correlateAlert(
                    alert(
                        "tenant-a",
                        "WorkerFailures",
                        "worker",
                        Map.of("severity", "warning"),
                        Map.of())));

    assertThat(result.owningTeam()).isEqualTo("platform-team");
    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("MEDIUM");
      assertThat(incident.aiRootCause()).contains("worker", "WorkerFailures");
      assertThat(incident.runbookSuggestion()).contains("AI response was empty");
    });
  }

  @Test
  @DisplayName("correlateAlert defaults blank owner and handles null AI response")
  void correlateAlertDefaultsBlankOwnerAndHandlesNullAiResponse() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null));

    RecordingIncidentStore store = new RecordingIncidentStore();
    store.openIncidents.add(
        new IncidentCorrelator.IncidentRecord(
            "inc-4",
            "tenant-a",
            "UnrelatedOpenIncident",
            "LOW",
            "OPEN",
            "other-module",
            "production",
            "other-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "OtherAlert", "other-module", Map.of("service", "other"), Map.of())),
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("   ")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "InfoAlert",
                    "",
                    Map.of("severity", "info", "environment", "production"),
                    Map.of())));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.owningTeam()).isEqualTo("platform-team");
      assertThat(incident.severity()).isEqualTo("LOW");
      assertThat(incident.aiRootCause()).contains("unknown-module", "InfoAlert");
      assertThat(incident.runbookSuggestion()).contains("AI response was empty", "InfoAlert");
    });
  }

  @Test
  @DisplayName("correlateAlert falls back to plain text AI summary and annotation severity")
  void correlateAlertFallsBackToPlainTextAiSummaryAndAnnotationSeverity() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("Investigate noisy neighbor impact"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("platform-observability")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "LatencySpike",
                    "",
                    Map.of(),
                    Map.of("pager", "true"))));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("HIGH");
      assertThat(incident.aiRootCause()).isEqualTo("Investigate noisy neighbor impact");
      assertThat(incident.runbookSuggestion()).contains("LatencySpike");
    });
  }

  @Test
  @DisplayName("correlateAlert falls back missing AI object fields and medium severity label")
  void correlateAlertFallsBackMissingAiObjectFieldsAndMediumSeverityLabel() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"rootCause\":\"\",\"runbookSuggestion\":\"\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "CacheMissRate",
                    "cache",
                    Map.of("severity", "medium"),
                    Map.of())));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("MEDIUM");
      assertThat(incident.aiRootCause()).contains("cache", "CacheMissRate");
      assertThat(incident.runbookSuggestion()).contains("cache");
    });
  }

  @Test
  @DisplayName("correlateAlert handles non object AI payloads and high severity label")
  void correlateAlertHandlesNonObjectAiPayloadsAndHighSeverityLabel() {
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("[\"inspect shards\"]"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("storage-team")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "DiskPressure",
                    "storage",
                    Map.of("severity", "high"),
                    Map.of())));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("HIGH");
      assertThat(incident.aiRootCause()).isEqualTo("[\"inspect shards\"]");
      assertThat(incident.runbookSuggestion()).contains("storage");
    });
  }

  @Test
  @DisplayName("correlateAlert creates a new incident when module is nonblank but does not match")
  void correlateAlertCreatesNewIncidentWhenModuleIsNonblankButDoesNotMatch() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"rootCause\":\"API saturation\",\"runbookSuggestion\":\"Inspect request fanout\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    store.openIncidents.add(
        new IncidentCorrelator.IncidentRecord(
            "inc-5",
            "tenant-a",
            "WorkerQueueLag",
            "MEDIUM",
            "OPEN",
            "worker",
            "production",
            "worker-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "WorkerQueueLag", "worker", Map.of("service", "worker"), Map.of())),
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("api-team")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "ApiQueueLag",
                    "api",
                    Map.of("environment", "production", "service", "api"),
                    Map.of())));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.owningTeam()).isEqualTo("api-team");
      assertThat(incident.aiRootCause()).isEqualTo("API saturation");
    });
  }

  @Test
  @DisplayName("correlateAlert supports low severity labels and default medium fallback")
  void correlateAlertSupportsLowSeverityLabelsAndDefaultMediumFallback() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"rootCause\":\"Low priority signal\",\"runbookSuggestion\":\"Observe only\"}"))
        .thenReturn(Promise.of("{\"rootCause\":\"Background noise\",\"runbookSuggestion\":\"Monitor trend\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "LowDiskForecast",
                    "storage",
                    Map.of("severity", "low"),
                    Map.of())));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "BackgroundNoise",
                    "ops",
                    Map.of(),
                    Map.of())));

    assertThat(store.createdIncidents).hasSize(2);
    assertThat(store.createdIncidents.get(0).severity()).isEqualTo("LOW");
    assertThat(store.createdIncidents.get(1).severity()).isEqualTo("MEDIUM");
  }

  @Test
  @DisplayName("correlateAlert falls back to medium for unknown severity labels")
  void correlateAlertFallsBackToMediumForUnknownSeverityLabels() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"rootCause\":\"Unknown classification\",\"runbookSuggestion\":\"Observe\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "CustomSeveritySignal",
                    "ops",
                    Map.of("severity", "trace"),
                    Map.of())));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("MEDIUM");
      assertThat(incident.aiRootCause()).isEqualTo("Unknown classification");
    });
  }

  @Test
  @DisplayName("correlateAlert supports uppercase info severity labels")
  void correlateAlertSupportsUppercaseInfoSeverityLabels() {
    when(aiService.reason(anyString(), anyMap()))
        .thenReturn(Promise.of("{\"rootCause\":\"Informational signal\",\"runbookSuggestion\":\"No action\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore();
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise(
        () ->
            correlator.correlateAlert(
                alert(
                    "tenant-a",
                    "InfoOnlySignal",
                    "ops",
                    Map.of("severity", "INFO"),
                    Map.of())));

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> {
      assertThat(incident.severity()).isEqualTo("LOW");
      assertThat(incident.aiRootCause()).isEqualTo("Informational signal");
    });
  }

  @Test
  @DisplayName("incident records normalize null values")
  void incidentRecordsNormalizeNullValues() {
    IncidentCorrelator.PrometheusAlert alert =
        new IncidentCorrelator.PrometheusAlert(null, null, null, null, null, null);
    IncidentCorrelator.IncidentRecord incident =
        new IncidentCorrelator.IncidentRecord(null, null, null, null, null, null, null, null, null, null, null, null);
    IncidentCorrelator.CorrelationResult result =
        new IncidentCorrelator.CorrelationResult(null, true, null, null);

    assertThat(alert.tenantId()).isEqualTo("unknown-tenant");
    assertThat(alert.alertName()).isEqualTo("unknown-alert");
    assertThat(alert.environment()).isEqualTo("production");

    assertThat(incident.incidentId()).isEqualTo("pending-incident");
    assertThat(incident.severity()).isEqualTo("MEDIUM");
    assertThat(incident.status()).isEqualTo("OPEN");
    assertThat(incident.owningTeam()).isEqualTo("platform-team");
    assertThat(incident.alerts()).isEmpty();

    assertThat(result.incidentId()).isEqualTo("unknown-incident");
    assertThat(result.owningTeam()).isEqualTo("platform-team");
    assertThat(result.summary()).isEmpty();
  }

  private IncidentCorrelator correlator(
      RecordingIncidentStore store, IncidentCorrelator.OwnershipResolver ownershipResolver) {
    return new IncidentCorrelator(aiService, store, ownershipResolver);
  }

  private IncidentCorrelator.PrometheusAlert alert(
      String tenantId,
      String name,
      String module,
      Map<String, String> labels,
      Map<String, String> annotations) {
    return new IncidentCorrelator.PrometheusAlert(
        tenantId,
        name,
        module,
        labels,
        annotations,
        Instant.parse("2026-04-06T12:15:00Z"));
  }

  private static final class RecordingIncidentStore implements IncidentCorrelator.IncidentStore {
    private final List<IncidentCorrelator.IncidentRecord> openIncidents = new ArrayList<>();
    private final List<String> addedToIncidentIds = new ArrayList<>();
    private final List<IncidentCorrelator.IncidentRecord> createdIncidents = new ArrayList<>();

    @Override
    public Promise<List<IncidentCorrelator.IncidentRecord>> findRecentOpen(
        String tenantId, java.time.Duration correlationWindow, Instant asOf) {
      return Promise.of(openIncidents);
    }

    @Override
    public Promise<Void> addAlert(String incidentId, IncidentCorrelator.PrometheusAlert alert) {
      addedToIncidentIds.add(incidentId);
      return Promise.complete();
    }

    @Override
    public Promise<String> create(IncidentCorrelator.IncidentRecord incident) {
      createdIncidents.add(incident);
      return Promise.of("inc-created-" + createdIncidents.size());
    }
  }
}
