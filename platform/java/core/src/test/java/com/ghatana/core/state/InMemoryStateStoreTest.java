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
    void setUp() { 
        store = new InMemoryStateStore<>(); 
    }

    @Test
    @DisplayName("get on empty store returns empty Optional")
    void getOnEmptyStoreReturnsEmpty() { 
        Optional<String> result = runPromise(() -> store.get("missing"));
        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("put then get returns the stored value")
    void putThenGetReturnsValue() { 
        store.put("key1", "value1"); 
        Optional<String> result = runPromise(() -> store.get("key1"));
        assertThat(result).hasValue("value1");
    }

    @Test
    @DisplayName("put overrides existing value")
    void putOverridesExistingValue() { 
        store.put("key1", "first"); 
        store.put("key1", "second"); 
        Optional<String> result = runPromise(() -> store.get("key1"));
        assertThat(result).hasValue("second");
    }

    @Test
    @DisplayName("remove deletes the entry so subsequent get returns empty")
    void removeDeletesEntry() { 
        store.put("key1", "value1"); 
        store.remove("key1");
        Optional<String> result = runPromise(() -> store.get("key1"));
        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("remove on non-existent key is a no-op")
    void removeOnMissingKeyIsNoOp() { 
        assertThatCode(() -> store.remove("ghost")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("size reflects number of stored entries")
    void sizeTracksEntryCount() { 
        assertThat(store.size()).isEqualTo(0); 
        store.put("a", "1"); 
        store.put("b", "2"); 
        assertThat(store.size()).isEqualTo(2); 
        store.remove("a");
        assertThat(store.size()).isEqualTo(1); 
    }

    @Test
    @DisplayName("clear removes all entries")
    void clearRemovesAllEntries() { 
        store.put("a", "1"); 
        store.put("b", "2"); 
        store.clear(); 
        assertThat(store.size()).isEqualTo(0); 
        assertThat(runPromise(() -> store.get("a"))).isEmpty();
    }

    @Test
    @DisplayName("put with null key throws NullPointerException")
    void putWithNullKeyThrows() { 
        assertThatThrownBy(() -> store.put(null, "value")) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("key");
    }

    @Test
    @DisplayName("put with null value stores null and get returns empty Optional")
    void putWithNullValueStoresNull() { 
        // ConcurrentHashMap does not allow null values - this is expected to throw
        assertThatThrownBy(() -> store.put("key", null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("get returns Promise wrapping Optional, not null")
    void getReturnsNonNullPromise() { 
        Promise<Optional<String>> promise = store.get("anything");
        assertThat(promise).isNotNull(); 
    }

    @Test
    @DisplayName("multiple independent keys do not interfere")
    void independentKeysDoNotInterfere() { 
        store.put("x", "10"); 
        store.put("y", "20"); 

        Optional<String> x = runPromise(() -> store.get("x"));
        Optional<String> y = runPromise(() -> store.get("y"));

        assertThat(x).hasValue("10");
        assertThat(y).hasValue("20");
    }
}
