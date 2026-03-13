/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.6 — Tests for embedded storage backends (RocksDB, SQLite, H2).
 */
package com.ghatana.datacloud.embedded;

import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedEventStream;
import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedStore;
import com.ghatana.datacloud.record.Record;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
class EmbeddedStorageBackendTest extends EventloopTestBase {

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
            runPromise(() -> store.put("key-1", entity));

            Optional<Record> found = runPromise(() -> store.get("key-1"));
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(entity.id());
            assertThat(found.get().tenantId()).isEqualTo("tenant-1");
            assertThat(found.get().data()).containsEntry("name", "Alice");
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() {
            assertThat(runPromise(() -> store.get("missing"))).isEmpty();
        }

        @Test
        @DisplayName("put overwrites existing key")
        void putOverwrite() {
            runPromise(() -> store.put("key-1", sampleEntity()));
            runPromise(() -> store.put("key-1", sampleEvent()));

            Record found = runPromise(() -> store.get("key-1")).orElseThrow();
            assertThat(found.collectionName()).isEqualTo("clicks");
        }

        @Test
        @DisplayName("delete returns true for existing key")
        void deleteExisting() {
            runPromise(() -> store.put("key-1", sampleEntity()));
            assertThat(runPromise(() -> store.delete("key-1"))).isTrue();
            assertThat(runPromise(() -> store.get("key-1"))).isEmpty();
        }

        @Test
        @DisplayName("delete returns false for missing key")
        void deleteMissing() {
            assertThat(runPromise(() -> store.delete("missing"))).isFalse();
        }

        @Test
        @DisplayName("exists checks correctly")
        void existsCheck() {
            assertThat(runPromise(() -> store.exists("key-1"))).isFalse();
            runPromise(() -> store.put("key-1", sampleEntity()));
            assertThat(runPromise(() -> store.exists("key-1"))).isTrue();
        }

        @Test
        @DisplayName("count tracks records")
        void countTracks() {
            assertThat(runPromise(() -> store.count())).isZero();
            runPromise(() -> store.put("k1", sampleEntity()));
            runPromise(() -> store.put("k2", sampleEvent()));
            assertThat(runPromise(() -> store.count())).isEqualTo(2);
        }

        @Test
        @DisplayName("clear removes all records")
        void clearAll() {
            runPromise(() -> store.put("k1", sampleEntity()));
            runPromise(() -> store.put("k2", sampleEvent()));
            runPromise(() -> store.clear());
            assertThat(runPromise(() -> store.count())).isZero();
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
            runPromise(() -> store.put("r-1", sampleEntity()));
            Optional<Record> found = runPromise(() -> store.get("r-1"));
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(sampleEntity().id());
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() {
            assertThat(runPromise(() -> store.get("absent"))).isEmpty();
        }

        @Test
        @DisplayName("delete removes record")
        void deleteRemoves() {
            runPromise(() -> store.put("r-1", sampleEntity()));
            assertThat(runPromise(() -> store.delete("r-1"))).isTrue();
            assertThat(runPromise(() -> store.get("r-1"))).isEmpty();
        }

        @Test
        @DisplayName("delete returns false for missing")
        void deleteFalse() {
            assertThat(runPromise(() -> store.delete("absent"))).isFalse();
        }

        @Test
        @DisplayName("exists checks correctly")
        void existsCheck() {
            assertThat(runPromise(() -> store.exists("r-1"))).isFalse();
            runPromise(() -> store.put("r-1", sampleEntity()));
            assertThat(runPromise(() -> store.exists("r-1"))).isTrue();
        }

        @Test
        @DisplayName("count and clear")
        void countAndClear() {
            runPromise(() -> store.put("a", sampleEntity()));
            runPromise(() -> store.put("b", sampleEvent()));
            assertThat(runPromise(() -> store.count())).isEqualTo(2);
            runPromise(() -> store.clear());
            assertThat(runPromise(() -> store.count())).isZero();
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
            runPromise(() -> store.put("s-1", sampleEntity()));
            Optional<Record> found = runPromise(() -> store.get("s-1"));
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(sampleEntity().id());
            assertThat(found.get().data()).containsEntry("name", "Alice");
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() {
            assertThat(runPromise(() -> store.get("absent"))).isEmpty();
        }

        @Test
        @DisplayName("delete removes record")
        void deleteRemoves() {
            runPromise(() -> store.put("s-1", sampleEntity()));
            assertThat(runPromise(() -> store.delete("s-1"))).isTrue();
            assertThat(runPromise(() -> store.get("s-1"))).isEmpty();
        }

        @Test
        @DisplayName("exists, count, clear")
        void existsCountClear() {
            assertThat(runPromise(() -> store.exists("s-1"))).isFalse();
            runPromise(() -> store.put("s-1", sampleEntity()));
            runPromise(() -> store.put("s-2", sampleEvent()));
            assertThat(runPromise(() -> store.exists("s-1"))).isTrue();
            assertThat(runPromise(() -> store.count())).isEqualTo(2);
            runPromise(() -> store.clear());
            assertThat(runPromise(() -> store.count())).isZero();
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
