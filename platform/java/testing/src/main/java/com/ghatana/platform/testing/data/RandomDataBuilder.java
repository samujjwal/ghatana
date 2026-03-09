package com.ghatana.platform.testing.data;

import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builder for generating random test data.
 * 
 * <p>Provides fluent API for generating random values of various types (strings, numbers, dates, collections).
 * Useful for creating test fixtures and randomized property-based tests.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Random Strings</b>: alphanumeric, alphabetic, numeric with configurable length</li>
 *   <li><b>Random Numbers</b>: integers, longs, doubles with min/max ranges</li>
 *   <li><b>Random Dates</b>: Instant, LocalDate, ZonedDateTime with ranges</li>
 *   <li><b>Random Collections</b>: lists, sets, maps with configurable size</li>
 *   <li><b>Seeded Random</b>: Reproducible tests with provided seed</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * RandomDataBuilder builder = new RandomDataBuilder();
 * String randomEmail = builder.alphaNumeric(10).get() + "@example.com";
 * int randomPort = builder.intBetween(1024, 65535).get();
 * LocalDate randomDate = builder.localDate().get();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fluent builder for generating random test data (strings, numbers, dates, collections)
 * @doc.layer testing
 * @doc.pattern Builder, DataGenerator, Test Utility
 */
public class RandomDataBuilder {
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMERIC = "0123456789";
    
    private final Random random;
    
    /**
     * Creates a new RandomDataBuilder with a new Random instance.
     */
    public RandomDataBuilder() {
        this(new Random());
    }
    
    /**
     * Creates a new RandomDataBuilder with the specified Random instance.
     *
     * @param random the Random instance to use
     */
    public RandomDataBuilder(Random random) {
        this.random = Objects.requireNonNull(random, "Random cannot be null");
    }
    
    /**
     * Creates a DataGenerator that generates random strings.
     *
     * @param length the length of the string to generate
     * @return a DataGenerator that generates random strings
     */
    public DataGenerator<String> string(int length) {
        return string(ALPHA_NUMERIC, length, length);
    }
    
    /**
     * Creates a DataGenerator that generates random strings with a length between min and max.
     *
     * @param minLength the minimum length of the string
     * @param maxLength the maximum length of the string
     * @return a DataGenerator that generates random strings
     */
    public DataGenerator<String> string(int minLength, int maxLength) {
        return string(ALPHA_NUMERIC, minLength, maxLength);
    }
    
    /**
     * Creates a DataGenerator that generates random strings from the given characters.
     *
     * @param chars the characters to use for generating the string
     * @param minLength the minimum length of the string
     * @param maxLength the maximum length of the string
     * @return a DataGenerator that generates random strings
     */
    public DataGenerator<String> string(String chars, int minLength, int maxLength) {
        if (minLength < 0 || maxLength < minLength) {
            throw new IllegalArgumentException("Invalid length range: [" + minLength + ", " + maxLength + "]");
        }
        
        return () -> {
            int length = minLength == maxLength ? minLength : minLength + random.nextInt(maxLength - minLength + 1);
            return random.ints(length, 0, chars.length())
                    .mapToObj(chars::charAt)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();
        };
    }
    
    /**
     * Creates a DataGenerator that generates random alphanumeric strings.
     *
     * @param length the length of the string
     * @return a DataGenerator that generates random alphanumeric strings
     */
    public DataGenerator<String> alphaNumeric(int length) {
        return string(ALPHA_NUMERIC, length, length);
    }
    
    /**
     * Creates a DataGenerator that generates random alphabetic strings.
     *
     * @param length the length of the string
     * @return a DataGenerator that generates random alphabetic strings
     */
    public DataGenerator<String> alphabetic(int length) {
        return string(ALPHA, length, length);
    }
    
    /**
     * Creates a DataGenerator that generates random numeric strings.
     *
     * @param length the length of the string
     * @return a DataGenerator that generates random numeric strings
     */
    public DataGenerator<String> numeric(int length) {
        return string(NUMERIC, length, length);
    }
    
