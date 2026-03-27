package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a resource conflict occurs (e.g., duplicate creation).
 *
 * @doc.type class
 * @doc.purpose Exception for resource conflicts (409)
 * @doc.layer core
 * @doc.pattern Exception
 */
public class ConflictException extends BaseException {

    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }

    public ConflictException(@NotNull String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.CONFLICT, message, cause);
    }

    public ConflictException(@Nullable Throwable cause) {
        super(ErrorCode.CONFLICT, cause);
    }
}
