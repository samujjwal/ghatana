/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.6 — Tests for embedded storage backends (RocksDB, SQLite, H2).
 */
package com.ghatana.datacloud.embedded;

import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedEventStream;
import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedStore;
import com.ghatana.datacloud.record.Record;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests verifying all three embedded storage backends:
 * RocksDB (LSM-tree), SQLite (embedded SQL), H2 (pure Java SQL).
 *
 * <p>Also tests the shared {@link RecordCodec} serialization layer.
 */
@DisplayName("Embedded Storage Backends")
class EmbeddedStorageBackendTest {

    @TempDir
    Path tempDir;

    // Shared no-op event stream for tests
    private static final EmbeddedEventStream NO_OP_EVENTS = new EmbeddedEventStream() {
        @Override
        public void subscribe(EmbeddedEventStream.ChangeListener listener) {}

        @Override
        public void unsubscribe(EmbeddedEventStream.ChangeListener listener) {}
    };

    private static Record testRecord(String id, String tenant, String collection,
                                      Record.RecordType type, Map<String, Object> data) {
        return new RecordCodec.StoredRecord(
                UUID.fromString(id), tenant, collection, type, data
        );
    }

    private static Record sampleEntity() {
        return testRecord(
                "00000000-0000-0000-0000-000000000001",
                "tenant-1", "users", Record.RecordType.ENTITY,
                Map.of("name", "Alice", "age", 30)
        );
    }

    private static Record sampleEvent() {
        return testRecord(
                "00000000-0000-0000-0000-000000000002",
                "tenant-1", "clicks", Record.RecordType.EVENT,
                Map.of("action", "click", "target", "button-1")
        );
    }

    // ─────────────────── RecordCodec ───────────────────

    @Nested
    @DisplayName("RecordCodec")
    class RecordCodecTests {

        @Test
        @DisplayName("round-trip serialization preserves all fields")
        void roundTrip() {
            Record original = sampleEntity();
            byte[] bytes = RecordCodec.serialize(original);
            Record restored = RecordCodec.deserialize(bytes);

            assertThat(restored.id()).isEqualTo(original.id());
            assertThat(restored.tenantId()).isEqualTo(original.tenantId());
            assertThat(restored.collectionName()).isEqualTo(original.collectionName());
            assertThat(restored.recordType()).isEqualTo(original.recordType());
            assertThat(restored.data()).isEqualTo(original.data());
        }

        @Test
        @DisplayName("empty data map round-trips")
        void emptyData() {
            Record rec = testRecord(
                    "00000000-0000-0000-0000-000000000003",
                    "t", "c", Record.RecordType.TIMESERIES, Map.of()
            );
            Record restored = RecordCodec.deserialize(RecordCodec.serialize(rec));
            assertThat(restored.data()).isEmpty();
        }

        @Test
        @DisplayName("all record types round-trip")
        void allRecordTypes() {
            for (Record.RecordType type : Record.RecordType.values()) {
                Record rec = testRecord(
                        UUID.randomUUID().toString(),
                        "t", "c", type, Map.of("x", "y")
                );
                Record restored = RecordCodec.deserialize(RecordCodec.serialize(rec));
                assertThat(restored.recordType()).isEqualTo(type);
            }
        }
    }

    // ─────────────────── H2Store (pure Java — most portable) ───────────────────

    @Nested
    @DisplayName("H2Store")
    class H2StoreTests {

        private H2Store store;

        @BeforeEach
        void setUp() {
            store = new H2Store(
                    tempDir.resolve("h2test-" + java.util.UUID.randomUUID()),
                    NO_OP_EVENTS,
                    H2Store.H2Config.inMemory()
            );
        }

        @AfterEach
        void tearDown() {
            store.close();
        }

        @Test
        @DisplayName("put and get round-trip")
        void putAndGet() {
            Record entity = sampleEntity();
            store.put("key-1", entity).getResult();

            Optional<Record> found = store.get("key-1").getResult();
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(entity.id());
            assertThat(found.get().tenantId()).isEqualTo("tenant-1");
            assertThat(found.get().data()).containsEntry("name", "Alice");
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() {
            assertThat(store.get("missing").getResult()).isEmpty();
        }

        @Test
        @DisplayName("put overwrites existing key")
        void putOverwrite() {
            store.put("key-1", sampleEntity()).getResult();
            store.put("key-1", sampleEvent()).getResult();

            Record found = store.get("key-1").getResult().orElseThrow();
            assertThat(found.collectionName()).isEqualTo("clicks");
        }

        @Test
        @DisplayName("delete returns true for existing key")
        void deleteExisting() {
            store.put("key-1", sampleEntity()).getResult();
            assertThat(store.delete("key-1").getResult()).isTrue();
            assertThat(store.get("key-1").getResult()).isEmpty();
        }

        @Test
        @DisplayName("delete returns false for missing key")
        void deleteMissing() {
            assertThat(store.delete("missing").getResult()).isFalse();
        }

        @Test
        @DisplayName("exists checks correctly")
        void existsCheck() {
            assertThat(store.exists("key-1").getResult()).isFalse();
            store.put("key-1", sampleEntity()).getResult();
            assertThat(store.exists("key-1").getResult()).isTrue();
        }

        @Test
        @DisplayName("count tracks records")
        void countTracks() {
            assertThat(store.count().getResult()).isZero();
            store.put("k1", sampleEntity()).getResult();
            store.put("k2", sampleEvent()).getResult();
            assertThat(store.count().getResult()).isEqualTo(2);
        }

        @Test
        @DisplayName("clear removes all records")
        void clearAll() {
            store.put("k1", sampleEntity()).getResult();
            store.put("k2", sampleEvent()).getResult();
            store.clear().getResult();
            assertThat(store.count().getResult()).isZero();
        }
    }

