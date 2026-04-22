package com.ghatana.datacloud.infrastructure.health;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DatabaseHealthCheck [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DatabaseHealthCheckTest extends EventloopTestBase {

    @Mock
    EntityManager entityManager;

    @Mock
    Query query;

    @Mock
    MetricsCollector metricsCollector;

    @Test
    @DisplayName("readiness returns true when query succeeds within threshold [GH-90000]")
    void readinessReturnsTrueWhenHealthy() { // GH-90000
        when(entityManager.createNativeQuery("SELECT 1 [GH-90000]")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1)); // GH-90000

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); // GH-90000

        Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

        assertThat(ready).isTrue(); // GH-90000
        assertThat(healthCheck.getLastStatus()).isEqualTo(DatabaseHealthCheck.HealthStatus.UP); // GH-90000
        assertThat(healthCheck.getLastLatencyMs()).isGreaterThanOrEqualTo(0L); // GH-90000
        verify(metricsCollector).recordTimer(anyString(), anyLong()); // GH-90000
    }

    @Test
    @DisplayName("liveness stays true when database is degraded [GH-90000]")
    void livenessReturnsTrueWhenDegraded() { // GH-90000
        when(entityManager.createNativeQuery("SELECT 1 [GH-90000]")).thenReturn(query);
        when(query.getResultList()).thenAnswer(invocation -> { // GH-90000
            Thread.sleep(10L); // GH-90000
            return List.of(1); // GH-90000
        });

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck( // GH-90000
            entityManager,
            metricsCollector,
            DatabaseHealthCheck.DatabaseHealthCheckConfig.builder() // GH-90000
                .degradedThresholdMs(1L) // GH-90000
                .build() // GH-90000
        );

        Boolean live = runPromise(healthCheck::checkLiveness); // GH-90000

        assertThat(live).isTrue(); // GH-90000
        assertThat(healthCheck.getLastStatus()).isEqualTo(DatabaseHealthCheck.HealthStatus.DEGRADED); // GH-90000
    }

    @Test
    @DisplayName("readiness returns false when query fails [GH-90000]")
    void readinessReturnsFalseWhenDown() { // GH-90000
        when(entityManager.createNativeQuery("SELECT 1 [GH-90000]")).thenThrow(new IllegalStateException("db unavailable [GH-90000]"));

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); // GH-90000

        Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

        assertThat(ready).isFalse(); // GH-90000
        assertThat(healthCheck.getLastStatus()).isEqualTo(DatabaseHealthCheck.HealthStatus.DOWN); // GH-90000
    }

    @Test
    @DisplayName("health response includes database details [GH-90000]")
    void healthResponseIncludesDatabaseDetails() { // GH-90000
        when(entityManager.createNativeQuery("SELECT 1 [GH-90000]")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1)); // GH-90000

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); // GH-90000

        DatabaseHealthCheck.HealthResponse response = runPromise(healthCheck::health); // GH-90000

        assertThat(response.getStatus()).isEqualTo("UP [GH-90000]");
        assertThat(response.getDetails()).containsKey("database [GH-90000]");
        Object database = response.getDetails().get("database [GH-90000]");
        assertThat(database).isInstanceOf(DatabaseHealthCheck.DatabaseHealthDetails.class); // GH-90000
        DatabaseHealthCheck.DatabaseHealthDetails details =
            (DatabaseHealthCheck.DatabaseHealthDetails) database; // GH-90000
        assertThat(details.isConnected()).isTrue(); // GH-90000
        assertThat(details.getPoolDetails()).containsEntry("pool_status", "active"); // GH-90000
    }

    @Test
    @DisplayName("health details expose status flags and pool state [GH-90000]")
    void healthDetailsExposeFlags() { // GH-90000
        when(entityManager.createNativeQuery("SELECT 1 [GH-90000]")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1)); // GH-90000

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); // GH-90000

        Map<String, Object> details = runPromise(healthCheck::getHealthDetails); // GH-90000

        assertThat(details).containsEntry("status", "UP"); // GH-90000
        assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "database"); // GH-90000
        assertThat(details.get("database [GH-90000]")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> database = (Map<String, Object>) details.get("database [GH-90000]");
        assertThat(database).containsEntry("connected", true); // GH-90000
        assertThat(database).containsEntry("healthy", true); // GH-90000
        assertThat(database).containsEntry("degraded", false); // GH-90000
        assertThat(database).containsEntry("pool_status", "active"); // GH-90000
    }
}
