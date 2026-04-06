package com.ghatana.platform.plugin.impl;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.plugin.spi.StoragePlugin;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage plugin that routes data to different tiers (Hot, Warm, Cold) based on age.
 *
 * @param <T> The type of record
 * @doc.type class
 * @doc.purpose Tiered storage routing
 * @doc.layer core
 */
public class TieredStoragePlugin<T> implements StoragePlugin<T> {

    private final String hotPluginId;
    private final String warmPluginId;
    private final Duration hotTierThreshold;

    private StoragePlugin<T> hotPlugin;
    private StoragePlugin<T> warmPlugin;
    
    private PluginState state = PluginState.UNLOADED;

    public TieredStoragePlugin(String hotPluginId, String warmPluginId, Duration hotTierThreshold) {
        this.hotPluginId = hotPluginId;
        this.warmPluginId = warmPluginId;
        this.hotTierThreshold = hotTierThreshold;
    }

    @Override
    public @NotNull PluginMetadata metadata() {
        return new PluginMetadata(
            "tiered-storage",
            "Tiered Storage Router",
            "1.0.0",
            "Routes data between Hot and Warm tiers",
            PluginType.STORAGE,
            "Ghatana",
            "Proprietary",
            Set.of(),
            Map.of(),
            Set.of(),
            null
        );
    }

    @Override
    public @NotNull PluginState getState() {
        return state;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
        this.hotPlugin = (StoragePlugin<T>) context.findPlugin(hotPluginId)
                .orElseThrow(() -> new IllegalStateException("Hot tier plugin not found: " + hotPluginId));
        
        this.warmPlugin = (StoragePlugin<T>) context.findPlugin(warmPluginId)
                .orElseThrow(() -> new IllegalStateException("Warm tier plugin not found: " + warmPluginId));
        
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
    public @NotNull Promise<Offset> write(@NotNull T record, @NotNull TenantId tenantId) {
        // Always write to HOT tier
        return hotPlugin.write(record, tenantId);
    }

    @Override
    public @NotNull Promise<List<T>> read(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset, int limit) {
        // Try HOT first, then WARM if needed (simplified)
        // In reality, offset might indicate which tier, or we check both.
        // For now, check HOT. If empty or insufficient, check WARM.
        return hotPlugin.read(stream, tenantId, offset, limit)
                .then(hotData -> {
                    if (hotData.size() >= limit) {
                        return Promise.of(hotData);
                    }
                    // Fetch remaining from WARM
                    return warmPlugin.read(stream, tenantId, offset, limit - hotData.size())
                            .map(warmData -> {
                                List<T> combined = new ArrayList<>(hotData);
                                combined.addAll(warmData);
                                return combined;
                            });
                });
    }

    @Override
    public @NotNull Promise<List<T>> readByTime(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Instant startTime, @NotNull Instant endTime, int limit) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(hotTierThreshold);

        if (startTime.isAfter(cutoff)) {
            // Fully in HOT tier
            return hotPlugin.readByTime(stream, tenantId, startTime, endTime, limit);
        } else if (endTime.isBefore(cutoff)) {
            // Fully in WARM tier
            return warmPlugin.readByTime(stream, tenantId, startTime, endTime, limit);
        } else {
            // Spans both
            return hotPlugin.readByTime(stream, tenantId, cutoff, endTime, limit)
                    .then(hotData -> {
                        if (hotData.size() >= limit) {
                            return Promise.of(hotData);
                        }
                        return warmPlugin.readByTime(stream, tenantId, startTime, cutoff, limit - hotData.size())
                                .map(warmData -> {
                                    List<T> combined = new ArrayList<>(warmData); // Warm is older, so it comes first? Depends on sort order.
                                    // Assuming time ascending: Warm (older) -> Hot (newer)
                                    combined.addAll(hotData);
                                    return combined;
                                });
                    });
        }
    }

    @Override
    public @NotNull Promise<Void> delete(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset) {
        return Promises.all(
            hotPlugin.delete(stream, tenantId, offset),
            warmPlugin.delete(stream, tenantId, offset)
        );
    }
}
