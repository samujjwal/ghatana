package com.ghatana.datacloud.embedded;

import com.ghatana.datacloud.record.Record;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link SQLiteStore} with 100% coverage.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>CRUD operations (put, get, delete, exists)</li> // GH-90000
 *   <li>Count and clear operations</li>
 *   <li>Configuration options (WAL, cache, timeout)</li> // GH-90000
 *   <li>Schema initialization</li>
 *   <li>Error handling and validation</li>
 *   <li>Resource cleanup</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Comprehensive test coverage for SQLiteStore
 * @doc.layer core
 * @doc.pattern Unit Test, Integration Test
 */
@DisplayName("SQLite Store Tests [GH-90000]")
class SQLiteStoreTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private SQLiteStore store;
    private EmbeddableDataCloud.EmbeddedEventStream mockEventStream;

    @BeforeEach
    void setup() { // GH-90000
        mockEventStream = mock(EmbeddableDataCloud.EmbeddedEventStream.class); // GH-90000
        Path dbFile = tempDir.resolve("test.db [GH-90000]");
        store = new SQLiteStore(dbFile, mockEventStream, SQLiteStore.SQLiteConfig.defaults()); // GH-90000
    }

    @AfterEach
    void teardown() { // GH-90000
        if (store != null) { // GH-90000
            try {
                runPromise(() -> store.close()); // GH-90000
            } catch (Exception e) { // GH-90000
                // Ignore cleanup errors
            }
        }
    }

    // ========================================================================
    // CONSTRUCTOR TESTS
    // ========================================================================

    @Test
    @DisplayName("Should create SQLiteStore with valid parameters [GH-90000]")
    void shouldCreateWithValidParameters() { // GH-90000
        // GIVEN: Valid parameters
        Path dbFile = tempDir.resolve("new-test.db [GH-90000]");

        // WHEN: Creating store
        SQLiteStore newStore = new SQLiteStore(dbFile, mockEventStream, SQLiteStore.SQLiteConfig.defaults()); // GH-90000

        // THEN: Store is created successfully
        assertThat(newStore).isNotNull(); // GH-90000

        // Cleanup
        runPromise(() -> newStore.close()); // GH-90000
    }

    @Test
    @DisplayName("Should create SQLiteStore with null config (uses defaults) [GH-90000]")
    void shouldCreateWithNullConfig() { // GH-90000
        // GIVEN: Null config
        Path dbFile = tempDir.resolve("null-config.db [GH-90000]");

        // WHEN: Creating store with null config
        SQLiteStore newStore = new SQLiteStore(dbFile, mockEventStream, null); // GH-90000

        // THEN: Store is created with default config
        assertThat(newStore).isNotNull(); // GH-90000

        // Cleanup
        runPromise(() -> newStore.close()); // GH-90000
    }

    @Test
    @DisplayName("Should reject null database file [GH-90000]")
    void shouldRejectNullDatabaseFile() { // GH-90000
        // WHEN/THEN: Throws exception for null file
        assertThatThrownBy(() -> // GH-90000
            new SQLiteStore(null, mockEventStream, SQLiteStore.SQLiteConfig.defaults()) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("databaseFile [GH-90000]");
    }

    @Test
    @DisplayName("Should reject null event stream [GH-90000]")
    void shouldRejectNullEventStream() { // GH-90000
        // GIVEN: Valid file
        Path dbFile = tempDir.resolve("test.db [GH-90000]");

        // WHEN/THEN: Throws exception for null event stream
        assertThatThrownBy(() -> // GH-90000
            new SQLiteStore(dbFile, null, SQLiteStore.SQLiteConfig.defaults()) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("events [GH-90000]");
    }

    // ========================================================================
    // PUT OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should put record successfully [GH-90000]")
    void shouldPutRecord() { // GH-90000
        // GIVEN: Valid record
        TestRecord record = createTestRecord("record-1 [GH-90000]");

        // WHEN: Putting record
        runPromise(() -> store.put("key-1", record)); // GH-90000

        // THEN: Record is stored (verify by getting it back) // GH-90000
        Optional<Record> retrieved = runPromise(() -> store.get("key-1 [GH-90000]"));
        assertThat(retrieved).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("Should update existing record with put [GH-90000]")
    void shouldUpdateExistingRecord() { // GH-90000
        // GIVEN: Existing record
        TestRecord original = createTestRecord("original [GH-90000]");
        runPromise(() -> store.put("key-1", original)); // GH-90000

        // WHEN: Putting new record with same key
        TestRecord updated = createTestRecord("updated [GH-90000]");
        runPromise(() -> store.put("key-1", updated)); // GH-90000

        // THEN: Record is updated
        Optional<Record> retrieved = runPromise(() -> store.get("key-1 [GH-90000]"));
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().collectionName()).isEqualTo("updated [GH-90000]");
    }

    @Test
    @DisplayName("Should reject null key in put [GH-90000]")
    void shouldRejectNullKeyInPut() { // GH-90000
        // GIVEN: Valid record
        TestRecord record = createTestRecord("test [GH-90000]");

        // WHEN/THEN: Throws exception for null key
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> store.put(null, record)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("key [GH-90000]");
    }

    @Test
    @DisplayName("Should reject null record in put [GH-90000]")
    void shouldRejectNullRecordInPut() { // GH-90000
        // WHEN/THEN: Throws exception for null record
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> store.put("key-1", null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("record [GH-90000]");
    }

    // ========================================================================
    // GET OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should get existing record [GH-90000]")
    void shouldGetExistingRecord() { // GH-90000
        // GIVEN: Stored record
        TestRecord record = createTestRecord("test-collection [GH-90000]");
        runPromise(() -> store.put("key-1", record)); // GH-90000

        // WHEN: Getting record
        Optional<Record> retrieved = runPromise(() -> store.get("key-1 [GH-90000]"));

        // THEN: Record is returned
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().id()).isEqualTo(record.id()); // GH-90000
        assertThat(retrieved.get().tenantId()).isEqualTo(record.tenantId()); // GH-90000
        assertThat(retrieved.get().collectionName()).isEqualTo("test-collection [GH-90000]");
    }

    @Test
    @DisplayName("Should return empty for non-existent key [GH-90000]")
    void shouldReturnEmptyForNonExistent() { // GH-90000
        // WHEN: Getting non-existent record
        Optional<Record> retrieved = runPromise(() -> store.get("non-existent [GH-90000]"));

        // THEN: Returns empty
        assertThat(retrieved).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null key in get [GH-90000]")
    void shouldRejectNullKeyInGet() { // GH-90000
        // WHEN/THEN: Throws exception for null key
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> store.get(null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("key [GH-90000]");
    }

    // ========================================================================
    // DELETE OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should delete existing record [GH-90000]")
    void shouldDeleteExistingRecord() { // GH-90000
        // GIVEN: Stored record
        TestRecord record = createTestRecord("test [GH-90000]");
        runPromise(() -> store.put("key-1", record)); // GH-90000

        // WHEN: Deleting record
        Boolean deleted = runPromise(() -> store.delete("key-1 [GH-90000]"));

        // THEN: Record is deleted
        assertThat(deleted).isTrue(); // GH-90000

        // AND: Record no longer exists
        Optional<Record> retrieved = runPromise(() -> store.get("key-1 [GH-90000]"));
        assertThat(retrieved).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should return false when deleting non-existent record [GH-90000]")
    void shouldReturnFalseForNonExistentDelete() { // GH-90000
        // WHEN: Deleting non-existent record
        Boolean deleted = runPromise(() -> store.delete("non-existent [GH-90000]"));

        // THEN: Returns false
        assertThat(deleted).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null key in delete [GH-90000]")
    void shouldRejectNullKeyInDelete() { // GH-90000
        // WHEN/THEN: Throws exception for null key
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> store.delete(null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("key [GH-90000]");
    }

    // ========================================================================
    // EXISTS OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should return true for existing key [GH-90000]")
    void shouldReturnTrueForExistingKey() { // GH-90000
        // GIVEN: Stored record
        TestRecord record = createTestRecord("test [GH-90000]");
        runPromise(() -> store.put("key-1", record)); // GH-90000

        // WHEN: Checking existence
        Boolean exists = runPromise(() -> store.exists("key-1 [GH-90000]"));

        // THEN: Returns true
        assertThat(exists).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should return false for non-existent key [GH-90000]")
    void shouldReturnFalseForNonExistentKey() { // GH-90000
        // WHEN: Checking existence of non-existent key
        Boolean exists = runPromise(() -> store.exists("non-existent [GH-90000]"));

        // THEN: Returns false
        assertThat(exists).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null key in exists [GH-90000]")
    void shouldRejectNullKeyInExists() { // GH-90000
        // WHEN/THEN: Throws exception for null key
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> store.exists(null)) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("key [GH-90000]");
    }

    // ========================================================================
    // COUNT OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should return zero for empty store [GH-90000]")
    void shouldReturnZeroForEmptyStore() { // GH-90000
        // WHEN: Counting empty store
        Long count = runPromise(() -> store.count()); // GH-90000

        // THEN: Returns zero
        assertThat(count).isEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("Should return correct count after puts [GH-90000]")
    void shouldReturnCorrectCount() { // GH-90000
        // GIVEN: Multiple records
        for (int i = 0; i < 5; i++) { // GH-90000
            final int index = i;
            TestRecord record = createTestRecord("collection-" + index); // GH-90000
            runPromise(() -> store.put("key-" + index, record)); // GH-90000
        }

        // WHEN: Counting
        Long count = runPromise(() -> store.count()); // GH-90000

        // THEN: Returns correct count
        assertThat(count).isEqualTo(5L); // GH-90000
    }

    @Test
    @DisplayName("Should update count after delete [GH-90000]")
    void shouldUpdateCountAfterDelete() { // GH-90000
        // GIVEN: Multiple records
        for (int i = 0; i < 3; i++) { // GH-90000
            final int index = i;
            TestRecord record = createTestRecord("collection-" + index); // GH-90000
            runPromise(() -> store.put("key-" + index, record)); // GH-90000
        }

        // WHEN: Deleting one record
        runPromise(() -> store.delete("key-1 [GH-90000]"));

        // THEN: Count is updated
        Long count = runPromise(() -> store.count()); // GH-90000
        assertThat(count).isEqualTo(2L); // GH-90000
    }

    // ========================================================================
    // CLEAR OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should clear all records [GH-90000]")
    void shouldClearAllRecords() { // GH-90000
        // GIVEN: Multiple records
        for (int i = 0; i < 5; i++) { // GH-90000
            final int index = i;
            TestRecord record = createTestRecord("collection-" + index); // GH-90000
            runPromise(() -> store.put("key-" + index, record)); // GH-90000
        }

        // WHEN: Clearing store
        runPromise(() -> store.clear()); // GH-90000

        // THEN: Store is empty
        Long count = runPromise(() -> store.count()); // GH-90000
        assertThat(count).isEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("Should handle clear on empty store [GH-90000]")
    void shouldHandleClearOnEmptyStore() { // GH-90000
        // WHEN: Clearing empty store
        runPromise(() -> store.clear()); // GH-90000

        // THEN: No error and count is still zero
        Long count = runPromise(() -> store.count()); // GH-90000
        assertThat(count).isEqualTo(0L); // GH-90000
    }

    // ========================================================================
    // CONFIGURATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should create store with default config [GH-90000]")
    void shouldCreateWithDefaultConfig() { // GH-90000
        // GIVEN: Default config
        SQLiteStore.SQLiteConfig config = SQLiteStore.SQLiteConfig.defaults(); // GH-90000

        // THEN: Config has expected values
        assertThat(config.enableWAL()).isTrue(); // GH-90000
        assertThat(config.cacheSize()).isEqualTo(2000); // GH-90000
        assertThat(config.busyTimeout()).isEqualTo(5000); // GH-90000
        assertThat(config.enableForeignKeys()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should create store with high performance config [GH-90000]")
    void shouldCreateWithHighPerformanceConfig() { // GH-90000
        // GIVEN: High performance config
        SQLiteStore.SQLiteConfig config = SQLiteStore.SQLiteConfig.highPerformance(); // GH-90000
        Path dbFile = tempDir.resolve("high-perf.db [GH-90000]");

        // WHEN: Creating store
        SQLiteStore highPerfStore = new SQLiteStore(dbFile, mockEventStream, config); // GH-90000

        // THEN: Store is created with high performance settings
        assertThat(config.enableWAL()).isTrue(); // GH-90000
        assertThat(config.cacheSize()).isEqualTo(10000); // GH-90000
        assertThat(config.busyTimeout()).isEqualTo(10000); // GH-90000

        // Cleanup
        runPromise(() -> highPerfStore.close()); // GH-90000
    }

    @Test
    @DisplayName("Should create store with low memory config [GH-90000]")
    void shouldCreateWithLowMemoryConfig() { // GH-90000
        // GIVEN: Low memory config
        SQLiteStore.SQLiteConfig config = SQLiteStore.SQLiteConfig.lowMemory(); // GH-90000
        Path dbFile = tempDir.resolve("low-mem.db [GH-90000]");

        // WHEN: Creating store
        SQLiteStore lowMemStore = new SQLiteStore(dbFile, mockEventStream, config); // GH-90000

        // THEN: Store is created with low memory settings
        assertThat(config.enableWAL()).isFalse(); // GH-90000
        assertThat(config.cacheSize()).isEqualTo(500); // GH-90000
        assertThat(config.busyTimeout()).isEqualTo(2000); // GH-90000

        // Cleanup
        runPromise(() -> lowMemStore.close()); // GH-90000
    }

    // ========================================================================
    // CLOSE OPERATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should close store gracefully [GH-90000]")
    void shouldCloseGracefully() { // GH-90000
        // GIVEN: Store with data
        TestRecord record = createTestRecord("test [GH-90000]");
        runPromise(() -> store.put("key-1", record)); // GH-90000

        // WHEN: Closing store
        runPromise(() -> store.close()); // GH-90000

        // THEN: No exception thrown (store closed successfully) // GH-90000
        // Note: After close, operations may fail, but close itself should succeed
    }

    // ========================================================================
    // PERSISTENCE TESTS
    // ========================================================================

    @Test
    @DisplayName("Should persist data across store instances [GH-90000]")
    void shouldPersistDataAcrossInstances() { // GH-90000
        // GIVEN: Store with data
        Path dbFile = tempDir.resolve("persist-test.db [GH-90000]");
        SQLiteStore store1 = new SQLiteStore(dbFile, mockEventStream, SQLiteStore.SQLiteConfig.defaults()); // GH-90000

        TestRecord record = createTestRecord("persistent [GH-90000]");
        runPromise(() -> store1.put("persist-key", record)); // GH-90000
        runPromise(() -> store1.close()); // GH-90000

        // WHEN: Creating new store instance with same file
        SQLiteStore store2 = new SQLiteStore(dbFile, mockEventStream, SQLiteStore.SQLiteConfig.defaults()); // GH-90000

        // THEN: Data is persisted
        Optional<Record> retrieved = runPromise(() -> store2.get("persist-key [GH-90000]"));
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().collectionName()).isEqualTo("persistent [GH-90000]");

        // Cleanup
        runPromise(() -> store2.close()); // GH-90000
    }

    // ========================================================================
    // CONCURRENT OPERATIONS TESTS
    // ========================================================================

    @Test
    @DisplayName("Should handle multiple sequential operations [GH-90000]")
    void shouldHandleSequentialOperations() { // GH-90000
        // GIVEN: Multiple operations
        for (int i = 0; i < 10; i++) { // GH-90000
            final int index = i;
            TestRecord record = createTestRecord("collection-" + index); // GH-90000
            runPromise(() -> store.put("key-" + index, record)); // GH-90000
        }

        // WHEN: Performing mixed operations
        runPromise(() -> store.delete("key-5 [GH-90000]"));
        runPromise(() -> store.put("key-5", createTestRecord("replaced [GH-90000]")));

        // THEN: All operations complete successfully
        Long count = runPromise(() -> store.count()); // GH-90000
        assertThat(count).isEqualTo(10L); // GH-90000

        Optional<Record> retrieved = runPromise(() -> store.get("key-5 [GH-90000]"));
        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().collectionName()).isEqualTo("replaced [GH-90000]");
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Creates a test record for testing.
     */
    private TestRecord createTestRecord(String collectionName) { // GH-90000
        return new TestRecord( // GH-90000
            UUID.randomUUID(), // GH-90000
            "tenant-1",
            collectionName,
            Map.of("field1", "value1", "field2", 123), // GH-90000
            Record.RecordType.ENTITY
        );
    }

    /**
     * Test implementation of Record interface.
     */
    private static class TestRecord implements Record, Serializable {
        private static final long serialVersionUID = 1L;

        private final UUID id;
        private final String tenantId;
        private final String collectionName;
        private final Map<String, Object> data;
        private final RecordType recordType;

        TestRecord(UUID id, String tenantId, String collectionName, // GH-90000
                   Map<String, Object> data, RecordType recordType) {
            this.id = id;
            this.tenantId = tenantId;
            this.collectionName = collectionName;
            this.data = new HashMap<>(data); // GH-90000
            this.recordType = recordType;
        }

        @Override
        public UUID id() { return id; } // GH-90000

        @Override
        public String tenantId() { return tenantId; } // GH-90000

        @Override
        public String collectionName() { return collectionName; } // GH-90000

        @Override
        public Map<String, Object> data() { return data; } // GH-90000

        @Override
        public RecordType recordType() { return recordType; } // GH-90000
    }
}
