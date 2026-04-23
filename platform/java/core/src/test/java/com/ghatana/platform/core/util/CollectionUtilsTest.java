package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for CollectionUtils null-safe collection helpers
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("CollectionUtils — null-safe collection helpers")
class CollectionUtilsTest {

    // ── isEmpty / isNotEmpty ─────────────────────────────────────────────────

    @Test
    @DisplayName("isEmpty returns true for null collection")
    void isEmptyNullReturnsTrue() { // GH-90000
        assertThat(CollectionUtils.isEmpty(null)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isEmpty returns true for empty collection")
    void isEmptyEmptyReturnsTrue() { // GH-90000
        assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isEmpty returns false for non-empty collection")
    void isEmptyNonEmptyReturnsFalse() { // GH-90000
        assertThat(CollectionUtils.isEmpty(List.of("a"))).isFalse();
    }

    @Test
    @DisplayName("isNotEmpty returns false for null collection")
    void isNotEmptyNullReturnsFalse() { // GH-90000
        assertThat(CollectionUtils.isNotEmpty(null)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("isNotEmpty returns true for non-empty collection")
    void isNotEmptyNonEmptyReturnsTrue() { // GH-90000
        assertThat(CollectionUtils.isNotEmpty(List.of("x"))).isTrue();
    }

    // ── emptyIfNull ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("emptyIfNull returns empty list for null input")
    void emptyIfNullReturnsEmptyForNull() { // GH-90000
        assertThat(CollectionUtils.emptyIfNull(null)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("emptyIfNull returns original list for non-null input")
    void emptyIfNullReturnsOriginalForNonNull() { // GH-90000
        List<String> input = List.of("a", "b"); // GH-90000
        assertThat(CollectionUtils.emptyIfNull(input)).containsExactly("a", "b"); // GH-90000
    }

    // ── toNonNullList ────────────────────────────────────────────────────────

    @Test
    @DisplayName("toNonNullList filters out null elements")
    void toNonNullListFiltersNulls() { // GH-90000
        List<String> result = CollectionUtils.toNonNullList(Arrays.asList("a", null, "b", null, "c")); // GH-90000
        assertThat(result).containsExactly("a", "b", "c"); // GH-90000
    }

    @Test
    @DisplayName("toNonNullList returns empty list for null input")
    void toNonNullListReturnsEmptyForNull() { // GH-90000
        assertThat(CollectionUtils.toNonNullList(null)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("toNonNullList returns unmodifiable list")
    void toNonNullListIsUnmodifiable() { // GH-90000
        List<String> result = CollectionUtils.toNonNullList(List.of("a", "b")); // GH-90000
        assertThatThrownBy(() -> result.add("c"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    // ── mapList ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("mapList transforms each element")
    void mapListTransformsElements() { // GH-90000
        List<Integer> result = CollectionUtils.mapList(List.of("a", "bb", "ccc"), String::length); // GH-90000
        assertThat(result).containsExactly(1, 2, 3); // GH-90000
    }

    @Test
    @DisplayName("mapList returns empty list for null input")
    void mapListReturnsEmptyForNull() { // GH-90000
        List<Integer> result = CollectionUtils.mapList(null, String::length); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("mapList returns empty list for empty input")
    void mapListReturnsEmptyForEmpty() { // GH-90000
        List<Integer> result = CollectionUtils.mapList(Collections.emptyList(), String::length); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    // ── toMap ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toMap creates map keyed by extractor result")
    void toMapCreatesKeyedMap() { // GH-90000
        List<String> items = List.of("apple", "banana", "cherry"); // GH-90000
        Map<Integer, String> result = CollectionUtils.toMap(items, String::length); // GH-90000
        assertThat(result).containsEntry(5, "apple").containsEntry(6, "cherry"); // GH-90000
    }

    @Test
    @DisplayName("toMap returns empty map for null input")
    void toMapReturnsEmptyForNull() { // GH-90000
        assertThat(CollectionUtils.toMap(null, String::length)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("toMap last value wins on duplicate keys")
    void toMapLastValueWinsOnDuplicateKeys() { // GH-90000
        List<String> items = List.of("ab", "cd"); // both have length 2 // GH-90000
        Map<Integer, String> result = CollectionUtils.toMap(items, String::length); // GH-90000
        assertThat(result.get(2)).isEqualTo("cd");
    }

    // ── partition ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("partition splits list into fixed-size batches")
    void partitionSplitsIntoBatches() { // GH-90000
        List<Integer> items = List.of(1, 2, 3, 4, 5); // GH-90000
        List<List<Integer>> batches = CollectionUtils.partition(items, 2); // GH-90000
        assertThat(batches).hasSize(3); // GH-90000
        assertThat(batches.get(0)).containsExactly(1, 2); // GH-90000
        assertThat(batches.get(1)).containsExactly(3, 4); // GH-90000
        assertThat(batches.get(2)).containsExactly(5); // GH-90000
    }

    @Test
    @DisplayName("partition handles batch size equal to list size")
    void partitionEqualBatchSize() { // GH-90000
        List<Integer> items = List.of(1, 2, 3); // GH-90000
        List<List<Integer>> batches = CollectionUtils.partition(items, 3); // GH-90000
        assertThat(batches).hasSize(1); // GH-90000
        assertThat(batches.get(0)).containsExactly(1, 2, 3); // GH-90000
    }

    @Test
    @DisplayName("partition returns empty list for empty input")
    void partitionReturnsEmptyForEmpty() { // GH-90000
        assertThat(CollectionUtils.partition(Collections.emptyList(), 5)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("partition throws for non-positive batch size")
    void partitionThrowsForNonPositiveBatchSize() { // GH-90000
        assertThatThrownBy(() -> CollectionUtils.partition(List.of(1, 2), 0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("batchSize");
    }

    // ── intersection ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("intersection returns common elements")
    void intersectionReturnsCommonElements() { // GH-90000
        List<String> a = List.of("apple", "banana", "cherry"); // GH-90000
        List<String> b = List.of("banana", "cherry", "date"); // GH-90000
        List<String> result = CollectionUtils.intersection(a, b); // GH-90000
        assertThat(result).containsExactlyInAnyOrder("banana", "cherry"); // GH-90000
    }

    @Test
    @DisplayName("intersection returns empty list when no common elements")
    void intersectionReturnsEmptyWhenDisjoint() { // GH-90000
        assertThat(CollectionUtils.intersection(List.of("a"), List.of("b"))).isEmpty();
    }

    @Test
    @DisplayName("intersection returns empty list for null first argument")
    void intersectionReturnsEmptyForNullFirst() { // GH-90000
        assertThat(CollectionUtils.intersection(null, List.of("a"))).isEmpty();
    }

    @Test
    @DisplayName("intersection returns empty list for null second argument")
    void intersectionReturnsEmptyForNullSecond() { // GH-90000
        assertThat(CollectionUtils.intersection(List.of("a"), null)).isEmpty();
    }
}
