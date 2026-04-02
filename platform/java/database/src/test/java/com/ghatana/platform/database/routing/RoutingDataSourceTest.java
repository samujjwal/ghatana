package com.ghatana.platform.database.routing;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for RoutingDataSource read/write splitting and circuit breaker
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RoutingDataSource — read/write splitting and replica circuit breaker")
class RoutingDataSourceTest {

    private DataSource primary;
    private DataSource replica1;
    private DataSource replica2;
    private RoutingDataSource routingDS;

    @BeforeEach
    void setUp() {
        primary = createH2DataSource("primary-db");
        replica1 = createH2DataSource("replica1-db");
        replica2 = createH2DataSource("replica2-db");

        routingDS = new RoutingDataSource(
                primary,
                Map.of("replica-1", replica1, "replica-2", replica2),
                60_000L);
    }

    @AfterEach
    void tearDown() {
        RoutingDataSource.clearReadOnly();
    }

    private static DataSource createH2DataSource(String dbName) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    // ── getConnection() — write mode ──────────────────────────────────────────

    @Test
    @DisplayName("getConnection() returns a usable connection in write mode")
    void getConnectionReturnsUsableConnectionInWriteMode() throws SQLException {
        RoutingDataSource.setReadOnly(false);

        try (Connection conn = routingDS.getConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    @DisplayName("getConnection() is non-null by default (write mode)")
    void getConnectionNonNullByDefault() throws SQLException {
        try (Connection conn = routingDS.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }

    // ── Read-only routing ────────────────────────────────────────────────────

    @Test
    @DisplayName("getConnection() returns a usable connection in read-only mode")
    void getConnectionUsableInReadOnlyMode() throws SQLException {
        RoutingDataSource.setReadOnly(true);

        try (Connection conn = routingDS.getConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    // ── ThreadLocal context ──────────────────────────────────────────────────

    @Test
    @DisplayName("setReadOnly(true) marks current thread context as read-only")
    void setReadOnlyMarksThreadAsReadOnly() {
        RoutingDataSource.setReadOnly(true);
        assertThat(RoutingDataSource.isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("setReadOnly(false) marks current thread context as writable")
    void setReadOnlyFalseMarksWritable() {
        RoutingDataSource.setReadOnly(true);
        RoutingDataSource.setReadOnly(false);
        assertThat(RoutingDataSource.isReadOnly()).isFalse();
    }

    @Test
    @DisplayName("clearReadOnly() removes thread-local read-only context")
    void clearReadOnlyRemovesContext() {
        RoutingDataSource.setReadOnly(true);
        RoutingDataSource.clearReadOnly();
        assertThat(RoutingDataSource.isReadOnly()).isFalse();
    }

    // ── getPrimaryDataSource / getReplicaDataSources ─────────────────────────

    @Test
    @DisplayName("getPrimaryDataSource returns the primary DataSource")
    void getPrimaryDataSourceReturnsPrimary() {
        assertThat(routingDS.getPrimaryDataSource()).isSameAs(primary);
    }

    @Test
    @DisplayName("getReplicaDataSources returns a copy with all replicas")
    void getReplicaDataSourcesReturnsDefensiveCopy() {
        Map<String, DataSource> replicas = routingDS.getReplicaDataSources();
        assertThat(replicas).containsKeys("replica-1", "replica-2");
        assertThat(replicas).hasSize(2);
    }

    // ── Circuit breaker ───────────────────────────────────────────────────────

    @Test
    @DisplayName("initially all replicas are available")
    void initiallyAllReplicasAvailable() {
        Map<String, Boolean> status = routingDS.getReplicaAvailabilityStatus();
        assertThat(status).containsEntry("replica-1", true);
        assertThat(status).containsEntry("replica-2", true);
    }

    @Test
    @DisplayName("markReplicaUnavailable() marks a replica as unavailable")
    void markReplicaUnavailableChangesStatus() {
        routingDS.markReplicaUnavailable("replica-1");
        Map<String, Boolean> status = routingDS.getReplicaAvailabilityStatus();
        assertThat(status).containsEntry("replica-1", false);
    }

    @Test
    @DisplayName("markReplicaAvailable() restores a previously unavailable replica")
    void markReplicaAvailableRestoresStatus() {
        routingDS.markReplicaUnavailable("replica-1");
        routingDS.markReplicaAvailable("replica-1");
        Map<String, Boolean> status = routingDS.getReplicaAvailabilityStatus();
        assertThat(status).containsEntry("replica-1", true);
    }

    // ── No replicas ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("falls back to primary when no replicas are configured")
    void fallbackToPrimaryWhenNoReplicas() throws SQLException {
        RoutingDataSource noReplicaDS = new RoutingDataSource(primary, Map.of());
        RoutingDataSource.setReadOnly(true);

        try (Connection conn = noReplicaDS.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }

    @Test
    @DisplayName("all replicas unavailable falls back to primary for reads")
    void fallbackToPrimaryWhenAllReplicasUnavailable() throws SQLException {
        routingDS.markReplicaUnavailable("replica-1");
        routingDS.markReplicaUnavailable("replica-2");
        RoutingDataSource.setReadOnly(true);

        try (Connection conn = routingDS.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }
}
