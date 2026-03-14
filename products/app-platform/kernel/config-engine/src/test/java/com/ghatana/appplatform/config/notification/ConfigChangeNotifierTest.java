package com.ghatana.appplatform.config.notification;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.adapter.PostgresConfigStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ConfigChangeNotifier}.
 *
 * <p>Verifies that listeners receive a notification within 3 seconds after a config
 * entry is inserted by the {@link PostgresConfigStore}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for config hot-reload LISTEN/NOTIFY
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("ConfigChangeNotifier — Integration Tests")
class ConfigChangeNotifierTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("config_test")
        .withUsername("test")
        .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresConfigStore store;

    private ConfigChangeNotifier notifier;

    @BeforeAll
    static void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(PG.getJdbcUrl());
        cfg.setUsername(PG.getUsername());
        cfg.setPassword(PG.getPassword());
        cfg.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(cfg);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:src/main/resources/db/migration")
            .load()
            .migrate();

        store = new PostgresConfigStore(dataSource, Executors.newFixedThreadPool(4));
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void startNotifier() throws Exception {
        notifier = new ConfigChangeNotifier(dataSource, 100L); // fast poll for tests
        notifier.start();
    }

    @AfterEach
    void stopNotifier() {
        notifier.stop();
    }

    @Test
    @DisplayName("insertConfig_listenerReceivesNotification — change event dispatched within 3s")
    void insertConfigListenerReceivesNotification() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedKeys = new ArrayList<>();

        notifier.addListener((namespace, key, level, levelId) -> {
            receivedKeys.add(key);
            latch.countDown();
        });

        // Insert a config entry to trigger the NOTIFY
        runPromise(() -> store.setEntry(new ConfigEntry(
            "app.feature", "dark-mode", "true",
            ConfigHierarchyLevel.GLOBAL, "global", "app.feature")));

        boolean notified = latch.await(3, TimeUnit.SECONDS);

        assertThat(notified)
            .as("Expected config change notification within 3 seconds")
            .isTrue();
        assertThat(receivedKeys).contains("dark-mode");
    }

    @Test
    @DisplayName("multipleListeners_allReceiveNotification — all registered listeners are called")
    void multipleListenersAllReceiveNotification() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> received = new ArrayList<>();

        notifier.addListener((ns, key, level, lid) -> { received.add("L1:" + key); latch.countDown(); });
        notifier.addListener((ns, key, level, lid) -> { received.add("L2:" + key); latch.countDown(); });

        runPromise(() -> store.setEntry(new ConfigEntry(
            "app.feature", "notifications-enabled", "false",
            ConfigHierarchyLevel.GLOBAL, "global", "app.feature")));

        boolean notified = latch.await(3, TimeUnit.SECONDS);

        assertThat(notified).as("Both listeners should have been notified").isTrue();
        assertThat(received)
            .anyMatch(s -> s.startsWith("L1:"))
            .anyMatch(s -> s.startsWith("L2:"));
    }

    @Test
    @DisplayName("stopStart_notifierIsIdempotent — stop/start cycle does not throw")
    void stopStartNotifierIsIdempotent() throws Exception {
        assertThat(notifier.isRunning()).isTrue();
        notifier.stop();
        assertThat(notifier.isRunning()).isFalse();
        notifier.stop(); // idempotent — no exception
        notifier.start();
        assertThat(notifier.isRunning()).isTrue();
    }
}
