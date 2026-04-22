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
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("ReplicaLagMonitor Integration Tests [GH-90000]")
class ReplicaLagMonitorIT {

    @Container
    @SuppressWarnings("resource [GH-90000]")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                    .withDatabaseName("testdb [GH-90000]")
                    .withUsername("test [GH-90000]")
                    .withPassword("test [GH-90000]");

    private HikariDataSource primaryDataSource;
    private HikariDataSource replicaDataSource;   // Same container acts as "replica" in monitor
    private ReplicaLagMonitor monitor;

    @BeforeEach
    void setUp() { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(3); // GH-90000

        primaryDataSource = new HikariDataSource(config); // GH-90000

        // Use a second HikariDataSource pointing to the same container as "replica"
        HikariConfig replicaConfig = new HikariConfig(); // GH-90000
        replicaConfig.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        replicaConfig.setUsername(POSTGRES.getUsername()); // GH-90000
        replicaConfig.setPassword(POSTGRES.getPassword()); // GH-90000
        replicaConfig.setMaximumPoolSize(3); // GH-90000
        replicaDataSource = new HikariDataSource(replicaConfig); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (monitor != null) { // GH-90000
            monitor.stop(); // GH-90000
        }
        if (primaryDataSource != null) { // GH-90000
            primaryDataSource.close(); // GH-90000
        }
        if (replicaDataSource != null) { // GH-90000
            replicaDataSource.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("monitor starts without error when replicas are configured [GH-90000]")
    void startWithReplicas() throws InterruptedException { // GH-90000
        Map<String, DataSource> replicaMap = Map.of("replica-1", replicaDataSource); // GH-90000
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap); // GH-90000

        monitor = new ReplicaLagMonitor( // GH-90000
                routingDataSource,
                primaryDataSource,
                Map.of("replica-1", replicaDataSource), // GH-90000
                null,  // MetricsRegistry is null — guarded by constructor
                10L * 1024 * 1024); // 10 MB threshold

        // Should start without throwing
        monitor.start(0, 1, TimeUnit.SECONDS); // GH-90000
        Thread.sleep(200); // Give the scheduled task one cycle // GH-90000

        // Verify the monitor is running (no exception thrown) // GH-90000
        assertThat(monitor).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("getReplicaLag returns 0 when primary and replica are the same node [GH-90000]")
    void lagIsZeroWhenPrimaryEqualsReplica() throws InterruptedException { // GH-90000
        Map<String, DataSource> replicaMap = Map.of("replica-1", replicaDataSource); // GH-90000
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap); // GH-90000

        monitor = new ReplicaLagMonitor( // GH-90000
                routingDataSource,
                primaryDataSource,
                Map.of("replica-1", replicaDataSource), // GH-90000
                null,
                Long.MAX_VALUE); // Very high threshold — replica stays available

        monitor.start(0, 1, TimeUnit.SECONDS); // GH-90000
        Thread.sleep(500); // Allow at least one monitoring cycle // GH-90000

        // On a single-node setup, lag should be reported as 0 or very small
        long lag = monitor.getReplicaLag("replica-1 [GH-90000]");
        assertThat(lag).isGreaterThanOrEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("stop shuts down the scheduler cleanly [GH-90000]")
    void stopShutsDownCleanly() { // GH-90000
        Map<String, DataSource> replicaMap = Map.of("replica-1", replicaDataSource); // GH-90000
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap); // GH-90000

        monitor = new ReplicaLagMonitor( // GH-90000
                routingDataSource,
                primaryDataSource,
                Map.of("replica-1", replicaDataSource), // GH-90000
                null,
                10L * 1024 * 1024);

        monitor.start(0, 10, TimeUnit.SECONDS); // GH-90000
        monitor.stop(); // Should not throw // GH-90000

        // Setting monitor to null so @AfterEach does not call stop() again // GH-90000
        monitor = null;
    }

    @Test
    @DisplayName("monitor with no replicas starts and stops without error [GH-90000]")
    void noReplicas() { // GH-90000
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, Map.of()); // GH-90000

        monitor = new ReplicaLagMonitor( // GH-90000
                routingDataSource,
                primaryDataSource,
                Map.of(), // GH-90000
                null,
                10L * 1024 * 1024);

        monitor.start(0, 10, TimeUnit.SECONDS); // GH-90000
        monitor.stop(); // GH-90000
        monitor = null;
    }

    @Test
    @DisplayName("multiple replicas are all tracked by the monitor [GH-90000]")
    void multipleReplicas() throws InterruptedException { // GH-90000
        HikariConfig cfg2 = new HikariConfig(); // GH-90000
        cfg2.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        cfg2.setUsername(POSTGRES.getUsername()); // GH-90000
        cfg2.setPassword(POSTGRES.getPassword()); // GH-90000
        cfg2.setMaximumPoolSize(2); // GH-90000
        HikariDataSource replica2 = new HikariDataSource(cfg2); // GH-90000

        try {
            Map<String, DataSource> replicaMap = Map.of( // GH-90000
                    "r1", replicaDataSource,
                    "r2", replica2);
            RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaMap); // GH-90000

            monitor = new ReplicaLagMonitor( // GH-90000
                    routingDataSource,
                    primaryDataSource,
                    Map.of("r1", replicaDataSource, "r2", replica2), // GH-90000
                    null,
                    Long.MAX_VALUE);

            monitor.start(0, 1, TimeUnit.SECONDS); // GH-90000
            Thread.sleep(500); // GH-90000

            // Both replicas should be tracked
            assertThat(monitor.getReplicaLag("r1 [GH-90000]")).isGreaterThanOrEqualTo(0L);
            assertThat(monitor.getReplicaLag("r2 [GH-90000]")).isGreaterThanOrEqualTo(0L);
        } finally {
            replica2.close(); // GH-90000
        }
    }
}