    /**
     * Creates a DataGenerator that generates random integers.
     *
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @return a DataGenerator that generates random integers
     */
    public DataGenerator<Integer> integer(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
        return () -> random.nextInt((max - min) + 1) + min;
    }
    
    /**
     * Creates a DataGenerator that generates random longs.
     *
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @return a DataGenerator that generates random longs
     */
    public DataGenerator<Long> longValue(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
        return () -> ThreadLocalRandom.current().nextLong(min, max + 1);
    }
    
    /**
     * Creates a DataGenerator that generates random doubles.
     *
     * @param min the minimum value (inclusive)
     * @param max the maximum value (exclusive)
     * @return a DataGenerator that generates random doubles
     */
    public DataGenerator<Double> doubleValue(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be less than or equal to max");
        }
        return () -> min + (max - min) * random.nextDouble();
    }
    
    /**
     * Creates a DataGenerator that generates random booleans.
     *
     * @return a DataGenerator that generates random booleans
     */
    public DataGenerator<Boolean> bool() {
        return random::nextBoolean;
    }
    
    /**
     * Creates a DataGenerator that generates random dates.
     *
     * @param startInclusive the start date (inclusive)
     * @param endExclusive the end date (exclusive)
     * @return a DataGenerator that generates random dates
     */
    public DataGenerator<LocalDate> date(LocalDate startInclusive, LocalDate endExclusive) {
        long startEpochDay = startInclusive.toEpochDay();
        long endEpochDay = endExclusive.toEpochDay();
        
        return () -> LocalDate.ofEpochDay(ThreadLocalRandom.current().nextLong(startEpochDay, endEpochDay));
    }
    
    /**
     * Creates a DataGenerator that generates random date-times.
     *
     * @param startInclusive the start date-time (inclusive)
     * @param endExclusive the end date-time (exclusive)
     * @return a DataGenerator that generates random date-times
     */
    public DataGenerator<LocalDateTime> dateTime(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        long startEpochSecond = startInclusive.toEpochSecond(ZoneOffset.UTC);
        long endEpochSecond = endExclusive.toEpochSecond(ZoneOffset.UTC);
        
        return () -> {
            long randomEpochSecond = ThreadLocalRandom.current().nextLong(startEpochSecond, endEpochSecond);
            return LocalDateTime.ofEpochSecond(randomEpochSecond, 0, ZoneOffset.UTC);
        };
    }
    
    /**
     * Creates a DataGenerator that generates random enum constants.
     *
     * @param enumClass the enum class
     * @param <E> the enum type
     * @return a DataGenerator that generates random enum constants
     */
    public <E extends Enum<E>> DataGenerator<E> enumValue(Class<E> enumClass) {
        E[] values = enumClass.getEnumConstants();
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Enum class " + enumClass.getName() + " has no constants");
        }
        
        return () -> values[random.nextInt(values.length)];
    }
    
    /**
     * Creates a DataGenerator that selects a random element from the given values.
     *
     * @param values the values to choose from
     * @param <T> the type of the values
     * @return a DataGenerator that selects a random element
     */
    @SafeVarargs
    public final <T> DataGenerator<T> oneOf(T... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Values cannot be empty");
        }
        
        return () -> values[random.nextInt(values.length)];
    }
    
    /**
     * Creates a DataGenerator that selects a random element from the given collection.
     *
     * @param values the values to choose from
     * @param <T> the type of the values
     * @return a DataGenerator that selects a random element
     */
    public <T> DataGenerator<T> oneOf(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Values cannot be empty");
        }
        
        List<T> valueList = new ArrayList<>(values);
        return () -> valueList.get(random.nextInt(valueList.size()));
    }
    
    /**
     * Creates a DataGenerator that generates a list of values.
     *
     * @param elementGenerator the generator for list elements
     * @param minSize the minimum size of the list (inclusive)
     * @param maxSize the maximum size of the list (inclusive)
     * @param <T> the type of the list elements
     * @return a DataGenerator that generates lists
     */
    public <T> DataGenerator<List<T>> list(DataGenerator<T> elementGenerator, int minSize, int maxSize) {
        if (minSize < 0 || maxSize < minSize) {
            throw new IllegalArgumentException("Invalid size range: [" + minSize + ", " + maxSize + "]");
        }
        
        return () -> {
            int size = minSize == maxSize ? minSize : minSize + random.nextInt(maxSize - minSize + 1);
            return IntStream.range(0, size)
                    .mapToObj(i -> elementGenerator.generate())
                    .collect(Collectors.toList());
        };
    }
    
    /**
     * Creates a DataGenerator that generates a set of values.
     *
     * @param elementGenerator the generator for set elements
     * @param minSize the minimum size of the set (inclusive)
     * @param maxSize the maximum size of the set (inclusive)
     * @param <T> the type of the set elements
     * @return a DataGenerator that generates sets
     */
    public <T> DataGenerator<Set<T>> set(DataGenerator<T> elementGenerator, int minSize, int maxSize) {
        return list(elementGenerator, minSize, maxSize)
                .map(HashSet::new);
    }
    
    /**
     * Creates a DataGenerator that generates a map of key-value pairs.
     *
     * @param keyGenerator the generator for map keys
     * @param valueGenerator the generator for map values
     * @param minSize the minimum size of the map (inclusive)
     * @param maxSize the maximum size of the map (inclusive)
     * @param <K> the type of the map keys
     * @param <V> the type of the map values
     * @return a DataGenerator that generates maps
     */
    public <K, V> DataGenerator<Map<K, V>> map(
            DataGenerator<K> keyGenerator,
            DataGenerator<V> valueGenerator,
            int minSize,
            int maxSize) {
        
        return () -> {
            int size = minSize == maxSize ? minSize : minSize + random.nextInt(maxSize - minSize + 1);
            Map<K, V> result = new HashMap<>();
            while (result.size() < size) {
                result.put(keyGenerator.generate(), valueGenerator.generate());
            }
            return result;
        };
    }
    
    /**
     * Creates a DataGenerator that generates values based on a list of weighted generators.
     *
     * @param weightedGenerators the list of weighted generators
     * @param <T> the type of the generated values
     * @return a DataGenerator that selects a generator based on weights
     */
    @SafeVarargs
    public final <T> DataGenerator<T> frequency(Pair<Integer, DataGenerator<T>>... weightedGenerators) {
        if (weightedGenerators == null || weightedGenerators.length == 0) {
            throw new IllegalArgumentException("Weighted generators cannot be empty");
        }
        
        int totalWeight = 0;
        for (Pair<Integer, DataGenerator<T>> pair : weightedGenerators) {
            if (pair.getFirst() <= 0) {
                throw new IllegalArgumentException("Weight must be positive: " + pair.getFirst());
            }
            totalWeight += pair.getFirst();
        }
        
        int finalTotalWeight = totalWeight;
        return () -> {
            int randomValue = random.nextInt(finalTotalWeight);
            int currentWeight = 0;
            
            for (Pair<Integer, DataGenerator<T>> pair : weightedGenerators) {
                currentWeight += pair.getFirst();
                if (randomValue < currentWeight) {
                    return pair.getSecond().generate();
                }
            }
            
            // This should never happen if the weights are correct
            throw new IllegalStateException("Failed to select a generator");
        };
    }
    
    /**
     * Creates a DataGenerator that generates values using the specified supplier.
     *
     * @param supplier the supplier to use for generating values
     * @param <T> the type of the generated values
     * @return a DataGenerator that uses the specified supplier
     */
    public static <T> DataGenerator<T> fromSupplier(Supplier<T> supplier) {
        return supplier::get;
    }
    
    /**
     * A simple pair class for holding two values.
     *
     * @param <A> the type of the first value
     * @param <B> the type of the second value
     */
    public static class Pair<A, B> {
        private final A first;
        private final B second;
        
        private Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
        
        public static <A, B> Pair<A, B> of(A first, B second) {
            return new Pair<>(first, second);
        }
        
        public A getFirst() {
            return first;
        }
        
        public B getSecond() {
            return second;
        }
    }
}
