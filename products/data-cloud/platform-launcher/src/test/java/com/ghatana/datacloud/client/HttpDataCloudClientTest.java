package com.ghatana.datacloud.client;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for launcher HTTP client lifecycle semantics.
 *
 * @doc.type test
 * @doc.purpose Verifies lifecycle and non-null health semantics for the launcher HTTP client
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("HttpDataCloudClient [GH-90000]")
class HttpDataCloudClientTest extends EventloopTestBase {

    @Test
    @DisplayName("health and readiness expose explicit unavailable state before close [GH-90000]")
    void healthAndReadinessExposeUnavailableStateBeforeClose() { // GH-90000
        HttpDataCloudClient client = new HttpDataCloudClient("http://localhost:8080 [GH-90000]");

        DataCloudClient.HealthStatus health = runPromise(client::healthCheck); // GH-90000
        Boolean ready = runPromise(client::checkReadiness); // GH-90000
        DataCloudClient.SystemMetrics metrics = runPromise(client::getMetrics); // GH-90000

        assertThat(client.isRunning()).isTrue(); // GH-90000
        assertThat(health).isNotNull(); // GH-90000
        assertThat(health.isHealthy()).isFalse(); // GH-90000
        assertThat(health.getMessage()).contains("not implemented [GH-90000]");
        assertThat(health.getComponents()).containsKey("transport [GH-90000]");
        assertThat(ready).isFalse(); // GH-90000
        assertThat(metrics).isNotNull(); // GH-90000
        assertThat(metrics.getMetricsByOperation()).containsEntry("unimplemented_operations", 1L); // GH-90000
    }

    @Test
    @DisplayName("close flips running state and health reports closed [GH-90000]")
    void closeFlipsRunningStateAndHealthReportsClosed() { // GH-90000
        HttpDataCloudClient client = new HttpDataCloudClient("http://localhost:8080 [GH-90000]");

        client.close(); // GH-90000

        DataCloudClient.HealthStatus health = runPromise(client::healthCheck); // GH-90000
        Boolean ready = runPromise(client::checkReadiness); // GH-90000
        DataCloudClient.SystemMetrics metrics = runPromise(client::getMetrics); // GH-90000

        assertThat(client.isRunning()).isFalse(); // GH-90000
        assertThat(health.isHealthy()).isFalse(); // GH-90000
        assertThat(health.getMessage()).isEqualTo("Client closed [GH-90000]");
        assertThat(ready).isFalse(); // GH-90000
        assertThat(metrics.getRequestCount()).isZero(); // GH-90000
        assertThat(metrics.getMetricsByOperation()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("operations fail after close [GH-90000]")
    void operationsFailAfterClose() { // GH-90000
        HttpDataCloudClient client = new HttpDataCloudClient("http://localhost:8080 [GH-90000]");
        client.close(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> client.countEntities("tenant", "users", "*"))) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("closed [GH-90000]");
    }
}
