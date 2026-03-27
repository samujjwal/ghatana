package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown for unexpected internal errors (500).
 *
 * @doc.type class
 * @doc.purpose Exception for unexpected internal server errors
 * @doc.layer core
 * @doc.pattern Exception
 */
public class InternalException extends BaseException {

    public InternalException() {
        super(ErrorCode.INTERNAL_ERROR);
    }

    public InternalException(@NotNull String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }

    public InternalException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
    }

    public InternalException(@Nullable Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, cause);
    }
}
