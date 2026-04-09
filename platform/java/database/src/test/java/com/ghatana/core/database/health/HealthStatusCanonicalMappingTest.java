package com.ghatana.core.database.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Database HealthStatus canonical mapping")
class HealthStatusCanonicalMappingTest {

    @Test
    @DisplayName("maps database status to canonical platform health")
    void shouldMapDatabaseStatusToPlatformHealth() {
        SQLException failure = new SQLException("Connection refused");
        HealthDetails details = HealthDetails.builder()
            .detail("database", "postgres")
            .detail("connectionsActive", 4)
            .build();
        HealthStatus databaseStatus = HealthStatus.unhealthy(
            "Database unavailable",
            Duration.ofSeconds(5),
            failure);

        com.ghatana.platform.health.HealthStatus platformStatus = databaseStatus.toPlatformHealthStatus();

        assertThat(platformStatus.getStatus()).isEqualTo(com.ghatana.platform.health.HealthStatus.Status.UNHEALTHY);
        assertThat(platformStatus.getMessage()).isEqualTo("Database unavailable");
        assertThat(platformStatus.getDetails()).containsEntry("responseTimeMs", 5000L);
        assertThat(platformStatus.getDetails()).containsEntry("exceptionType", SQLException.class.getName());
        assertThat(platformStatus.getException()).isSameAs(failure);
    }

    @Test
    @DisplayName("rebuilds database status from canonical platform health")
    void shouldMapPlatformHealthToDatabaseStatus() {
        RuntimeException failure = new RuntimeException("Timed out");
        com.ghatana.platform.health.HealthStatus platformStatus =
            com.ghatana.platform.health.HealthStatus.builder()
                .withStatus(com.ghatana.platform.health.HealthStatus.Status.HEALTHY)
                .withMessage("Database connection validated")
                .withDetail("responseTimeMs", 25L)
                .withDetail("database", "postgres")
                .withException(failure)
                .build();

        HealthStatus databaseStatus = HealthStatus.fromPlatformHealthStatus(platformStatus);

        assertThat(databaseStatus.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY);
        assertThat(databaseStatus.getMessage()).isEqualTo("Database connection validated");
        assertThat(databaseStatus.getResponseTime()).isEqualTo(Duration.ofMillis(25));
        assertThat(databaseStatus.getDetails()).isNotNull();
        assertThat(databaseStatus.getDetails().getDetail("database")).isEqualTo("postgres");
        assertThat(databaseStatus.getException()).isSameAs(failure);
    }
}
