/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 5.6 — Tests for embedded storage backends (RocksDB, SQLite, H2). // GH-90000
 */
package com.ghatana.datacloud.embedded;

import com.ghatana.datacloud.embedded.EmbeddableDataCloud.EmbeddedEventStream;
import com.ghatana.datacloud.record.Record;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests verifying all three embedded storage backends:
 * RocksDB (LSM-tree), SQLite (embedded SQL), H2 (pure Java SQL). // GH-90000
 *
 * <p>Also tests the shared {@link RecordCodec} serialization layer.
 */
@DisplayName("Embedded Storage Backends")
class EmbeddedStorageBackendTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    // Shared no-op event stream for tests
    private static final EmbeddedEventStream NO_OP_EVENTS = new EmbeddedEventStream() { // GH-90000
        @Override
        public void subscribe(EmbeddedEventStream.ChangeListener listener) {} // GH-90000

        @Override
        public void unsubscribe(EmbeddedEventStream.ChangeListener listener) {} // GH-90000
    };

    private static Record testRecord(String id, String tenant, String collection, // GH-90000
                                      Record.RecordType type, Map<String, Object> data) {
        return new RecordCodec.StoredRecord( // GH-90000
                UUID.fromString(id), tenant, collection, type, data // GH-90000
        );
    }

    private static Record sampleEntity() { // GH-90000
        return testRecord( // GH-90000
                "00000000-0000-0000-0000-000000000001",
                "tenant-1", "users", Record.RecordType.ENTITY,
                Map.of("name", "Alice", "age", 30) // GH-90000
        );
    }

    private static Record sampleEvent() { // GH-90000
        return testRecord( // GH-90000
                "00000000-0000-0000-0000-000000000002",
                "tenant-1", "clicks", Record.RecordType.EVENT,
                Map.of("action", "click", "target", "button-1") // GH-90000
        );
    }

    // ─────────────────── RecordCodec ───────────────────

    @Nested
    @DisplayName("RecordCodec")
    class RecordCodecTests {

        @Test
        @DisplayName("round-trip serialization preserves all fields")
        void roundTrip() { // GH-90000
            Record original = sampleEntity(); // GH-90000
            byte[] bytes = RecordCodec.serialize(original); // GH-90000
            Record restored = RecordCodec.deserialize(bytes); // GH-90000

            assertThat(restored.id()).isEqualTo(original.id()); // GH-90000
            assertThat(restored.tenantId()).isEqualTo(original.tenantId()); // GH-90000
            assertThat(restored.collectionName()).isEqualTo(original.collectionName()); // GH-90000
            assertThat(restored.recordType()).isEqualTo(original.recordType()); // GH-90000
            assertThat(restored.data()).isEqualTo(original.data()); // GH-90000
        }

        @Test
        @DisplayName("empty data map round-trips")
        void emptyData() { // GH-90000
            Record rec = testRecord( // GH-90000
                    "00000000-0000-0000-0000-000000000003",
                    "t", "c", Record.RecordType.TIMESERIES, Map.of() // GH-90000
            );
            Record restored = RecordCodec.deserialize(RecordCodec.serialize(rec)); // GH-90000
            assertThat(restored.data()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("all record types round-trip")
        void allRecordTypes() { // GH-90000
            for (Record.RecordType type : Record.RecordType.values()) { // GH-90000
                Record rec = testRecord( // GH-90000
                        UUID.randomUUID().toString(), // GH-90000
                        "t", "c", type, Map.of("x", "y") // GH-90000
                );
                Record restored = RecordCodec.deserialize(RecordCodec.serialize(rec)); // GH-90000
                assertThat(restored.recordType()).isEqualTo(type); // GH-90000
            }
        }
    }

    // ─────────────────── H2Store (pure Java — most portable) ─────────────────── // GH-90000

    @Nested
    @DisplayName("H2Store")
    class H2StoreTests {

        private H2Store store;

        @BeforeEach
        void setUp() { // GH-90000
            store = new H2Store( // GH-90000
                    tempDir.resolve("h2test-" + java.util.UUID.randomUUID()), // GH-90000
                    NO_OP_EVENTS,
                    H2Store.H2Config.inMemory() // GH-90000
            );
        }

        @AfterEach
        void tearDown() { // GH-90000
            store.close(); // GH-90000
        }

        @Test
        @DisplayName("put and get round-trip")
        void putAndGet() { // GH-90000
            Record entity = sampleEntity(); // GH-90000
            runPromise(() -> store.put("key-1", entity)); // GH-90000

            Optional<Record> found = runPromise(() -> store.get("key-1"));
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().id()).isEqualTo(entity.id()); // GH-90000
            assertThat(found.get().tenantId()).isEqualTo("tenant-1");
            assertThat(found.get().data()).containsEntry("name", "Alice"); // GH-90000
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() { // GH-90000
            assertThat(runPromise(() -> store.get("missing"))).isEmpty();
        }

        @Test
        @DisplayName("put overwrites existing key")
        void putOverwrite() { // GH-90000
            runPromise(() -> store.put("key-1", sampleEntity())); // GH-90000
            runPromise(() -> store.put("key-1", sampleEvent())); // GH-90000

            Record found = runPromise(() -> store.get("key-1")).orElseThrow();
            assertThat(found.collectionName()).isEqualTo("clicks");
        }

        @Test
        @DisplayName("delete returns true for existing key")
        void deleteExisting() { // GH-90000
            runPromise(() -> store.put("key-1", sampleEntity())); // GH-90000
            assertThat(runPromise(() -> store.delete("key-1"))).isTrue();
            assertThat(runPromise(() -> store.get("key-1"))).isEmpty();
        }

        @Test
        @DisplayName("delete returns false for missing key")
        void deleteMissing() { // GH-90000
            assertThat(runPromise(() -> store.delete("missing"))).isFalse();
        }

        @Test
        @DisplayName("exists checks correctly")
        void existsCheck() { // GH-90000
            assertThat(runPromise(() -> store.exists("key-1"))).isFalse();
            runPromise(() -> store.put("key-1", sampleEntity())); // GH-90000
            assertThat(runPromise(() -> store.exists("key-1"))).isTrue();
        }

        @Test
        @DisplayName("count tracks records")
        void countTracks() { // GH-90000
            assertThat(runPromise(() -> store.count())).isZero(); // GH-90000
            runPromise(() -> store.put("k1", sampleEntity())); // GH-90000
            runPromise(() -> store.put("k2", sampleEvent())); // GH-90000
            assertThat(runPromise(() -> store.count())).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("clear removes all records")
        void clearAll() { // GH-90000
            runPromise(() -> store.put("k1", sampleEntity())); // GH-90000
            runPromise(() -> store.put("k2", sampleEvent())); // GH-90000
            runPromise(() -> store.clear()); // GH-90000
            assertThat(runPromise(() -> store.count())).isZero(); // GH-90000
        }
    }

    // ─────────────────── RocksDBStore ───────────────────

    @Nested
    @DisplayName("RocksDBStore")
    class RocksDBStoreTests {

        private RocksDBStore store;

        @BeforeEach
        void setUp() { // GH-90000
            store = new RocksDBStore( // GH-90000
                    tempDir.resolve("rocksdb-test"),
                    NO_OP_EVENTS,
                    RocksDBStore.RocksDBConfig.defaults() // GH-90000
            );
        }

        @AfterEach
        void tearDown() { // GH-90000
            store.close(); // GH-90000
        }

        @Test
        @DisplayName("put and get round-trip")
        void putAndGet() { // GH-90000
            runPromise(() -> store.put("r-1", sampleEntity())); // GH-90000
            Optional<Record> found = runPromise(() -> store.get("r-1"));
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().id()).isEqualTo(sampleEntity().id()); // GH-90000
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() { // GH-90000
            assertThat(runPromise(() -> store.get("absent"))).isEmpty();
        }

        @Test
        @DisplayName("delete removes record")
        void deleteRemoves() { // GH-90000
            runPromise(() -> store.put("r-1", sampleEntity())); // GH-90000
            assertThat(runPromise(() -> store.delete("r-1"))).isTrue();
            assertThat(runPromise(() -> store.get("r-1"))).isEmpty();
        }

        @Test
        @DisplayName("delete returns false for missing")
        void deleteFalse() { // GH-90000
            assertThat(runPromise(() -> store.delete("absent"))).isFalse();
        }

        @Test
        @DisplayName("exists checks correctly")
        void existsCheck() { // GH-90000
            assertThat(runPromise(() -> store.exists("r-1"))).isFalse();
            runPromise(() -> store.put("r-1", sampleEntity())); // GH-90000
            assertThat(runPromise(() -> store.exists("r-1"))).isTrue();
        }

        @Test
        @DisplayName("count and clear")
        void countAndClear() { // GH-90000
            runPromise(() -> store.put("a", sampleEntity())); // GH-90000
            runPromise(() -> store.put("b", sampleEvent())); // GH-90000
            assertThat(runPromise(() -> store.count())).isEqualTo(2); // GH-90000
            runPromise(() -> store.clear()); // GH-90000
            assertThat(runPromise(() -> store.count())).isZero(); // GH-90000
        }

        @Test
        @DisplayName("operations throw after close")
        void throwsAfterClose() { // GH-90000
            store.close(); // GH-90000
            assertThatThrownBy(() -> store.put("k", sampleEntity())) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000
        }
    }

    // ─────────────────── SQLiteStore ───────────────────

    @Nested
    @DisplayName("SQLiteStore")
    class SQLiteStoreTests {

        private SQLiteStore store;

        @BeforeEach
        void setUp() { // GH-90000
            store = new SQLiteStore( // GH-90000
                    tempDir.resolve("sqlite-test.db"),
                    NO_OP_EVENTS,
                    SQLiteStore.SQLiteConfig.defaults() // GH-90000
            );
        }

        @AfterEach
        void tearDown() { // GH-90000
            store.close(); // GH-90000
        }

        @Test
        @DisplayName("put and get round-trip")
        void putAndGet() { // GH-90000
            runPromise(() -> store.put("s-1", sampleEntity())); // GH-90000
            Optional<Record> found = runPromise(() -> store.get("s-1"));
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().id()).isEqualTo(sampleEntity().id()); // GH-90000
            assertThat(found.get().data()).containsEntry("name", "Alice"); // GH-90000
        }

        @Test
        @DisplayName("get returns empty for missing key")
        void getMissing() { // GH-90000
            assertThat(runPromise(() -> store.get("absent"))).isEmpty();
        }

        @Test
        @DisplayName("delete removes record")
        void deleteRemoves() { // GH-90000
            runPromise(() -> store.put("s-1", sampleEntity())); // GH-90000
            assertThat(runPromise(() -> store.delete("s-1"))).isTrue();
            assertThat(runPromise(() -> store.get("s-1"))).isEmpty();
        }

        @Test
        @DisplayName("exists, count, clear")
        void existsCountClear() { // GH-90000
            assertThat(runPromise(() -> store.exists("s-1"))).isFalse();
            runPromise(() -> store.put("s-1", sampleEntity())); // GH-90000
            runPromise(() -> store.put("s-2", sampleEvent())); // GH-90000
            assertThat(runPromise(() -> store.exists("s-1"))).isTrue();
            assertThat(runPromise(() -> store.count())).isEqualTo(2); // GH-90000
            runPromise(() -> store.clear()); // GH-90000
            assertThat(runPromise(() -> store.count())).isZero(); // GH-90000
        }
    }

    // ─────────────────── Config Presets ───────────────────

    @Nested
    @DisplayName("Configuration Presets")
    class ConfigTests {

        @Test
        @DisplayName("RocksDB config presets are valid")
        void rocksDbConfigs() { // GH-90000
            assertThat(RocksDBStore.RocksDBConfig.defaults().writeBufferSize()).isPositive(); // GH-90000
            assertThat(RocksDBStore.RocksDBConfig.highThroughput().maxWriteBufferNumber()).isEqualTo(5); // GH-90000
            assertThat(RocksDBStore.RocksDBConfig.lowMemory().enableCompression()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SQLite config presets are valid")
        void sqliteConfigs() { // GH-90000
            assertThat(SQLiteStore.SQLiteConfig.defaults().enableWAL()).isTrue(); // GH-90000
            assertThat(SQLiteStore.SQLiteConfig.highPerformance().cacheSize()).isEqualTo(10000); // GH-90000
            assertThat(SQLiteStore.SQLiteConfig.lowMemory().enableWAL()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("H2 config presets are valid")
        void h2Configs() { // GH-90000
            assertThat(H2Store.H2Config.defaults().mode()).isEqualTo("EMBEDDED");
            assertThat(H2Store.H2Config.inMemory().mode()).isEqualTo("MEMORY");
            assertThat(H2Store.H2Config.highPerformance().cacheSize()).isEqualTo(65536); // GH-90000
            assertThat(H2Store.H2Config.lowMemory().compress()).isTrue(); // GH-90000
        }
    }
}
