package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Base exception class for all platform-level exceptions.
 * 
 * Provides structured error handling with:
 * - Error codes (not just messages)
 * - Contextual metadata for debugging
 * - HTTP status code mapping
 *
 * @doc.type class
 * @doc.purpose Base exception for all platform-level errors with error codes
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> metadata;

    /**
     * Create an exception with an error code.
     */
    public PlatformException(@NotNull ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Create an exception with an error code and custom message.
     */
    public PlatformException(@NotNull ErrorCode errorCode, @NotNull String message) {
        super(message);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Create an exception with an error code, message, and cause.
     */
    public PlatformException(@NotNull ErrorCode errorCode, @NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Create an exception with an error code and cause.
     */
    public PlatformException(@NotNull ErrorCode errorCode, @Nullable Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.metadata = new HashMap<>();
    }

    /**
     * Get the error code.
     */
    @NotNull
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Get the error code string.
     */
    @NotNull
    public String getErrorCodeString() {
        return errorCode.getCode();
    }

    /**
     * Get the HTTP status code.
     */
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    /**
     * Get the error category.
     */
    @NotNull
    public String getCategory() {
        return errorCode.getCategory();
    }

    /**
     * Get all metadata.
     */
    @NotNull
    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }

    /**
     * Get a metadata value by key.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(@NotNull String key) {
        return (T) metadata.get(key);
    }

    /**
     * Add metadata to the exception.
     */
    @NotNull
    public PlatformException withMetadata(@NotNull String key, @Nullable Object value) {
        metadata.put(key, value);
        return this;
    }

    /**
     * Add multiple metadata entries.
     */
    @NotNull
    public PlatformException withMetadata(@NotNull Map<String, Object> entries) {
        metadata.putAll(entries);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName())
          .append(" [").append(errorCode.getCode()).append("]: ")
          .append(getMessage());
        
        if (!metadata.isEmpty()) {
            sb.append(" | Metadata: ").append(metadata);
        }
        
        return sb.toString();
    }
}
