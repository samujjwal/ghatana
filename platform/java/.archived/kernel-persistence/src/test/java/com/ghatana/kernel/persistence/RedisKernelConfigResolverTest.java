package com.ghatana.kernel.persistence;

import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.platform.cache.InMemoryCacheAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    private RedisKernelConfigResolver resolver;
    private KernelTenantContext tenant;

    @BeforeEach
    void setUp() {
        resolver = new RedisKernelConfigResolver(
            new InMemoryCacheAdapter<>(1000, Duration.ofMinutes(5)),
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
