package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose HTTP integration tests for live alerts routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Alerts Endpoints")
class DataCloudHttpServerAlertsTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
        when(mockClient.appendEvent(anyString(), any())).thenReturn(Promise.of(DataCloudClient.Offset.of(1))); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    @Test
    @DisplayName("lists alerts from tenant-scoped alert entities")
    void listAlertsReturnsCanonicalEnvelope() throws Exception { // GH-90000
        when(mockClient.query(eq(TestConstants.TENANT_DEFAULT), eq("dc_alerts"), any()))
            .thenReturn(Promise.of(List.of( // GH-90000
                DataCloudClient.Entity.of( // GH-90000
                    "alert-1",
                    "dc_alerts",
                    Map.of( // GH-90000
                        "title", "Kafka lag spike",
                        "description", "Consumer lag exceeded threshold",
                        "severity", "critical",
                        "status", "active",
                        "source", "kafka",
                        "createdAt", "2026-04-18T10:00:00Z"
                    )
                )
            )));

        startServer(); // GH-90000

        HttpResponse<String> response = get("/api/v1/alerts?status=active", withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

        assertStatusCode(response, TestConstants.HTTP_OK); // GH-90000
        Map<String, Object> body = parseJsonResponse(response); // GH-90000
        assertThat(body.get("tenantId")).isEqualTo(TestConstants.TENANT_DEFAULT);
        assertThat(body.get("count")).isEqualTo(1);
        assertThat(response.body()).contains("Kafka lag spike");
    }

    @Test
    @DisplayName("acknowledges alerts through the live mutation route")
    void acknowledgeAlertReturnsUpdatedAlert() throws Exception { // GH-90000
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
            "alert-1",
            "dc_alerts",
            Map.of( // GH-90000
                "title", "Kafka lag spike",
                "description", "Consumer lag exceeded threshold",
                "severity", "critical",
                "status", "active",
                "source", "kafka",
                "createdAt", "2026-04-18T10:00:00Z"
            )
        );
        when(mockClient.findById(eq(TestConstants.TENANT_DEFAULT), eq("dc_alerts"), eq("alert-1")))
            .thenReturn(Promise.of(Optional.of(entity))); // GH-90000
        when(mockClient.save(eq(TestConstants.TENANT_DEFAULT), eq("dc_alerts"), any()))
            .thenReturn(Promise.of(entity)); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> response = postJson( // GH-90000
            "/api/v1/alerts/alert-1/acknowledge",
            Map.of(), // GH-90000
            withTenant(TestConstants.TENANT_DEFAULT) // GH-90000
        );

        assertStatusCode(response, TestConstants.HTTP_OK); // GH-90000
        assertThat(response.body()).contains("acknowledged");
        assertThat(response.body()).contains("alert-1");
    }

    @Test
    @DisplayName("derives correlated alert groups from active alerts")
    void listAlertGroupsReturnsDerivedGroups() throws Exception { // GH-90000
        when(mockClient.query(eq(TestConstants.TENANT_DEFAULT), eq("dc_alerts"), any()))
            .thenReturn(Promise.of(List.of( // GH-90000
                DataCloudClient.Entity.of( // GH-90000
                    "alert-1",
                    "dc_alerts",
                    Map.of( // GH-90000
                        "title", "Kafka lag spike",
                        "description", "Consumer lag exceeded threshold",
                        "severity", "critical",
                        "status", "active",
                        "source", "kafka",
                        "createdAt", "2026-04-18T10:00:00Z"
                    )
                ),
                DataCloudClient.Entity.of( // GH-90000
                    "alert-2",
                    "dc_alerts",
                    Map.of( // GH-90000
                        "title", "Kafka rebalance storm",
                        "description", "Repeated partition movement detected",
                        "severity", "warning",
                        "status", "active",
                        "source", "kafka",
                        "createdAt", "2026-04-18T10:01:00Z"
                    )
                )
            )));

        startServer(); // GH-90000

        HttpResponse<String> response = get("/api/v1/alerts/groups", withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

        assertStatusCode(response, TestConstants.HTTP_OK); // GH-90000
        assertThat(response.body()).contains("group-kafka");
        assertThat(response.body()).contains("Kafka degradation");
    }

    @Test
    @DisplayName("creates and lists alert rules")
    void createAndListAlertRules() throws Exception { // GH-90000
        DataCloudClient.Entity createdRule = DataCloudClient.Entity.of( // GH-90000
            "rule-1",
            "dc_alert_rules",
            Map.of( // GH-90000
                "name", "Kafka lag",
                "enabled", true,
                "severity", "critical",
                "conditionType", "threshold",
                "metric", "queue_depth",
                "operator", "gt",
                "threshold", 100,
                "duration", 10,
                "channels", List.of("slack")
            )
        );
        when(mockClient.save(eq(TestConstants.TENANT_DEFAULT), eq("dc_alert_rules"), any()))
            .thenReturn(Promise.of(createdRule)); // GH-90000
        when(mockClient.query(eq(TestConstants.TENANT_DEFAULT), eq("dc_alert_rules"), any()))
            .thenReturn(Promise.of(List.of(createdRule))); // GH-90000

        startServer(); // GH-90000

        HttpResponse<String> createResponse = postJson( // GH-90000
            "/api/v1/alerts/rules",
            Map.of( // GH-90000
                "name", "Kafka lag",
                "enabled", true,
                "severity", "critical",
                "conditionType", "threshold",
                "metric", "queue_depth",
                "operator", "gt",
                "threshold", 100,
                "duration", 10,
                "channels", List.of("slack")
            ),
            withTenant(TestConstants.TENANT_DEFAULT) // GH-90000
        );
        HttpResponse<String> listResponse = get("/api/v1/alerts/rules", withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

        assertStatusCode(createResponse, TestConstants.HTTP_CREATED); // GH-90000
        assertThat(createResponse.body()).contains("Kafka lag");
        assertStatusCode(listResponse, TestConstants.HTTP_OK); // GH-90000
        assertThat(listResponse.body()).contains("rule-1");
    }
}