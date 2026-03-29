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
@DisplayName("HttpDataCloudClient")
class HttpDataCloudClientTest extends EventloopTestBase {

    @Test
    @DisplayName("health and readiness expose explicit unavailable state before close")
    void healthAndReadinessExposeUnavailableStateBeforeClose() {
        HttpDataCloudClient client = new HttpDataCloudClient("http://localhost:8080");

        DataCloudClient.HealthStatus health = runPromise(client::healthCheck);
        Boolean ready = runPromise(client::checkReadiness);
        DataCloudClient.SystemMetrics metrics = runPromise(client::getMetrics);

        assertThat(client.isRunning()).isTrue();
        assertThat(health).isNotNull();
        assertThat(health.isHealthy()).isFalse();
        assertThat(health.getMessage()).contains("not implemented");
        assertThat(health.getComponents()).containsKey("transport");
        assertThat(ready).isFalse();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getMetricsByOperation()).containsEntry("unimplemented_operations", 1L);
    }

    @Test
    @DisplayName("close flips running state and health reports closed")
    void closeFlipsRunningStateAndHealthReportsClosed() {
        HttpDataCloudClient client = new HttpDataCloudClient("http://localhost:8080");

        client.close();

        DataCloudClient.HealthStatus health = runPromise(client::healthCheck);
        Boolean ready = runPromise(client::checkReadiness);
        DataCloudClient.SystemMetrics metrics = runPromise(client::getMetrics);

        assertThat(client.isRunning()).isFalse();
        assertThat(health.isHealthy()).isFalse();
        assertThat(health.getMessage()).isEqualTo("Client closed");
        assertThat(ready).isFalse();
        assertThat(metrics.getRequestCount()).isZero();
        assertThat(metrics.getMetricsByOperation()).isEmpty();
    }

    @Test
    @DisplayName("operations fail after close")
    void operationsFailAfterClose() {
        HttpDataCloudClient client = new HttpDataCloudClient("http://localhost:8080");
        client.close();

        assertThatThrownBy(() -> runPromise(() -> client.countEntities("tenant", "users", "*")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
    }
}