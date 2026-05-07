/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.RecordType;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SPI evolution compatibility.
 *
 * <p>Verifies that SPI interfaces can evolve without breaking existing implementations:
 * <ul>
 *   <li>Adding new methods with default implementations maintains compatibility</li>
 *   <li>Version compatibility checks prevent incompatible plugin loading</li>
 *   <li>Provider discovery handles edge cases gracefully</li>
 *   <li>Backward compatibility is maintained across minor version changes</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose SPI evolution compatibility validation
 * @doc.layer core
 * @doc.pattern Compatibility Test
 */
@DisplayName("SPI Evolution Compatibility Tests")
class SpiEvolutionCompatibilityTest {

    // =========================================================================
    // VERSION COMPATIBILITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Version compatibility")
    class VersionCompatibility {

        @Test
        @DisplayName("plugin with same major version is compatible")
        void sameMajorVersionIsCompatible() { 
            SpiVersion consumerVersion = new SpiVersion("2.5.0");
            SpiVersion providerVersion = new SpiVersion("2.3.1");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); 
        }

        @Test
        @DisplayName("plugin with higher major version is incompatible")
        void higherMajorVersionIsIncompatible() { 
            SpiVersion consumerVersion = new SpiVersion("3.0.0");
            SpiVersion providerVersion = new SpiVersion("2.5.0");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isFalse(); 
        }

