package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Base exception class for all platform-level exceptions.
 *
 * <p>Extends {@link BaseException} to participate in the single platform exception
 * hierarchy. Adds HTTP status mapping and category accessors derived from the
 * {@link ErrorCode}, plus a fluent {@code withMetadata} API that is a façade
 * over {@link BaseException#addMetadata(String, Object)}.
 *
 * @doc.type class
 * @doc.purpose Base exception for all platform-level errors with HTTP status and category
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class PlatformException extends BaseException {

    /**
     * Create an exception with an error code.
     */
    public PlatformException(@NotNull ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * Create an exception with an error code and custom message.
     */
    public PlatformException(@NotNull ErrorCode errorCode, @NotNull String message) {
        super(errorCode, message);
    }

    /**
     * Create an exception with an error code, message, and cause.
     */
    public PlatformException(@NotNull ErrorCode errorCode, @NotNull String message, @Nullable Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Create an exception with an error code and cause.
     */
    public PlatformException(@NotNull ErrorCode errorCode, @Nullable Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * Create an exception with an error code, message, cause, and initial metadata.
     */
    public PlatformException(
            @NotNull ErrorCode errorCode,
            @NotNull String message,
            @Nullable Throwable cause,
            @NotNull Map<String, Object> metadata) {
        super(errorCode, message, cause, metadata);
    }

    /**
     * Get the HTTP status code derived from the error code.
     */
    public int getHttpStatus() {
        return getErrorCode().getHttpStatus();
    }

    /**
     * Get the error category derived from the error code.
     */
    @NotNull
    public String getCategory() {
        return getErrorCode().getCategory();
    }

    /**
     * Returns an immutable snapshot of all metadata entries.
     * Overrides to preserve the defensive-copy contract of the original API.
     */
    @Override
    @NotNull
    public Map<String, Object> getMetadata() {
        return Map.copyOf(super.getMetadata());
    }

    /**
     * Fluent metadata setter — delegates to {@link BaseException#addMetadata(String, Object)}.
     *
     * @param key   the metadata key
     * @param value the metadata value (nullable)
     * @return this exception for chaining
     */
    @NotNull
    public PlatformException withMetadata(@NotNull String key, @Nullable Object value) {
        addMetadata(key, value);
        return this;
    }

    /**
     * Fluent metadata setter for bulk entries.
     *
     * @param entries the metadata entries to add
     * @return this exception for chaining
     */
    @NotNull
    public PlatformException withMetadata(@NotNull Map<String, Object> entries) {
        addMetadata(entries);
        return this;
    }
}
