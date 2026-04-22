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
@DisplayName("DatabaseHealthCheck — health check with H2 in-memory database [GH-90000]")
class DatabaseHealthCheckTest {

    private DataSource workingDataSource;

    @BeforeEach
    void setUp() { // GH-90000
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        ds.setURL("jdbc:h2:mem:healthcheck-test;DB_CLOSE_DELAY=-1 [GH-90000]");
        ds.setUser("sa [GH-90000]");
        ds.setPassword(" [GH-90000]");
        workingDataSource = ds;
    }

    // ── builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder() creates a valid check with defaults [GH-90000]")
    void builderCreatesCheckWithDefaults() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        assertThat(check.getValidationQuery()).isEqualTo("SELECT 1 [GH-90000]");
        assertThat(check.getTimeout()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
        assertThat(check.getDataSource()).isSameAs(workingDataSource); // GH-90000
    }

    @Test
    @DisplayName("builder() throws NullPointerException when dataSource is null [GH-90000]")
    void builderThrowsForNullDataSource() { // GH-90000
        assertThatThrownBy(() -> DatabaseHealthCheck.builder() // GH-90000
                .dataSource(null) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("builder() throws when validationQuery is blank [GH-90000]")
    void builderThrowsForBlankValidationQuery() { // GH-90000
        assertThatThrownBy(() -> DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .validationQuery("    [GH-90000]")
                .build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ── check() with working database ──────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("check() returns HEALTHY status for a working H2 database [GH-90000]")
    void checkReturnsHealthyForWorkingDatabase() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .validationQuery("SELECT 1 [GH-90000]")
                .timeout(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY); // GH-90000
    }

    @Test
    @DisplayName("check() result message is non-null for healthy database [GH-90000]")
    void checkHealthyStatusHasNonNullMessage() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.getMessage()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("check() response time is non-negative for healthy database [GH-90000]")
    void checkHealthyStatusHasNonNegativeResponseTime() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.getResponseTime()).isGreaterThanOrEqualTo(Duration.ZERO); // GH-90000
    }

    @Test
    @DisplayName("check() has no exception for healthy database [GH-90000]")
    void checkHealthyStatusHasNoException() { // GH-90000
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(workingDataSource) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.getException()).isNull(); // GH-90000
    }

    // ── check() with broken datasource ──────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("check() returns UNHEALTHY status for an invalid datasource URL [GH-90000]")
    void checkReturnsUnhealthyForBrokenDatabase() { // GH-90000
        JdbcDataSource broken = new JdbcDataSource(); // GH-90000
        broken.setURL("jdbc:h2:mem:nonexistent-forced-fail;OPEN_NEW=false [GH-90000]");
        broken.setUser("invalid_user [GH-90000]");
        broken.setPassword("wrong_password [GH-90000]");

        // Use a very short timeout to fail fast
        DatabaseHealthCheck check = DatabaseHealthCheck.builder() // GH-90000
                .dataSource(broken) // GH-90000
                .validationQuery("SELECT invalid_column FROM nonexistent_table [GH-90000]")
                .timeout(Duration.ofSeconds(2)) // GH-90000
                .build(); // GH-90000

        HealthStatus status = check.check(); // GH-90000

        assertThat(status.isHealthy()).isFalse(); // GH-90000
    }

    // ── HealthStatus value object checks ────────────────────────────────────

    @Test
    @DisplayName("HealthStatus.healthy() is healthy and not unhealthy [GH-90000]")
    void healthyStatusIsHealthy() { // GH-90000
        HealthStatus status = HealthStatus.healthy("OK", Duration.ofMillis(10), null); // GH-90000

        assertThat(status.isHealthy()).isTrue(); // GH-90000
        assertThat(status.isUnhealthy()).isFalse(); // GH-90000
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY); // GH-90000
    }

    @Test
    @DisplayName("HealthStatus.unhealthy() is unhealthy and not healthy [GH-90000]")
    void unhealthyStatusIsUnhealthy() { // GH-90000
        HealthStatus status = HealthStatus.unhealthy("Failed", Duration.ofMillis(5000), null); // GH-90000

        assertThat(status.isUnhealthy()).isTrue(); // GH-90000
        assertThat(status.isHealthy()).isFalse(); // GH-90000
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.UNHEALTHY); // GH-90000
    }

    @Test
    @DisplayName("HealthStatus.unknown() has UNKNOWN state [GH-90000]")
    void unknownStatusHasUnknownState() { // GH-90000
        HealthStatus status = HealthStatus.unknown("Indeterminate", Duration.ofMillis(100)); // GH-90000

        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.UNKNOWN); // GH-90000
        assertThat(status.isHealthy()).isFalse(); // GH-90000
        assertThat(status.isUnhealthy()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("HealthStatus preserves message and response time [GH-90000]")
    void healthStatusPreservesFields() { // GH-90000
        Duration responseTime = Duration.ofMillis(42); // GH-90000
        HealthStatus status = HealthStatus.healthy("Database up", responseTime, null); // GH-90000

        assertThat(status.getMessage()).isEqualTo("Database up [GH-90000]");
        assertThat(status.getResponseTime()).isEqualTo(responseTime); // GH-90000
        assertThat(status.getTimestamp()).isNotNull(); // GH-90000
    }
}
