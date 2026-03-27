package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a resource (memory, connections, threads) is exhausted.
 *
 * @doc.type class
 * @doc.purpose Exception for resource exhaustion conditions
 * @doc.layer core
 * @doc.pattern Exception
 */
public class ResourceExhaustedException extends BaseException {

    public ResourceExhaustedException() {
        super(ErrorCode.STORAGE_QUOTA_EXCEEDED);
    }

    public ResourceExhaustedException(@NotNull String message) {
        super(ErrorCode.STORAGE_QUOTA_EXCEEDED, message);
    }

    public ResourceExhaustedException(@NotNull String message, @Nullable Throwable cause) {
        super(ErrorCode.STORAGE_QUOTA_EXCEEDED, message, cause);
    }

    public ResourceExhaustedException(@Nullable Throwable cause) {
        super(ErrorCode.STORAGE_QUOTA_EXCEEDED, cause);
    }
}
