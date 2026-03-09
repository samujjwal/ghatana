package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable generic pair for returning two related values.
 * 
 * Lightweight value object for pairing two related values when creating a full domain class
 * would be overkill. Commonly used for method return values and stream operations.
 *
 * @param <F> Type of first element
 * @param <S> Type of second element
 *
 * @doc.type record
 * @doc.purpose Generic immutable pair of two values
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record Pair<F, S>(
        @NotNull F first,
        @NotNull S second
) {
    
    public Pair {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(second, "second must not be null");
    }
    
    /**
     * Create a pair of two values.
     */
    public static <F, S> Pair<F, S> of(@NotNull F first, @NotNull S second) {
        return new Pair<>(first, second);
    }
    
    /**
     * Get the first element.
     */
    @NotNull
    public F getFirst() {
        return first;
    }
    
    /**
     * Get the second element.
     */
    @NotNull
    public S getSecond() {
        return second;
    }
}
