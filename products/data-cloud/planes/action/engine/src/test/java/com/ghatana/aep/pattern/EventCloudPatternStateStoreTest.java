package com.ghatana.aep.pattern;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EventCloudPatternStateStore} in in-memory fallback mode
 * (no Redis required — safe for CI and local development). 
 *
 * @doc.type class
 * @doc.purpose Verify EventCloudPatternStateStore CRUD and tenant isolation without Redis
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventCloudPatternStateStore (in-memory mode)")
class EventCloudPatternStateStoreTest extends EventloopTestBase {

    private EventCloudPatternStateStore<String> store;

    @BeforeEach
    void setUp() { 
        store = new EventCloudPatternStateStore<>( 
            s -> s.getBytes(StandardCharsets.UTF_8), 
            bytes -> new String(bytes, StandardCharsets.UTF_8) 
        );
    }

    @Nested
    @DisplayName("save() and load()")
    class SaveAndLoad {

        @Test
        @DisplayName("saves state and loads it back correctly")
        void shouldSaveAndLoadState() { 
            runPromise(() -> store.save("t1", "p1", "state-value")); 
            Optional<String> result = runPromise(() -> store.load("t1", "p1")); 
            assertThat(result).isPresent().hasValue("state-value");
        }

        @Test
        @DisplayName("load() returns empty when no state stored")
        void shouldReturnEmptyWhenMissing() { 
            Optional<String> result = runPromise(() -> store.load("t1", "missing")); 
            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("second save() overwrites previous state")
        void shouldOverwriteOnSave() { 
            runPromise(() -> store.save("t1", "p1", "v1")); 
            runPromise(() -> store.save("t1", "p1", "v2")); 
            Optional<String> result = runPromise(() -> store.load("t1", "p1")); 
            assertThat(result).isPresent().hasValue("v2");
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("exists() returns true after save()")
        void shouldReturnTrueAfterSave() { 
            runPromise(() -> store.save("t1", "p1", "some-state")); 
            boolean exists = runPromise(() -> store.exists("t1", "p1")); 
            assertThat(exists).isTrue(); 
        }

        @Test
        @DisplayName("exists() returns false when not saved")
        void shouldReturnFalseWhenNotSaved() { 
            boolean exists = runPromise(() -> store.exists("t1", "ghost-pattern")); 
            assertThat(exists).isFalse(); 
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("delete() removes state so load() returns empty")
        void shouldDeleteState() { 
            runPromise(() -> store.save("t1", "p1", "state")); 
            runPromise(() -> store.delete("t1", "p1")); 
            Optional<String> result = runPromise(() -> store.load("t1", "p1")); 
            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("delete() on non-existent pattern completes without error")
        void shouldDeleteNonExistentSilently() { 
            runPromise(() -> store.delete("t1", "never-existed")); 
            // No exception == pass
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("same patternId for different tenants is stored independently")
        void shouldIsolateTenants() { 
            runPromise(() -> store.save("tenant-a", "p1", "state-a")); 
            runPromise(() -> store.save("tenant-b", "p1", "state-b")); 

            Optional<String> resultA = runPromise(() -> store.load("tenant-a", "p1")); 
            Optional<String> resultB = runPromise(() -> store.load("tenant-b", "p1")); 

            assertThat(resultA).isPresent().hasValue("state-a");
            assertThat(resultB).isPresent().hasValue("state-b");
        }

        @Test
        @DisplayName("delete() for tenant-a does not affect tenant-b")
        void shouldNotDeleteAcrossTenants() { 
            runPromise(() -> store.save("tenant-a", "p1", "state-a")); 
            runPromise(() -> store.save("tenant-b", "p1", "state-b")); 

            runPromise(() -> store.delete("tenant-a", "p1")); 

            assertThat(runPromise(() -> store.load("tenant-a", "p1"))).isEmpty(); 
            assertThat(runPromise(() -> store.load("tenant-b", "p1"))).isPresent().hasValue("state-b");
        }
    }
}
