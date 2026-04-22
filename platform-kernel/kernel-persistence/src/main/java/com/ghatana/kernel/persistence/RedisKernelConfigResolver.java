package com.ghatana.kernel.persistence;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.platform.cache.DistributedCachePort;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Distributed-cache backed {@link KernelConfigResolver}.
 *
 * <p>In production this is expected to be wired with {@code RedisDistributedCacheAdapter};
 * tests may use {@code InMemoryCacheAdapter}. Values are resolved with priority:
 * tenant key in distributed cache, then configured providers, then kernel defaults.
 *
 * @doc.type class
 * @doc.purpose Redis-backed kernel config resolver with provider and default fallback
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public class RedisKernelConfigResolver implements KernelConfigResolver {

    private final DistributedCachePort<String, Object> cache;
    private final Duration defaultTtl;
    private final List<ConfigProvider> providers = new CopyOnWriteArrayList<>();
    private final Map<String, Object> kernelDefaults = new ConcurrentHashMap<>();

    public RedisKernelConfigResolver(DistributedCachePort<String, Object> cache, Duration defaultTtl) {
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl cannot be null");
    }

    @Override
    public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) {
        return resolveOptional(configKey, type, context)
            .orElseThrow(() -> new IllegalArgumentException(
                "Configuration key not found: " + configKey + " for tenant " + context.getTenantId()));
    }

    @Override
    public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context) {
        return resolveOptional(configKey, type, context).orElse(defaultValue);
    }

    @Override
    public <T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context) {
        Objects.requireNonNull(configKey, "configKey cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        String cacheKey = key(context.getTenantId(), configKey);
        Optional<Object> cached = await(cache.get(cacheKey));
        if (cached.isPresent() && type.isInstance(cached.get())) {
            return Optional.of(type.cast(cached.get()));
        }

        for (ConfigProvider provider : providers) {
            Optional<T> value = provider.get(configKey, type, context);
            if (value.isPresent()) {
                await(cache.put(cacheKey, value.get(), defaultTtl));
                return value;
            }
        }

        Object fallback = kernelDefaults.get(configKey);
        if (type.isInstance(fallback)) {
            return Optional.of(type.cast(fallback));
        }

        return Optional.empty();
    }

    @Override
    public void addConfigProvider(ConfigProvider provider) {
        Objects.requireNonNull(provider, "provider cannot be null");
        providers.add(provider);
        providers.sort(Comparator.comparingInt(ConfigProvider::getPriority).reversed());
    }

    @Override
    public Promise<Void> reloadConfig(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        // Namespace-wide invalidation is safer than stale cross-key config reads.
        return cache.invalidateAll();
    }

    @Override
    public List<String> getAvailableKeys(KernelTenantContext context) {
        Set<String> keys = ConcurrentHashMap.newKeySet();
        keys.addAll(kernelDefaults.keySet());
        return new ArrayList<>(keys);
    }

    /** Sets a kernel-wide default value. */
    public void setKernelDefault(String key, Object value) {
        kernelDefaults.put(Objects.requireNonNull(key, "key cannot be null"), value);
    }

    /** Stores a tenant override directly in distributed cache. */
    public Promise<Void> putTenantOverride(String tenantId, String key, Object value) {
        return cache.put(key(tenantId, key), value, defaultTtl);
    }

    private static String key(String tenantId, String configKey) {
        return "kernel:cfg:" + tenantId + ":" + configKey;
    }

    private static <T> T await(Promise<T> promise) {
        try {
            return promise.toCompletableFuture().join();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to resolve async cache operation", exception);
        }
    }
}
