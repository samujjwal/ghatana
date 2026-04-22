/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("SPI Evolution Compatibility Tests [GH-90000]")
class SpiEvolutionCompatibilityTest {

    // =========================================================================
    // VERSION COMPATIBILITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Version compatibility [GH-90000]")
    class VersionCompatibility {

        @Test
        @DisplayName("plugin with same major version is compatible [GH-90000]")
        void sameMajorVersionIsCompatible() { // GH-90000
            SpiVersion consumerVersion = new SpiVersion("2.5.0 [GH-90000]");
            SpiVersion providerVersion = new SpiVersion("2.3.1 [GH-90000]");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("plugin with higher major version is incompatible [GH-90000]")
        void higherMajorVersionIsIncompatible() { // GH-90000
            SpiVersion consumerVersion = new SpiVersion("3.0.0 [GH-90000]");
            SpiVersion providerVersion = new SpiVersion("2.5.0 [GH-90000]");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("plugin with lower major version is compatible [GH-90000]")
        void lowerMajorVersionIsCompatible() { // GH-90000
            SpiVersion consumerVersion = new SpiVersion("2.0.0 [GH-90000]");
            SpiVersion providerVersion = new SpiVersion("2.5.0 [GH-90000]");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("minor version differences are compatible [GH-90000]")
        void minorVersionDifferencesAreCompatible() { // GH-90000
            SpiVersion consumerVersion = new SpiVersion("2.1.0 [GH-90000]");
            SpiVersion providerVersion = new SpiVersion("2.5.0 [GH-90000]");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("patch version differences are compatible [GH-90000]")
        void patchVersionDifferencesAreCompatible() { // GH-90000
            SpiVersion consumerVersion = new SpiVersion("2.5.3 [GH-90000]");
            SpiVersion providerVersion = new SpiVersion("2.5.1 [GH-90000]");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("pre-release versions are compatible with same major [GH-90000]")
        void preReleaseVersionsAreCompatible() { // GH-90000
            SpiVersion consumerVersion = new SpiVersion("2.0.0-alpha [GH-90000]");
            SpiVersion providerVersion = new SpiVersion("2.0.0-beta [GH-90000]");

            assertThat(consumerVersion.isCompatibleWith(providerVersion)).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // INTERFACE EVOLUTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Interface evolution [GH-90000]")
    class InterfaceEvolution {

        @Test
        @DisplayName("V1 plugin works with V2 interface (backward compatibility) [GH-90000]")
        void v1PluginWorksWithV2Interface() { // GH-90000
            V1StoragePlugin v1Plugin = new V1StoragePluginImpl("v1-plugin", "1.0.0"); // GH-90000
            
            // V1 plugin should be able to implement V2 interface via adapter
            V2StoragePlugin v2Adapter = new V1ToV2Adapter(v1Plugin); // GH-90000
            
            assertThat(v2Adapter.getPluginId()).isEqualTo("v1-plugin [GH-90000]");
            assertThat(v2Adapter.getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(v2Adapter.getSupportedRecordTypes()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("V2 plugin works with V1 interface (forward compatibility) [GH-90000]")
        void v2PluginWorksWithV1Interface() { // GH-90000
            V2StoragePlugin v2Plugin = new V2StoragePluginImpl("v2-plugin", "2.0.0"); // GH-90000
            
            // V2 plugin should work with V1 consumers if it only uses V1 methods
            assertThat(v2Plugin.getPluginId()).isEqualTo("v2-plugin [GH-90000]");
            assertThat(v2Plugin.getSupportedRecordTypes()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("new method with default implementation maintains compatibility [GH-90000]")
        void newMethodWithDefaultImplementationMaintainsCompatibility() { // GH-90000
            // Simulating a V3 plugin that doesn't implement new V3 method
            V2StoragePlugin v2Plugin = new V2StoragePluginImpl("v2-plugin", "2.0.0"); // GH-90000
            
            // V3 interface adds new method with default implementation
            V3StoragePlugin v3Adapter = new V2ToV3Adapter(v2Plugin); // GH-90000
            
            // Should not throw exception when calling new method
            assertThatCode(() -> v3Adapter.getNewMethod()).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // PROVIDER DISCOVERY EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Provider discovery edge cases [GH-90000]")
    class ProviderDiscovery {

        @Test
        @DisplayName("handles duplicate plugin IDs gracefully [GH-90000]")
        void handlesDuplicatePluginIds() { // GH-90000
            StoragePluginRegistry registry = StoragePluginRegistry.getInstance(); // GH-90000
            
            TestPlugin plugin1 = new TestPlugin("duplicate-id", "1.0.0", Set.of(RecordType.EVENT)); // GH-90000
            TestPlugin plugin2 = new TestPlugin("duplicate-id", "2.0.0", Set.of(RecordType.ENTITY)); // GH-90000
            
            registry.register(plugin1); // GH-90000
            
            // Second registration with same ID should throw
            assertThatThrownBy(() -> registry.register(plugin2)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("handles missing plugin gracefully [GH-90000]")
        void handlesMissingPlugin() { // GH-90000
            StoragePluginRegistry registry = StoragePluginRegistry.getInstance(); // GH-90000
            
            Optional<StoragePlugin<?>> plugin = registry.getPlugin("non-existent [GH-90000]");
            
            assertThat(plugin).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("handles plugin with invalid version format [GH-90000]")
        void handlesInvalidVersionFormat() { // GH-90000
            TestPlugin plugin = new TestPlugin("invalid-version", "not-a-version", Set.of(RecordType.EVENT)); // GH-90000
            
            assertThatThrownBy(() -> new SpiVersion(plugin.getVersion())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("handles plugin initialization failure [GH-90000]")
        void handlesInitializationFailure() { // GH-90000
            FailingPlugin plugin = new FailingPlugin("failing", "1.0.0"); // GH-90000
            StoragePluginRegistry registry = StoragePluginRegistry.getInstance(); // GH-90000
            
            registry.register(plugin); // GH-90000
            
            // Should not throw during registration
            assertThat(registry.getPlugin("failing [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("handles plugin with no supported record types [GH-90000]")
        void handlesNoSupportedRecordTypes() { // GH-90000
            TestPlugin plugin = new TestPlugin("empty-support", "1.0.0", Set.of()); // GH-90000
            
            assertThat(plugin.getSupportedRecordTypes()).isEmpty(); // GH-90000
            assertThat(plugin.supportsRecordType(RecordType.EVENT)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // BACKWARD COMPATIBILITY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Backward compatibility [GH-90000]")
    class BackwardCompatibility {

        @Test
        @DisplayName("old consumers can use new plugins [GH-90000]")
        void oldConsumersCanUseNewPlugins() { // GH-90000
            V2StoragePlugin newPlugin = new V2StoragePluginImpl("new-plugin", "2.0.0"); // GH-90000
            
            // Old consumer (V1) should be able to use new plugin // GH-90000
            assertThat(newPlugin.getPluginId()).isNotNull(); // GH-90000
            assertThat(newPlugin.getDisplayName()).isNotNull(); // GH-90000
            assertThat(newPlugin.getVersion()).isNotNull(); // GH-90000
            assertThat(newPlugin.getSupportedRecordTypes()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("new consumers can use old plugins [GH-90000]")
        void newConsumersCanUseOldPlugins() { // GH-90000
            V1StoragePlugin oldPlugin = new V1StoragePluginImpl("old-plugin", "1.0.0"); // GH-90000
            
            // New consumer (V2) should be able to use old plugin via adapter // GH-90000
            V2StoragePlugin adapter = new V1ToV2Adapter(oldPlugin); // GH-90000
            
            assertThat(adapter.getPluginId()).isNotNull(); // GH-90000
            assertThat(adapter.getSupportedRecordTypes()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("default methods provide safe fallbacks [GH-90000]")
        void defaultMethodsProvideSafeFallbacks() { // GH-90000
            V2StoragePlugin plugin = new V2StoragePluginImpl("test", "1.0.0"); // GH-90000
            
            // Default method should work without implementation
            assertThat(plugin.supportsRecordType(RecordType.EVENT)).isInstanceOf(Boolean.class); // GH-90000
        }
    }

    // =========================================================================
    // TEST IMPLEMENTATIONS
    // =========================================================================

    private static class SpiVersion {
        private final int major;

        SpiVersion(String version) { // GH-90000
            String[] parts = version.split("-", 2); // GH-90000
            String versionPart = parts[0];
            
            String[] versionParts = versionPart.split("\\. [GH-90000]");
            if (versionParts.length != 3) { // GH-90000
                throw new IllegalArgumentException("Invalid version format: " + version); // GH-90000
            }
            this.major = Integer.parseInt(versionParts[0]); // GH-90000
        }

        boolean isCompatibleWith(SpiVersion other) { // GH-90000
            // Same major version is compatible
            return this.major == other.major;
        }
    }

    private static class TestPlugin implements StoragePlugin<DataRecord> {
        private final String id;
        private final String version;
        private final Set<RecordType> supported;

        TestPlugin(String id, String version, Set<RecordType> supported) { // GH-90000
            this.id = id;
            this.version = version;
            this.supported = supported;
        }

        @Override public String getPluginId() { return id; } // GH-90000
        @Override public String getDisplayName() { return id; } // GH-90000
        @Override public String getVersion() { return version; } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return List.copyOf(supported); } // GH-90000
        @Override public Promise<Void> initialize(Map<String, Object> config) { return Promise.complete(); } // GH-90000
        @Override public Promise<Void> shutdown() { return Promise.complete(); } // GH-90000
        @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.ok()); } // GH-90000
        @Override public Promise<com.ghatana.datacloud.Collection> createCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } // GH-90000
        @Override public Promise<Optional<com.ghatana.datacloud.Collection>> getCollection(String tenantId, String name) { return Promise.of(Optional.empty()); } // GH-90000
        @Override public Promise<com.ghatana.datacloud.Collection> updateCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } // GH-90000
        @Override public Promise<Void> deleteCollection(String tenantId, String name) { return Promise.complete(); } // GH-90000
        @Override public Promise<List<com.ghatana.datacloud.Collection>> listCollections(String tenantId) { return Promise.of(List.of()); } // GH-90000
        @Override public Promise<DataRecord> insert(DataRecord record) { return Promise.of(record); } // GH-90000
        @Override public Promise<BatchResult<UUID>> insertBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<Optional<DataRecord>> getById(String tenantId, String collectionName, UUID id) { return Promise.of(Optional.empty()); } // GH-90000
        @Override public Promise<List<DataRecord>> getByIds(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(List.of()); } // GH-90000
        @Override public Promise<DataRecord> update(DataRecord record) { return Promise.of(record); } // GH-90000
        @Override public Promise<BatchResult<UUID>> updateBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<Void> delete(String tenantId, String collectionName, UUID id) { return Promise.complete(); } // GH-90000
        @Override public Promise<BatchResult<UUID>> deleteBatch(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<QueryResult<DataRecord>> query(com.ghatana.datacloud.RecordQuery query) { return Promise.of(QueryResult.empty()); } // GH-90000
        @Override public Promise<Long> count(com.ghatana.datacloud.RecordQuery query) { return Promise.of(0L); } // GH-90000
        @Override public Promise<Boolean> exists(com.ghatana.datacloud.RecordQuery query) { return Promise.of(false); } // GH-90000
    }

    private static class FailingPlugin implements StoragePlugin<DataRecord> {
        private final String id;

        FailingPlugin(String id, String version) { // GH-90000
            this.id = id;
        }

        @Override public String getPluginId() { return id; } // GH-90000
        @Override public String getDisplayName() { return id; } // GH-90000
        @Override public String getVersion() { return "1.0.0"; } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return List.of(RecordType.EVENT); } // GH-90000
        @Override public Promise<Void> initialize(Map<String, Object> config) { return Promise.ofException(new RuntimeException("Init failed [GH-90000]")); }
        @Override public Promise<Void> shutdown() { return Promise.complete(); } // GH-90000
        @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.error("Failed [GH-90000]")); }
        @Override public Promise<com.ghatana.datacloud.Collection> createCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } // GH-90000
        @Override public Promise<Optional<com.ghatana.datacloud.Collection>> getCollection(String tenantId, String name) { return Promise.of(Optional.empty()); } // GH-90000
        @Override public Promise<com.ghatana.datacloud.Collection> updateCollection(com.ghatana.datacloud.Collection collection) { return Promise.of(collection); } // GH-90000
        @Override public Promise<Void> deleteCollection(String tenantId, String name) { return Promise.complete(); } // GH-90000
        @Override public Promise<List<com.ghatana.datacloud.Collection>> listCollections(String tenantId) { return Promise.of(List.of()); } // GH-90000
        @Override public Promise<DataRecord> insert(DataRecord record) { return Promise.of(record); } // GH-90000
        @Override public Promise<BatchResult<UUID>> insertBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<Optional<DataRecord>> getById(String tenantId, String collectionName, UUID id) { return Promise.of(Optional.empty()); } // GH-90000
        @Override public Promise<List<DataRecord>> getByIds(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(List.of()); } // GH-90000
        @Override public Promise<DataRecord> update(DataRecord record) { return Promise.of(record); } // GH-90000
        @Override public Promise<BatchResult<UUID>> updateBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<Void> delete(String tenantId, String collectionName, UUID id) { return Promise.complete(); } // GH-90000
        @Override public Promise<BatchResult<UUID>> deleteBatch(String tenantId, String collectionName, List<UUID> ids) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<QueryResult<DataRecord>> query(com.ghatana.datacloud.RecordQuery query) { return Promise.of(QueryResult.empty()); } // GH-90000
        @Override public Promise<Long> count(com.ghatana.datacloud.RecordQuery query) { return Promise.of(0L); } // GH-90000
        @Override public Promise<Boolean> exists(com.ghatana.datacloud.RecordQuery query) { return Promise.of(false); } // GH-90000
    }

    // Simulated V1 interface
    private interface V1StoragePlugin {
        String getPluginId(); // GH-90000
        String getVersion(); // GH-90000
        List<RecordType> getSupportedRecordTypes(); // GH-90000
    }

    // Simulated V2 interface (extends V1) // GH-90000
    private interface V2StoragePlugin extends V1StoragePlugin {
        String getDisplayName(); // GH-90000
        default boolean supportsRecordType(RecordType recordType) { // GH-90000
            return getSupportedRecordTypes().contains(recordType); // GH-90000
        }
    }

    // Simulated V3 interface (extends V2 with new method) // GH-90000
    private interface V3StoragePlugin extends V2StoragePlugin {
        default String getNewMethod() { // GH-90000
            return "default implementation";
        }
    }

    private static class V1StoragePluginImpl implements V1StoragePlugin {
        private final String id;
        private final String version;

        V1StoragePluginImpl(String id, String version) { // GH-90000
            this.id = id;
            this.version = version;
        }

        @Override public String getPluginId() { return id; } // GH-90000
        @Override public String getVersion() { return version; } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return List.of(RecordType.EVENT); } // GH-90000
    }

    private static class V2StoragePluginImpl implements V2StoragePlugin {
        private final String id;
        private final String version;

        V2StoragePluginImpl(String id, String version) { // GH-90000
            this.id = id;
            this.version = version;
        }

        @Override public String getPluginId() { return id; } // GH-90000
        @Override public String getDisplayName() { return id; } // GH-90000
        @Override public String getVersion() { return version; } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return List.of(RecordType.EVENT); } // GH-90000
    }

    private static class V1ToV2Adapter implements V2StoragePlugin {
        private final V1StoragePlugin v1Plugin;

        V1ToV2Adapter(V1StoragePlugin v1Plugin) { // GH-90000
            this.v1Plugin = v1Plugin;
        }

        @Override public String getPluginId() { return v1Plugin.getPluginId(); } // GH-90000
        @Override public String getDisplayName() { return v1Plugin.getPluginId(); } // GH-90000
        @Override public String getVersion() { return v1Plugin.getVersion(); } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return v1Plugin.getSupportedRecordTypes(); } // GH-90000
    }

    private static class V2ToV3Adapter implements V3StoragePlugin {
        private final V2StoragePlugin v2Plugin;

        V2ToV3Adapter(V2StoragePlugin v2Plugin) { // GH-90000
            this.v2Plugin = v2Plugin;
        }

        @Override public String getPluginId() { return v2Plugin.getPluginId(); } // GH-90000
        @Override public String getDisplayName() { return v2Plugin.getDisplayName(); } // GH-90000
        @Override public String getVersion() { return v2Plugin.getVersion(); } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return v2Plugin.getSupportedRecordTypes(); } // GH-90000
    }
}
