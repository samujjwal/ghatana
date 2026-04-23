package com.ghatana.kernel.persistence;

import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.platform.cache.DistributedCachePort;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests distributed-cache-backed kernel config resolver behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RedisKernelConfigResolver")
class RedisKernelConfigResolverTest {

    private static final class InMemoryDistributedCachePort implements DistributedCachePort<String, Object> {
        private final Map<String, Object> storage = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public Promise<Optional<Object>> get(String key) { // GH-90000
            return Promise.of(Optional.ofNullable(storage.get(key))); // GH-90000
        }

        @Override
        public Promise<Void> put(String key, Object value, Duration ttl) { // GH-90000
            storage.put(key, value); // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> invalidateAll() { // GH-90000
            storage.clear(); // GH-90000
            return Promise.complete(); // GH-90000
        }
    }

    private RedisKernelConfigResolver resolver;
    private KernelTenantContext tenant;

    @BeforeEach
    void setUp() { // GH-90000
        resolver = new RedisKernelConfigResolver( // GH-90000
            new InMemoryDistributedCachePort(), // GH-90000
            Duration.ofMinutes(5) // GH-90000
        );
        tenant = new KernelTenantContext( // GH-90000
            "tenant-1",
            KernelTenantContext.TenantType.STANDARD,
            Map.of(), // GH-90000
            Set.of(), // GH-90000
            null,
            Runnable::run
        );
    }

    @Test
    @DisplayName("resolves tenant override first")
    void resolvesTenantOverride() { // GH-90000
        resolver.putTenantOverride("tenant-1", "feature.flag", true).toCompletableFuture().join(); // GH-90000
        Boolean value = resolver.resolve("feature.flag", Boolean.class, tenant); // GH-90000
        assertTrue(value); // GH-90000
    }

    @Test
    @DisplayName("falls back to kernel default")
    void fallsBackToDefault() { // GH-90000
        resolver.setKernelDefault("risk.threshold", 0.9d); // GH-90000
        Double threshold = resolver.resolve("risk.threshold", Double.class, tenant); // GH-90000
        assertEquals(0.9d, threshold); // GH-90000
    }

    @Test
    @DisplayName("returns optional empty when key missing")
    void optionalEmpty() { // GH-90000
        Optional<String> missing = resolver.resolveOptional("missing.key", String.class, tenant); // GH-90000
        assertTrue(missing.isEmpty()); // GH-90000
    }
}
