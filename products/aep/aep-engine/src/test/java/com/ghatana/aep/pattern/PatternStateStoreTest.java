/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.pattern;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InMemoryPatternStateStore}.
 *
 * @doc.type class
 * @doc.purpose Verify in-memory pattern state CRUD operations and tenant isolation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryPatternStateStore [GH-90000]")
class PatternStateStoreTest extends EventloopTestBase {

    private InMemoryPatternStateStore<String> store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new InMemoryPatternStateStore<>(); // GH-90000
    }

    @Nested
    @DisplayName("save() and load() [GH-90000]")
    class SaveAndLoad {

        @Test
        @DisplayName("saves and loads state correctly [GH-90000]")
        void shouldSaveAndLoad() { // GH-90000
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-v1")); // GH-90000

            Optional<String> loaded = runPromise(() -> store.load("tenant-1", "pattern-a")); // GH-90000

            assertThat(loaded).isPresent().hasValue("state-v1 [GH-90000]");
        }

        @Test
        @DisplayName("returns empty Optional when no state exists [GH-90000]")
        void shouldReturnEmptyWhenNotFound() { // GH-90000
            Optional<String> loaded = runPromise(() -> store.load("tenant-1", "nonexistent")); // GH-90000
            assertThat(loaded).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("overwrites previous state on subsequent save [GH-90000]")
        void shouldOverwriteOnSave() { // GH-90000
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-v1")); // GH-90000
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-v2")); // GH-90000

            Optional<String> loaded = runPromise(() -> store.load("tenant-1", "pattern-a")); // GH-90000
            assertThat(loaded).isPresent().hasValue("state-v2 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Tenant isolation [GH-90000]")
    class TenantIsolation {

        @Test
        @DisplayName("different tenants with same patternId have separate state [GH-90000]")
        void shouldIsolateTenants() { // GH-90000
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-t1")); // GH-90000
            runPromise(() -> store.save("tenant-2", "pattern-a", "state-t2")); // GH-90000

            Optional<String> t1 = runPromise(() -> store.load("tenant-1", "pattern-a")); // GH-90000
            Optional<String> t2 = runPromise(() -> store.load("tenant-2", "pattern-a")); // GH-90000

            assertThat(t1).hasValue("state-t1 [GH-90000]");
            assertThat(t2).hasValue("state-t2 [GH-90000]");
        }

        @Test
        @DisplayName("deleting one tenant's state does not affect another [GH-90000]")
        void shouldNotCrossDeleteTenants() { // GH-90000
            runPromise(() -> store.save("tenant-1", "p", "s1")); // GH-90000
            runPromise(() -> store.save("tenant-2", "p", "s2")); // GH-90000

            runPromise(() -> store.delete("tenant-1", "p")); // GH-90000

            assertThat(runPromise(() -> store.load("tenant-1", "p"))).isEmpty(); // GH-90000
            assertThat(runPromise(() -> store.load("tenant-2", "p"))).hasValue("s2 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("delete() [GH-90000]")
    class DeleteTests {

        @Test
        @DisplayName("delete removes existing state [GH-90000]")
        void shouldDeleteExistingState() { // GH-90000
            runPromise(() -> store.save("t", "p", "v")); // GH-90000
            runPromise(() -> store.delete("t", "p")); // GH-90000

            assertThat(runPromise(() -> store.exists("t", "p"))).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("delete on non-existent key is a no-op [GH-90000]")
        void shouldAllowDeleteOfMissingKey() { // GH-90000
            runPromise(() -> store.delete("t", "missing")); // must not throw // GH-90000
            assertThat(store.size()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("exists() [GH-90000]")
    class ExistsTests {

        @Test
        @DisplayName("returns true after save [GH-90000]")
        void shouldExistAfterSave() { // GH-90000
            runPromise(() -> store.save("t", "p", "v")); // GH-90000
            assertThat(runPromise(() -> store.exists("t", "p"))).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns false when not saved [GH-90000]")
        void shouldNotExistBeforeSave() { // GH-90000
            assertThat(runPromise(() -> store.exists("t", "unsaved"))).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("null argument guards [GH-90000]")
    class NullArgGuards {

        @Test
        @DisplayName("save() rejects null tenantId [GH-90000]")
        void saveRejectsNullTenant() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> store.save(null, "p", "v"))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("save() rejects null state [GH-90000]")
        void saveRejectsNullState() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> store.save("t", "p", null))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("load() rejects null patternId [GH-90000]")
        void loadRejectsNullPattern() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> store.load("t", null))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
