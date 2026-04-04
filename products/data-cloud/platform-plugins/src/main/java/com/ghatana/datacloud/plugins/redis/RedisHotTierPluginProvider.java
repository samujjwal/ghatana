package com.ghatana.datacloud.plugins.redis;

import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginProvider;
import com.ghatana.platform.plugin.PluginType;
import org.jetbrains.annotations.NotNull;

/**
 * ServiceLoader provider for Redis hot tier plugin.
 * 
 * <p>This provider enables automatic discovery of the Redis
 * hot tier storage plugin via Java's ServiceLoader mechanism.
 *
 * @doc.type class
 * @doc.purpose ServiceLoader provider for Redis plugin
 * @doc.layer plugin
 * @doc.pattern Provider, Factory
 */
public class RedisHotTierPluginProvider implements PluginProvider {

    @Override
    public @NotNull Plugin createPlugin() {
        return new RedisHotTierPlugin();
    }

    @Override
    public @NotNull PluginMetadata getMetadata() {
        return PluginMetadata.builder()
            .id("redis-m0-hot-tier")
            .name("Redis M0 Hot Tier Plugin")
            .version("1.0.0")
            .vendor("Ghatana")
            .description("Redis M0 (HOT tier) storage using Redis Streams")
            .type(PluginType.STORAGE)
            .build();
    }

    @Override
    public int priority() {
        return 200; // Highest priority for hot tier
    }
}
