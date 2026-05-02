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

@DisplayName("AlertWebhookHandler Tests")
@ExtendWith(MockitoExtension.class) 
class AlertWebhookHandlerTest extends EventloopTestBase {

  @Mock private IncidentCorrelator incidentCorrelator;

  @Test
  @DisplayName("handle ingests alert batches and records metrics")
  void handleIngestsAlertBatchesAndRecordsMetrics() { 
    AtomicInteger alertsReceived = new AtomicInteger(); 
    AtomicInteger incidentsCreated = new AtomicInteger(); 
    when(incidentCorrelator.correlateAlert(org.mockito.ArgumentMatchers.any())) 
        .thenReturn(Promise.of(new IncidentCorrelator.CorrelationResult("incident-1", true, "platform", "created"))); 

    AlertWebhookHandler handler =
        new AlertWebhookHandler( 
            incidentCorrelator,
            new AlertWebhookHandler.WebhookMetrics() { 
              @Override
              public void recordAlertsReceived(int count) { 
                alertsReceived.set(count); 
              }

              @Override
              public void recordIncidentsCreated(int count) { 
                incidentsCreated.set(count); 
              }
            },
            new ObjectMapper(), 
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC));

    AlertWebhookHandler.AlertHandlingResult result =
        runPromise( 
            () -> 
                handler.handle( 
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

    assertThat(result.alertCount()).isEqualTo(2); 
    assertThat(result.createdIncidentCount()).isEqualTo(2); 
    assertThat(result.results()).hasSize(2); 
    assertThat(alertsReceived.get()).isEqualTo(2); 
    assertThat(incidentsCreated.get()).isEqualTo(2); 
  }

  @Test
  @DisplayName("handle rejects malformed webhook payloads")
  void handleRejectsMalformedWebhookPayloads() { 
    AlertWebhookHandler handler =
        new AlertWebhookHandler( 
            incidentCorrelator,
            new AlertWebhookHandler.WebhookMetrics() { 
              @Override
              public void recordAlertsReceived(int count) {} 

              @Override
              public void recordIncidentsCreated(int count) {} 
            });

    assertThatThrownBy(() -> runPromise(() -> handler.handle("not-json")))
        .isInstanceOf(IllegalArgumentException.class) 
        .hasMessageContaining("Invalid alert webhook payload");
  }
}
