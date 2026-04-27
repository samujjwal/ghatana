package com.ghatana.yappc.plugin;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Canonical discriminated result type for YAPPC operations.
 *
 * <p>Represents either a successful outcome carrying a value of type {@code T},
 * or a failure carrying a non-null error message and an optional cause. All
 * public surface methods are null-safe.</p>
 *
 * <p>Usage example:
 * <pre>{@code
 * Result<String> r = someService.doWork();
 * r.ifSuccess(value -> log.info("Got: {}", value))
 *  .ifFailure(msg -> log.error("Failed: {}", msg));
 *
 * String value = r.getOrElse("default");
 * }</pre>
 * </p>
 *
 * @param <T> the type of the success value
 *
 * @doc.type class
 * @doc.purpose Canonical discriminated result type for YAPPC operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * Create a successful result carrying {@code value}.
     *
     * @param value the success value; must not be null
     * @param <T>   value type
     * @return a {@link Success} wrapping value
     */
    static <T> Result<T> success(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Success value must not be null");
        }
        return new Success<>(value);
    }

    /**
     * Create a failure result with the given message and no cause.
     *
     * @param message human-readable failure description; must not be null or blank
     * @param <T>     value type
     * @return a {@link Failure}
     */
    static <T> Result<T> failure(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Failure message must not be null or blank");
        }
        return new Failure<>(message, null);
    }

    /**
     * Create a failure result with the given message and a root cause.
     *
     * @param message human-readable failure description; must not be null or blank
     * @param cause   the underlying exception; may be null
     * @param <T>     value type
     * @return a {@link Failure}
     */
    static <T> Result<T> failure(String message, Throwable cause) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Failure message must not be null or blank");
        }
        return new Failure<>(message, cause);
    }

    /**
     * Wrap a checked call, converting any exception into a {@link Failure}.
     *
     * @param supplier the operation to run
     * @param <T>      return type
     * @return {@link Success} on success, {@link Failure} on any exception
     */
    static <T> Result<T> tryGet(CheckedSupplier<T> supplier) {
        try {
            T value = supplier.get();
            return success(value);
        } catch (Exception e) {
            return failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
        }
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /** Returns {@code true} if this is a {@link Success}. */
    boolean isSuccess();

    /** Returns {@code true} if this is a {@link Failure}. */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the success value.
     *
     * @throws NoSuchElementException if this is a {@link Failure}
     */
    T getValue();

    /**
     * Returns the failure message.
     *
     * @throws NoSuchElementException if this is a {@link Success}
     */
    String getError();

    /**
     * Returns the failure cause, or empty if absent / if this is a success.
     */
    Optional<Throwable> getCause();

    // =========================================================================
    // Transformation Methods
    // =========================================================================

    /**
     * Map the success value; propagates failures unchanged.
     *
     * @param mapper function applied to the success value
     * @param <U>    mapped type
     * @return mapped result
     */
    <U> Result<U> map(Function<T, U> mapper);

    /**
     * FlatMap — chain another Result-returning operation on the success value.
     *
     * @param mapper function returning a Result
     * @param <U>    output type
     * @return result of the chained operation, or the original failure
     */
    <U> Result<U> flatMap(Function<T, Result<U>> mapper);

    /**
     * Returns the success value, or {@code defaultValue} if this is a failure.
     */
    T getOrElse(T defaultValue);

    /**
     * Returns the success value, or throws the cause if present, or throws
     * a {@link RuntimeException} with the error message.
     */
    T getOrThrow();

    // =========================================================================
    // Side-Effect Methods (return {@code this} for chaining)
    // =========================================================================

    /** Invoke {@code action} if this is a success; no-op on failure. */
    Result<T> ifSuccess(Consumer<T> action);

    /** Invoke {@code action} if this is a failure; no-op on success. */
    Result<T> ifFailure(Consumer<String> action);

    // =========================================================================
    // Implementations
    // =========================================================================

    /**
     * The success variant of {@link Result}.
     */
    record Success<T>(T value) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public String getError() {
            throw new NoSuchElementException("Result.Success has no error");
        }

        @Override
        public Optional<Throwable> getCause() {
            return Optional.empty();
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            try {
                return Result.success(mapper.apply(value));
            } catch (Exception e) {
                return Result.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
            }
        }

        @Override
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return Result.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
            }
        }

        @Override
        public T getOrElse(T defaultValue) {
            return value;
        }

        @Override
        public T getOrThrow() {
            return value;
        }

        @Override
        public Result<T> ifSuccess(Consumer<T> action) {
            action.accept(value);
            return this;
        }

        @Override
        public Result<T> ifFailure(Consumer<String> action) {
            return this;
        }
    }

    /**
     * The failure variant of {@link Result}.
     */
    final class Failure<T> implements Result<T> {

        private final String message;
        private final Throwable cause;

        Failure(String message, Throwable cause) {
            this.message = message;
            this.cause = cause;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T getValue() {
            throw new NoSuchElementException("Result.Failure has no value: " + message);
        }

        @Override
        public String getError() {
            return message;
        }

        @Override
        public Optional<Throwable> getCause() {
            return Optional.ofNullable(cause);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> map(Function<T, U> mapper) {
            return (Result<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            return (Result<U>) this;
        }

        @Override
        public T getOrElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        public T getOrThrow() {
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause != null) {
                throw new RuntimeException(message, cause);
            }
            throw new RuntimeException(message);
        }

        @Override
        public Result<T> ifSuccess(Consumer<T> action) {
            return this;
        }

        @Override
        public Result<T> ifFailure(Consumer<String> action) {
            action.accept(message);
            return this;
        }

        @Override
        public String toString() {
            return "Failure{message='" + message + "', cause=" + cause + '}';
        }
    }

    // =========================================================================
    // Functional Interface
    // =========================================================================

    /** Checked supplier for use with {@link #tryGet(CheckedSupplier)}. */
    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
