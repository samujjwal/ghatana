package com.ghatana.platform.core.common;

/**
 * Interface for error codes used in Result type.
 * Products can define their own error codes by implementing this interface.
 *
 * @doc.type interface
 * @doc.purpose Standard error code contract for platform exceptions
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public interface ErrorCode {
    String getCode();
    String getDefaultMessage();
    int getHttpStatus();
    default String getCategory() {
        return "GENERAL";
    }
}
