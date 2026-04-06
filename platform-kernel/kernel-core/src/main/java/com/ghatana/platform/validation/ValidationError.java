/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.validation;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable value object representing a single validation error.
 *
 * Encapsulates validation failures with machine-readable error codes,
 * human-readable messages, and optional field path context.
 *
 * @doc.type class
 * @doc.purpose Represents a single validation failure with field and message
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class ValidationError {
    private final String code;
    private final String message;
    private final String path;
    private final Object rejectedValue;

    public ValidationError(@NotNull String code, @NotNull String message) {
        this(code, message, null, null);
    }

    public ValidationError(@NotNull String code, @NotNull String message, String path, Object rejectedValue) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.path = path;
        this.rejectedValue = rejectedValue;
    }

    @NotNull
    public String getCode() {
        return code;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    @Override
    public String toString() {
        return String.format("ValidationError[code=%s, message=%s, path=%s]", code, message, path);
    }
}
