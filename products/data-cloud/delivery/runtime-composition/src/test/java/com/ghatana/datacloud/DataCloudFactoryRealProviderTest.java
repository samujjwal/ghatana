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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
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
@Testcontainers(disabledWithoutDocker = true) 
@DisplayName("DataCloud Factory Real Provider Tests")
class DataCloudFactoryRealProviderTest extends EventloopTestBase {

    private static final DataCloudConfig PRODUCTION_CONFIG = DataCloudConfig.builder() 
        .profile(DataCloudProfile.PRODUCTION) 
        .build(); 

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("datacloud_factory_it")
        .withUsername("dc_factory")
        .withPassword("dc_factory_secret")
        .withReuse(true);

    @Container
    @SuppressWarnings("resource")
    private static final KafkaContainer KAFKA = new KafkaContainer( 
        DockerImageName.parse("apache/kafka-native:3.8.0")
    ).withReuse(true); 

    @BeforeAll
    static void migrateSchemaAndConfigureProviders() { 
        Flyway.configure() 
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()) 
            .locations("classpath:db/migration") 
            .load() 
            .migrate(); 

        System.setProperty("datacloud.db.url", POSTGRES.getJdbcUrl());
        System.setProperty("datacloud.db.user", POSTGRES.getUsername());
        System.setProperty("datacloud.db.password", POSTGRES.getPassword());
        // Strip protocol prefix from bootstrap servers (e.g., PLAINTEXT://host:port -> host:port)
        String bootstrapServers = KAFKA.getBootstrapServers().replaceAll("^[a-zA-Z]+://", "");
        System.setProperty("datacloud.kafka.bootstrapServers", bootstrapServers); 
    }

    @AfterEach
    void truncateEntities() throws Exception { 
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement()) { 
            statement.execute("TRUNCATE TABLE entities CASCADE");
        }
    }

    @AfterEach
    void clearOptionalProviderOverrides() { 
        System.clearProperty("datacloud.db.poolMaxSize");
        System.clearProperty("datacloud.db.poolMinIdle");
        System.clearProperty("datacloud.db.connectionTimeoutMs");
        System.clearProperty("datacloud.db.idleTimeoutMs");
        System.clearProperty("datacloud.db.maxLifetimeMs");
    }

    @AfterAll
    static void clearProviderBootstrapProperties() { 
        System.clearProperty("datacloud.db.url");
        System.clearProperty("datacloud.db.user");
        System.clearProperty("datacloud.db.password");
        System.clearProperty("datacloud.kafka.bootstrapServers");
    }

    @Test
    @DisplayName("production profile persists entities and events across restart with service-loaded providers")
    void productionProfilePersistsEntitiesAndEventsAcrossRestartWithServiceLoadedProviders() { 
        String tenantId = "tenant-provider-" + UUID.randomUUID(); 
        String entityId = UUID.randomUUID().toString(); 
        String eventType = "document.created." + UUID.randomUUID(); 

        DataCloudClient firstClient = DataCloud.create(PRODUCTION_CONFIG); 
        try {
            assertThat(firstClient.entityStore().getClass().getName()).contains("PostgresEntityStore");
            assertThat(firstClient.eventLogStore().getClass().getName()).doesNotContain("InMemoryEventLogStore");

            runPromise(() -> firstClient.save( 
                tenantId,
                "documents",
                Map.of("id", entityId, "title", "Provider-backed manifest") 
            ));
            runPromise(() -> firstClient.appendEvent( 
                tenantId,
                DataCloudClient.Event.builder()
                    .type(eventType)
                    .payload(Map.of("entityId", entityId, "tenantId", tenantId))
                    .source("data-cloud-factory-real-provider-test")
                    .build()
            ));
        } finally {
            firstClient.close(); 
        }

        DataCloudClient secondClient = DataCloud.create(PRODUCTION_CONFIG); 
        try {
            assertThat(runPromise(() -> secondClient.findById(tenantId, "documents", entityId))) 
                .isPresent() 
                .hasValueSatisfying(entity -> assertThat(entity.id()).isEqualTo(entityId)); 

            List<DataCloudClient.Event> events = runPromise(() -> secondClient.queryEvents( 
                tenantId,
                DataCloudClient.EventQuery.byType(eventType) 
            ));

            assertThat(events).singleElement().satisfies(event -> { 
                assertThat(event.type()).isEqualTo(eventType); 
                assertThat(event.payload()).containsEntry("entityId", entityId); 
            });
        } finally {
            secondClient.close(); 
        }
    }

    @Test
    @DisplayName("production profile keeps Postgres and Kafka provider data tenant-isolated across restart")
    void productionProfileKeepsProviderDataTenantIsolatedAcrossRestart() { 
        String tenantA = "tenant-a-" + UUID.randomUUID(); 
        String tenantB = "tenant-b-" + UUID.randomUUID(); 
        String entityA = UUID.randomUUID().toString(); 
        String entityB = UUID.randomUUID().toString(); 
        String eventType = "workflow.executed." + UUID.randomUUID(); 

        DataCloudClient firstClient = DataCloud.create(PRODUCTION_CONFIG); 
        try {
            runPromise(() -> firstClient.save(tenantA, "workflows", Map.of("id", entityA, "name", "Tenant A workflow"))); 
            runPromise(() -> firstClient.save(tenantB, "workflows", Map.of("id", entityB, "name", "Tenant B workflow"))); 
            runPromise(() -> firstClient.appendEvent(
                tenantA,
                DataCloudClient.Event.builder()
                    .type(eventType)
                    .payload(Map.of("entityId", entityA))
                    .source("data-cloud-factory-real-provider-test")
                    .build()));
            runPromise(() -> firstClient.appendEvent(
                tenantB,
                DataCloudClient.Event.builder()
                    .type(eventType)
                    .payload(Map.of("entityId", entityB))
                    .source("data-cloud-factory-real-provider-test")
                    .build()));
        } finally {
            firstClient.close(); 
        }

        DataCloudClient secondClient = DataCloud.create(PRODUCTION_CONFIG); 
        try {
            assertThat(runPromise(() -> secondClient.query(tenantA, "workflows", DataCloudClient.Query.all()))) 
                .extracting(DataCloudClient.Entity::id) 
                .containsExactly(entityA); 
            assertThat(runPromise(() -> secondClient.query(tenantB, "workflows", DataCloudClient.Query.all()))) 
                .extracting(DataCloudClient.Entity::id) 
                .containsExactly(entityB); 

            assertThat(runPromise(() -> secondClient.findById(tenantA, "workflows", entityB))).isEmpty(); 
            assertThat(runPromise(() -> secondClient.findById(tenantB, "workflows", entityA))).isEmpty(); 

            assertThat(runPromise(() -> secondClient.queryEvents(tenantA, DataCloudClient.EventQuery.byType(eventType)))) 
                .singleElement() 
                .extracting(event -> event.payload().get("entityId"))
                .isEqualTo(entityA); 
            assertThat(runPromise(() -> secondClient.queryEvents(tenantB, DataCloudClient.EventQuery.byType(eventType)))) 
                .singleElement() 
                .extracting(event -> event.payload().get("entityId"))
                .isEqualTo(entityB); 
        } finally {
            secondClient.close(); 
        }
    }
}
