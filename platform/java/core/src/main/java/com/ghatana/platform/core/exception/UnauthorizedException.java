package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a request is unauthorized (authentication required).
 *
 * @doc.type class
 * @doc.purpose Exception for authentication-related failures
 * @doc.layer core
 * @doc.pattern Exception
 */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(@NotNull String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.UNAUTHORIZED, message, cause);
    }

    public UnauthorizedException(@Nullable Throwable cause) {
        super(ErrorCode.UNAUTHORIZED, cause);
    }
}
