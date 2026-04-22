package com.ghatana.core.state;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for HybridStateStore — write-through and local cache behaviour
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HybridStateStore — local-first reads, sync strategies [GH-90000]")
class HybridStateStoreTest extends EventloopTestBase {

    private HybridStateStore<String, String> buildStore(SyncStrategy strategy) { // GH-90000
        return HybridStateStore.<String, String>builder() // GH-90000
                .localStore(new InMemoryStateStore<>()) // GH-90000
                .centralStore(new InMemoryStateStore<>()) // GH-90000
                .syncStrategy(strategy) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("get on empty store returns empty Optional [GH-90000]")
    void getOnEmptyStoreReturnsEmpty() { // GH-90000
        HybridStateStore<String, String> store = buildStore(SyncStrategy.IMMEDIATE); // GH-90000
        Optional<String> result = runPromise(() -> store.get("missing [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("IMMEDIATE strategy writes to both local and central [GH-90000]")
    void immediateStrategyWritesToBothStores() { // GH-90000
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); // GH-90000
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); // GH-90000
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(local) // GH-90000
                .centralStore(central) // GH-90000
                .syncStrategy(SyncStrategy.IMMEDIATE) // GH-90000
                .build(); // GH-90000

        store.put("key", "val"); // GH-90000

        assertThat(runPromise(() -> local.get("key [GH-90000]"))).hasValue("val [GH-90000]");
        assertThat(runPromise(() -> central.get("key [GH-90000]"))).hasValue("val [GH-90000]");
    }

    @Test
    @DisplayName("BATCHED strategy writes only to local store [GH-90000]")
    void batchedStrategyWritesOnlyToLocal() { // GH-90000
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); // GH-90000
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); // GH-90000
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(local) // GH-90000
                .centralStore(central) // GH-90000
                .syncStrategy(SyncStrategy.BATCHED) // GH-90000
                .build(); // GH-90000

        store.put("key", "val"); // GH-90000

        assertThat(runPromise(() -> local.get("key [GH-90000]"))).hasValue("val [GH-90000]");
        assertThat(runPromise(() -> central.get("key [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("NONE strategy writes only to local store [GH-90000]")
    void noneStrategyWritesOnlyToLocal() { // GH-90000
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); // GH-90000
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); // GH-90000
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(local) // GH-90000
                .centralStore(central) // GH-90000
                .syncStrategy(SyncStrategy.NONE) // GH-90000
                .build(); // GH-90000

        store.put("key", "val"); // GH-90000

        assertThat(runPromise(() -> local.get("key [GH-90000]"))).hasValue("val [GH-90000]");
        assertThat(runPromise(() -> central.get("key [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("get falls back to central store when local cache miss [GH-90000]")
    void getFallsBackToCentralOnLocalMiss() { // GH-90000
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); // GH-90000
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); // GH-90000
        central.put("remote-key", "from-central"); // GH-90000

        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(local) // GH-90000
                .centralStore(central) // GH-90000
                .syncStrategy(SyncStrategy.BATCHED) // GH-90000
                .build(); // GH-90000

        Optional<String> result = runPromise(() -> store.get("remote-key [GH-90000]"));
        assertThat(result).hasValue("from-central [GH-90000]");
    }

    @Test
    @DisplayName("get populates local cache from central on first access [GH-90000]")
    void getPopulatesLocalCacheFromCentral() { // GH-90000
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); // GH-90000
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); // GH-90000
        central.put("k", "v"); // GH-90000

        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(local) // GH-90000
                .centralStore(central) // GH-90000
                .syncStrategy(SyncStrategy.BATCHED) // GH-90000
                .build(); // GH-90000

        runPromise(() -> store.get("k [GH-90000]")); // populates local cache

        // After population, local should have it
        assertThat(runPromise(() -> local.get("k [GH-90000]"))).hasValue("v [GH-90000]");
    }

    @Test
    @DisplayName("local value takes priority over central when both present [GH-90000]")
    void localTakesPriorityOverCentral() { // GH-90000
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); // GH-90000
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); // GH-90000
        local.put("key", "local-value"); // GH-90000
        central.put("key", "central-value"); // GH-90000

        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(local) // GH-90000
                .centralStore(central) // GH-90000
                .syncStrategy(SyncStrategy.BATCHED) // GH-90000
                .build(); // GH-90000

        Optional<String> result = runPromise(() -> store.get("key [GH-90000]"));
        assertThat(result).hasValue("local-value [GH-90000]");
    }

    @Test
    @DisplayName("getSyncStrategy returns configured strategy [GH-90000]")
    void getSyncStrategyReturnsConfiguredStrategy() { // GH-90000
        HybridStateStore<String, String> store = buildStore(SyncStrategy.IMMEDIATE); // GH-90000
        assertThat(store.getSyncStrategy()).isEqualTo(SyncStrategy.IMMEDIATE); // GH-90000
    }

    @Test
    @DisplayName("builder requires localStore [GH-90000]")
    void builderRequiresLocalStore() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                HybridStateStore.<String, String>builder() // GH-90000
                        .centralStore(new InMemoryStateStore<>()) // GH-90000
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("localStore [GH-90000]");
    }

    @Test
    @DisplayName("builder requires centralStore [GH-90000]")
    void builderRequiresCentralStore() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                HybridStateStore.<String, String>builder() // GH-90000
                        .localStore(new InMemoryStateStore<>()) // GH-90000
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("centralStore [GH-90000]");
    }

    @Test
    @DisplayName("default sync strategy is BATCHED when not set [GH-90000]")
    void defaultSyncStrategyIsBatched() { // GH-90000
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() // GH-90000
                .localStore(new InMemoryStateStore<>()) // GH-90000
                .centralStore(new InMemoryStateStore<>()) // GH-90000
                .build(); // GH-90000
        assertThat(store.getSyncStrategy()).isEqualTo(SyncStrategy.BATCHED); // GH-90000
    }
}
