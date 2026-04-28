package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for DataCloud factory behavior and profile-gated store discovery.
 *
 * @doc.type class
 * @doc.purpose Verifies DataCloud factory startup semantics across profiles
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloud Factory Tests")
class DataCloudFactoryTest extends EventloopTestBase {

    private static final DataCloudConfig LOCAL_CONFIG = DataCloudConfig.builder() // GH-90000
        .profile(DataCloudProfile.LOCAL) // GH-90000
        .build(); // GH-90000

    private static final DataCloudConfig PRODUCTION_CONFIG = DataCloudConfig.builder() // GH-90000
        .profile(DataCloudProfile.PRODUCTION) // GH-90000
        .build(); // GH-90000

    @TempDir
    Path tempDir;

    @AfterEach
    void clearKafkaBootstrapOverride() { // GH-90000
        System.clearProperty("datacloud.kafka.bootstrapServers");
    }

    @Test
    @DisplayName("production profile fails fast when no durable entity store is registered")
    void productionProfileFailsFastWhenNoDurableEntityStoreIsRegistered() { // GH-90000
        assertThatThrownBy(() -> DataCloud.discoverEntityStore(PRODUCTION_CONFIG, java.util.Optional.empty())) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("No durable EntityStore provider found");
    }

    @Test
    @DisplayName("entity store discovery uses registered provider when present")
    void entityStoreDiscoveryUsesRegisteredProviderWhenPresent() { // GH-90000
        DataCloudClient client = DataCloud.forTesting(); // GH-90000

        try {
            EntityStore entityStore = client.entityStore(); // GH-90000

            assertThat(DataCloud.discoverEntityStore(PRODUCTION_CONFIG, java.util.Optional.of(entityStore))) // GH-90000
                .isSameAs(entityStore); // GH-90000
        } finally {
            client.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("entity store discovery prefers in-memory in local profile even when a provider is present")
    void entityStoreDiscoveryPrefersInMemoryInLocalProfileEvenWhenAProviderIsPresent() { // GH-90000
        assertThat(DataCloud.discoverEntityStore(LOCAL_CONFIG, java.util.Optional.of(mock(EntityStore.class)))) // GH-90000
            .extracting(store -> store.getClass().getName()) // GH-90000
            .asString() // GH-90000
            .contains("InMemoryEntityStore");
    }

    @Test
    @DisplayName("event log discovery fails fast when no durable store is registered")
    void eventLogDiscoveryFailsFastWhenNoDurableStoreIsRegistered() { // GH-90000
        assertThatThrownBy(() -> DataCloud.discoverEventLogStore(PRODUCTION_CONFIG, java.util.Optional.empty())) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("No durable EventLogStore provider found");
    }

    @Test
    @DisplayName("event log discovery uses registered provider when present")
    void eventLogDiscoveryUsesRegisteredProviderWhenPresent() { // GH-90000
        DataCloudClient client = DataCloud.forTesting(); // GH-90000

        try {
            EventLogStore eventLogStore = client.eventLogStore(); // GH-90000

            assertThat(DataCloud.discoverEventLogStore(PRODUCTION_CONFIG, java.util.Optional.of(eventLogStore))) // GH-90000
                .isSameAs(eventLogStore); // GH-90000
        } finally {
            client.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("event log discovery prefers in-memory in local profile even when a provider is present")
    void eventLogDiscoveryPrefersInMemoryInLocalProfileEvenWhenAProviderIsPresent() { // GH-90000
        assertThat(DataCloud.discoverEventLogStore(LOCAL_CONFIG, java.util.Optional.of(mock(EventLogStore.class)))) // GH-90000
            .extracting(store -> store.getClass().getName()) // GH-90000
            .asString() // GH-90000
            .contains("InMemoryEventLogStore");
    }

    @Test
    @DisplayName("event log discovery adapts a legacy SPI provider when the platform provider is absent")
    void eventLogDiscoveryAdaptsLegacySpiProviderWhenPresent() { // GH-90000
        com.ghatana.datacloud.spi.EventLogStore legacyStore = mock(com.ghatana.datacloud.spi.EventLogStore.class); // GH-90000
        when(legacyStore.getLatestOffset(argThat(tenant -> "tenant-adapter".equals(tenant.tenantId())))) // GH-90000
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(12L))); // GH-90000

        EventLogStore discovered = DataCloud.discoverEventLogStore( // GH-90000
            PRODUCTION_CONFIG,
            Optional.empty(), // GH-90000
            Optional.of(legacyStore) // GH-90000
        );

        com.ghatana.platform.types.identity.Offset latest = runPromise(() -> discovered.getLatestOffset( // GH-90000
            new com.ghatana.datacloud.spi.TenantContext("tenant-adapter", null, Map.of()) // GH-90000
        ));

        assertThat(latest.value()).isEqualTo("12");
        verify(legacyStore).getLatestOffset(argThat(tenant -> "tenant-adapter".equals(tenant.tenantId()))); // GH-90000
    }

    @Test
    @DisplayName("production profile discovers Kafka legacy provider via ServiceLoader")
    @org.junit.jupiter.api.Disabled("Requires Kafka infrastructure via ServiceLoader - enable when Kafka provider is available")
    void productionProfileDiscoversKafkaLegacyProviderViaServiceLoader() { // GH-90000
        System.setProperty("datacloud.kafka.bootstrapServers", "localhost:19092"); // GH-90000

        EventLogStore discovered = DataCloud.discoverEventLogStore(PRODUCTION_CONFIG); // GH-90000

        assertThat(discovered.getClass().getName()) // GH-90000
            .doesNotContain("InMemoryEventLogStore")
            .contains("EventLogStoreAdapters");
    }

    @Test
    @DisplayName("local profile falls back to in-memory stores")
    void localProfileFallsBackToInMemoryStores() { // GH-90000
        DataCloudClient client = DataCloud.create(LOCAL_CONFIG); // GH-90000

        try {
            assertThat(client.entityStore().getClass().getName()) // GH-90000
                .contains("InMemoryEntityStore");
            assertThat(client.eventLogStore().getClass().getName()) // GH-90000
                .contains("InMemoryEventLogStore");

            DataCloudClient.Entity saved = runPromise(() -> client.save( // GH-90000
                "tenant-a",
                "users",
                Map.<String, Object>of("id", "u-1", "name", "Ada") // GH-90000
            ));
            assertThat(saved.id()).isEqualTo("u-1");

            runPromise(() -> client.appendEvent( // GH-90000
                "tenant-a",
                DataCloudClient.Event.of("user.created", Map.of("userId", "u-1")) // GH-90000
            ));

            List<DataCloudClient.Event> events = runPromise(() -> client.queryEvents( // GH-90000
                "tenant-a",
                DataCloudClient.EventQuery.byType("user.created")
            ));
            assertThat(events).hasSize(1); // GH-90000
            assertThat(events.get(0).type()).isEqualTo("user.created");
        } finally {
            client.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("forTesting uses the explicit in-memory test path")
    void forTestingUsesTheExplicitInMemoryTestPath() { // GH-90000
        DataCloudClient client = DataCloud.forTesting(); // GH-90000

        try {
            assertThat(client.entityStore().getClass().getName()) // GH-90000
                .contains("InMemoryEntityStore");
            assertThat(client.eventLogStore().getClass().getName()) // GH-90000
                .contains("InMemoryEventLogStore");

            DataCloudClient.Entity saved = runPromise(() -> client.save( // GH-90000
                "tenant-b",
                "items",
                Map.<String, Object>of("id", "item-1", "kind", "demo") // GH-90000
            ));
            assertThat(saved.id()).isEqualTo("item-1");
        } finally {
            client.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("sovereign profile uses file-backed H2 stores and persists across restart")
    void sovereignProfileUsesFileBackedStoresAndPersistsAcrossRestart() { // GH-90000
        DataCloudConfig sovereignConfig = DataCloudConfig.builder() // GH-90000
            .profile(DataCloudProfile.SOVEREIGN) // GH-90000
            .customConfig(Map.of("sovereign.dataDir", tempDir.toString())) // GH-90000
            .build(); // GH-90000

        DataCloudClient firstClient = DataCloud.create(sovereignConfig); // GH-90000
        try {
            assertThat(firstClient.entityStore().getClass().getName()).contains("H2SovereignEntityStore");
            assertThat(firstClient.eventLogStore().getClass().getName()).contains("H2SovereignEventLogStore");

            runPromise(() -> firstClient.save( // GH-90000
                "tenant-sovereign",
                "documents",
                Map.<String, Object>of("id", "doc-1", "title", "Manifest"))); // GH-90000
            runPromise(() -> firstClient.appendEvent( // GH-90000
                "tenant-sovereign",
                DataCloudClient.Event.of("document.created", Map.of("entityId", "doc-1")))); // GH-90000
        } finally {
            firstClient.close(); // GH-90000
        }

        DataCloudClient secondClient = DataCloud.create(sovereignConfig); // GH-90000
        try {
            java.util.Optional<DataCloudClient.Entity> entity = runPromise(() -> secondClient.findById( // GH-90000
                "tenant-sovereign",
                "documents",
                "doc-1"));
            List<DataCloudClient.Event> events = runPromise(() -> secondClient.queryEvents( // GH-90000
                "tenant-sovereign",
                DataCloudClient.EventQuery.byType("document.created")));

            assertThat(entity).isPresent(); // GH-90000
            assertThat(entity.orElseThrow().id()).isEqualTo("doc-1");
            assertThat(events).hasSize(1); // GH-90000
            assertThat(events.get(0).type()).isEqualTo("document.created");
        } finally {
            secondClient.close(); // GH-90000
        }
    }
}
