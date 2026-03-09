package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A result type that represents either a successful value or an error.
 * 
 * This is a functional alternative to throwing exceptions for expected error cases.
 * Use this for operations that can fail in expected ways (validation, parsing, etc.).
 *
 * @param <T> the type of the success value
 * @param <E> the type of the error
 * @doc.type interface
 * @doc.purpose Functional result type for expected error cases
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {
    
    /**
     * Create a successful result.
     */
    static <T, E> Result<T, E> success(@NotNull T value) {
        return new Success<>(Objects.requireNonNull(value, "value"));
    }
    
    /**
     * Create a failed result.
     */
    static <T, E> Result<T, E> failure(@NotNull E error) {
        return new Failure<>(Objects.requireNonNull(error, "error"));
    }
    
    /**
     * Create a result from a nullable value, using the error if value is null.
     */
    static <T, E> Result<T, E> ofNullable(@Nullable T value, @NotNull E errorIfNull) {
        return value != null ? success(value) : failure(errorIfNull);
    }
    
    /**
     * Create a result from an Optional, using the error if empty.
     */
    static <T, E> Result<T, E> fromOptional(@NotNull Optional<T> optional, @NotNull E errorIfEmpty) {
        return optional.map(Result::<T, E>success).orElseGet(() -> failure(errorIfEmpty));
    }
    
    /**
     * Create a result by executing a supplier, catching exceptions.
     */
    static <T> Result<T, Exception> of(@NotNull Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (Exception e) {
            return failure(e);
        }
    }
    
    /**
     * Check if this result is a success.
     */
    boolean isSuccess();
    
    /**
     * Check if this result is a failure.
     */
    boolean isFailure();
    
    /**
     * Get the success value, or throw if this is a failure.
     */
    T get();
    
    /**
     * Get the error, or throw if this is a success.
     */
    E getError();
    
    /**
     * Get the success value, or return the default if this is a failure.
     */
    T getOrElse(@NotNull T defaultValue);
    
    /**
     * Get the success value, or compute a default if this is a failure.
     */
    T getOrElseGet(@NotNull Supplier<? extends T> supplier);
    
    /**
     * Get the success value, or throw the exception if this is a failure.
     */
    <X extends Throwable> T getOrElseThrow(@NotNull Supplier<? extends X> exceptionSupplier) throws X;
    
    /**
     * Transform the success value.
     */
    <U> Result<U, E> map(@NotNull Function<? super T, ? extends U> mapper);
    
    /**
     * Transform the error.
     */
    <F> Result<T, F> mapError(@NotNull Function<? super E, ? extends F> mapper);
    
    /**
     * Transform the success value with a function that returns a Result.
     */
    <U> Result<U, E> flatMap(@NotNull Function<? super T, ? extends Result<U, E>> mapper);
    
    /**
     * Execute an action if this is a success.
     */
    Result<T, E> ifSuccess(@NotNull Consumer<? super T> action);
    
    /**
     * Execute an action if this is a failure.
     */
    Result<T, E> ifFailure(@NotNull Consumer<? super E> action);
    
    /**
     * Convert to Optional (empty if failure).
     */
    Optional<T> toOptional();
    
    // ==========================================================================
    // Implementations
    // ==========================================================================
    
    record Success<T, E>(@NotNull T value) implements Result<T, E> {
        
        @Override
        public boolean isSuccess() {
            return true;
        }
        
        @Override
        public boolean isFailure() {
            return false;
        }
        
        @Override
        public T get() {
            return value;
        }
        
        @Override
        public E getError() {
            throw new NoSuchElementException("No error present in Success");
        }
        
        @Override
        public T getOrElse(@NotNull T defaultValue) {
            return value;
        }
        
        @Override
        public T getOrElseGet(@NotNull Supplier<? extends T> supplier) {
            return value;
        }
        
        @Override
        public <X extends Throwable> T getOrElseThrow(@NotNull Supplier<? extends X> exceptionSupplier) {
            return value;
        }
        
        @Override
        public <U> Result<U, E> map(@NotNull Function<? super T, ? extends U> mapper) {
            return success(mapper.apply(value));
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <F> Result<T, F> mapError(@NotNull Function<? super E, ? extends F> mapper) {
            return (Result<T, F>) this;
        }
        
        @Override
        public <U> Result<U, E> flatMap(@NotNull Function<? super T, ? extends Result<U, E>> mapper) {
            return mapper.apply(value);
        }
        
        @Override
        public Result<T, E> ifSuccess(@NotNull Consumer<? super T> action) {
            action.accept(value);
            return this;
        }
        
        @Override
        public Result<T, E> ifFailure(@NotNull Consumer<? super E> action) {
            return this;
        }
        
        @Override
        public Optional<T> toOptional() {
            return Optional.of(value);
        }
    }
    
    record Failure<T, E>(@NotNull E error) implements Result<T, E> {
        
        @Override
        public boolean isSuccess() {
            return false;
        }
        
        @Override
        public boolean isFailure() {
            return true;
        }
        
        @Override
        public T get() {
            throw new NoSuchElementException("No value present in Failure: " + error);
        }
        
        @Override
        public E getError() {
            return error;
        }
        
        @Override
        public T getOrElse(@NotNull T defaultValue) {
            return defaultValue;
        }
        
        @Override
        public T getOrElseGet(@NotNull Supplier<? extends T> supplier) {
            return supplier.get();
        }
        
        @Override
        public <X extends Throwable> T getOrElseThrow(@NotNull Supplier<? extends X> exceptionSupplier) throws X {
            throw exceptionSupplier.get();
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> map(@NotNull Function<? super T, ? extends U> mapper) {
            return (Result<U, E>) this;
        }
        
        @Override
        public <F> Result<T, F> mapError(@NotNull Function<? super E, ? extends F> mapper) {
            return failure(mapper.apply(error));
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U, E> flatMap(@NotNull Function<? super T, ? extends Result<U, E>> mapper) {
            return (Result<U, E>) this;
        }
        
        @Override
        public Result<T, E> ifSuccess(@NotNull Consumer<? super T> action) {
            return this;
        }
        
        @Override
        public Result<T, E> ifFailure(@NotNull Consumer<? super E> action) {
            action.accept(error);
            return this;
        }
        
        @Override
        public Optional<T> toOptional() {
            return Optional.empty();
        }
    }
}
