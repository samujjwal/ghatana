package com.ghatana.platform.testing.data;

/**
 * Interface for generating test data of type T.
 *
 * @param <T> the type of data to generate
 */
/**
 * Data generator.
 *
 * @doc.type interface
 * @doc.purpose Data generator
 * @doc.layer core
 * @doc.pattern Interface
 */
@FunctionalInterface
public interface DataGenerator<T> {
    
    /**
     * Generates a new instance of the test data.
     *
     * @return a new instance of T
     */
    T generate();
    
    /**
     * Creates a new DataGenerator that always returns the same value.
     *
     * @param value the value to always return
     * @param <T> the type of the value
     * @return a DataGenerator that always returns the given value
     */
    static <T> DataGenerator<T> constant(T value) {
        return () -> value;
    }
    
    /**
     * Creates a new DataGenerator that transforms the output of this generator.
     *
     * @param mapper the mapping function
     * @param <R> the type of the result
     * @return a new DataGenerator
     */
    default <R> DataGenerator<R> map(java.util.function.Function<? super T, ? extends R> mapper) {
        return () -> mapper.apply(generate());
    }
    
    /**
     * Creates a new DataGenerator that applies the given function to the output of this generator.
     *
     * @param mapper the mapping function
     * @param <R> the type of the result
     * @return a new DataGenerator
     */
    default <R> DataGenerator<R> flatMap(java.util.function.Function<? super T, ? extends DataGenerator<? extends R>> mapper) {
        return () -> mapper.apply(generate()).generate();
    }
}
