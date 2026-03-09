package com.ghatana.platform.core.common;

import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight compatibility Result type used across the codebase to represent
 * success/failure outcomes. This is intentionally minimal and only implements
 * the surface API used by many modules (success(), getValue(),
 * getErrorMessage(), errorMessage(), getErrorCode(), static builders).
 *
 * @doc.type class
 * @doc.purpose Lightweight Result type representing success/failure outcomes with optional error codes
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class Result<T> {

    private final boolean success;
    private final T value;
    private final ErrorCode errorCode;
    private final String errorMessage;

    private Result(boolean success, T value, ErrorCode errorCode, String errorMessage) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, null, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String message) {
        return new Result<>(false, null, errorCode, message);
    }

    public static <T> Result<T> failure(String message) {
        return new Result<>(false, null, null, message);
    }

    public boolean success() {
        return success;
    }

    public Optional<T> getValue() {
        return Optional.ofNullable(value);
    }

    public T value() {
        return value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @deprecated Use {@link #getErrorMessage()} instead. This alias exists for
     *             backward compatibility only and will be removed in a future release.
     */
    @Deprecated
    public String errorMessage() {
        return errorMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        if (success) {
            return "Result{success, value=" + value + '}';
        }
        return "Result{failure, code=" + errorCode + ", message='" + errorMessage + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Result)) {
            return false;
        }
        Result<?> result = (Result<?>) o;
        return success == result.success
                && Objects.equals(value, result.value)
                && errorCode == result.errorCode
                && Objects.equals(errorMessage, result.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, value, errorCode, errorMessage);
    }
}
