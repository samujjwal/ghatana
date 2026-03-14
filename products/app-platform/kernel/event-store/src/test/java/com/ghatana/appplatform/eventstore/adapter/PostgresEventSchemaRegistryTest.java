package com.ghatana.appplatform.eventstore.adapter;

import com.ghatana.appplatform.eventstore.domain.CompatibilityType;
import com.ghatana.appplatform.eventstore.domain.EventSchemaVersion;
import com.ghatana.appplatform.eventstore.domain.SchemaStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PostgresEventSchemaRegistry}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for the event schema registry adapter
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("EventSchemaRegistry — Integration Tests")
class PostgresEventSchemaRegistryTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("app_platform_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresEventSchemaRegistry registry;

    private static final String SCHEMA_JSON = """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "amount": { "type": "number" }
          },
          "required": ["amount"]
        }
        """;

    @BeforeAll
    static void setUpDataSource() {
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

        registry = new PostgresEventSchemaRegistry(dataSource, Executors.newFixedThreadPool(4));
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM event_schema_registry");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean event_schema_registry before test", e);
        }
    }

    private EventSchemaVersion buildSchema(String eventType, int version) {
        return EventSchemaVersion.builder()
            .eventType(eventType)
            .version(version)
            .jsonSchema(SCHEMA_JSON)
            .status(SchemaStatus.DRAFT)
            .compatType(CompatibilityType.BACKWARD)
            .description("Test schema v" + version)
            .build();
    }

    @Test
    @DisplayName("register_newSchema_isPersisted — registered schema can be retrieved")
    void registerNewSchemaIsPersisted() {
        EventSchemaVersion schema = buildSchema("com.ghatana.order.OrderPlaced", 1);

        runPromise(() -> registry.registerSchema(schema));

        Optional<EventSchemaVersion> found = runPromise(() ->
            registry.getSchema("com.ghatana.order.OrderPlaced", 1));

        assertThat(found).isPresent();
        assertThat(found.get().eventType()).isEqualTo("com.ghatana.order.OrderPlaced");
        assertThat(found.get().version()).isEqualTo(1);
        assertThat(found.get().status()).isEqualTo(SchemaStatus.DRAFT);
    }

    @Test
    @DisplayName("activate_draftSchema_becomesActive — activation promotes DRAFT to ACTIVE")
    void activateDraftSchemaBecomesActive() {
        EventSchemaVersion schema = buildSchema("com.ghatana.order.OrderPlaced", 1);
        runPromise(() -> registry.registerSchema(schema));

        runPromise(() -> registry.activateSchema("com.ghatana.order.OrderPlaced", 1));

        Optional<EventSchemaVersion> active = runPromise(() ->
            registry.getActiveSchema("com.ghatana.order.OrderPlaced"));

        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(1);
        assertThat(active.get().status()).isEqualTo(SchemaStatus.ACTIVE);
        assertThat(active.get().activatedAt()).isNotNull();
    }

    @Test
    @DisplayName("activate_v2_deprecatesV1 — activating V2 transitions V1 to DEPRECATED")
    void activateV2DeprecatesV1() {
        runPromise(() -> registry.registerSchema(buildSchema("com.ghatana.payment.PaymentCreated", 1)));
        runPromise(() -> registry.activateSchema("com.ghatana.payment.PaymentCreated", 1));
        runPromise(() -> registry.registerSchema(buildSchema("com.ghatana.payment.PaymentCreated", 2)));

        runPromise(() -> registry.activateSchema("com.ghatana.payment.PaymentCreated", 2));

        Optional<EventSchemaVersion> active = runPromise(() ->
            registry.getActiveSchema("com.ghatana.payment.PaymentCreated"));
        Optional<EventSchemaVersion> v1 = runPromise(() ->
            registry.getSchema("com.ghatana.payment.PaymentCreated", 1));

        assertThat(active).isPresent();
        assertThat(active.get().version()).isEqualTo(2);

        assertThat(v1).isPresent();
        assertThat(v1.get().status()).isEqualTo(SchemaStatus.DEPRECATED);
    }

    @Test
    @DisplayName("getActiveSchema_whenNoneRegistered_returnsEmpty — no schema → empty")
    void getActiveSchemaWhenNoneRegisteredReturnsEmpty() {
        Optional<EventSchemaVersion> active = runPromise(() ->
            registry.getActiveSchema("com.ghatana.unknown.UnknownEvent"));

        assertThat(active).isEmpty();
    }

    @Test
    @DisplayName("listVersions_multipleVersions_returnedAscending — versions ordered by version number")
    void listVersionsMultipleVersionsReturnedAscending() {
        runPromise(() -> registry.registerSchema(buildSchema("com.ghatana.order.OrderCancelled", 3)));
        runPromise(() -> registry.registerSchema(buildSchema("com.ghatana.order.OrderCancelled", 1)));
        runPromise(() -> registry.registerSchema(buildSchema("com.ghatana.order.OrderCancelled", 2)));

        List<EventSchemaVersion> versions = runPromise(() ->
            registry.listVersions("com.ghatana.order.OrderCancelled"));

        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).version()).isEqualTo(1);
        assertThat(versions.get(1).version()).isEqualTo(2);
        assertThat(versions.get(2).version()).isEqualTo(3);
    }

    @Test
    @DisplayName("activateNonExistent_throwsIllegalState — cannot activate a schema that was never registered")
    void activateNonExistentThrowsIllegalState() {
        assertThatThrownBy(() ->
            runPromise(() -> registry.activateSchema("com.ghatana.order.OrderPlaced", 999)))
            .hasMessageContaining("Schema not found for activation");
    }
}
