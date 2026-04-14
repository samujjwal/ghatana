package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
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

    @Test
    @DisplayName("production profile fails fast when no durable entity store is registered")
    void productionProfileFailsFastWhenNoDurableEntityStoreIsRegistered() {
        DataCloudConfig config = DataCloudConfig.builder()
            .profile(DataCloudProfile.PRODUCTION)
            .build();

        assertThatThrownBy(() -> DataCloud.create(config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No durable EntityStore provider found");
    }

    @Test
    @DisplayName("local profile falls back to in-memory stores")
    void localProfileFallsBackToInMemoryStores() {
        DataCloudClient client = DataCloud.create(DataCloudConfig.builder()
            .profile(DataCloudProfile.LOCAL)
            .build());

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


