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
@DisplayName("HybridStateStore — local-first reads, sync strategies")
class HybridStateStoreTest extends EventloopTestBase {

    private HybridStateStore<String, String> buildStore(SyncStrategy strategy) { 
        return HybridStateStore.<String, String>builder() 
                .localStore(new InMemoryStateStore<>()) 
                .centralStore(new InMemoryStateStore<>()) 
                .syncStrategy(strategy) 
                .build(); 
    }

    @Test
    @DisplayName("get on empty store returns empty Optional")
    void getOnEmptyStoreReturnsEmpty() { 
        HybridStateStore<String, String> store = buildStore(SyncStrategy.IMMEDIATE); 
        Optional<String> result = runPromise(() -> store.get("missing"));
        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("IMMEDIATE strategy writes to both local and central")
    void immediateStrategyWritesToBothStores() { 
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); 
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); 
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(local) 
                .centralStore(central) 
                .syncStrategy(SyncStrategy.IMMEDIATE) 
                .build(); 

        store.put("key", "val"); 

        assertThat(runPromise(() -> local.get("key"))).hasValue("val");
        assertThat(runPromise(() -> central.get("key"))).hasValue("val");
    }

    @Test
    @DisplayName("BATCHED strategy writes only to local store")
    void batchedStrategyWritesOnlyToLocal() { 
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); 
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); 
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(local) 
                .centralStore(central) 
                .syncStrategy(SyncStrategy.BATCHED) 
                .build(); 

        store.put("key", "val"); 

        assertThat(runPromise(() -> local.get("key"))).hasValue("val");
        assertThat(runPromise(() -> central.get("key"))).isEmpty();
    }

    @Test
    @DisplayName("NONE strategy writes only to local store")
    void noneStrategyWritesOnlyToLocal() { 
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); 
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); 
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(local) 
                .centralStore(central) 
                .syncStrategy(SyncStrategy.NONE) 
                .build(); 

        store.put("key", "val"); 

        assertThat(runPromise(() -> local.get("key"))).hasValue("val");
        assertThat(runPromise(() -> central.get("key"))).isEmpty();
    }

    @Test
    @DisplayName("get falls back to central store when local cache miss")
    void getFallsBackToCentralOnLocalMiss() { 
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); 
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); 
        central.put("remote-key", "from-central"); 

        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(local) 
                .centralStore(central) 
                .syncStrategy(SyncStrategy.BATCHED) 
                .build(); 

        Optional<String> result = runPromise(() -> store.get("remote-key"));
        assertThat(result).hasValue("from-central");
    }

    @Test
    @DisplayName("get populates local cache from central on first access")
    void getPopulatesLocalCacheFromCentral() { 
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); 
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); 
        central.put("k", "v"); 

        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(local) 
                .centralStore(central) 
                .syncStrategy(SyncStrategy.BATCHED) 
                .build(); 

        runPromise(() -> store.get("k")); // populates local cache

        // After population, local should have it
        assertThat(runPromise(() -> local.get("k"))).hasValue("v");
    }

    @Test
    @DisplayName("local value takes priority over central when both present")
    void localTakesPriorityOverCentral() { 
        InMemoryStateStore<String, String> local = new InMemoryStateStore<>(); 
        InMemoryStateStore<String, String> central = new InMemoryStateStore<>(); 
        local.put("key", "local-value"); 
        central.put("key", "central-value"); 

        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(local) 
                .centralStore(central) 
                .syncStrategy(SyncStrategy.BATCHED) 
                .build(); 

        Optional<String> result = runPromise(() -> store.get("key"));
        assertThat(result).hasValue("local-value");
    }

    @Test
    @DisplayName("getSyncStrategy returns configured strategy")
    void getSyncStrategyReturnsConfiguredStrategy() { 
        HybridStateStore<String, String> store = buildStore(SyncStrategy.IMMEDIATE); 
        assertThat(store.getSyncStrategy()).isEqualTo(SyncStrategy.IMMEDIATE); 
    }

    @Test
    @DisplayName("builder requires localStore")
    void builderRequiresLocalStore() { 
        assertThatThrownBy(() -> 
                HybridStateStore.<String, String>builder() 
                        .centralStore(new InMemoryStateStore<>()) 
                        .build()) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("localStore");
    }

    @Test
    @DisplayName("builder requires centralStore")
    void builderRequiresCentralStore() { 
        assertThatThrownBy(() -> 
                HybridStateStore.<String, String>builder() 
                        .localStore(new InMemoryStateStore<>()) 
                        .build()) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("centralStore");
    }

    @Test
    @DisplayName("default sync strategy is BATCHED when not set")
    void defaultSyncStrategyIsBatched() { 
        HybridStateStore<String, String> store = HybridStateStore.<String, String>builder() 
                .localStore(new InMemoryStateStore<>()) 
                .centralStore(new InMemoryStateStore<>()) 
                .build(); 
        assertThat(store.getSyncStrategy()).isEqualTo(SyncStrategy.BATCHED); 
    }
}
