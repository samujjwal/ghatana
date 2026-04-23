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
 * (no Redis required — safe for CI and local development). // GH-90000
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
    void setUp() { // GH-90000
        store = new EventCloudPatternStateStore<>( // GH-90000
            s -> s.getBytes(StandardCharsets.UTF_8), // GH-90000
            bytes -> new String(bytes, StandardCharsets.UTF_8) // GH-90000
        );
    }

    @Nested
    @DisplayName("save() and load()")
    class SaveAndLoad {

        @Test
        @DisplayName("saves state and loads it back correctly")
        void shouldSaveAndLoadState() { // GH-90000
            runPromise(() -> store.save("t1", "p1", "state-value")); // GH-90000
            Optional<String> result = runPromise(() -> store.load("t1", "p1")); // GH-90000
            assertThat(result).isPresent().hasValue("state-value");
        }

        @Test
        @DisplayName("load() returns empty when no state stored")
        void shouldReturnEmptyWhenMissing() { // GH-90000
            Optional<String> result = runPromise(() -> store.load("t1", "missing")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("second save() overwrites previous state")
        void shouldOverwriteOnSave() { // GH-90000
            runPromise(() -> store.save("t1", "p1", "v1")); // GH-90000
            runPromise(() -> store.save("t1", "p1", "v2")); // GH-90000
            Optional<String> result = runPromise(() -> store.load("t1", "p1")); // GH-90000
            assertThat(result).isPresent().hasValue("v2");
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("exists() returns true after save()")
        void shouldReturnTrueAfterSave() { // GH-90000
            runPromise(() -> store.save("t1", "p1", "some-state")); // GH-90000
            boolean exists = runPromise(() -> store.exists("t1", "p1")); // GH-90000
            assertThat(exists).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("exists() returns false when not saved")
        void shouldReturnFalseWhenNotSaved() { // GH-90000
            boolean exists = runPromise(() -> store.exists("t1", "ghost-pattern")); // GH-90000
            assertThat(exists).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("delete() removes state so load() returns empty")
        void shouldDeleteState() { // GH-90000
            runPromise(() -> store.save("t1", "p1", "state")); // GH-90000
            runPromise(() -> store.delete("t1", "p1")); // GH-90000
            Optional<String> result = runPromise(() -> store.load("t1", "p1")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("delete() on non-existent pattern completes without error")
        void shouldDeleteNonExistentSilently() { // GH-90000
            runPromise(() -> store.delete("t1", "never-existed")); // GH-90000
            // No exception == pass
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("same patternId for different tenants is stored independently")
        void shouldIsolateTenants() { // GH-90000
            runPromise(() -> store.save("tenant-a", "p1", "state-a")); // GH-90000
            runPromise(() -> store.save("tenant-b", "p1", "state-b")); // GH-90000

            Optional<String> resultA = runPromise(() -> store.load("tenant-a", "p1")); // GH-90000
            Optional<String> resultB = runPromise(() -> store.load("tenant-b", "p1")); // GH-90000

            assertThat(resultA).isPresent().hasValue("state-a");
            assertThat(resultB).isPresent().hasValue("state-b");
        }

        @Test
        @DisplayName("delete() for tenant-a does not affect tenant-b")
        void shouldNotDeleteAcrossTenants() { // GH-90000
            runPromise(() -> store.save("tenant-a", "p1", "state-a")); // GH-90000
            runPromise(() -> store.save("tenant-b", "p1", "state-b")); // GH-90000

            runPromise(() -> store.delete("tenant-a", "p1")); // GH-90000

            assertThat(runPromise(() -> store.load("tenant-a", "p1"))).isEmpty(); // GH-90000
            assertThat(runPromise(() -> store.load("tenant-b", "p1"))).isPresent().hasValue("state-b");
        }
    }
}
