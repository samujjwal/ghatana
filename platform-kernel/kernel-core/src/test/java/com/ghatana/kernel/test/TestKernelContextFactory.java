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

    public static KernelContext create(KernelRegistry registry) { // GH-90000
        return create(registry, Eventloop.create()); // GH-90000
    }

    public static KernelContext create(KernelRegistry registry, Eventloop eventloop) { // GH-90000
        KernelConfigResolver mockConfigResolver = new MockConfigResolver(); // GH-90000
        return new DefaultKernelContext(registry, mockConfigResolver, eventloop, "1.0.0", "test"); // GH-90000
    }

    private static class MockConfigResolver implements KernelConfigResolver {
        @Override
        public <T> T resolve(String configKey, Class<T> type, KernelTenantContext context) { // GH-90000
            throw new IllegalArgumentException("Config not found: " + configKey); // GH-90000
        }

        @Override
        public <T> T resolveWithDefault(String configKey, Class<T> type, T defaultValue, KernelTenantContext context) { // GH-90000
            return defaultValue;
        }

        @Override
        public <T> Optional<T> resolveOptional(String configKey, Class<T> type, KernelTenantContext context) { // GH-90000
            return Optional.empty(); // GH-90000
        }

        @Override
        public void addConfigProvider(ConfigProvider provider) { // GH-90000
            // No-op for test
        }

        @Override
        public Promise<Void> reloadConfig(String tenantId) { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public List<String> getAvailableKeys(KernelTenantContext context) { // GH-90000
            return List.of(); // GH-90000
        }
    }
}
