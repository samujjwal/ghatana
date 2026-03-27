package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a downstream service is unavailable.
 *
 * @doc.type class
 * @doc.purpose Exception for service unavailability (503)
 * @doc.layer core
 * @doc.pattern Exception
 */
public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException() {
        super(ErrorCode.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(@NotNull String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }

    public ServiceUnavailableException(@Nullable Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, cause);
    }
}
