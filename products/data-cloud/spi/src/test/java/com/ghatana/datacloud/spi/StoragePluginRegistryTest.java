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
        DummyPlugin(String id, RecordType... supported) {
            this.id = id;
            this.supported = Set.of(supported);
        }
        @Override public String getPluginId() { return id; }
        @Override public String getDisplayName() { return id; }
        @Override public String getVersion() { return "1.0.0"; }
        @Override public List<RecordType> getSupportedRecordTypes() { return List.copyOf(supported); }
        @Override public Promise<Void> initialize(Map<String, Object> config) { return Promise.complete(); }
        @Override public Promise<Void> shutdown() { return Promise.complete(); }
        @Override public Promise<HealthStatus> healthCheck() { return Promise.of(HealthStatus.ok()); }
        @Override public Promise<Collection> createCollection(Collection collection) { return Promise.of(collection); }
        @Override public Promise<Optional<Collection>> getCollection(String tenantId, String name) { return Promise.of(Optional.empty()); }
        @Override public Promise<Collection> updateCollection(Collection collection) { return Promise.of(collection); }
        @Override public Promise<Void> deleteCollection(String tenantId, String name) { return Promise.complete(); }
        @Override public Promise<List<Collection>> listCollections(String tenantId) { return Promise.of(List.of()); }
        @Override public Promise<DataRecord> insert(DataRecord record) { return Promise.of(record); }
        @Override public Promise<BatchResult<java.util.UUID>> insertBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); }
        @Override public Promise<Optional<DataRecord>> getById(String tenantId, String collectionName, java.util.UUID id) { return Promise.of(Optional.empty()); }
        @Override public Promise<List<DataRecord>> getByIds(String tenantId, String collectionName, List<java.util.UUID> ids) { return Promise.of(List.of()); }
        @Override public Promise<DataRecord> update(DataRecord record) { return Promise.of(record); }
        @Override public Promise<BatchResult<java.util.UUID>> updateBatch(List<DataRecord> records) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); }
        @Override public Promise<Void> delete(String tenantId, String collectionName, java.util.UUID id) { return Promise.complete(); }
        @Override public Promise<BatchResult<java.util.UUID>> deleteBatch(String tenantId, String collectionName, List<java.util.UUID> ids) { return Promise.of(new BatchResult<>(0, 0, 0, List.of())); }
        @Override public Promise<com.ghatana.datacloud.spi.StoragePlugin.QueryResult<DataRecord>> query(RecordQuery query) { return Promise.of(com.ghatana.datacloud.spi.StoragePlugin.QueryResult.empty()); }
        @Override public Promise<Long> count(RecordQuery query) { return Promise.of(0L); }
        @Override public Promise<Boolean> exists(RecordQuery query) { return Promise.of(false); }
    }

    @Test
    void register_and_getPlugin() {
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance();
        DummyPlugin plugin = new DummyPlugin("test", RecordType.EVENT);
        reg.register(plugin);
        assertThat(reg.getPlugin("test")).contains(plugin);
        assertThatThrownBy(() -> reg.register(plugin)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void set_and_getDefaultPlugin() {
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance();
        DummyPlugin plugin = new DummyPlugin("def", RecordType.ENTITY);
        reg.register(plugin);
        reg.setDefaultPlugin(RecordType.ENTITY, "def");
        assertThat(reg.getDefaultPlugin(RecordType.ENTITY)).contains(plugin);
    }

    @Test
    void unregister_removes_plugin() {
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance();
        DummyPlugin plugin = new DummyPlugin("unreg", RecordType.EVENT);
        reg.register(plugin);
        reg.unregister("unreg").whenComplete(($,e) -> {});
        assertThat(reg.getPlugin("unreg")).isEmpty();
    }

    @Test
    void plugin_config_management() {
        StoragePluginRegistry reg = StoragePluginRegistry.getInstance();
        DummyPlugin plugin = new DummyPlugin("cfg", RecordType.EVENT);
        reg.register(plugin);
        Map<String,Object> cfg = Map.of("foo", 42);
        reg.setPluginConfig("cfg", cfg);
        assertThat(reg.getPluginConfig("cfg")).containsEntry("foo", 42);
    }
}
