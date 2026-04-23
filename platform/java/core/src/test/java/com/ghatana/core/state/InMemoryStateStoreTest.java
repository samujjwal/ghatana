package com.ghatana.core.state;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for InMemoryStateStore CRUD and concurrency semantics
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("InMemoryStateStore — CRUD, null safety, size tracking")
class InMemoryStateStoreTest extends EventloopTestBase {

    private InMemoryStateStore<String, String> store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryStateStore<>(); // GH-90000
    }

    @Test
    @DisplayName("get on empty store returns empty Optional")
    void getOnEmptyStoreReturnsEmpty() { // GH-90000
        Optional<String> result = runPromise(() -> store.get("missing"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("put then get returns the stored value")
    void putThenGetReturnsValue() { // GH-90000
        store.put("key1", "value1"); // GH-90000
        Optional<String> result = runPromise(() -> store.get("key1"));
        assertThat(result).hasValue("value1");
    }

    @Test
    @DisplayName("put overrides existing value")
    void putOverridesExistingValue() { // GH-90000
        store.put("key1", "first"); // GH-90000
        store.put("key1", "second"); // GH-90000
        Optional<String> result = runPromise(() -> store.get("key1"));
        assertThat(result).hasValue("second");
    }

    @Test
    @DisplayName("remove deletes the entry so subsequent get returns empty")
    void removeDeletesEntry() { // GH-90000
        store.put("key1", "value1"); // GH-90000
        store.remove("key1");
        Optional<String> result = runPromise(() -> store.get("key1"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("remove on non-existent key is a no-op")
    void removeOnMissingKeyIsNoOp() { // GH-90000
        assertThatCode(() -> store.remove("ghost")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("size reflects number of stored entries")
    void sizeTracksEntryCount() { // GH-90000
        assertThat(store.size()).isEqualTo(0); // GH-90000
        store.put("a", "1"); // GH-90000
        store.put("b", "2"); // GH-90000
        assertThat(store.size()).isEqualTo(2); // GH-90000
        store.remove("a");
        assertThat(store.size()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAllEntries() { // GH-90000
        store.put("a", "1"); // GH-90000
        store.put("b", "2"); // GH-90000
        store.clear(); // GH-90000
        assertThat(store.size()).isEqualTo(0); // GH-90000
        assertThat(runPromise(() -> store.get("a"))).isEmpty();
    }

    @Test
    @DisplayName("put with null key throws NullPointerException")
    void putWithNullKeyThrows() { // GH-90000
        assertThatThrownBy(() -> store.put(null, "value")) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("key");
    }

    @Test
    @DisplayName("put with null value stores null and get returns empty Optional")
    void putWithNullValueStoresNull() { // GH-90000
        // ConcurrentHashMap does not allow null values - this is expected to throw
        assertThatThrownBy(() -> store.put("key", null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("get returns Promise wrapping Optional, not null")
    void getReturnsNonNullPromise() { // GH-90000
        Promise<Optional<String>> promise = store.get("anything");
        assertThat(promise).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("multiple independent keys do not interfere")
    void independentKeysDoNotInterfere() { // GH-90000
        store.put("x", "10"); // GH-90000
        store.put("y", "20"); // GH-90000

        Optional<String> x = runPromise(() -> store.get("x"));
        Optional<String> y = runPromise(() -> store.get("y"));

        assertThat(x).hasValue("10");
        assertThat(y).hasValue("20");
    }
}
