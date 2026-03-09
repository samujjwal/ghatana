package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Utility class for argument validation and precondition checking.
 * 
 * Provides fluent, readable validation methods that throw appropriate exceptions
 * with clear error messages.
 *
 * @doc.type class
 * @doc.purpose Argument validation and precondition checking utilities
 * @doc.layer core
 * @doc.pattern Utility
 */
public final class Preconditions {
    
    private Preconditions() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Ensures that an object reference is not null.
     *
     * @param reference the object reference to check
     * @param name the name of the parameter for the error message
     * @return the non-null reference
     * @throws NullPointerException if reference is null
     */
    @NotNull
    public static <T> T requireNonNull(@Nullable T reference, @NotNull String name) {
        if (reference == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return reference;
    }
    
    /**
     * Ensures that a string is not null or empty.
     *
     * @param value the string to check
     * @param name the name of the parameter for the error message
     * @return the non-empty string
     * @throws IllegalArgumentException if value is null or empty
     */
    @NotNull
    public static String requireNonEmpty(@Nullable String value, @NotNull String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
        return value;
    }
    
    /**
     * Ensures that a string is not null, empty, or blank.
     *
     * @param value the string to check
     * @param name the name of the parameter for the error message
     * @return the non-blank string
     * @throws IllegalArgumentException if value is null, empty, or blank
     */
    @NotNull
    public static String requireNonBlank(@Nullable String value, @NotNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null, empty, or blank");
        }
        return value;
    }
    
    /**
     * Ensures that a collection is not null or empty.
     *
     * @param collection the collection to check
     * @param name the name of the parameter for the error message
     * @return the non-empty collection
     * @throws IllegalArgumentException if collection is null or empty
     */
    @NotNull
    public static <T extends Collection<?>> T requireNonEmpty(@Nullable T collection, @NotNull String name) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
        return collection;
    }
    
    /**
     * Ensures that a map is not null or empty.
     *
     * @param map the map to check
     * @param name the name of the parameter for the error message
     * @return the non-empty map
     * @throws IllegalArgumentException if map is null or empty
     */
    @NotNull
    public static <T extends Map<?, ?>> T requireNonEmpty(@Nullable T map, @NotNull String name) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
        return map;
    }
    
    /**
     * Ensures that a condition is true.
     *
     * @param condition the condition to check
     * @param message the error message if condition is false
     * @throws IllegalArgumentException if condition is false
     */
    public static void require(boolean condition, @NotNull String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Ensures that a value is positive (greater than zero).
     *
     * @param value the value to check
     * @param name the name of the parameter for the error message
     * @return the positive value
     * @throws IllegalArgumentException if value is not positive
     */
    public static int requirePositive(int value, @NotNull String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
        return value;
    }
    
    /**
     * Ensures that a value is positive (greater than zero).
     *
     * @param value the value to check
     * @param name the name of the parameter for the error message
     * @return the positive value
     * @throws IllegalArgumentException if value is not positive
     */
    public static long requirePositive(long value, @NotNull String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got: " + value);
        }
        return value;
    }
    
    /**
     * Ensures that a value is non-negative (greater than or equal to zero).
     *
     * @param value the value to check
     * @param name the name of the parameter for the error message
     * @return the non-negative value
     * @throws IllegalArgumentException if value is negative
     */
    public static int requireNonNegative(int value, @NotNull String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, got: " + value);
        }
        return value;
    }
    
    /**
     * Ensures that a value is non-negative (greater than or equal to zero).
     *
     * @param value the value to check
     * @param name the name of the parameter for the error message
     * @return the non-negative value
     * @throws IllegalArgumentException if value is negative
     */
    public static long requireNonNegative(long value, @NotNull String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, got: " + value);
        }
        return value;
    }
    
    /**
     * Ensures that an array has an even number of elements (for key-value pairs).
     *
     * @param array the array to check
     * @param name the name of the parameter for the error message
     * @throws IllegalArgumentException if array is null or has odd length
     */
    public static <T> T[] requireEvenLength(T[] array, @NotNull String name) {
        if (array == null || array.length % 2 != 0) {
            throw new IllegalArgumentException(name + " must have an even number of elements");
        }
        return array;
    }

    public static int requireInRange(int value, int min, int max, @NotNull String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    String.format("%s must be between %d and %d (inclusive), got: %d", name, min, max, value));
        }
        return value;
    }
}
