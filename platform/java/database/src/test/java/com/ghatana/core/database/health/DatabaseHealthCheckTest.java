package com.ghatana.core.database.health;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for DatabaseHealthCheck using H2 in-memory database
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DatabaseHealthCheck — health check with H2 in-memory database")
class DatabaseHealthCheckTest {

    private DataSource workingDataSource;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        ds.setURL("jdbc:h2:mem:healthcheck-test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        workingDataSource = ds;
    }

    // ── builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder() creates a valid check with defaults")
    void builderCreatesCheckWithDefaults() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        assertThat(check.getValidationQuery()).isEqualTo("SELECT 1");
        assertThat(check.getTimeout()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
        assertThat(check.getDataSource()).isSameAs(workingDataSource); // GH-90000
    }

    @Test
    @DisplayName("builder() throws NullPointerException when dataSource is null")
    void builderThrowsForNullDataSource() { // GH-90000
        assertThatThrownBy(() -> DatabaseHealthCheck.builder() // GH-90000
                .dataSource(null) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("builder() throws when validationQuery is blank")
    void builderThrowsForBlankValidationQuery() { // GH-90000
        assertThatThrownBy(() -> DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .validationQuery("   ")
                .build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ── check() with working database ──────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("check() returns HEALTHY status for a working H2 database")
    void checkReturnsHealthyForWorkingDatabase() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .validationQuery("SELECT 1")
                .timeout(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY); // GH-90000
    }

    @Test
    @DisplayName("check() result message is non-null for healthy database")
    void checkHealthyStatusHasNonNullMessage() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.getMessage()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("check() response time is non-negative for healthy database")
    void checkHealthyStatusHasNonNegativeResponseTime() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.getResponseTime()).isGreaterThanOrEqualTo(Duration.ZERO); // GH-90000
    }

    @Test
    @DisplayName("check() has no exception for healthy database")
    void checkHealthyStatusHasNoException() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
            .timeout(Duration.ofSeconds(15)) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.getException()).isNull(); // GH-90000
    }

    // ── check() with broken datasource ──────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("check() returns UNHEALTHY status for an invalid datasource URL")
    void checkReturnsUnhealthyForBrokenDatabase() { // GH-90000
        JdbcDataSource broken = new JdbcDataSource(); // GH-90000
        broken.setURL("jdbc:h2:mem:nonexistent-forced-fail;OPEN_NEW=false");
        broken.setUser("invalid_user");
        broken.setPassword("wrong_password");

        // Use a very short timeout to fail fast
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(broken) // GH-90000
                .validationQuery("SELECT invalid_column FROM nonexistent_table")
                .timeout(Duration.ofSeconds(2)) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.isHealthy()).isFalse(); // GH-90000
    }

    // ── HealthStatus value object checks ────────────────────────────────────

    @Test
    @DisplayName("HealthStatus.healthy() is healthy and not unhealthy")
    void healthyStatusIsHealthy() { // GH-90000
        HealthStatus status = HealthStatus.healthy("OK", Duration.ofMillis(10), null); // GH-90000

        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.isUnhealthy()).isFalse(); // GH-90000
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY); // GH-90000
    }

    @Test
    @DisplayName("HealthStatus.unhealthy() is unhealthy and not healthy")
    void unhealthyStatusIsUnhealthy() { // GH-90000
        HealthStatus status = HealthStatus.unhealthy("Failed", Duration.ofMillis(5000), null); // GH-90000

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.isHealthy()).isFalse(); // GH-90000
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.UNHEALTHY); // GH-90000
    }

    @Test
    @DisplayName("HealthStatus.unknown() has UNKNOWN state")
    void unknownStatusHasUnknownState() { // GH-90000
        HealthStatus status = HealthStatus.unknown("Indeterminate", Duration.ofMillis(100)); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.UNKNOWN); // GH-90000
        assertThat(status.isHealthy()).isFalse(); // GH-90000
        assertThat(status.isUnhealthy()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("HealthStatus preserves message and response time")
    void healthStatusPreservesFields() { // GH-90000
        Duration responseTime = Duration.ofMillis(42); // GH-90000
        HealthStatus status = HealthStatus.healthy("Database up", responseTime, null); // GH-90000

        assertThat(status.getMessage()).isEqualTo("Database up");
        assertThat(status.getResponseTime()).isEqualTo(responseTime); // GH-90000
        assertThat(status.getTimestamp()).isNotNull(); // GH-90000
    }
}
