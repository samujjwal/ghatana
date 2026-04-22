package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Proves production-profile Data Cloud factory wiring against real PostgreSQL and Kafka providers.
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("DataCloud Factory Real Provider Tests [GH-90000]")
class DataCloudFactoryRealProviderTest extends EventloopTestBase {

    private static final DataCloudConfig PRODUCTION_CONFIG = DataCloudConfig.builder() // GH-90000
        .profile(DataCloudProfile.PRODUCTION) // GH-90000
        .build(); // GH-90000

    @Container
    @SuppressWarnings("resource [GH-90000]")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine [GH-90000]")
        .withDatabaseName("datacloud_factory_it [GH-90000]")
        .withUsername("dc_factory [GH-90000]")
        .withPassword("dc_factory_secret [GH-90000]");

    @Container
    @SuppressWarnings("resource [GH-90000]")
    private static final KafkaContainer KAFKA = new KafkaContainer( // GH-90000
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0 [GH-90000]")
    ).withEmbeddedZookeeper(); // GH-90000

    @BeforeAll
    static void migrateSchemaAndConfigureProviders() { // GH-90000
        Flyway.configure() // GH-90000
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) // GH-90000
            .locations("filesystem:" + Path.of( // GH-90000
                System.getProperty("user.dir [GH-90000]"),
                "products",
                "data-cloud",
                "platform-launcher",
                "src",
                "main",
                "resources",
                "db",
                "migration"
            ))
            .load() // GH-90000
            .migrate(); // GH-90000

