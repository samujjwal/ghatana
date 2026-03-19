/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.config.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.test.EventloopTestBase;
import com.ghatana.platform.config.ConfigManager;
import com.ghatana.platform.config.ConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ConfigService.
 *
 * <p>Validates type-safe configuration access, tenant isolation,
 * and production-grade functionality.</p>
 *
 * @doc.type test
 * @doc.purpose Validate ConfigService functionality and tenant isolation
 * @doc.layer test
 * @doc.pattern UnitTest
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("ConfigService Tests")
public class ConfigServiceTest extends EventloopTestBase {

    private ConfigService configService;
    private KernelContext context;
    private ConfigManager platformConfigManager;

    @BeforeEach
    void setUp() {
        context = createTestContext();
        platformConfigManager = createTestConfigManager();
        configService = new ConfigService(context, platformConfigManager);
        configService.start();
    }

    // ==================== Type-Safe Configuration Tests ====================

    @Test
    @DisplayName("Should get string configuration value")
    void shouldGetStringConfigurationValue() {
        Optional<String> value = configService.getString("app.name");

        assertTrue(value.isPresent());
        assertEquals("TestApp", value.get());
    }

    @Test
    @DisplayName("Should return empty optional for missing string key")
    void shouldReturnEmptyOptionalForMissingStringKey() {
        Optional<String> value = configService.getString("nonexistent.key");

        assertFalse(value.isPresent());
    }

    @Test
    @DisplayName("Should get string with default value")
    void shouldGetStringWithDefaultValue() {
        String value = configService.getString("nonexistent.key", "default");

        assertEquals("default", value);
    }

    @Test
    @DisplayName("Should get integer configuration value")
    void shouldGetIntegerConfigurationValue() {
        Optional<Integer> value = configService.getInt("app.port");

        assertTrue(value.isPresent());
        assertEquals(8080, value.get());
    }

    @Test
    @DisplayName("Should get integer with default value")
    void shouldGetIntegerWithDefaultValue() {
        int value = configService.getInt("nonexistent.port", 3000);

        assertEquals(3000, value);
    }

    @Test
    @DisplayName("Should get boolean configuration value")
    void shouldGetBooleanConfigurationValue() {
        Optional<Boolean> value = configService.getBoolean("app.debug");

        assertTrue(value.isPresent());
        assertTrue(value.get());
    }

    @Test
    @DisplayName("Should get boolean with default value")
    void shouldGetBooleanWithDefaultValue() {
        boolean value = configService.getBoolean("nonexistent.flag", false);

        assertFalse(value);
    }

    @Test
    @DisplayName("Should get long configuration value")
    void shouldGetLongConfigurationValue() {
        Optional<Long> value = configService.getLong("cache.ttl");

        assertTrue(value.isPresent());
        assertEquals(3600L, value.get());
    }

    @Test
    @DisplayName("Should get double configuration value")
    void shouldGetDoubleConfigurationValue() {
        Optional<Double> value = configService.getDouble("threshold.percentage");

        assertTrue(value.isPresent());
        assertEquals(0.85, value.get(), 0.001);
    }

    // ==================== Tenant Isolation Tests ====================

    @Test
    @DisplayName("Should provide tenant-specific string configuration")
    void shouldProvideTenantSpecificStringConfiguration() {
        configService.addTenantConfigSource("tenant-1", createTenantConfigSource(Map.of(
            "app.name", "Tenant1App"
        )));

        Optional<String> value = configService.getTenantString("tenant-1", "app.name");

        assertTrue(value.isPresent());
        assertEquals("Tenant1App", value.get());
    }

    @Test
    @DisplayName("Should provide tenant-specific integer configuration")
    void shouldProvideTenantSpecificIntegerConfiguration() {
        configService.addTenantConfigSource("tenant-1", createTenantConfigSource(Map.of(
            "app.port", "9000"
        )));

        Optional<Integer> value = configService.getTenantInt("tenant-1", "app.port");

        assertTrue(value.isPresent());
        assertEquals(9000, value.get());
    }

    @Test
    @DisplayName("Should fall back to platform config for missing tenant value")
    void shouldFallBackToPlatformConfigForMissingTenantValue() {
        // Don't add tenant-specific source for "app.name"

        Optional<String> value = configService.getTenantString("tenant-1", "app.name");

        assertTrue(value.isPresent());
        assertEquals("TestApp", value.get()); // From platform config
    }

    @Test
    @DisplayName("Should provide default for missing tenant value with default")
    void shouldProvideDefaultForMissingTenantValueWithDefault() {
        String value = configService.getTenantString("tenant-1", "nonexistent.key", "tenant-default");

        assertEquals("tenant-default", value);
    }

