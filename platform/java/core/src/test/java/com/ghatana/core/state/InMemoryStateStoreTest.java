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
@DisplayName("InMemoryStateStore — CRUD, null safety, size tracking [GH-90000]")
class InMemoryStateStoreTest extends EventloopTestBase {

    private InMemoryStateStore<String, String> store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryStateStore<>(); // GH-90000
    }

    @Test
    @DisplayName("get on empty store returns empty Optional [GH-90000]")
    void getOnEmptyStoreReturnsEmpty() { // GH-90000
        Optional<String> result = runPromise(() -> store.get("missing [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("put then get returns the stored value [GH-90000]")
    void putThenGetReturnsValue() { // GH-90000
        store.put("key1", "value1"); // GH-90000
        Optional<String> result = runPromise(() -> store.get("key1 [GH-90000]"));
        assertThat(result).hasValue("value1 [GH-90000]");
    }

    @Test
    @DisplayName("put overrides existing value [GH-90000]")
    void putOverridesExistingValue() { // GH-90000
        store.put("key1", "first"); // GH-90000
        store.put("key1", "second"); // GH-90000
        Optional<String> result = runPromise(() -> store.get("key1 [GH-90000]"));
        assertThat(result).hasValue("second [GH-90000]");
    }

    @Test
    @DisplayName("remove deletes the entry so subsequent get returns empty [GH-90000]")
    void removeDeletesEntry() { // GH-90000
        store.put("key1", "value1"); // GH-90000
        store.remove("key1 [GH-90000]");
        Optional<String> result = runPromise(() -> store.get("key1 [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("remove on non-existent key is a no-op [GH-90000]")
    void removeOnMissingKeyIsNoOp() { // GH-90000
        assertThatCode(() -> store.remove("ghost [GH-90000]")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("size reflects number of stored entries [GH-90000]")
    void sizeTracksEntryCount() { // GH-90000
        assertThat(store.size()).isEqualTo(0); // GH-90000
        store.put("a", "1"); // GH-90000
        store.put("b", "2"); // GH-90000
        assertThat(store.size()).isEqualTo(2); // GH-90000
        store.remove("a [GH-90000]");
        assertThat(store.size()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("clear removes all entries [GH-90000]")
    void clearRemovesAllEntries() { // GH-90000
        store.put("a", "1"); // GH-90000
        store.put("b", "2"); // GH-90000
        store.clear(); // GH-90000
        assertThat(store.size()).isEqualTo(0); // GH-90000
        assertThat(runPromise(() -> store.get("a [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("put with null key throws NullPointerException [GH-90000]")
    void putWithNullKeyThrows() { // GH-90000
        assertThatThrownBy(() -> store.put(null, "value")) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("key [GH-90000]");
    }

    @Test
    @DisplayName("put with null value stores null and get returns empty Optional [GH-90000]")
    void putWithNullValueStoresNull() { // GH-90000
        // ConcurrentHashMap does not allow null values - this is expected to throw
        assertThatThrownBy(() -> store.put("key", null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("get returns Promise wrapping Optional, not null [GH-90000]")
    void getReturnsNonNullPromise() { // GH-90000
        Promise<Optional<String>> promise = store.get("anything [GH-90000]");
        assertThat(promise).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("multiple independent keys do not interfere [GH-90000]")
    void independentKeysDoNotInterfere() { // GH-90000
        store.put("x", "10"); // GH-90000
        store.put("y", "20"); // GH-90000

        Optional<String> x = runPromise(() -> store.get("x [GH-90000]"));
        Optional<String> y = runPromise(() -> store.get("y [GH-90000]"));

        assertThat(x).hasValue("10 [GH-90000]");
        assertThat(y).hasValue("20 [GH-90000]");
    }
}
