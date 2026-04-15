package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("production profile fails fast when no durable entity store is registered")
    void productionProfileFailsFastWhenNoDurableEntityStoreIsRegistered() {
        assertThatThrownBy(() -> DataCloud.create(PRODUCTION_CONFIG))
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
    @DisplayName("entity store discovery falls back to in-memory in local profile")
    void entityStoreDiscoveryFallsBackToInMemoryInLocalProfile() {
        assertThat(DataCloud.discoverEntityStore(LOCAL_CONFIG, java.util.Optional.empty()))
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
    @DisplayName("event log discovery falls back to in-memory in local profile")
    void eventLogDiscoveryFallsBackToInMemoryInLocalProfile() {
        assertThat(DataCloud.discoverEventLogStore(LOCAL_CONFIG, java.util.Optional.empty()))
            .extracting(store -> store.getClass().getName())
            .asString()
            .contains("InMemoryEventLogStore");
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
                DataCloudClient.Event.of("user.created", Map.of("userId", "u-1"))
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
}