    @Test
    @DisplayName("Should isolate tenant configurations")
    void shouldIsolateTenantConfigurations() {
        configService.addTenantConfigSource("tenant-1", createTenantConfigSource(Map.of(
            "app.name", "Tenant1App"
        )));
        configService.addTenantConfigSource("tenant-2", createTenantConfigSource(Map.of(
            "app.name", "Tenant2App"
        )));

        Optional<String> tenant1Value = configService.getTenantString("tenant-1", "app.name");
        Optional<String> tenant2Value = configService.getTenantString("tenant-2", "app.name");

        assertTrue(tenant1Value.isPresent());
        assertTrue(tenant2Value.isPresent());
        assertEquals("Tenant1App", tenant1Value.get());
        assertEquals("Tenant2App", tenant2Value.get());
    }

    // ==================== Health Check Tests ====================

    @Test
    @DisplayName("Should be healthy when started")
    void shouldBeHealthyWhenStarted() {
        assertTrue(configService.isHealthy());
    }

    @Test
    @DisplayName("Should be unhealthy when stopped")
    void shouldBeUnhealthyWhenStopped() {
        configService.stop();

        assertFalse(configService.isHealthy());
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle null key gracefully")
    void shouldHandleNullKeyGracefully() {
        assertThrows(NullPointerException.class, () -> configService.getString(null));
    }

    @Test
    @DisplayName("Should handle empty key gracefully")
    void shouldHandleEmptyKeyGracefully() {
        Optional<String> value = configService.getString("");

        assertFalse(value.isPresent());
    }

    @Test
    @DisplayName("Should handle invalid integer value gracefully")
    void shouldHandleInvalidIntegerValueGracefully() {
        platformConfigManager = createTestConfigManagerWithInvalidValue();
        configService = new ConfigService(context, platformConfigManager);
        configService.start();

        // Should not throw, but return empty optional
        Optional<Integer> value = configService.getInt("invalid.int");
        assertTrue(value.isEmpty() || value.get() == 0);
    }

    // ==================== Private Helper Methods ====================

    private KernelContext createTestContext() {
        return new TestKernelContext();
    }

    private ConfigManager createTestConfigManager() {
        ConfigManager manager = ConfigManager.createDefault("test");
        manager.addSource(new MapConfigSource(Map.of(
            "app.name", "TestApp",
            "app.port", "8080",
            "app.debug", "true",
            "cache.ttl", "3600",
            "threshold.percentage", "0.85"
        )));
        return manager;
    }

    private ConfigManager createTestConfigManagerWithInvalidValue() {
        ConfigManager manager = ConfigManager.createDefault("test");
        manager.addSource(new MapConfigSource(Map.of(
            "invalid.int", "not-a-number"
        )));
        return manager;
    }

    private ConfigSource createTenantConfigSource(Map<String, String> values) {
        return new MapConfigSource(values);
    }

    /**
     * Test implementation of KernelContext.
     */
    private static class TestKernelContext implements KernelContext {
        @Override
        public String getKernelId() {
            return "test-kernel";
        }

        @Override
        public String getTenantId() {
            return "test-tenant";
        }

        @Override
        public <T> void registerService(Class<T> serviceClass, T service) {
            // No-op for testing
        }

        @Override
        public <T> T getService(Class<T> serviceClass) {
            return null;
        }

        @Override
        public ConfigManager getConfig() {
            return ConfigManager.createDefault("test");
        }

        @Override
        public java.util.concurrent.Executor getExecutor(String name) {
            return java.util.concurrent.Executors.newSingleThreadExecutor();
        }

        @Override
        public boolean hasCapability(String capabilityId) {
            return true;
        }

        @Override
        public java.util.Set<com.ghatana.kernel.descriptor.KernelCapability> getAvailableCapabilities() {
            return java.util.Set.of();
        }
    }

    /**
     * Simple Map-based ConfigSource for testing.
     */
    private static class MapConfigSource implements ConfigSource {
        private final Map<String, String> values;

        MapConfigSource(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<String> getString(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Optional<Integer> getInt(String key) {
            String value = values.get(key);
            if (value == null) return Optional.empty();
            try {
                return Optional.of(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Boolean> getBoolean(String key) {
            String value = values.get(key);
            if (value == null) return Optional.empty();
            return Optional.of(Boolean.parseBoolean(value));
        }

        @Override
        public Optional<Long> getLong(String key) {
            String value = values.get(key);
            if (value == null) return Optional.empty();
            try {
                return Optional.of(Long.parseLong(value));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Double> getDouble(String key) {
            String value = values.get(key);
            if (value == null) return Optional.empty();
            try {
                return Optional.of(Double.parseDouble(value));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        @Override
        public Map<String, Object> getAll() {
            return new java.util.HashMap<>(values);
        }

        @Override
        public boolean hasKey(String key) {
            return values.containsKey(key);
        }
    }
}
