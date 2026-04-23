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

    private final Map<String, List<String>> storage = new ConcurrentHashMap<>(); // GH-90000
    private final AtomicLong offsetCounter = new AtomicLong(0); // GH-90000
    private PluginState state = PluginState.UNLOADED;

    @Override
    public @NotNull PluginMetadata metadata() { // GH-90000
        return new PluginMetadata( // GH-90000
            "test-storage",
            "In-Memory Storage",
            "1.0.0",
            "Test storage",
            PluginType.STORAGE,
            "Test",
            "MIT",
            Set.of(), // GH-90000
            Map.of(), // GH-90000
            Set.of(), // GH-90000
            PluginCompatibility.dataCloudVersion("1.0.0")
        );
    }

    @Override
    public @NotNull PluginState getState() { // GH-90000
        return state;
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull PluginContext context) { // GH-90000
        state = PluginState.INITIALIZED;
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> start() { // GH-90000
        state = PluginState.RUNNING;
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> stop() { // GH-90000
        state = PluginState.STOPPED;
        return Promise.complete(); // GH-90000
    }

    @Override
    public @NotNull Promise<Offset> write(@NotNull String record, @NotNull TenantId tenantId) { // GH-90000
        storage.computeIfAbsent(tenantId.toString(), k -> new ArrayList<>()).add(record); // GH-90000
        return Promise.of(Offset.of(offsetCounter.incrementAndGet())); // GH-90000
    }

    @Override
    public @NotNull Promise<List<String>> read(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset, int limit) { // GH-90000
        List<String> records = storage.getOrDefault(tenantId.toString(), List.of()); // GH-90000
        return Promise.of(records); // GH-90000
    }

    @Override
    public @NotNull Promise<Void> delete(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset) { // GH-90000
        storage.remove(tenantId.toString()); // GH-90000
        return Promise.complete(); // GH-90000
    }
}
