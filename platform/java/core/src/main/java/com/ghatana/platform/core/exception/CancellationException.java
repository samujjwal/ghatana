package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when an operation is cancelled or interrupted before completion.
 *
 * @doc.type class
 * @doc.purpose Exception for cancelled or interrupted operations
 * @doc.layer core
 * @doc.pattern Exception
 */
public class CancellationException extends BaseException {

    public CancellationException() {
        super(ErrorCode.INTERNAL_ERROR);
    }

    public CancellationException(@NotNull String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }

    public CancellationException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
    }

    public CancellationException(@Nullable Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, cause);
    }
}