        System.setProperty("datacloud.db.url", POSTGRES.getJdbcUrl()); // GH-90000
        System.setProperty("datacloud.db.user", POSTGRES.getUsername()); // GH-90000
        System.setProperty("datacloud.db.password", POSTGRES.getPassword()); // GH-90000
        System.setProperty("datacloud.kafka.bootstrapServers", KAFKA.getBootstrapServers()); // GH-90000
    }

    @AfterEach
    void truncateEntities() throws Exception { // GH-90000
        try (var connection = POSTGRES.createConnection(" [GH-90000]");
             var statement = connection.createStatement()) { // GH-90000
            statement.execute("TRUNCATE TABLE entities CASCADE [GH-90000]");
        }
    }

    @AfterEach
    void clearOptionalProviderOverrides() { // GH-90000
        System.clearProperty("datacloud.db.poolMaxSize [GH-90000]");
        System.clearProperty("datacloud.db.poolMinIdle [GH-90000]");
        System.clearProperty("datacloud.db.connectionTimeoutMs [GH-90000]");
        System.clearProperty("datacloud.db.idleTimeoutMs [GH-90000]");
        System.clearProperty("datacloud.db.maxLifetimeMs [GH-90000]");
    }

    @AfterAll
    static void clearProviderBootstrapProperties() { // GH-90000
        System.clearProperty("datacloud.db.url [GH-90000]");
        System.clearProperty("datacloud.db.user [GH-90000]");
        System.clearProperty("datacloud.db.password [GH-90000]");
        System.clearProperty("datacloud.kafka.bootstrapServers [GH-90000]");
    }

    @Test
    @DisplayName("production profile persists entities and events across restart with service-loaded providers [GH-90000]")
    void productionProfilePersistsEntitiesAndEventsAcrossRestartWithServiceLoadedProviders() { // GH-90000
        String tenantId = "tenant-provider-" + UUID.randomUUID(); // GH-90000
        String entityId = UUID.randomUUID().toString(); // GH-90000
        String eventType = "document.created." + UUID.randomUUID(); // GH-90000

        DataCloudClient firstClient = DataCloud.create(PRODUCTION_CONFIG); // GH-90000
        try {
            assertThat(firstClient.entityStore().getClass().getName()).contains("PostgresEntityStore [GH-90000]");
            assertThat(firstClient.eventLogStore().getClass().getName()).doesNotContain("InMemoryEventLogStore [GH-90000]");

            runPromise(() -> firstClient.save( // GH-90000
                tenantId,
                "documents",
                Map.of("id", entityId, "title", "Provider-backed manifest") // GH-90000
            ));
            runPromise(() -> firstClient.appendEvent( // GH-90000
                tenantId,
                DataCloudClient.Event.of(eventType, Map.of("entityId", entityId, "tenantId", tenantId)) // GH-90000
            ));
        } finally {
            firstClient.close(); // GH-90000
        }

        DataCloudClient secondClient = DataCloud.create(PRODUCTION_CONFIG); // GH-90000
        try {
            assertThat(runPromise(() -> secondClient.findById(tenantId, "documents", entityId))) // GH-90000
                .isPresent() // GH-90000
                .hasValueSatisfying(entity -> assertThat(entity.id()).isEqualTo(entityId)); // GH-90000

            List<DataCloudClient.Event> events = runPromise(() -> secondClient.queryEvents( // GH-90000
                tenantId,
                DataCloudClient.EventQuery.byType(eventType) // GH-90000
            ));

            assertThat(events).singleElement().satisfies(event -> { // GH-90000
                assertThat(event.type()).isEqualTo(eventType); // GH-90000
                assertThat(event.payload()).containsEntry("entityId", entityId); // GH-90000
            });
        } finally {
            secondClient.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("production profile keeps Postgres and Kafka provider data tenant-isolated across restart [GH-90000]")
    void productionProfileKeepsProviderDataTenantIsolatedAcrossRestart() { // GH-90000
        String tenantA = "tenant-a-" + UUID.randomUUID(); // GH-90000
        String tenantB = "tenant-b-" + UUID.randomUUID(); // GH-90000
        String entityA = UUID.randomUUID().toString(); // GH-90000
        String entityB = UUID.randomUUID().toString(); // GH-90000
        String eventType = "workflow.executed." + UUID.randomUUID(); // GH-90000

        DataCloudClient firstClient = DataCloud.create(PRODUCTION_CONFIG); // GH-90000
        try {
            runPromise(() -> firstClient.save(tenantA, "workflows", Map.of("id", entityA, "name", "Tenant A workflow"))); // GH-90000
            runPromise(() -> firstClient.save(tenantB, "workflows", Map.of("id", entityB, "name", "Tenant B workflow"))); // GH-90000
            runPromise(() -> firstClient.appendEvent(tenantA, DataCloudClient.Event.of(eventType, Map.of("entityId", entityA)))); // GH-90000
            runPromise(() -> firstClient.appendEvent(tenantB, DataCloudClient.Event.of(eventType, Map.of("entityId", entityB)))); // GH-90000
        } finally {
            firstClient.close(); // GH-90000
        }

        DataCloudClient secondClient = DataCloud.create(PRODUCTION_CONFIG); // GH-90000
        try {
            assertThat(runPromise(() -> secondClient.query(tenantA, "workflows", DataCloudClient.Query.all()))) // GH-90000
                .extracting(DataCloudClient.Entity::id) // GH-90000
                .containsExactly(entityA); // GH-90000
            assertThat(runPromise(() -> secondClient.query(tenantB, "workflows", DataCloudClient.Query.all()))) // GH-90000
                .extracting(DataCloudClient.Entity::id) // GH-90000
                .containsExactly(entityB); // GH-90000

            assertThat(runPromise(() -> secondClient.findById(tenantA, "workflows", entityB))).isEmpty(); // GH-90000
            assertThat(runPromise(() -> secondClient.findById(tenantB, "workflows", entityA))).isEmpty(); // GH-90000

            assertThat(runPromise(() -> secondClient.queryEvents(tenantA, DataCloudClient.EventQuery.byType(eventType)))) // GH-90000
                .singleElement() // GH-90000
                .extracting(event -> event.payload().get("entityId [GH-90000]"))
                .isEqualTo(entityA); // GH-90000
            assertThat(runPromise(() -> secondClient.queryEvents(tenantB, DataCloudClient.EventQuery.byType(eventType)))) // GH-90000
                .singleElement() // GH-90000
                .extracting(event -> event.payload().get("entityId [GH-90000]"))
                .isEqualTo(entityB); // GH-90000
        } finally {
            secondClient.close(); // GH-90000
        }
    }
}