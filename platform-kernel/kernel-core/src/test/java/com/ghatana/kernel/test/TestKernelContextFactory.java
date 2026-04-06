package com.ghatana.kernel.test;

import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.DefaultKernelContext;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.registry.KernelRegistry;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Factory for creating test kernel contexts.
 *
 * @doc.type class
 * @doc.purpose Test utility for creating kernel contexts
 * @doc.layer test
 * @doc.pattern Factory
 */
public class TestKernelContextFactory {

    public static KernelContext create(KernelRegistry registry) {
        return create(registry, Eventloop.create());
    }

    public static KernelContext create(KernelRegistry registry, Eventloop eventloop) {
        KernelConfigResolver mockConfigResolver = new MockConfigResolver();
        return new DefaultKernelContext(registry, mockConfigResolver, eventloop, "1.0.0", "test");
    }

    private static class MockConfigResolver implements KernelConfigResolver {
        @Override
        public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) {
            throw new IllegalArgumentException("Config not found: " + configKey);
        }

        @Override
        public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context) {
            return defaultValue;
        }

        @Override
        public <T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context) {
            return Optional.empty();
        }

        @Override
        public void addConfigProvider(ConfigProvider provider) {
            // No-op for test
        }

        @Override
        public Promise<Void> reloadConfig(String tenantId) {
            return Promise.complete();
        }

        @Override
        public List<String> getAvailableKeys(KernelTenantContext context) {
            return List.of();
        }
    }
}
