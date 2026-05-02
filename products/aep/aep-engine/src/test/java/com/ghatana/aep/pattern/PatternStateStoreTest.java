/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("InMemoryPatternStateStore")
class PatternStateStoreTest extends EventloopTestBase {

    private InMemoryPatternStateStore<String> store;

    @BeforeEach
    void setUp() { 
        store = new InMemoryPatternStateStore<>(); 
    }

    @Nested
    @DisplayName("save() and load()")
    class SaveAndLoad {

        @Test
        @DisplayName("saves and loads state correctly")
        void shouldSaveAndLoad() { 
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-v1")); 

            Optional<String> loaded = runPromise(() -> store.load("tenant-1", "pattern-a")); 

            assertThat(loaded).isPresent().hasValue("state-v1");
        }

        @Test
        @DisplayName("returns empty Optional when no state exists")
        void shouldReturnEmptyWhenNotFound() { 
            Optional<String> loaded = runPromise(() -> store.load("tenant-1", "nonexistent")); 
            assertThat(loaded).isEmpty(); 
        }

        @Test
        @DisplayName("overwrites previous state on subsequent save")
        void shouldOverwriteOnSave() { 
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-v1")); 
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-v2")); 

            Optional<String> loaded = runPromise(() -> store.load("tenant-1", "pattern-a")); 
            assertThat(loaded).isPresent().hasValue("state-v2");
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("different tenants with same patternId have separate state")
        void shouldIsolateTenants() { 
            runPromise(() -> store.save("tenant-1", "pattern-a", "state-t1")); 
            runPromise(() -> store.save("tenant-2", "pattern-a", "state-t2")); 

            Optional<String> t1 = runPromise(() -> store.load("tenant-1", "pattern-a")); 
            Optional<String> t2 = runPromise(() -> store.load("tenant-2", "pattern-a")); 

            assertThat(t1).hasValue("state-t1");
            assertThat(t2).hasValue("state-t2");
        }

        @Test
        @DisplayName("deleting one tenant's state does not affect another")
        void shouldNotCrossDeleteTenants() { 
            runPromise(() -> store.save("tenant-1", "p", "s1")); 
            runPromise(() -> store.save("tenant-2", "p", "s2")); 

            runPromise(() -> store.delete("tenant-1", "p")); 

            assertThat(runPromise(() -> store.load("tenant-1", "p"))).isEmpty(); 
            assertThat(runPromise(() -> store.load("tenant-2", "p"))).hasValue("s2");
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("delete removes existing state")
        void shouldDeleteExistingState() { 
            runPromise(() -> store.save("t", "p", "v")); 
            runPromise(() -> store.delete("t", "p")); 

            assertThat(runPromise(() -> store.exists("t", "p"))).isFalse(); 
        }

        @Test
        @DisplayName("delete on non-existent key is a no-op")
        void shouldAllowDeleteOfMissingKey() { 
            runPromise(() -> store.delete("t", "missing")); // must not throw 
            assertThat(store.size()).isZero(); 
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("returns true after save")
        void shouldExistAfterSave() { 
            runPromise(() -> store.save("t", "p", "v")); 
            assertThat(runPromise(() -> store.exists("t", "p"))).isTrue(); 
        }

        @Test
        @DisplayName("returns false when not saved")
        void shouldNotExistBeforeSave() { 
            assertThat(runPromise(() -> store.exists("t", "unsaved"))).isFalse(); 
        }
    }

    @Nested
    @DisplayName("null argument guards")
    class NullArgGuards {

        @Test
        @DisplayName("save() rejects null tenantId")
        void saveRejectsNullTenant() { 
            assertThatThrownBy(() -> runPromise(() -> store.save(null, "p", "v"))) 
                .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("save() rejects null state")
        void saveRejectsNullState() { 
            assertThatThrownBy(() -> runPromise(() -> store.save("t", "p", null))) 
                .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("load() rejects null patternId")
        void loadRejectsNullPattern() { 
            assertThatThrownBy(() -> runPromise(() -> store.load("t", null))) 
                .isInstanceOf(NullPointerException.class); 
        }
    }
}
