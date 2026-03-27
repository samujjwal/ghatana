package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when an operation times out.
 *
 * @doc.type class
 * @doc.purpose Exception for operation timeout failures
 * @doc.layer core
 * @doc.pattern Exception
 */
public class TimeoutException extends BaseException {

    public TimeoutException() {
        super(ErrorCode.TIMEOUT_ERROR);
    }

    public TimeoutException(@NotNull String message) {
        super(ErrorCode.TIMEOUT_ERROR, message);
    }

    public TimeoutException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.TIMEOUT_ERROR, message, cause);
    }

    public TimeoutException(@Nullable Throwable cause) {
        super(ErrorCode.TIMEOUT_ERROR, cause);
    }
}
