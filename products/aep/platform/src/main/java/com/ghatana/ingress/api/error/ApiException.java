/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.ingress.api.error;

import com.ghatana.platform.core.exception.ErrorCode;
import lombok.Getter;
import lombok.NonNull;

/**
 * Exception thrown by API operations with structured error information.
 */
@Getter
public final class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(@NonNull ErrorCode errorCode, @NonNull String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(@NonNull ErrorCode errorCode, @NonNull String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
