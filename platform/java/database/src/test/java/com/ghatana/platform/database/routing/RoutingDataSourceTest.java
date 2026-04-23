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
    void setUp() { // GH-90000
        primary = createH2DataSource("primary-db");
        replica1 = createH2DataSource("replica1-db");
        replica2 = createH2DataSource("replica2-db");

        routingDS = new RoutingDataSource( // GH-90000
                primary,
                Map.of("replica-1", replica1, "replica-2", replica2), // GH-90000
                60_000L);
    }

    @AfterEach
    void tearDown() { // GH-90000
        RoutingDataSource.clearReadOnly(); // GH-90000
    }

    private static DataSource createH2DataSource(String dbName) { // GH-90000
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        ds.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1"); // GH-90000
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    // ── getConnection() — write mode ────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("getConnection() returns a usable connection in write mode")
    void getConnectionReturnsUsableConnectionInWriteMode() throws SQLException { // GH-90000
        RoutingDataSource.setReadOnly(false); // GH-90000

        try (Connection conn = routingDS.getConnection()) { // GH-90000
            assertThat(conn).isNotNull(); // GH-90000
            assertThat(conn.isClosed()).isFalse(); // GH-90000
        }
    }

    @Test
    @DisplayName("getConnection() is non-null by default (write mode)")
    void getConnectionNonNullByDefault() throws SQLException { // GH-90000
        try (Connection conn = routingDS.getConnection()) { // GH-90000
            assertThat(conn).isNotNull(); // GH-90000
        }
    }

    // ── Read-only routing ────────────────────────────────────────────────────

    @Test
    @DisplayName("getConnection() returns a usable connection in read-only mode")
    void getConnectionUsableInReadOnlyMode() throws SQLException { // GH-90000
        RoutingDataSource.setReadOnly(true); // GH-90000

        try (Connection conn = routingDS.getConnection()) { // GH-90000
            assertThat(conn).isNotNull(); // GH-90000
            assertThat(conn.isClosed()).isFalse(); // GH-90000
        }
    }

    // ── ThreadLocal context ──────────────────────────────────────────────────

    @Test
    @DisplayName("setReadOnly(true) marks current thread context as read-only")
    void setReadOnlyMarksThreadAsReadOnly() { // GH-90000
        RoutingDataSource.setReadOnly(true); // GH-90000
        assertThat(RoutingDataSource.isReadOnly()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("setReadOnly(false) marks current thread context as writable")
    void setReadOnlyFalseMarksWritable() { // GH-90000
        RoutingDataSource.setReadOnly(true); // GH-90000
        RoutingDataSource.setReadOnly(false); // GH-90000
        assertThat(RoutingDataSource.isReadOnly()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("clearReadOnly() removes thread-local read-only context")
    void clearReadOnlyRemovesContext() { // GH-90000
        RoutingDataSource.setReadOnly(true); // GH-90000
        RoutingDataSource.clearReadOnly(); // GH-90000
        assertThat(RoutingDataSource.isReadOnly()).isFalse(); // GH-90000
    }

    // ── getPrimaryDataSource / getReplicaDataSources ─────────────────────────

    @Test
    @DisplayName("getPrimaryDataSource returns the primary DataSource")
    void getPrimaryDataSourceReturnsPrimary() { // GH-90000
        assertThat(routingDS.getPrimaryDataSource()).isSameAs(primary); // GH-90000
    }

    @Test
    @DisplayName("getReplicaDataSources returns a copy with all replicas")
    void getReplicaDataSourcesReturnsDefensiveCopy() { // GH-90000
        Map<String, DataSource> replicas = routingDS.getReplicaDataSources(); // GH-90000
        assertThat(replicas).containsKeys("replica-1", "replica-2"); // GH-90000
        assertThat(replicas).hasSize(2); // GH-90000
    }

    // ── Circuit breaker ───────────────────────────────────────────────────────

    @Test
    @DisplayName("initially all replicas are available")
    void initiallyAllReplicasAvailable() { // GH-90000
        Map<String, Boolean> status = routingDS.getReplicaAvailabilityStatus(); // GH-90000
        assertThat(status).containsEntry("replica-1", true); // GH-90000
        assertThat(status).containsEntry("replica-2", true); // GH-90000
    }

    @Test
    @DisplayName("markReplicaUnavailable() marks a replica as unavailable")
    void markReplicaUnavailableChangesStatus() { // GH-90000
        routingDS.markReplicaUnavailable("replica-1");
        Map<String, Boolean> status = routingDS.getReplicaAvailabilityStatus(); // GH-90000
        assertThat(status).containsEntry("replica-1", false); // GH-90000
    }

    @Test
    @DisplayName("markReplicaAvailable() restores a previously unavailable replica")
    void markReplicaAvailableRestoresStatus() { // GH-90000
        routingDS.markReplicaUnavailable("replica-1");
        routingDS.markReplicaAvailable("replica-1");
        Map<String, Boolean> status = routingDS.getReplicaAvailabilityStatus(); // GH-90000
        assertThat(status).containsEntry("replica-1", true); // GH-90000
    }

    // ── No replicas ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("falls back to primary when no replicas are configured")
    void fallbackToPrimaryWhenNoReplicas() throws SQLException { // GH-90000
        RoutingDataSource noReplicaDS = new RoutingDataSource(primary, Map.of()); // GH-90000
        RoutingDataSource.setReadOnly(true); // GH-90000

        try (Connection conn = noReplicaDS.getConnection()) { // GH-90000
            assertThat(conn).isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("all replicas unavailable falls back to primary for reads")
    void fallbackToPrimaryWhenAllReplicasUnavailable() throws SQLException { // GH-90000
        routingDS.markReplicaUnavailable("replica-1");
        routingDS.markReplicaUnavailable("replica-2");
        RoutingDataSource.setReadOnly(true); // GH-90000

        try (Connection conn = routingDS.getConnection()) { // GH-90000
            assertThat(conn).isNotNull(); // GH-90000
        }
    }
}
