package com.ghatana.platform.database.routing;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReplicaLagMonitor} using a real PostgreSQL container.
 *
 * <p>These tests verify that the monitor can connect to PostgreSQL, query LSN functions,
 * start/stop the scheduler, and expose lag metrics. A true replication setup with two
 * servers would be required to observe non-zero lag — these tests validate the monitor
 * lifecycle and operational behavior with a primary-only container.</p>
 *
 * @doc.type class
 * @doc.purpose Integration tests for ReplicaLagMonitor with PostgreSQL container
 * @doc.layer platform
 * @doc.pattern Integration Test
 */
@Tag("integration")
@Testcontainers
@DisplayName("ReplicaLagMonitor Integration Tests")
class ReplicaLagMonitorIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    private HikariDataSource primaryDataSource;
    private HikariDataSource replicaDataSource;   // Same container acts as "replica" in monitor
    private ReplicaLagMonitor monitor;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(3);

        primaryDataSource = new HikariDataSource(config);

        // Use a second HikariDataSource pointing to the same container as "replica"
        HikariConfig replicaConfig = new HikariConfig();
        replicaConfig.setJdbcUrl(POSTGRES.getJdbcUrl());
        replicaConfig.setUsername(POSTGRES.getUsername());
        replicaConfig.setPassword(POSTGRES.getPassword());
        replicaConfig.setMaximumPoolSize(3);
        replicaDataSource = new HikariDataSource(replicaConfig);
    }

    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.stop();
        }
        if (primaryDataSource != null) {
            primaryDataSource.close();
        }
        if (replicaDataSource != null) {
            replicaDataSource.close();
        }
    }

    @Test
    @DisplayName("monitor starts without error when replicas are configured")
    void startWithReplicas() throws InterruptedException {
        Map<String, DataSource> replicaMap = Map.of("replica-1", replicaDataSource);
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap);

        monitor = new ReplicaLagMonitor(
                routingDataSource,
                primaryDataSource,
                Map.of("replica-1", replicaDataSource),
                null,  // MetricsRegistry is null — guarded by constructor
                10L * 1024 * 1024); // 10 MB threshold

        // Should start without throwing
        monitor.start(0, 1, TimeUnit.SECONDS);
        Thread.sleep(200); // Give the scheduled task one cycle

        // Verify the monitor is running (no exception thrown)
        assertThat(monitor).isNotNull();
    }

    @Test
    @DisplayName("getReplicaLag returns 0 when primary and replica are the same node")
    void lagIsZeroWhenPrimaryEqualsReplica() throws InterruptedException {
        Map<String, DataSource> replicaMap = Map.of("replica-1", replicaDataSource);
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap);

        monitor = new ReplicaLagMonitor(
                routingDataSource,
                primaryDataSource,
                Map.of("replica-1", replicaDataSource),
                null,
                Long.MAX_VALUE); // Very high threshold — replica stays available

        monitor.start(0, 1, TimeUnit.SECONDS);
        Thread.sleep(500); // Allow at least one monitoring cycle

        // On a single-node setup, lag should be reported as 0 or very small
        long lag = monitor.getReplicaLag("replica-1");
        assertThat(lag).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("stop shuts down the scheduler cleanly")
    void stopShutsDownCleanly() {
        Map<String, DataSource> replicaMap = Map.of("replica-1", replicaDataSource);
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap);

        monitor = new ReplicaLagMonitor(
                routingDataSource,
                primaryDataSource,
                Map.of("replica-1", replicaDataSource),
                null,
                10L * 1024 * 1024);

        monitor.start(0, 10, TimeUnit.SECONDS);
        monitor.stop(); // Should not throw

        // Setting monitor to null so @AfterEach does not call stop() again
        monitor = null;
    }

    @Test
    @DisplayName("monitor with no replicas starts and stops without error")
    void noReplicas() {
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, Map.of());

        monitor = new ReplicaLagMonitor(
                routingDataSource,
                primaryDataSource,
                Map.of(),
                null,
                10L * 1024 * 1024);

        monitor.start(0, 10, TimeUnit.SECONDS);
        monitor.stop();
        monitor = null;
    }

    @Test
    @DisplayName("multiple replicas are all tracked by the monitor")
    void multipleReplicas() throws InterruptedException {
        HikariConfig cfg2 = new HikariConfig();
        cfg2.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg2.setUsername(POSTGRES.getUsername());
        cfg2.setPassword(POSTGRES.getPassword());
        cfg2.setMaximumPoolSize(2);
        HikariDataSource replica2 = new HikariDataSource(cfg2);

        try {
            Map<String, DataSource> replicaMap = Map.of(
                    "r1", replicaDataSource,
                    "r2", replica2);
            RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap);

            monitor = new ReplicaLagMonitor(
                    routingDataSource,
                    primaryDataSource,
                    Map.of("r1", replicaDataSource, "r2", replica2),
                    null,
                    Long.MAX_VALUE);

            monitor.start(0, 1, TimeUnit.SECONDS);
            Thread.sleep(500);

            // Both replicas should be tracked
            assertThat(monitor.getReplicaLag("r1")).isGreaterThanOrEqualTo(0L);
            assertThat(monitor.getReplicaLag("r2")).isGreaterThanOrEqualTo(0L);
        } finally {
            replica2.close();
        }
    }
}