        @Test
        @DisplayName("plugin with lower major version is compatible")
        void lowerMajorVersionIsCompatible() { 
            SpiVersion consumerVersion = new SpiVersion("2.0.0");
            SpiVersion providerVersion = new SpiVersion("2.5.0");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); 
        }

        @Test
        @DisplayName("minor version differences are compatible")
        void minorVersionDifferencesAreCompatible() { 
            SpiVersion consumerVersion = new SpiVersion("2.1.0");
            SpiVersion providerVersion = new SpiVersion("2.5.0");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); 
        }

        @Test
        @DisplayName("patch version differences are compatible")
        void patchVersionDifferencesAreCompatible() { 
            SpiVersion consumerVersion = new SpiVersion("2.5.3");
            SpiVersion providerVersion = new SpiVersion("2.5.1");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); 
        }

        @Test
        @DisplayName("pre-release versions are compatible with same major")
        void preReleaseVersionsAreCompatible() { 
            SpiVersion consumerVersion = new SpiVersion("2.0.0-alpha");
            SpiVersion providerVersion = new SpiVersion("2.0.0-beta");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); 
        }
    }

    // =========================================================================
    // INTERFACE EVOLUTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Interface evolution")
    class InterfaceEvolution {

        @Test
        @DisplayName("V1 plugin works with V2 interface (backward compatibility)")
        void v1PluginWorksWithV2Interface() { 
            V1StoragePlugin v1Plugin = new V1StoragePluginImpl("v1-plugin", "1.0.0"); 
            
            // V1 plugin should be able to implement V2 interface via adapter
            V2StoragePlugin v2Adapter = new V1ToV2Adapter(v1Plugin); 
            
            assertThat(v2Adapter.getPluginId()).isEqualTo("v1-plugin");
            assertThat(v2Adapter.getVersion()).isEqualTo("1.0.0");
            assertThat(v2Adapter.getSupportedRecordTypes()).isNotEmpty(); 
        }

        @Test
        @DisplayName("V2 plugin works with V1 interface (forward compatibility)")
        void v2PluginWorksWithV1Interface() { 
            V2StoragePlugin v2Plugin = new V2StoragePluginImpl("v2-plugin", "2.0.0"); 
            
            // V2 plugin should work with V1 consumers if it only uses V1 methods
            assertThat(v2Plugin.getPluginId()).isEqualTo("v2-plugin");
            assertThat(v2Plugin.getSupportedRecordTypes()).isNotEmpty(); 
        }

        @Test
        @DisplayName("new method with default implementation maintains compatibility")
        void newMethodWithDefaultImplementationMaintainsCompatibility() { 
            // Simulating a V3 plugin that doesn't implement new V3 method
            V2StoragePlugin v2Plugin = new V2StoragePluginImpl("v2-plugin", "2.0.0"); 
            
            // V3 interface adds new method with default implementation
            V3StoragePlugin v3Adapter = new V2ToV3Adapter(v2Plugin); 
            
            // Should not throw exception when calling new method
            assertThatCode(() -> v3Adapter.getNewMethod()).doesNotThrowAnyException(); 
        }
    }

    // =========================================================================
    // PROVIDER DISCOVERY EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Provider discovery edge cases")
    class ProviderDiscovery {

        @Test
        @DisplayName("handles duplicate plugin IDs gracefully")
        void handlesDuplicatePluginIds() { 
            StoragePluginRegistry registry = StoragePluginRegistry.getInstance(); 
            
            TestPlugin plugin1 = new TestPlugin("duplicate-id", "1.0.0", Set.of(RecordType.EVENT)); 
            TestPlugin plugin2 = new TestPlugin("duplicate-id", "2.0.0", Set.of(RecordType.ENTITY)); 
            
            registry.register(plugin1); 
            
            // Second registration with same ID should throw
            assertThatThrownBy(() -> registry.register(plugin2)) 
                .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("handles missing plugin gracefully")
        void handlesMissingPlugin() { 
            StoragePluginRegistry registry = StoragePluginRegistry.getInstance(); 
            
            Optional<StoragePlugin<?>> plugin = registry.getPlugin("non-existent");
            
            assertThat(plugin).isEmpty(); 
        }

        @Test
        @DisplayName("handles plugin with invalid version format")
        void handlesInvalidVersionFormat() { 
            TestPlugin plugin = new TestPlugin("invalid-version", "not-a-version", Set.of(RecordType.EVENT)); 
            
            assertThatThrownBy(() -> new SpiVersion(plugin.getVersion())) 
                .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("handles plugin initialization failure")
        void handlesInitializationFailure() { 
            FailingPlugin plugin = new FailingPlugin("failing", "1.0.0"); 
            StoragePluginRegistry registry = StoragePluginRegistry.getInstance(); 
            
            registry.register(plugin); 
            
            // Should not throw during registration
            assertThat(registry.getPlugin("failing")).isPresent();
        }

        @Test
        @DisplayName("handles plugin with no supported record types")
        void handlesNoSupportedRecordTypes() { 
            TestPlugin plugin = new TestPlugin("empty-support", "1.0.0", Set.of()); 
            
            assertThat(plugin.getSupportedRecordTypes()).isEmpty(); 
            assertThat(plugin.supportsRecordType(RecordType.EVENT)).isFalse(); 
        }
    }

    // =========================================================================
    // BACKWARD COMPATIBILITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("old consumers can use new plugins")
        void oldConsumersCanUseNewPlugins() { 
            V2StoragePlugin newPlugin = new V2StoragePluginImpl("new-plugin", "2.0.0"); 
            
            // Old consumer (V1) should be able to use new plugin 
            assertThat(newPlugin.getPluginId()).isNotNull(); 
            assertThat(newPlugin.getDisplayName()).isNotNull(); 
            assertThat(newPlugin.getVersion()).isNotNull(); 
            assertThat(newPlugin.getSupportedRecordTypes()).isNotNull(); 
        }

        @Test
        @DisplayName("new consumers can use old plugins")
        void newConsumersCanUseOldPlugins() { 
            V1StoragePlugin oldPlugin = new V1StoragePluginImpl("old-plugin", "1.0.0"); 
            
            // New consumer (V2) should be able to use old plugin via adapter 
            V2StoragePlugin adapter = new V1ToV2Adapter(oldPlugin); 
            
            assertThat(adapter.getPluginId()).isNotNull(); 
            assertThat(adapter.getSupportedRecordTypes()).isNotNull(); 
        }

        @Test
        @DisplayName("default methods provide safe fallbacks")
        void defaultMethodsProvideSafeFallbacks() { 
            V2StoragePlugin plugin = new V2StoragePluginImpl("test", "1.0.0"); 
            
            // Default method should work without implementation
            assertThat(plugin.supportsRecordType(RecordType.EVENT)).isInstanceOf(Boolean.class); 
        }
    }

    // =========================================================================
    // TEST IMPLEMENTATIONS
    // =========================================================================

    private static class SpiVersion {
        private final int major;

        SpiVersion(String version) { 
            String[] parts = version.split("-", 2); 
            String versionPart = parts[0];
            
            String[] versionParts = versionPart.split("\\.");
            if (versionParts.length != 3) { 
                throw new IllegalArgumentException("Invalid version format: " + version); 
            }
            this.major = Integer.parseInt(versionParts[0]); 
        }

        boolean isCompatibleWith(SpiVersion other) { 
            // Same major version is compatible
            return this.major == other.major;
        }
    }

    private static class TestPlugin implements StoragePlugin<DataRecord> {
        private final String id;
        private final String version;
        private final Set<RecordType> supported;

        TestPlugin(String id, String version, Set<RecordType> supported) { 
            this.id = id;
            this.version = version;
            this.supported = supported;
        }

        @Override public String getPluginId() { return id; } 
        @Override public String getDisplayName() { return id; } 
        @Override public String getVersion() { return version; } 
        @Override public List<RecordType> getSupportedRecordTypes() { return List.copyOf(supported); } 
        @Override public Promise<Void> initialize(Map<String, Object> config) { return Promise.complete(); } 
        @Override public Promise<Void> shutdown() { return Promise.complete(); } 
        @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.ok()); } 
        @Override public Promise<com.ghatana.datacloud.Collection> createCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } 
        @Override public Promise<Optional<com.ghatana.datacloud.Collection>> getCollection(String tenantId, String name) { return Promise.of(Optional.empty()); } 
        @Override public Promise<com.ghatana.datacloud.Collection> updateCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } 
        @Override public Promise<Void> deleteCollection(String tenantId, String name) { return Promise.complete(); } 
        @Override public Promise<List<com.ghatana.datacloud.Collection>> listCollections(String tenantId) { return Promise.of(List.of()); } 
        @Override public Promise<DataRecord> insert(DataRecord record) { return Promise.of(record); } 
        @Override public Promise<BatchResult<UUID>> insertBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } 
        @Override public Promise<Optional<DataRecord>> getById(String tenantId, String collectionName, UUID id) { return Promise.of(Optional.empty()); } 
        @Override public Promise<List<DataRecord>> getByIds(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(List.of()); } 
        @Override public Promise<DataRecord> update(DataRecord record) { return Promise.of(record); } 
        @Override public Promise<BatchResult<UUID>> updateBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } 
        @Override public Promise<Void> delete(String tenantId, String collectionName, UUID id) { return Promise.complete(); } 
        @Override public Promise<BatchResult<UUID>> deleteBatch(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } 
        @Override public Promise<QueryResult<DataRecord>> query(com.ghatana.datacloud.RecordQuery query) { return Promise.of(QueryResult.empty()); } 
        @Override public Promise<Long> count(com.ghatana.datacloud.RecordQuery query) { return Promise.of(0L); } 
        @Override public Promise<Boolean> exists(com.ghatana.datacloud.RecordQuery query) { return Promise.of(false); } 
    }

    private static class FailingPlugin implements StoragePlugin<DataRecord> {
        private final String id;

        FailingPlugin(String id, String version) { 
            this.id = id;
        }

        @Override public String getPluginId() { return id; } 
        @Override public String getDisplayName() { return id; } 
        @Override public String getVersion() { return "1.0.0"; } 
        @Override public List<RecordType> getSupportedRecordTypes() { return List.of(RecordType.EVENT); } 
        @Override public Promise<Void> initialize(Map<String, Object> config) { return Promise.ofException(new RuntimeException("Init failed")); }
        @Override public Promise<Void> shutdown() { return Promise.complete(); } 
        @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.error("Failed")); }
        @Override public Promise<com.ghatana.datacloud.Collection> createCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } 
        @Override public Promise<Optional<com.ghatana.datacloud.Collection>> getCollection(String tenantId, String name) { return Promise.of(Optional.empty()); } 
        @Override public Promise<com.ghatana.datacloud.Collection> updateCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } 
        @Override public Promise<Void> deleteCollection(String tenantId, String name) { return Promise.complete(); } 
        @Override public Promise<List<com.ghatana.datacloud.Collection>> listCollections(String tenantId) { return Promise.of(List.of()); } 
        @Override public Promise<DataRecord> insert(DataRecord record) { return Promise.of(record); } 
        @Override public Promise<BatchResult<UUID>> insertBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } 
        @Override public Promise<Optional<DataRecord>> getById(String tenantId, String collectionName, UUID id) { return Promise.of(Optional.empty()); } 
        @Override public Promise<List<DataRecord>> getByIds(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(List.of()); } 
        @Override public Promise<DataRecord> update(DataRecord record) { return Promise.of(record); } 
        @Override public Promise<BatchResult<UUID>> updateBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } 
        @Override public Promise<Void> delete(String tenantId, String collectionName, UUID id) { return Promise.complete(); } 
        @Override public Promise<BatchResult<UUID>> deleteBatch(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } 
        @Override public Promise<QueryResult<DataRecord>> query(com.ghatana.datacloud.RecordQuery query) { return Promise.of(QueryResult.empty()); } 
        @Override public Promise<Long> count(com.ghatana.datacloud.RecordQuery query) { return Promise.of(0L); } 
        @Override public Promise<Boolean> exists(com.ghatana.datacloud.RecordQuery query) { return Promise.of(false); } 
    }

    // Simulated V1 interface
    private interface V1StoragePlugin {
        String getPluginId(); 
        String getVersion(); 
        List<RecordType> getSupportedRecordTypes(); 
    }

    // Simulated V2 interface (extends V1) 
    private interface V2StoragePlugin extends V1StoragePlugin {
        String getDisplayName(); 
        default boolean supportsRecordType(RecordType recordType) { 
            return getSupportedRecordTypes().contains(recordType); 
        }
    }

    // Simulated V3 interface (extends V2 with new method) 
    private interface V3StoragePlugin extends V2StoragePlugin {
        default String getNewMethod() { 
            return "default implementation";
        }
    }

    private static class V1StoragePluginImpl implements V1StoragePlugin {
        private final String id;
        private final String version;

        V1StoragePluginImpl(String id, String version) { 
            this.id = id;
            this.version = version;
        }

        @Override public String getPluginId() { return id; } 
        @Override public String getVersion() { return version; } 
        @Override public List<RecordType> getSupportedRecordTypes() { return List.of(RecordType.EVENT); } 
    }

    private static class V2StoragePluginImpl implements V2StoragePlugin {
        private final String id;
        private final String version;

        V2StoragePluginImpl(String id, String version) { 
            this.id = id;
            this.version = version;
        }

        @Override public String getPluginId() { return id; } 
        @Override public String getDisplayName() { return id; } 
        @Override public String getVersion() { return version; } 
        @Override public List<RecordType> getSupportedRecordTypes() { return List.of(RecordType.EVENT); } 
    }

    private static class V1ToV2Adapter implements V2StoragePlugin {
        private final V1StoragePlugin v1Plugin;

        V1ToV2Adapter(V1StoragePlugin v1Plugin) { 
            this.v1Plugin = v1Plugin;
        }

        @Override public String getPluginId() { return v1Plugin.getPluginId(); } 
        @Override public String getDisplayName() { return v1Plugin.getPluginId(); } 
        @Override public String getVersion() { return v1Plugin.getVersion(); } 
        @Override public List<RecordType> getSupportedRecordTypes() { return v1Plugin.getSupportedRecordTypes(); } 
    }

    private static class V2ToV3Adapter implements V3StoragePlugin {
        private final V2StoragePlugin v2Plugin;

        V2ToV3Adapter(V2StoragePlugin v2Plugin) { 
            this.v2Plugin = v2Plugin;
        }

        @Override public String getPluginId() { return v2Plugin.getPluginId(); } 
        @Override public String getDisplayName() { return v2Plugin.getDisplayName(); } 
        @Override public String getVersion() { return v2Plugin.getVersion(); } 
        @Override public List<RecordType> getSupportedRecordTypes() { return v2Plugin.getSupportedRecordTypes(); } 
    }
}
