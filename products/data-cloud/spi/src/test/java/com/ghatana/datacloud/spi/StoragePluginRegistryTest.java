package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.RecordType;
import org.junit.jupiter.api.*;
import io.activej.promise.Promise;
import com.ghatana.datacloud.spi.StoragePlugin.HealthStatus;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import com.ghatana.datacloud.Collection;
import com.ghatana.datacloud.RecordQuery;

class StoragePluginRegistryTest {
    static class DummyPlugin implements StoragePlugin<DataRecord> {
        private final String id;
        private final Set<RecordType> supported;
        DummyPlugin(String id, RecordType... supported) { // GH-90000
            this.id = id;
            this.supported = Set.of(supported); // GH-90000
        }
        @Override public String getPluginId() { return id; } // GH-90000
        @Override public String getDisplayName() { return id; } // GH-90000
        @Override public String getVersion() { return "1.0.0"; } // GH-90000
        @Override public List<RecordType> getSupportedRecordTypes() { return List.copyOf(supported); } // GH-90000
        @Override public Promise<Void> initialize(Map<String, Object> config) { return Promise.complete(); } // GH-90000
        @Override public Promise<Void> shutdown() { return Promise.complete(); } // GH-90000
        @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.ok()); } // GH-90000
        @Override public Promise<Collection> createCollection(Collection collection) { return Promise.of(collection); } // GH-90000
        @Override public Promise<Optional<Collection>> getCollection(String tenantId, String name) { return Promise.of(Optional.empty()); } // GH-90000
        @Override public Promise<Collection> updateCollection(Collection collection) { return Promise.of(collection); } // GH-90000
        @Override public Promise<Void> deleteCollection(String tenantId, String name) { return Promise.complete(); } // GH-90000
        @Override public Promise<List<Collection>> listCollections(String tenantId) { return Promise.of(List.of()); } // GH-90000
        @Override public Promise<DataRecord> insert(DataRecord record) { return Promise.of(record); } // GH-90000
        @Override public Promise<BatchResult<java.util.UUID>> insertBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<Optional<DataRecord>> getById(String tenantId, String collectionName, java.util.UUID id) { return Promise.of(Optional.empty()); } // GH-90000
        @Override public Promise<List<DataRecord>> getByIds(String tenantId, String collectionName, List<java.util.UUID> ids) { return Promise.of(List.of()); } // GH-90000
        @Override public Promise<DataRecord> update(DataRecord record) { return Promise.of(record); } // GH-90000
        @Override public Promise<BatchResult<java.util.UUID>> updateBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<Void> delete(String tenantId, String collectionName, java.util.UUID id) { return Promise.complete(); } // GH-90000
        @Override public Promise<BatchResult<java.util.UUID>> deleteBatch(String tenantId, String collectionName, List<java.util.UUID> ids) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); } // GH-90000
        @Override public Promise<com.ghatana.datacloud.spi.StoragePlugin.QueryResult<DataRecord>> query(RecordQuery query) { return Promise.of(com.ghatana.datacloud.spi.StoragePlugin.QueryResult.empty()); } // GH-90000
        @Override public Promise<Long> count(RecordQuery query) { return Promise.of(0L); } // GH-90000
        @Override public Promise<Boolean> exists(RecordQuery query) { return Promise.of(false); } // GH-90000
    }

    @Test
    void register_and_getPlugin() { // GH-90000
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance(); // GH-90000
        DummyPlugin plugin = new DummyPlugin("test", RecordType.EVENT); // GH-90000
        reg.register(plugin); // GH-90000
        assertThat(reg.getPlugin("test")).contains(plugin);
        assertThatThrownBy(() -> reg.register(plugin)).isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    void set_and_getDefaultPlugin() { // GH-90000
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance(); // GH-90000
        DummyPlugin plugin = new DummyPlugin("def", RecordType.ENTITY); // GH-90000
        reg.register(plugin); // GH-90000
        reg.setDefaultPlugin(RecordType.ENTITY, "def"); // GH-90000
        assertThat(reg.getDefaultPlugin(RecordType.ENTITY)).contains(plugin); // GH-90000
    }

    @Test
    void unregister_removes_plugin() { // GH-90000
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance(); // GH-90000
        DummyPlugin plugin = new DummyPlugin("unreg", RecordType.EVENT); // GH-90000
        reg.register(plugin); // GH-90000
        reg.unregister("unreg").whenComplete(($,e) -> {});
        assertThat(reg.getPlugin("unreg")).isEmpty();
    }

    @Test
    void plugin_config_management() { // GH-90000
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance(); // GH-90000
        DummyPlugin plugin = new DummyPlugin("cfg", RecordType.EVENT); // GH-90000
        reg.register(plugin); // GH-90000
        Map<String,Object> cfg = Map.of("foo", 42); // GH-90000
        reg.setPluginConfig("cfg", cfg); // GH-90000
        assertThat(reg.getPluginConfig("cfg")).containsEntry("foo", 42);
    }
}
