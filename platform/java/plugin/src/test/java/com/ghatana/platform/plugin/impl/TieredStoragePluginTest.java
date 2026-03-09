package com.ghatana.platform.plugin.impl;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.spi.StoragePlugin;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tiered Storage Plugin Tests")
class TieredStoragePluginTest extends EventloopTestBase {

    private static final TenantId TENANT = TenantId.random();
    private static final String STREAM = "test-stream";

    @Test
    void shouldRouteToHotStorageWhenTimeIsRecent() {
        // GIVEN
        MockStoragePlugin hot = new MockStoragePlugin("hot");
        MockStoragePlugin warm = new MockStoragePlugin("warm");
        MockPluginContext context = new MockPluginContext(Map.of("hot", hot, "warm", warm));
        
        Duration retention = Duration.ofHours(1);
        
        TieredStoragePlugin<String> tiered = new TieredStoragePlugin<>("hot", "warm", retention);
        runPromise(() -> tiered.initialize(context));
        
        Instant now = Instant.now();
        Instant startTime = now.minus(Duration.ofMinutes(30)); // Within 1 hour
        Instant endTime = now;

        // WHEN
        List<String> result = runPromise(() -> tiered.readByTime(STREAM, TENANT, startTime, endTime, 10));

        // THEN
        assertTrue(hot.readByTimeCalled.get(), "Hot storage should be called");
        assertFalse(warm.readByTimeCalled.get(), "Warm storage should NOT be called");
        assertEquals(Collections.singletonList("hot-data"), result);
    }

    @Test
    void shouldRouteToWarmStorageWhenTimeIsOld() {
        // GIVEN
        MockStoragePlugin hot = new MockStoragePlugin("hot");
        MockStoragePlugin warm = new MockStoragePlugin("warm");
        MockPluginContext context = new MockPluginContext(Map.of("hot", hot, "warm", warm));
        
        Duration retention = Duration.ofHours(1);
        
        TieredStoragePlugin<String> tiered = new TieredStoragePlugin<>("hot", "warm", retention);
        runPromise(() -> tiered.initialize(context));
        
        Instant now = Instant.now();
        Instant startTime = now.minus(Duration.ofHours(2)); // Older than 1 hour
        Instant endTime = now.minus(Duration.ofHours(1).plusMinutes(1));

        // WHEN
        List<String> result = runPromise(() -> tiered.readByTime(STREAM, TENANT, startTime, endTime, 10));

        // THEN
        assertFalse(hot.readByTimeCalled.get(), "Hot storage should NOT be called");
        assertTrue(warm.readByTimeCalled.get(), "Warm storage should be called");
        assertEquals(Collections.singletonList("warm-data"), result);
    }

    @Test
    void shouldWriteToHotStorageOnly() {
        // GIVEN
        MockStoragePlugin hot = new MockStoragePlugin("hot");
        MockStoragePlugin warm = new MockStoragePlugin("warm");
        MockPluginContext context = new MockPluginContext(Map.of("hot", hot, "warm", warm));
        
        Duration retention = Duration.ofHours(1);
        
        TieredStoragePlugin<String> tiered = new TieredStoragePlugin<>("hot", "warm", retention);
        runPromise(() -> tiered.initialize(context));

        // WHEN
        Offset result = runPromise(() -> tiered.write("data", TENANT));

        // THEN
        assertTrue(hot.writeCalled.get(), "Hot storage should be written to");
        assertFalse(warm.writeCalled.get(), "Warm storage should NOT be written to");
        assertNotNull(result);
    }

    // Mock Context
    static class MockPluginContext implements PluginContext {
        private final Map<String, Plugin> plugins;

        MockPluginContext(Map<String, Plugin> plugins) {
            this.plugins = plugins;
        }

        @Override
        public @Nullable <T> T getConfig(@NotNull Class<T> configType) {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public @NotNull <T extends Plugin> Optional<T> findPlugin(@NotNull String pluginId) {
            return Optional.ofNullable((T) plugins.get(pluginId));
        }

        @Override
        public @NotNull List<Plugin> findPluginsByCapability(@NotNull Class<? extends PluginCapability> capability) {
            return Collections.emptyList();
        }

        @Override
        public @NotNull PluginInteractionBus getInteractionBus() {
            return new PluginInteractionBus() {
                @Override
                public @NotNull <Req, Res> Promise<Res> request(@NotNull String targetPluginId, @NotNull Req request, @NotNull Class<Res> responseType, @NotNull Duration timeout) {
                    return Promise.of(null);
                }

                @Override
                public void publish(@NotNull String topic, @NotNull Object event) {}

                @Override
                public void subscribe(@NotNull String topic, @NotNull Consumer<Object> listener) {}
            };
        }
    }

    // Mock Implementation
    static class MockStoragePlugin implements StoragePlugin<String> {
        final String name;
        final AtomicBoolean readByTimeCalled = new AtomicBoolean(false);
        final AtomicBoolean writeCalled = new AtomicBoolean(false);

        MockStoragePlugin(String name) {
            this.name = name;
        }

        @Override
        public @NotNull Promise<Offset> write(@NotNull String record, @NotNull TenantId tenantId) {
            writeCalled.set(true);
            return Promise.of(Offset.of(1));
        }

        @Override
        public @NotNull Promise<List<String>> read(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset, int limit) {
            return Promise.of(Collections.emptyList());
        }

        @Override
        public @NotNull Promise<List<String>> readByTime(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Instant startTime, @NotNull Instant endTime, int limit) {
            readByTimeCalled.set(true);
            return Promise.of(Collections.singletonList(name + "-data"));
        }

        @Override
        public @NotNull Promise<Void> delete(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset) {
            return Promise.complete();
        }

        @Override
        public @NotNull PluginMetadata metadata() {
            return new PluginMetadata(
                name,
                name,
                "1.0.0",
                "Mock Plugin",
                PluginType.STORAGE,
                "Test",
                "MIT",
                Set.of(),
                Map.of(),
                Set.of(),
                PluginCompatibility.dataCloudVersion("1.0.0")
            );
        }

        @Override
        public @NotNull PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> stop() {
            return Promise.complete();
        }
    }
}
