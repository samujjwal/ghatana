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

@DisplayName("DatabaseHealthCheck")
@ExtendWith(MockitoExtension.class) 
class DatabaseHealthCheckTest extends EventloopTestBase {

    @Mock
    EntityManager entityManager;

    @Mock
    Query query;

    @Mock
    MetricsCollector metricsCollector;

    @Test
    @DisplayName("readiness returns true when query succeeds within threshold")
    void readinessReturnsTrueWhenHealthy() { 
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1)); 

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); 

        Boolean ready = runPromise(healthCheck::checkReadiness); 

        assertThat(ready).isTrue(); 
        assertThat(healthCheck.getLastStatus()).isEqualTo(DatabaseHealthCheck.HealthStatus.UP); 
        assertThat(healthCheck.getLastLatencyMs()).isGreaterThanOrEqualTo(0L); 
        verify(metricsCollector).recordTimer(anyString(), anyLong()); 
    }

    @Test
    @DisplayName("liveness stays true when database is degraded")
    void livenessReturnsTrueWhenDegraded() { 
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(query);
        when(query.getResultList()).thenAnswer(invocation -> { 
            Thread.sleep(10L); 
            return List.of(1); 
        });

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck( 
            entityManager,
            metricsCollector,
            DatabaseHealthCheck.DatabaseHealthCheckConfig.builder() 
                .degradedThresholdMs(1L) 
                .build() 
        );

        Boolean live = runPromise(healthCheck::checkLiveness); 

        assertThat(live).isTrue(); 
        assertThat(healthCheck.getLastStatus()).isEqualTo(DatabaseHealthCheck.HealthStatus.DEGRADED); 
    }

    @Test
    @DisplayName("readiness returns false when query fails")
    void readinessReturnsFalseWhenDown() { 
        when(entityManager.createNativeQuery("SELECT 1")).thenThrow(new IllegalStateException("db unavailable"));

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); 

        Boolean ready = runPromise(healthCheck::checkReadiness); 

        assertThat(ready).isFalse(); 
        assertThat(healthCheck.getLastStatus()).isEqualTo(DatabaseHealthCheck.HealthStatus.DOWN); 
    }

    @Test
    @DisplayName("health response includes database details")
    void healthResponseIncludesDatabaseDetails() { 
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1)); 

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); 

        DatabaseHealthCheck.HealthResponse response = runPromise(healthCheck::health); 

        assertThat(response.getStatus()).isEqualTo("UP");
        assertThat(response.getDetails()).containsKey("database");
        Object database = response.getDetails().get("database");
        assertThat(database).isInstanceOf(DatabaseHealthCheck.DatabaseHealthDetails.class); 
        DatabaseHealthCheck.DatabaseHealthDetails details =
            (DatabaseHealthCheck.DatabaseHealthDetails) database; 
        assertThat(details.isConnected()).isTrue(); 
        assertThat(details.getPoolDetails()).containsEntry("pool_status", "active"); 
    }

    @Test
    @DisplayName("health details expose status flags and pool state")
    void healthDetailsExposeFlags() { 
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1)); 

        DatabaseHealthCheck healthCheck = new DatabaseHealthCheck(entityManager, metricsCollector); 

        Map<String, Object> details = runPromise(healthCheck::getHealthDetails); 

        assertThat(details).containsEntry("status", "UP"); 
        assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "database"); 
        assertThat(details.get("database")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) details.get("database");
        assertThat(database).containsEntry("connected", true); 
        assertThat(database).containsEntry("healthy", true); 
        assertThat(database).containsEntry("degraded", false); 
        assertThat(database).containsEntry("pool_status", "active"); 
    }
}
