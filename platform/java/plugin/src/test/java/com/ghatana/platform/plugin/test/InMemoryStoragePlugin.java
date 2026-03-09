package com.ghatana.platform.plugin.test;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.spi.StoragePlugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryStoragePlugin implements StoragePlugin<String> {

    private final Map<String, List<String>> storage = new ConcurrentHashMap<>();
    private final AtomicLong offsetCounter = new AtomicLong(0);
    private PluginState state = PluginState.UNLOADED;

    @Override
    public @NotNull PluginMetadata metadata() {
        return new PluginMetadata(
            "test-storage",
            "In-Memory Storage",
            "1.0.0",
            "Test storage",
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
        return state;
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
        state = PluginState.INITIALIZED;
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> start() {
        state = PluginState.RUNNING;
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> stop() {
        state = PluginState.STOPPED;
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Offset> write(@NotNull String record, @NotNull TenantId tenantId) {
        storage.computeIfAbsent(tenantId.toString(), k -> new ArrayList<>()).add(record);
        return Promise.of(Offset.of(offsetCounter.incrementAndGet()));
    }

    @Override
    public @NotNull Promise<List<String>> read(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset, int limit) {
        List<String> records = storage.getOrDefault(tenantId.toString(), List.of());
        return Promise.of(records);
    }

    @Override
    public @NotNull Promise<Void> delete(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset) {
        storage.remove(tenantId.toString());
        return Promise.complete();
    }
}
