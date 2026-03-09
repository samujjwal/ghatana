package com.ghatana.platform.testing.fixtures;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base interface for test fixtures.
 * 
 * Provides a consistent pattern for setting up and tearing down test resources.
 *
 * @param <T> the type of the fixture resource
 *
 * @doc.type interface
 * @doc.purpose Contract for reusable test data fixtures
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface TestFixture<T> extends AutoCloseable {
    
    /**
     * Get the fixture resource.
     */
    @NotNull
    T get();
    
    /**
     * Set up the fixture.
     */
    void setUp();
    
    /**
     * Tear down the fixture.
     */
    void tearDown();
    
    @Override
    default void close() {
        tearDown();
    }
    
    /**
     * Create a simple fixture from a supplier and cleanup action.
     */
    static <T> TestFixture<T> of(@NotNull Supplier<T> supplier, @NotNull Consumer<T> cleanup) {
        return new TestFixture<>() {
            private T resource;
            
            @Override
            public @NotNull T get() {
                if (resource == null) {
                    throw new IllegalStateException("Fixture not set up");
                }
                return resource;
            }
            
            @Override
            public void setUp() {
                resource = supplier.get();
            }
            
            @Override
            public void tearDown() {
                if (resource != null) {
                    cleanup.accept(resource);
                    resource = null;
                }
            }
        };
    }
    
    /**
     * Create a fixture that doesn't need cleanup.
     */
    static <T> TestFixture<T> of(@NotNull Supplier<T> supplier) {
        return of(supplier, r -> {});
    }
}
