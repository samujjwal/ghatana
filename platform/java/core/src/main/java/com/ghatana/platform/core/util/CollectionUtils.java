/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for common collection operations.
 *
 * <p>Provides null-safe, concise helpers for the most common list, set, and map
 * transformations across the platform. Prefer these helpers over inline stream
 * pipelines when the operation is a common one-liner already covered here.</p>
 *
 * @doc.type class
 * @doc.purpose Common null-safe collection utilities for platform-wide reuse
 * @doc.layer core
 * @doc.pattern Utility
 */
public final class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns {@code true} if the collection is null or empty.
     *
     * @param collection the collection to check
     * @return {@code true} if null or empty
     */
    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Returns {@code true} if the collection is non-null and non-empty.
     *
     * @param collection the collection to check
     * @return {@code true} if non-null and non-empty
     */
    public static boolean isNotEmpty(@Nullable Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Returns the collection if non-null and non-empty, otherwise returns an empty list.
     *
     * @param list the list to check
     * @param <T>  the element type
     * @return the list, or an empty list
     */
    @NotNull
    public static <T> List<T> emptyIfNull(@Nullable List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * Converts a collection to an unmodifiable list, filtering out null elements.
     *
     * @param collection the source collection
     * @param <T>        the element type
     * @return an unmodifiable list without null elements
     */
    @NotNull
    public static <T> List<T> toNonNullList(@Nullable Collection<? extends T> collection) {
        if (collection == null) {
            return Collections.emptyList();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Maps a list to a new list by applying a transformation function.
     *
     * @param list   the source list
     * @param mapper the transformation function
     * @param <T>    the input type
     * @param <R>    the output type
     * @return a new list with mapped values
     */
    @NotNull
    public static <T, R> List<R> mapList(
            @Nullable List<T> list, @NotNull Function<T, R> mapper) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * Converts a collection to a map using the provided key extractor.
     * If duplicate keys exist, the last value wins.
     *
     * @param collection   the source collection
     * @param keyExtractor function to extract the map key from each element
     * @param <K>          the key type
     * @param <V>          the value type
     * @return a map keyed by the extracted key
     */
    @NotNull
    public static <K, V> Map<K, V> toMap(
            @Nullable Collection<V> collection, @NotNull Function<V, K> keyExtractor) {
        if (collection == null || collection.isEmpty()) {
            return Collections.emptyMap();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new));
    }

    /**
     * Partitions a list into batches of the given size.
     *
     * @param list      the source list
     * @param batchSize the maximum size of each batch
     * @param <T>       the element type
     * @return a list of sublists each of at most {@code batchSize} elements
     * @throws IllegalArgumentException if {@code batchSize} is not positive
     */
    @NotNull
    public static <T> List<List<T>> partition(@NotNull List<T> list, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive (was " + batchSize + ")");
        }
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(Collections.unmodifiableList(
                    list.subList(i, Math.min(i + batchSize, list.size()))));
        }
        return Collections.unmodifiableList(batches);
    }

    /**
     * Returns a new list that contains only elements present in both collections (intersection).
     *
     * @param first  the first collection
     * @param second the second collection
     * @param <T>    the element type
     * @return a list of elements common to both collections
     */
    @NotNull
    public static <T> List<T> intersection(
            @Nullable Collection<T> first, @Nullable Collection<T> second) {
        if (isEmpty(first) || isEmpty(second)) {
            return Collections.emptyList();
        }
        Set<T> secondSet = Set.copyOf(second);
        return first.stream()
                .filter(secondSet::contains)
                .distinct()
                .collect(Collectors.toList());
    }
}
