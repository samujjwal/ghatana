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
    void shouldMapDatabaseStatusToPlatformHealth() { // GH-90000
        SQLException failure = new SQLException("Connection refused");
        HealthDetails details = HealthDetails.builder() // GH-90000
            .detail("database", "postgres") // GH-90000
            .detail("connectionsActive", 4) // GH-90000
            .build(); // GH-90000
        HealthStatus databaseStatus = HealthStatus.unhealthy( // GH-90000
            "Database unavailable",
            Duration.ofSeconds(5), // GH-90000
            failure);

        com.ghatana.platform.health.HealthStatus platformStatus = databaseStatus.toPlatformHealthStatus(); // GH-90000

        assertThat(platformStatus.getStatus()).isEqualTo(com.ghatana.platform.health.HealthStatus.Status.UNHEALTHY); // GH-90000
        assertThat(platformStatus.getMessage()).isEqualTo("Database unavailable");
        assertThat(platformStatus.getDetails()).containsEntry("responseTimeMs", 5000L); // GH-90000
        assertThat(platformStatus.getDetails()).containsEntry("exceptionType", SQLException.class.getName()); // GH-90000
        assertThat(platformStatus.getException()).isSameAs(failure); // GH-90000
    }

    @Test
    @DisplayName("rebuilds database status from canonical platform health")
    void shouldMapPlatformHealthToDatabaseStatus() { // GH-90000
        RuntimeException failure = new RuntimeException("Timed out");
        com.ghatana.platform.health.HealthStatus platformStatus =
            com.ghatana.platform.health.HealthStatus.builder() // GH-90000
                .withStatus(com.ghatana.platform.health.HealthStatus.Status.HEALTHY) // GH-90000
                .withMessage("Database connection validated")
                .withDetail("responseTimeMs", 25L) // GH-90000
                .withDetail("database", "postgres") // GH-90000
                .withException(failure) // GH-90000
                .build(); // GH-90000

        HealthStatus databaseStatus = HealthStatus.fromPlatformHealthStatus(platformStatus); // GH-90000

        assertThat(databaseStatus.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY); // GH-90000
        assertThat(databaseStatus.getMessage()).isEqualTo("Database connection validated");
        assertThat(databaseStatus.getResponseTime()).isEqualTo(Duration.ofMillis(25)); // GH-90000
        assertThat(databaseStatus.getDetails()).isNotNull(); // GH-90000
        assertThat(databaseStatus.getDetails().getDetail("database")).isEqualTo("postgres");
        assertThat(databaseStatus.getException()).isSameAs(failure); // GH-90000
    }
}
