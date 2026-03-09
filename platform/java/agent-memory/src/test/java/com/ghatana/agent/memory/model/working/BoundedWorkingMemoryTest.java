package com.ghatana.agent.memory.model.working;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BoundedWorkingMemory}.
 *
 * @doc.type class
 * @doc.purpose BoundedWorkingMemory LRU eviction and thread-safety tests
 * @doc.layer agent-memory
 * @doc.pattern Unit Test
 */
@DisplayName("BoundedWorkingMemory")
class BoundedWorkingMemoryTest {

    @Nested
    @DisplayName("basic operations")
    class BasicOps {

        private BoundedWorkingMemory memory;

        @BeforeEach
        void setUp() {
            memory = new BoundedWorkingMemory(
                    WorkingMemoryConfig.builder().maxEntries(100).build());
        }

        @Test
        @DisplayName("should store and retrieve a value")
        void shouldPutAndGet() {
            memory.put("key1", "value1");
            assertThat(memory.get("key1")).isEqualTo("value1");
        }

        @Test
        @DisplayName("should return null for missing key")
        void shouldReturnNullForMissing() {
            assertThat(memory.get("nonexistent")).isNull();
        }

        @Test
        @DisplayName("should return typed value when type matches")
        void shouldGetTyped() {
            memory.put("count", 42);
            assertThat(memory.get("count", Integer.class)).isEqualTo(42);
        }

        @Test
        @DisplayName("should return null when type does not match")
        void shouldReturnNullForTypeMismatch() {
            memory.put("count", 42);
            assertThat(memory.get("count", String.class)).isNull();
        }

        @Test
        @DisplayName("should remove and return value")
        void shouldRemove() {
            memory.put("key1", "value1");
            Object removed = memory.remove("key1");

            assertThat(removed).isEqualTo("value1");
            assertThat(memory.get("key1")).isNull();
        }

        @Test
        @DisplayName("should return null when removing missing key")
        void shouldReturnNullForMissingRemove() {
            assertThat(memory.remove("nonexistent")).isNull();
        }

        @Test
        @DisplayName("should report correct size")
        void shouldReportSize() {
            assertThat(memory.size()).isZero();
            memory.put("a", 1);
            memory.put("b", 2);
            assertThat(memory.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("should report configured capacity")
        void shouldReportCapacity() {
            assertThat(memory.capacity()).isEqualTo(100);
        }

        @Test
        @DisplayName("should clear all entries")
        void shouldClear() {
            memory.put("a", 1);
            memory.put("b", 2);
            memory.clear();

            assertThat(memory.size()).isZero();
            assertThat(memory.get("a")).isNull();
        }

        @Test
        @DisplayName("should return immutable snapshot")
        void shouldReturnSnapshot() {
            memory.put("x", "y");
            Map<String, Object> snap = memory.snapshot();

            assertThat(snap).containsEntry("x", "y");
            // Mutations to memory should not affect snapshot
            memory.put("z", "w");
            assertThat(snap).doesNotContainKey("z");
        }
    }

    @Nested
    @DisplayName("LRU eviction")
    class LruEviction {

        @Test
        @DisplayName("should evict oldest entry when capacity exceeded")
        void shouldEvictOldest() {
            BoundedWorkingMemory memory = new BoundedWorkingMemory(
                    WorkingMemoryConfig.builder().maxEntries(3).build());

            memory.put("a", 1);
            memory.put("b", 2);
            memory.put("c", 3);
            // At capacity — adding one more should evict "a"
            memory.put("d", 4);

            assertThat(memory.size()).isEqualTo(3);
            assertThat(memory.get("a")).isNull(); // evicted
            assertThat(memory.get("b")).isEqualTo(2);
            assertThat(memory.get("d")).isEqualTo(4);
        }

        @Test
        @DisplayName("should fire eviction callback on eviction")
        void shouldFireEvictionCallback() {
            BoundedWorkingMemory memory = new BoundedWorkingMemory(
                    WorkingMemoryConfig.builder().maxEntries(2).build());

            List<String> evictedKeys = new ArrayList<>();
            memory.onEviction((key, value) -> evictedKeys.add(key));

            memory.put("a", 1);
            memory.put("b", 2);
            memory.put("c", 3); // should evict "a"

            assertThat(evictedKeys).contains("a");
        }

        @Test
        @DisplayName("should refresh access order on get")
        void shouldRefreshAccessOrderOnGet() {
            BoundedWorkingMemory memory = new BoundedWorkingMemory(
                    WorkingMemoryConfig.builder().maxEntries(3).build());

            memory.put("a", 1);
            memory.put("b", 2);
            memory.put("c", 3);

            // Access "a" so it becomes most recently used
            memory.get("a");

            // Adding "d" should evict "b" (least recently used), not "a"
            memory.put("d", 4);

            assertThat(memory.get("a")).isEqualTo(1); // still present
            assertThat(memory.get("b")).isNull(); // evicted
        }
    }

    @Nested
    @DisplayName("default constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should use default config with 1000 entries")
        void shouldUseDefaultConfig() {
            BoundedWorkingMemory memory = new BoundedWorkingMemory();
            assertThat(memory.capacity()).isEqualTo(1000);
        }
    }
}
