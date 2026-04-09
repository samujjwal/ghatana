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
    void isEmptyNullReturnsTrue() {
        assertThat(CollectionUtils.isEmpty(null)).isTrue();
    }

    @Test
    @DisplayName("isEmpty returns true for empty collection")
    void isEmptyEmptyReturnsTrue() {
        assertThat(CollectionUtils.isEmpty(Collections.emptyList())).isTrue();
    }

    @Test
    @DisplayName("isEmpty returns false for non-empty collection")
    void isEmptyNonEmptyReturnsFalse() {
        assertThat(CollectionUtils.isEmpty(List.of("a"))).isFalse();
    }

    @Test
    @DisplayName("isNotEmpty returns false for null collection")
    void isNotEmptyNullReturnsFalse() {
        assertThat(CollectionUtils.isNotEmpty(null)).isFalse();
    }

    @Test
    @DisplayName("isNotEmpty returns true for non-empty collection")
    void isNotEmptyNonEmptyReturnsTrue() {
        assertThat(CollectionUtils.isNotEmpty(List.of("x"))).isTrue();
    }

    // ── emptyIfNull ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("emptyIfNull returns empty list for null input")
    void emptyIfNullReturnsEmptyForNull() {
        assertThat(CollectionUtils.emptyIfNull(null)).isEmpty();
    }

    @Test
    @DisplayName("emptyIfNull returns original list for non-null input")
    void emptyIfNullReturnsOriginalForNonNull() {
        List<String> input = List.of("a", "b");
        assertThat(CollectionUtils.emptyIfNull(input)).containsExactly("a", "b");
    }

    // ── toNonNullList ────────────────────────────────────────────────────────

    @Test
    @DisplayName("toNonNullList filters out null elements")
    void toNonNullListFiltersNulls() {
        List<String> result = CollectionUtils.toNonNullList(Arrays.asList("a", null, "b", null, "c"));
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("toNonNullList returns empty list for null input")
    void toNonNullListReturnsEmptyForNull() {
        assertThat(CollectionUtils.toNonNullList(null)).isEmpty();
    }

    @Test
    @DisplayName("toNonNullList returns unmodifiable list")
    void toNonNullListIsUnmodifiable() {
        List<String> result = CollectionUtils.toNonNullList(List.of("a", "b"));
        assertThatThrownBy(() -> result.add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── mapList ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("mapList transforms each element")
    void mapListTransformsElements() {
        List<Integer> result = CollectionUtils.mapList(List.of("a", "bb", "ccc"), String::length);
        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("mapList returns empty list for null input")
    void mapListReturnsEmptyForNull() {
        List<Integer> result = CollectionUtils.mapList(null, String::length);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("mapList returns empty list for empty input")
    void mapListReturnsEmptyForEmpty() {
        List<Integer> result = CollectionUtils.mapList(Collections.emptyList(), String::length);
        assertThat(result).isEmpty();
    }

    // ── toMap ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toMap creates map keyed by extractor result")
    void toMapCreatesKeyedMap() {
        List<String> items = List.of("apple", "banana", "cherry");
        Map<Integer, String> result = CollectionUtils.toMap(items, String::length);
        assertThat(result).containsEntry(5, "apple").containsEntry(6, "cherry");
    }

    @Test
    @DisplayName("toMap returns empty map for null input")
    void toMapReturnsEmptyForNull() {
        assertThat(CollectionUtils.toMap(null, String::length)).isEmpty();
    }

    @Test
    @DisplayName("toMap last value wins on duplicate keys")
    void toMapLastValueWinsOnDuplicateKeys() {
        List<String> items = List.of("ab", "cd"); // both have length 2
        Map<Integer, String> result = CollectionUtils.toMap(items, String::length);
        assertThat(result.get(2)).isEqualTo("cd");
    }

    // ── partition ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("partition splits list into fixed-size batches")
    void partitionSplitsIntoBatches() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        List<List<Integer>> batches = CollectionUtils.partition(items, 2);
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).containsExactly(1, 2);
        assertThat(batches.get(1)).containsExactly(3, 4);
        assertThat(batches.get(2)).containsExactly(5);
    }

    @Test
    @DisplayName("partition handles batch size equal to list size")
    void partitionEqualBatchSize() {
        List<Integer> items = List.of(1, 2, 3);
        List<List<Integer>> batches = CollectionUtils.partition(items, 3);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("partition returns empty list for empty input")
    void partitionReturnsEmptyForEmpty() {
        assertThat(CollectionUtils.partition(Collections.emptyList(), 5)).isEmpty();
    }

    @Test
    @DisplayName("partition throws for non-positive batch size")
    void partitionThrowsForNonPositiveBatchSize() {
        assertThatThrownBy(() -> CollectionUtils.partition(List.of(1, 2), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    // ── intersection ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("intersection returns common elements")
    void intersectionReturnsCommonElements() {
        List<String> a = List.of("apple", "banana", "cherry");
        List<String> b = List.of("banana", "cherry", "date");
        List<String> result = CollectionUtils.intersection(a, b);
        assertThat(result).containsExactlyInAnyOrder("banana", "cherry");
    }

    @Test
    @DisplayName("intersection returns empty list when no common elements")
    void intersectionReturnsEmptyWhenDisjoint() {
        assertThat(CollectionUtils.intersection(List.of("a"), List.of("b"))).isEmpty();
    }

    @Test
    @DisplayName("intersection returns empty list for null first argument")
    void intersectionReturnsEmptyForNullFirst() {
        assertThat(CollectionUtils.intersection(null, List.of("a"))).isEmpty();
    }

    @Test
    @DisplayName("intersection returns empty list for null second argument")
    void intersectionReturnsEmptyForNullSecond() {
        assertThat(CollectionUtils.intersection(List.of("a"), null)).isEmpty();
    }
}