    // ─────────────────── RocksDBStore ───────────────────

    @Nested
    @DisplayName("RocksDBStore")
    class RocksDBStoreTests {

        private RocksDBStore store;

        @BeforeEach
        void setUp() {
            store = new RocksDBStore(
                    tempDir.resolve("rocksdb-test"),
                    NO_OP_EVENTS,
                    RocksDBStore.RocksDBConfig.defaults()
            );
        }

        @AfterEach
        void tearDown() {
            store.close();
        }

        @Test
        @DisplayName("put and get round-trip")
        void putAndGet() {
            store.put("r-1", sampleEntity()).getResult();
            Optional<Record> found = store.get("r-1").getResult();
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(sampleEntity().id());
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() {
            assertThat(store.get("absent").getResult()).isEmpty();
        }

        @Test
        @DisplayName("delete removes record")
        void deleteRemoves() {
            store.put("r-1", sampleEntity()).getResult();
            assertThat(store.delete("r-1").getResult()).isTrue();
            assertThat(store.get("r-1").getResult()).isEmpty();
        }

        @Test
        @DisplayName("delete returns false for missing")
        void deleteFalse() {
            assertThat(store.delete("absent").getResult()).isFalse();
        }

        @Test
        @DisplayName("exists checks correctly")
        void existsCheck() {
            assertThat(store.exists("r-1").getResult()).isFalse();
            store.put("r-1", sampleEntity()).getResult();
            assertThat(store.exists("r-1").getResult()).isTrue();
        }

        @Test
        @DisplayName("count and clear")
        void countAndClear() {
            store.put("a", sampleEntity()).getResult();
            store.put("b", sampleEvent()).getResult();
            assertThat(store.count().getResult()).isEqualTo(2);
            store.clear().getResult();
            assertThat(store.count().getResult()).isZero();
        }

        @Test
        @DisplayName("operations throw after close")
        void throwsAfterClose() {
            store.close();
            assertThatThrownBy(() -> store.put("k", sampleEntity()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─────────────────── SQLiteStore ───────────────────

    @Nested
    @DisplayName("SQLiteStore")
    class SQLiteStoreTests {

        private SQLiteStore store;

        @BeforeEach
        void setUp() {
            store = new SQLiteStore(
                    tempDir.resolve("sqlite-test.db"),
                    NO_OP_EVENTS,
                    SQLiteStore.SQLiteConfig.defaults()
            );
        }

        @AfterEach
        void tearDown() {
            store.close();
        }

        @Test
        @DisplayName("put and get round-trip")
        void putAndGet() {
            store.put("s-1", sampleEntity()).getResult();
            Optional<Record> found = store.get("s-1").getResult();
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(sampleEntity().id());
            assertThat(found.get().data()).containsEntry("name", "Alice");
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() {
            assertThat(store.get("absent").getResult()).isEmpty();
        }

        @Test
        @DisplayName("delete removes record")
        void deleteRemoves() {
            store.put("s-1", sampleEntity()).getResult();
            assertThat(store.delete("s-1").getResult()).isTrue();
            assertThat(store.get("s-1").getResult()).isEmpty();
        }

        @Test
        @DisplayName("exists, count, clear")
        void existsCountClear() {
            assertThat(store.exists("s-1").getResult()).isFalse();
            store.put("s-1", sampleEntity()).getResult();
            store.put("s-2", sampleEvent()).getResult();
            assertThat(store.exists("s-1").getResult()).isTrue();
            assertThat(store.count().getResult()).isEqualTo(2);
            store.clear().getResult();
            assertThat(store.count().getResult()).isZero();
        }
    }

    // ─────────────────── Config Presets ───────────────────

    @Nested
    @DisplayName("Configuration Presets")
    class ConfigTests {

        @Test
        @DisplayName("RocksDB config presets are valid")
        void rocksDbConfigs() {
            assertThat(RocksDBStore.RocksDBConfig.defaults().writeBufferSize()).isPositive();
            assertThat(RocksDBStore.RocksDBConfig.highThroughput().maxWriteBufferNumber()).isEqualTo(5);
            assertThat(RocksDBStore.RocksDBConfig.lowMemory().enableCompression()).isFalse();
        }

        @Test
        @DisplayName("SQLite config presets are valid")
        void sqliteConfigs() {
            assertThat(SQLiteStore.SQLiteConfig.defaults().enableWAL()).isTrue();
            assertThat(SQLiteStore.SQLiteConfig.highPerformance().cacheSize()).isEqualTo(10000);
            assertThat(SQLiteStore.SQLiteConfig.lowMemory().enableWAL()).isFalse();
        }

        @Test
        @DisplayName("H2 config presets are valid")
        void h2Configs() {
            assertThat(H2Store.H2Config.defaults().mode()).isEqualTo("EMBEDDED");
            assertThat(H2Store.H2Config.inMemory().mode()).isEqualTo("MEMORY");
            assertThat(H2Store.H2Config.highPerformance().cacheSize()).isEqualTo(65536);
            assertThat(H2Store.H2Config.lowMemory().compress()).isTrue();
        }
    }
}
