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
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:healthcheck-test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        workingDataSource = ds;
    }

    // ── builder ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder() creates a valid check with defaults")
    void builderCreatesCheckWithDefaults() {
        DatabaseHealthCheck check = DatabaseHealthCheck.builder()
                .dataSource(workingDataSource)
                .build();

        assertThat(check.getValidationQuery()).isEqualTo("SELECT 1");
        assertThat(check.getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(check.getDataSource()).isSameAs(workingDataSource);
    }

    @Test
    @DisplayName("builder() throws NullPointerException when dataSource is null")
    void builderThrowsForNullDataSource() {
        assertThatThrownBy(() -> DatabaseHealthCheck.builder()
                .dataSource(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder() throws when validationQuery is blank")
    void builderThrowsForBlankValidationQuery() {
        assertThatThrownBy(() -> DatabaseHealthCheck.builder()
                .dataSource(workingDataSource)
                .validationQuery("   ")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── check() with working database ────────────────────────────────────────

    @Test
    @DisplayName("check() returns HEALTHY status for a working H2 database")
    void checkReturnsHealthyForWorkingDatabase() {
        DatabaseHealthCheck check = DatabaseHealthCheck.builder()
                .dataSource(workingDataSource)
                .validationQuery("SELECT 1")
                .timeout(Duration.ofSeconds(5))
                .build();

        HealthStatus status = check.check();

        assertThat(status.isHealthy()).isTrue();
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY);
    }

    @Test
    @DisplayName("check() result message is non-null for healthy database")
    void checkHealthyStatusHasNonNullMessage() {
        DatabaseHealthCheck check = DatabaseHealthCheck.builder()
                .dataSource(workingDataSource)
                .build();

        HealthStatus status = check.check();

        assertThat(status.getMessage()).isNotNull();
    }

    @Test
    @DisplayName("check() response time is non-negative for healthy database")
    void checkHealthyStatusHasNonNegativeResponseTime() {
        DatabaseHealthCheck check = DatabaseHealthCheck.builder()
                .dataSource(workingDataSource)
                .build();

        HealthStatus status = check.check();

        assertThat(status.getResponseTime()).isNotNegative();
    }

    @Test
    @DisplayName("check() has no exception for healthy database")
    void checkHealthyStatusHasNoException() {
        DatabaseHealthCheck check = DatabaseHealthCheck.builder()
                .dataSource(workingDataSource)
                .build();

        HealthStatus status = check.check();

        assertThat(status.getException()).isNull();
    }

    // ── check() with broken datasource ────────────────────────────────────────

    @Test
    @DisplayName("check() returns UNHEALTHY status for an invalid datasource URL")
    void checkReturnsUnhealthyForBrokenDatabase() {
        JdbcDataSource broken = new JdbcDataSource();
        broken.setURL("jdbc:h2:mem:nonexistent-forced-fail;OPEN_NEW=false");
        broken.setUser("invalid_user");
        broken.setPassword("wrong_password");

        // Use a very short timeout to fail fast
        DatabaseHealthCheck check = DatabaseHealthCheck.builder()
                .dataSource(broken)
                .validationQuery("SELECT invalid_column FROM nonexistent_table")
                .timeout(Duration.ofSeconds(2))
                .build();

        HealthStatus status = check.check();

        assertThat(status.isHealthy()).isFalse();
    }

    // ── HealthStatus value object checks ────────────────────────────────────

    @Test
    @DisplayName("HealthStatus.healthy() is healthy and not unhealthy")
    void healthyStatusIsHealthy() {
        HealthStatus status = HealthStatus.healthy("OK", Duration.ofMillis(10), null);

        assertThat(status.isHealthy()).isTrue();
        assertThat(status.isUnhealthy()).isFalse();
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.HEALTHY);
    }

    @Test
    @DisplayName("HealthStatus.unhealthy() is unhealthy and not healthy")
    void unhealthyStatusIsUnhealthy() {
        HealthStatus status = HealthStatus.unhealthy("Failed", Duration.ofMillis(5000), null);

        assertThat(status.isUnhealthy()).isTrue();
        assertThat(status.isHealthy()).isFalse();
        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.UNHEALTHY);
    }

    @Test
    @DisplayName("HealthStatus.unknown() has UNKNOWN state")
    void unknownStatusHasUnknownState() {
        HealthStatus status = HealthStatus.unknown("Indeterminate", Duration.ofMillis(100));

        assertThat(status.getStatus()).isEqualTo(HealthStatus.HealthState.UNKNOWN);
        assertThat(status.isHealthy()).isFalse();
        assertThat(status.isUnhealthy()).isFalse();
    }

    @Test
    @DisplayName("HealthStatus preserves message and response time")
    void healthStatusPreservesFields() {
        Duration responseTime = Duration.ofMillis(42);
        HealthStatus status = HealthStatus.healthy("Database up", responseTime, null);

        assertThat(status.getMessage()).isEqualTo("Database up");
        assertThat(status.getResponseTime()).isEqualTo(responseTime);
        assertThat(status.getTimestamp()).isNotNull();
    }
}
