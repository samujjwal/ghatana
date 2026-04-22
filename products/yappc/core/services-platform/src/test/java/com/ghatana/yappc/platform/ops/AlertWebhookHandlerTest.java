package com.ghatana.yappc.platform.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("AlertWebhookHandler Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AlertWebhookHandlerTest extends EventloopTestBase {

  @Mock private IncidentCorrelator incidentCorrelator;

  @Test
  @DisplayName("handle ingests alert batches and records metrics [GH-90000]")
  void handleIngestsAlertBatchesAndRecordsMetrics() { // GH-90000
    AtomicInteger alertsReceived = new AtomicInteger(); // GH-90000
    AtomicInteger incidentsCreated = new AtomicInteger(); // GH-90000
    when(incidentCorrelator.correlateAlert(org.mockito.ArgumentMatchers.any())) // GH-90000
        .thenReturn(Promise.of(new IncidentCorrelator.CorrelationResult("incident-1", true, "platform", "created"))); // GH-90000

    AlertWebhookHandler handler =
        new AlertWebhookHandler( // GH-90000
            incidentCorrelator,
            new AlertWebhookHandler.WebhookMetrics() { // GH-90000
              @Override
              public void recordAlertsReceived(int count) { // GH-90000
                alertsReceived.set(count); // GH-90000
              }

              @Override
              public void recordIncidentsCreated(int count) { // GH-90000
                incidentsCreated.set(count); // GH-90000
              }
            },
            new ObjectMapper(), // GH-90000
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z [GH-90000]"), ZoneOffset.UTC));

    AlertWebhookHandler.AlertHandlingResult result =
        runPromise( // GH-90000
            () -> // GH-90000
                handler.handle( // GH-90000
                    """
                    {
                      "tenantId": "tenant-a",
                      "alerts": [
                        {
                          "alertName": "HighErrorRate",
                          "affectedModule": "deploy-service",
                          "labels": {"severity": "critical", "service": "deploy-service", "environment": "prod"},
                          "annotations": {"summary": "Error rate spike"}
                        },
                        {
                          "alertName": "LatencySpike",
                          "affectedModule": "deploy-service",
                          "labels": {"severity": "warning", "service": "deploy-service", "environment": "prod"},
                          "annotations": {"summary": "Latency above SLO"}
                        }
                      ]
                    }
                    """));

    assertThat(result.alertCount()).isEqualTo(2); // GH-90000
    assertThat(result.createdIncidentCount()).isEqualTo(2); // GH-90000
    assertThat(result.results()).hasSize(2); // GH-90000
    assertThat(alertsReceived.get()).isEqualTo(2); // GH-90000
    assertThat(incidentsCreated.get()).isEqualTo(2); // GH-90000
  }

  @Test
  @DisplayName("handle rejects malformed webhook payloads [GH-90000]")
  void handleRejectsMalformedWebhookPayloads() { // GH-90000
    AlertWebhookHandler handler =
        new AlertWebhookHandler( // GH-90000
            incidentCorrelator,
            new AlertWebhookHandler.WebhookMetrics() { // GH-90000
              @Override
              public void recordAlertsReceived(int count) {} // GH-90000

              @Override
              public void recordIncidentsCreated(int count) {} // GH-90000
            });

    assertThatThrownBy(() -> runPromise(() -> handler.handle("not-json [GH-90000]")))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("Invalid alert webhook payload [GH-90000]");
  }
}
