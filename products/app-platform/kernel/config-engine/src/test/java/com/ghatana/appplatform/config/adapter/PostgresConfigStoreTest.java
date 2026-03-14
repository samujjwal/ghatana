package com.ghatana.appplatform.config.adapter;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigSchema;
import com.ghatana.appplatform.config.domain.ConfigValue;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresConfigStore}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for the PostgreSQL config store adapter
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("PostgresConfigStore — Integration Tests")
class PostgresConfigStoreTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("config_test")
        .withUsername("test")
        .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresConfigStore store;

    @BeforeAll
    static void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(PG.getJdbcUrl());
        cfg.setUsername(PG.getUsername());
        cfg.setPassword(PG.getPassword());
        cfg.setMaximumPoolSize(5);
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
    void clean() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM config_entries");
            stmt.execute("DELETE FROM config_schemas");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean config tables", e);
        }
    }

    private static final String PAYMENTS_SCHEMA = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "max_limit": { "type": "number" }
          }
        }
        """;

    @Test
    @DisplayName("registerSchema persists and getSchema retrieves the schema")
    void registerAndRetrieveSchema() {
        ConfigSchema schema = new ConfigSchema(
            "payments", "1.0.0", PAYMENTS_SCHEMA, "Payment config", "{}");
        runPromise(() -> store.registerSchema(schema));

        Optional<ConfigSchema> result = runPromise(() -> store.getSchema("payments", "1.0.0"));

        assertThat(result).isPresent();
        assertThat(result.get().namespace()).isEqualTo("payments");
        assertThat(result.get().version()).isEqualTo("1.0.0");
        assertThat(result.get().description()).isEqualTo("Payment config");
    }

    @Test
    @DisplayName("getSchema returns empty for unknown namespace/version")
    void getSchemaReturnsEmptyWhenMissing() {
        Optional<ConfigSchema> result = runPromise(() -> store.getSchema("unknown", "1.0.0"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("registerSchema is idempotent (upsert)")
    void registerSchemaIdempotent() {
        ConfigSchema v1 = new ConfigSchema("payments", "1.0.0", PAYMENTS_SCHEMA, "v1 desc", "{}");
        ConfigSchema v1Updated = new ConfigSchema("payments", "1.0.0", PAYMENTS_SCHEMA, "updated desc", "{}");
        runPromise(() -> store.registerSchema(v1));
        runPromise(() -> store.registerSchema(v1Updated));

        Optional<ConfigSchema> result = runPromise(() -> store.getSchema("payments", "1.0.0"));
        assertThat(result.get().description()).isEqualTo("updated desc");
    }

    @Test
    @DisplayName("setEntry persists entry and resolve retrieves it at GLOBAL level")
    void setAndResolveGlobal() {
        ConfigEntry global = new ConfigEntry(
            "payments", "max_limit", "\"10000\"",
            ConfigHierarchyLevel.GLOBAL, "global", "payments");
        runPromise(() -> store.setEntry(global));

        Map<String, ConfigValue> resolved = runPromise(() ->
            store.resolve("payments", null, null, null, null));

        assertThat(resolved).containsKey("max_limit");
        assertThat(resolved.get("max_limit").value()).isEqualTo("\"10000\"");
        assertThat(resolved.get("max_limit").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.GLOBAL);
    }

    @Test
    @DisplayName("TENANT entry overrides GLOBAL for the same key")
    void tenantOverridesGlobal() {
        runPromise(() -> store.setEntry(new ConfigEntry(
            "payments", "max_limit", "\"10000\"",
            ConfigHierarchyLevel.GLOBAL, "global", "payments")));
        runPromise(() -> store.setEntry(new ConfigEntry(
            "payments", "max_limit", "\"50000\"",
            ConfigHierarchyLevel.TENANT, "tenant-abc", "payments")));

        Map<String, ConfigValue> resolved = runPromise(() ->
            store.resolve("payments", "tenant-abc", null, null, null));

        assertThat(resolved.get("max_limit").value()).isEqualTo("\"50000\"");
        assertThat(resolved.get("max_limit").resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.TENANT);
    }

    @Test
    @DisplayName("resolve returns empty map when namespace has no entries")
    void resolveEmptyNamespace() {
        Map<String, ConfigValue> result = runPromise(() ->
            store.resolve("empty-namespace", null, null, null, null));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("setEntry is idempotent (upsert)")
    void setEntryIdempotent() {
        ConfigEntry e1 = new ConfigEntry("payments", "timeout", "\"30\"",
            ConfigHierarchyLevel.GLOBAL, "global", "payments");
        ConfigEntry e2 = new ConfigEntry("payments", "timeout", "\"60\"",
            ConfigHierarchyLevel.GLOBAL, "global", "payments");
        runPromise(() -> store.setEntry(e1));
        runPromise(() -> store.setEntry(e2));

        Map<String, ConfigValue> result = runPromise(() ->
            store.resolve("payments", null, null, null, null));
        assertThat(result.get("timeout").value()).isEqualTo("\"60\"");
    }
}
