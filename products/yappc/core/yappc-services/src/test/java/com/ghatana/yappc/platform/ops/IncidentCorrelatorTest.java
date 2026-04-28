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

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("IncidentCorrelator Tests")
class IncidentCorrelatorTest extends EventloopTestBase {

  @Mock private YAPPCAIService aiService;

  @Test
  @DisplayName("correlateAlert groups related alerts into an open incident")
  void correlateAlertGroupsRelatedAlertsIntoAnOpenIncident() { // GH-90000
    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    store.openIncidents.add( // GH-90000
        new IncidentCorrelator.IncidentRecord( // GH-90000
            "inc-1",
            "tenant-a",
            "HighErrorRate",
            "HIGH",
            "OPEN",
            "payments",
            "production",
            "payments-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "LatencySpike", "payments", Map.of("service", "payments"), Map.of())), // GH-90000
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator = correlator(store, (module, tenantId) -> Promise.of(Optional.of("ignored")));

    IncidentCorrelator.CorrelationResult result =
        runPromise( // GH-90000
            () -> // GH-90000
                correlator.correlateAlert( // GH-90000
                    alert( // GH-90000
                        "tenant-a",
                        "QueueDepthHigh",
                        "payments",
                        Map.of("service", "payments", "environment", "production"), // GH-90000
                        Map.of()))); // GH-90000

    assertThat(result.createdNewIncident()).isFalse(); // GH-90000
    assertThat(result.incidentId()).isEqualTo("inc-1");
    assertThat(store.addedToIncidentIds).containsExactly("inc-1");
    verifyNoInteractions(aiService); // GH-90000
  }

  @Test
  @DisplayName("correlateAlert groups incidents when the alert title already matches")
  void correlateAlertGroupsIncidentsWhenAlertTitleMatches() { // GH-90000
    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    store.openIncidents.add( // GH-90000
        new IncidentCorrelator.IncidentRecord( // GH-90000
            "inc-2",
            "tenant-a",
            "CpuSaturation",
            "HIGH",
            "OPEN",
            "compute",
            "production",
            "compute-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "OlderAlert", "storage", Map.of("service", "storage"), Map.of())), // GH-90000
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator = correlator(store, (module, tenantId) -> Promise.of(Optional.of("ignored")));

    IncidentCorrelator.CorrelationResult result =
        runPromise( // GH-90000
            () -> // GH-90000
                correlator.correlateAlert( // GH-90000
                    alert("tenant-a", "CpuSaturation", "api", Map.of("environment", "production"), Map.of()))); // GH-90000

    assertThat(result.createdNewIncident()).isFalse(); // GH-90000
    assertThat(result.incidentId()).isEqualTo("inc-2");
  }

  @Test
  @DisplayName("correlateAlert groups incidents when module matches but signatures do not")
  void correlateAlertGroupsIncidentsWhenModuleMatchesButSignaturesDoNot() { // GH-90000
    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    store.openIncidents.add( // GH-90000
        new IncidentCorrelator.IncidentRecord( // GH-90000
            "inc-3",
            "tenant-a",
            "StoragePressure",
            "MEDIUM",
            "OPEN",
            "billing",
            "production",
            "billing-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "OlderAlert", "storage", Map.of("service", "storage"), Map.of())), // GH-90000
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator = correlator(store, (module, tenantId) -> Promise.of(Optional.of("ignored")));

    IncidentCorrelator.CorrelationResult result =
        runPromise( // GH-90000
            () -> // GH-90000
                correlator.correlateAlert( // GH-90000
                    alert( // GH-90000
                        "tenant-a",
                        "QueueBacklog",
                        "billing",
                        Map.of("service", "queue", "environment", "production"), // GH-90000
                        Map.of()))); // GH-90000

    assertThat(result.createdNewIncident()).isFalse(); // GH-90000
    assertThat(result.incidentId()).isEqualTo("inc-3");
  }

  @Test
  @DisplayName("correlateAlert creates a new incident with routed owner and AI summary")
  void correlateAlertCreatesNewIncidentWithRoutedOwnerAndAiSummary() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn( // GH-90000
            Promise.of( // GH-90000
                "{\"rootCause\":\"Database saturation\",\"runbookSuggestion\":\"Scale the writer pool and inspect slow queries\"}"));

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("database-team")));

    IncidentCorrelator.CorrelationResult result =
        runPromise( // GH-90000
            () -> // GH-90000
                correlator.correlateAlert( // GH-90000
                    alert( // GH-90000
                        "tenant-a",
                        "DatabaseConnectionsHigh",
                        "database",
                        Map.of("severity", "critical", "environment", "production"), // GH-90000
                        Map.of("pager", "true")))); // GH-90000

    assertThat(result.createdNewIncident()).isTrue(); // GH-90000
    assertThat(result.incidentId()).isEqualTo("inc-created-1");
    assertThat(result.owningTeam()).isEqualTo("database-team");
    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("CRITICAL");
      assertThat(incident.owningTeam()).isEqualTo("database-team");
      assertThat(incident.aiRootCause()).isEqualTo("Database saturation");
      assertThat(incident.runbookSuggestion()).contains("Scale the writer pool");
    });
  }

  @Test
  @DisplayName("correlateAlert defaults owner and summary when AI response is blank")
  void correlateAlertDefaultsOwnerAndSummaryWhenAiResponseIsBlank() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(" "));

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.empty())); // GH-90000

    IncidentCorrelator.CorrelationResult result =
        runPromise( // GH-90000
            () -> // GH-90000
                correlator.correlateAlert( // GH-90000
                    alert( // GH-90000
                        "tenant-a",
                        "WorkerFailures",
                        "worker",
                        Map.of("severity", "warning"), // GH-90000
                        Map.of()))); // GH-90000

    assertThat(result.owningTeam()).isEqualTo("platform-team");
    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("MEDIUM");
      assertThat(incident.aiRootCause()).contains("worker", "WorkerFailures"); // GH-90000
      assertThat(incident.runbookSuggestion()).contains("AI response was empty");
    });
  }

  @Test
  @DisplayName("correlateAlert defaults blank owner and handles null AI response")
  void correlateAlertDefaultsBlankOwnerAndHandlesNullAiResponse() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(null)); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    store.openIncidents.add( // GH-90000
        new IncidentCorrelator.IncidentRecord( // GH-90000
            "inc-4",
            "tenant-a",
            "UnrelatedOpenIncident",
            "LOW",
            "OPEN",
            "other-module",
            "production",
            "other-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "OtherAlert", "other-module", Map.of("service", "other"), Map.of())), // GH-90000
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("   ")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "InfoAlert",
                    "",
                    Map.of("severity", "info", "environment", "production"), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.owningTeam()).isEqualTo("platform-team");
      assertThat(incident.severity()).isEqualTo("LOW");
      assertThat(incident.aiRootCause()).contains("unknown-module", "InfoAlert"); // GH-90000
      assertThat(incident.runbookSuggestion()).contains("AI response was empty", "InfoAlert"); // GH-90000
    });
  }

  @Test
  @DisplayName("correlateAlert falls back to plain text AI summary and annotation severity")
  void correlateAlertFallsBackToPlainTextAiSummaryAndAnnotationSeverity() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("Investigate noisy neighbor impact"));

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("platform-observability")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "LatencySpike",
                    "",
                    Map.of(), // GH-90000
                    Map.of("pager", "true")))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("HIGH");
      assertThat(incident.aiRootCause()).isEqualTo("Investigate noisy neighbor impact");
      assertThat(incident.runbookSuggestion()).contains("LatencySpike");
    });
  }

  @Test
  @DisplayName("correlateAlert falls back missing AI object fields and medium severity label")
  void correlateAlertFallsBackMissingAiObjectFieldsAndMediumSeverityLabel() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"rootCause\":\"\",\"runbookSuggestion\":\"\"}")); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "CacheMissRate",
                    "cache",
                    Map.of("severity", "medium"), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("MEDIUM");
      assertThat(incident.aiRootCause()).contains("cache", "CacheMissRate"); // GH-90000
      assertThat(incident.runbookSuggestion()).contains("cache");
    });
  }

  @Test
  @DisplayName("correlateAlert handles non object AI payloads and high severity label")
  void correlateAlertHandlesNonObjectAiPayloadsAndHighSeverityLabel() { // GH-90000
    when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of("[\"inspect shards\"]")); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("storage-team")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "DiskPressure",
                    "storage",
                    Map.of("severity", "high"), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("HIGH");
      assertThat(incident.aiRootCause()).isEqualTo("[\"inspect shards\"]"); // GH-90000
      assertThat(incident.runbookSuggestion()).contains("storage");
    });
  }

  @Test
  @DisplayName("correlateAlert creates a new incident when module is nonblank but does not match")
  void correlateAlertCreatesNewIncidentWhenModuleIsNonblankButDoesNotMatch() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"rootCause\":\"API saturation\",\"runbookSuggestion\":\"Inspect request fanout\"}")); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    store.openIncidents.add( // GH-90000
        new IncidentCorrelator.IncidentRecord( // GH-90000
            "inc-5",
            "tenant-a",
            "WorkerQueueLag",
            "MEDIUM",
            "OPEN",
            "worker",
            "production",
            "worker-team",
            Instant.parse("2026-04-06T12:00:00Z"),
            List.of(alert("tenant-a", "WorkerQueueLag", "worker", Map.of("service", "worker"), Map.of())), // GH-90000
            "Cause",
            "Runbook"));

    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("api-team")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "ApiQueueLag",
                    "api",
                    Map.of("environment", "production", "service", "api"), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.owningTeam()).isEqualTo("api-team");
      assertThat(incident.aiRootCause()).isEqualTo("API saturation");
    });
  }

  @Test
  @DisplayName("correlateAlert supports low severity labels and default medium fallback")
  void correlateAlertSupportsLowSeverityLabelsAndDefaultMediumFallback() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"rootCause\":\"Low priority signal\",\"runbookSuggestion\":\"Observe only\"}")) // GH-90000
        .thenReturn(Promise.of("{\"rootCause\":\"Background noise\",\"runbookSuggestion\":\"Monitor trend\"}")); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "LowDiskForecast",
                    "storage",
                    Map.of("severity", "low"), // GH-90000
                    Map.of()))); // GH-90000

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "BackgroundNoise",
                    "ops",
                    Map.of(), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).hasSize(2); // GH-90000
    assertThat(store.createdIncidents.get(0).severity()).isEqualTo("LOW");
    assertThat(store.createdIncidents.get(1).severity()).isEqualTo("MEDIUM");
  }

  @Test
  @DisplayName("correlateAlert falls back to medium for unknown severity labels")
  void correlateAlertFallsBackToMediumForUnknownSeverityLabels() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"rootCause\":\"Unknown classification\",\"runbookSuggestion\":\"Observe\"}")); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "CustomSeveritySignal",
                    "ops",
                    Map.of("severity", "trace"), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("MEDIUM");
      assertThat(incident.aiRootCause()).isEqualTo("Unknown classification");
    });
  }

  @Test
  @DisplayName("correlateAlert supports uppercase info severity labels")
  void correlateAlertSupportsUppercaseInfoSeverityLabels() { // GH-90000
    when(aiService.reason(anyString(), anyMap())) // GH-90000
        .thenReturn(Promise.of("{\"rootCause\":\"Informational signal\",\"runbookSuggestion\":\"No action\"}")); // GH-90000

    RecordingIncidentStore store = new RecordingIncidentStore(); // GH-90000
    IncidentCorrelator correlator =
        correlator(store, (module, tenantId) -> Promise.of(Optional.of("ops-team")));

    runPromise( // GH-90000
        () -> // GH-90000
            correlator.correlateAlert( // GH-90000
                alert( // GH-90000
                    "tenant-a",
                    "InfoOnlySignal",
                    "ops",
                    Map.of("severity", "INFO"), // GH-90000
                    Map.of()))); // GH-90000

    assertThat(store.createdIncidents).singleElement().satisfies(incident -> { // GH-90000
      assertThat(incident.severity()).isEqualTo("LOW");
      assertThat(incident.aiRootCause()).isEqualTo("Informational signal");
    });
  }

  @Test
  @DisplayName("incident records normalize null values")
  void incidentRecordsNormalizeNullValues() { // GH-90000
    IncidentCorrelator.PrometheusAlert alert =
        new IncidentCorrelator.PrometheusAlert(null, null, null, null, null, null); // GH-90000
    IncidentCorrelator.IncidentRecord incident =
        new IncidentCorrelator.IncidentRecord(null, null, null, null, null, null, null, null, null, null, null, null); // GH-90000
    IncidentCorrelator.CorrelationResult result =
        new IncidentCorrelator.CorrelationResult(null, true, null, null); // GH-90000

    assertThat(alert.tenantId()).isEqualTo("unknown-tenant");
    assertThat(alert.alertName()).isEqualTo("unknown-alert");
    assertThat(alert.environment()).isEqualTo("production");

    assertThat(incident.incidentId()).isEqualTo("pending-incident");
    assertThat(incident.severity()).isEqualTo("MEDIUM");
    assertThat(incident.status()).isEqualTo("OPEN");
    assertThat(incident.owningTeam()).isEqualTo("platform-team");
    assertThat(incident.alerts()).isEmpty(); // GH-90000

    assertThat(result.incidentId()).isEqualTo("unknown-incident");
    assertThat(result.owningTeam()).isEqualTo("platform-team");
    assertThat(result.summary()).isEmpty(); // GH-90000
  }

  private IncidentCorrelator correlator( // GH-90000
      RecordingIncidentStore store, IncidentCorrelator.OwnershipResolver ownershipResolver) {
    return new IncidentCorrelator(aiService, store, ownershipResolver); // GH-90000
  }

  private IncidentCorrelator.PrometheusAlert alert( // GH-90000
      String tenantId,
      String name,
      String module,
      Map<String, String> labels,
      Map<String, String> annotations) {
    return new IncidentCorrelator.PrometheusAlert( // GH-90000
        tenantId,
        name,
        module,
        labels,
        annotations,
        Instant.parse("2026-04-06T12:15:00Z"));
  }

  private static final class RecordingIncidentStore implements IncidentCorrelator.IncidentStore {
    private final List<IncidentCorrelator.IncidentRecord> openIncidents = new ArrayList<>(); // GH-90000
    private final List<String> addedToIncidentIds = new ArrayList<>(); // GH-90000
    private final List<IncidentCorrelator.IncidentRecord> createdIncidents = new ArrayList<>(); // GH-90000

    @Override
    public Promise<List<IncidentCorrelator.IncidentRecord>> findRecentOpen( // GH-90000
        String tenantId, java.time.Duration correlationWindow, Instant asOf) {
      return Promise.of(openIncidents); // GH-90000
    }

    @Override
    public Promise<Void> addAlert(String incidentId, IncidentCorrelator.PrometheusAlert alert) { // GH-90000
      addedToIncidentIds.add(incidentId); // GH-90000
      return Promise.complete(); // GH-90000
    }

    @Override
    public Promise<String> create(IncidentCorrelator.IncidentRecord incident) { // GH-90000
      createdIncidents.add(incident); // GH-90000
      return Promise.of("inc-created-" + createdIncidents.size()); // GH-90000
    }
  }
}
