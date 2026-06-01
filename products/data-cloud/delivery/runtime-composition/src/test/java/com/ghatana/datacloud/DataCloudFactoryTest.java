package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.datacloud.storage.H2SovereignEventLogStore;
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

    private static final DataCloudConfig LOCAL_CONFIG = DataCloudConfig.builder() 
        .profile(DataCloudProfile.LOCAL) 
        .build(); 

    private static final DataCloudConfig PRODUCTION_CONFIG = DataCloudConfig.builder() 
        .profile(DataCloudProfile.PRODUCTION) 
        .build(); 

    @TempDir
    Path tempDir;

    @AfterEach
    void clearKafkaBootstrapOverride() { 
        System.clearProperty("datacloud.kafka.bootstrapServers");
    }

    @Test
    @DisplayName("production profile fails fast when no durable entity store is registered")
    void productionProfileFailsFastWhenNoDurableEntityStoreIsRegistered() { 
        assertThatThrownBy(() -> DataCloud.discoverEntityStore(PRODUCTION_CONFIG, java.util.Optional.empty())) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("No durable EntityStore provider found");
    }

    @Test
    @DisplayName("entity store discovery uses registered provider when present")
    void entityStoreDiscoveryUsesRegisteredProviderWhenPresent() { 
        DataCloudClient client = DataCloud.forTesting(); 

        try {
            EntityStore entityStore = client.entityStore(); 

            assertThat(DataCloud.discoverEntityStore(PRODUCTION_CONFIG, java.util.Optional.of(entityStore))) 
                .isSameAs(entityStore); 
        } finally {
            client.close(); 
        }
    }

    @Test
    @DisplayName("entity store discovery prefers in-memory in local profile even when a provider is present")
    void entityStoreDiscoveryPrefersInMemoryInLocalProfileEvenWhenAProviderIsPresent() { 
        assertThat(DataCloud.discoverEntityStore(LOCAL_CONFIG, java.util.Optional.of(mock(EntityStore.class)))) 
            .extracting(store -> store.getClass().getName()) 
            .asString() 
            .contains("InMemoryEntityStore");
    }

    @Test
    @DisplayName("event log discovery fails fast when no durable store is registered")
    void eventLogDiscoveryFailsFastWhenNoDurableStoreIsRegistered() { 
        assertThatThrownBy(() -> DataCloud.discoverEventLogStore(PRODUCTION_CONFIG, java.util.Optional.empty())) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("No durable EventLogStore provider found");
    }

    @Test
    @DisplayName("event log discovery uses registered provider when present")
    void eventLogDiscoveryUsesRegisteredProviderWhenPresent() { 
        DataCloudClient client = DataCloud.forTesting(); 

        try {
            EventLogStore eventLogStore = client.eventLogStore(); 

            assertThat(DataCloud.discoverEventLogStore(PRODUCTION_CONFIG, java.util.Optional.of(eventLogStore))) 
                .isSameAs(eventLogStore); 
        } finally {
            client.close(); 
        }
    }

    @Test
    @DisplayName("event log discovery prefers in-memory in local profile even when a provider is present")
    void eventLogDiscoveryPrefersInMemoryInLocalProfileEvenWhenAProviderIsPresent() { 
        assertThat(DataCloud.discoverEventLogStore(LOCAL_CONFIG, java.util.Optional.of(mock(EventLogStore.class)))) 
            .extracting(store -> store.getClass().getName()) 
            .asString() 
            .contains("InMemoryEventLogStore");
    }

    @Test
    @DisplayName("event log discovery adapts a legacy SPI provider when the platform provider is absent")
    void eventLogDiscoveryAdaptsLegacySpiProviderWhenPresent() { 
        com.ghatana.datacloud.spi.EventLogStore legacyStore = mock(com.ghatana.platform.domain.eventstore.EventLogStore.class); 
        when(legacyStore.getLatestOffset(argThat(tenant -> "tenant-adapter".equals(tenant.tenantId())))) 
            .thenReturn(Promise.of(com.ghatana.platform.types.identity.Offset.of(12L))); 

        EventLogStore discovered = DataCloud.discoverEventLogStore( 
            PRODUCTION_CONFIG,
            Optional.empty(), 
            Optional.of(legacyStore) 
        );

        com.ghatana.platform.types.identity.Offset latest = runPromise(() -> discovered.getLatestOffset( 
            new com.ghatana.platform.domain.eventstore.TenantContext("tenant-adapter", null, Map.of()) 
        ));

        assertThat(latest.value()).isEqualTo("12");
        verify(legacyStore).getLatestOffset(argThat(tenant -> "tenant-adapter".equals(tenant.tenantId()))); 
    }

    @Test
    @DisplayName("production profile discovers Kafka legacy provider via ServiceLoader")
    @org.junit.jupiter.api.Disabled("Requires Kafka infrastructure via ServiceLoader - enable when Kafka provider is available")
    void productionProfileDiscoversKafkaLegacyProviderViaServiceLoader() { 
        System.setProperty("datacloud.kafka.bootstrapServers", "localhost:19092"); 

        EventLogStore discovered = DataCloud.discoverEventLogStore(PRODUCTION_CONFIG); 

        assertThat(discovered.getClass().getName()) 
            .doesNotContain("InMemoryEventLogStore")
            .contains("EventLogStoreAdapters");
    }

    @Test
    @DisplayName("local profile falls back to in-memory stores")
    void localProfileFallsBackToInMemoryStores() { 
        DataCloudClient client = DataCloud.create(LOCAL_CONFIG); 

        try {
            assertThat(client.entityStore().getClass().getName()) 
                .contains("InMemoryEntityStore");
            assertThat(client.eventLogStore().getClass().getName()) 
                .contains("InMemoryEventLogStore");

            DataCloudClient.Entity saved = runPromise(() -> client.save( 
                "tenant-a",
                "users",
                Map.<String, Object>of("id", "u-1", "name", "Ada") 
            ));
            assertThat(saved.id()).isEqualTo("u-1");

            runPromise(() -> client.appendEvent( 
                "tenant-a",
                DataCloudClient.Event.builder()
                    .type("user.created")
                    .payload(Map.of("userId", "u-1"))
                    .build()
            ));

            List<DataCloudClient.Event> events = runPromise(() -> client.queryEvents( 
                "tenant-a",
                DataCloudClient.EventQuery.byType("user.created")
            ));
            assertThat(events).hasSize(1); 
            assertThat(events.get(0).type()).isEqualTo("user.created");
        } finally {
            client.close(); 
        }
    }

    @Test
    @DisplayName("forTesting uses the explicit in-memory test path")
    void forTestingUsesTheExplicitInMemoryTestPath() { 
        DataCloudClient client = DataCloud.forTesting(); 

        try {
            assertThat(client.entityStore().getClass().getName()) 
                .contains("InMemoryEntityStore");
            assertThat(client.eventLogStore().getClass().getName()) 
                .contains("InMemoryEventLogStore");

            DataCloudClient.Entity saved = runPromise(() -> client.save( 
                "tenant-b",
                "items",
                Map.<String, Object>of("id", "item-1", "kind", "demo") 
            ));
            assertThat(saved.id()).isEqualTo("item-1");
        } finally {
            client.close(); 
        }
    }

    @Test
    @DisplayName("sovereign profile uses file-backed H2 stores and persists across restart")
    void sovereignProfileUsesFileBackedStoresAndPersistsAcrossRestart() { 
        DataCloudConfig sovereignConfig = DataCloudConfig.builder() 
            .profile(DataCloudProfile.SOVEREIGN) 
            .customConfig(Map.of("sovereign.dataDir", tempDir.toString())) 
            .build(); 

        DataCloudClient firstClient = DataCloud.create(sovereignConfig); 
        try {
            assertThat(firstClient.entityStore().getClass().getName()).contains("H2SovereignEntityStore");
            assertThat(firstClient.eventLogStore().getClass().getName()).contains("H2SovereignEventLogStore");

            runPromise(() -> firstClient.save( 
                "tenant-sovereign",
                "documents",
                Map.<String, Object>of("id", "doc-1", "title", "Manifest"))); 
            runPromise(() -> firstClient.appendEvent( 
                "tenant-sovereign",
                DataCloudClient.Event.builder()
                    .type("document.created")
                    .payload(Map.of("entityId", "doc-1"))
                    .source("datacloud.factory-test")
                    .build())); 
        } finally {
            firstClient.close(); 
        }

        DataCloudClient secondClient = DataCloud.create(sovereignConfig); 
        try {
            java.util.Optional<DataCloudClient.Entity> entity = runPromise(() -> secondClient.findById( 
                "tenant-sovereign",
                "documents",
                "doc-1"));
            List<DataCloudClient.Event> events = runPromise(() -> secondClient.queryEvents( 
                "tenant-sovereign",
                DataCloudClient.EventQuery.byType("document.created")));

            assertThat(entity).isPresent(); 
            assertThat(entity.orElseThrow().id()).isEqualTo("doc-1");
            assertThat(events).hasSize(1); 
            assertThat(events.get(0).type()).isEqualTo("document.created");
        } finally {
            secondClient.close(); 
        }
    }

    @Test
    @DisplayName("sovereign profile applies tail polling configuration from custom config")
    void sovereignProfileAppliesTailPollingConfiguration() {
        DataCloudConfig sovereignConfig = DataCloudConfig.builder()
            .profile(DataCloudProfile.SOVEREIGN)
            .customConfig(Map.of(
                "sovereign.dataDir", tempDir.resolve("tail-config").toString(),
                "sovereign.tail.pollIntervalMs", 75,
                "sovereign.tail.maxSubscribers", 7,
                "sovereign.tail.maxBatchSize", 33,
                "sovereign.tail.maxBackoffMs", 5000))
            .build();

        DataCloudClient client = DataCloud.create(sovereignConfig);
        try {
            assertThat(client.eventLogStore()).isInstanceOf(H2SovereignEventLogStore.class);
            H2SovereignEventLogStore store = (H2SovereignEventLogStore) client.eventLogStore();
            Map<String, Object> snapshot = store.tailRuntimeSnapshot();

            assertThat(snapshot.get("pollIntervalMs")).isEqualTo(75L);
            assertThat(snapshot.get("maxSubscribers")).isEqualTo(7);
            assertThat(snapshot.get("maxBatchSize")).isEqualTo(33);
            assertThat(snapshot.get("maxBackoffMs")).isEqualTo(5000L);
        } finally {
            client.close();
        }
    }
}
