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
        private final Map<String, Object> storage = new ConcurrentHashMap<>(); 

        @Override
        public Promise<Optional<Object>> get(String key) { 
            return Promise.of(Optional.ofNullable(storage.get(key))); 
        }

        @Override
        public Promise<Object> getOrLoad(String key, java.util.function.Function<String, Promise<Object>> loader) {
            Object existing = storage.get(key);
            if (existing != null) {
                return Promise.of(existing);
            }
            return loader.apply(key).then(value -> {
                storage.put(key, value);
                return Promise.of(value);
            });
        }

        @Override
        public Promise<Void> put(String key, Object value) { 
            storage.put(key, value); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> put(String key, Object value, Duration ttl) { 
            storage.put(key, value); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> invalidate(String key) { 
            storage.remove(key); 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> invalidateAll() { 
            storage.clear(); 
            return Promise.complete(); 
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }

    private RedisKernelConfigResolver resolver;
    private KernelTenantContext tenant;

    @BeforeEach
    void setUp() { 
        resolver = new RedisKernelConfigResolver( 
            new InMemoryDistributedCachePort(), 
            Duration.ofMinutes(5) 
        );
        tenant = new KernelTenantContext( 
            "tenant-1",
            KernelTenantContext.TenantType.STANDARD,
            Map.of(), 
            Set.of(), 
            null,
            Runnable::run
        );
    }

    @Test
    @DisplayName("resolves tenant override first")
    void resolvesTenantOverride() { 
        resolver.putTenantOverride("tenant-1", "feature.flag", true).toCompletableFuture().join(); 
        Boolean value = resolver.resolve("feature.flag", Boolean.class, tenant); 
        assertTrue(value); 
    }

    @Test
    @DisplayName("falls back to kernel default")
    void fallsBackToDefault() { 
        resolver.setKernelDefault("risk.threshold", 0.9d); 
        Double threshold = resolver.resolve("risk.threshold", Double.class, tenant); 
        assertEquals(0.9d, threshold); 
    }

    @Test
    @DisplayName("returns optional empty when key missing")
    void optionalEmpty() { 
        Optional<String> missing = resolver.resolveOptional("missing.key", String.class, tenant); 
        assertTrue(missing.isEmpty()); 
    }
}
