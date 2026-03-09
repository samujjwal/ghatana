package com.ghatana.pattern.api.exception;

import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.pattern.api.codegen.GeneratedTypeKey;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Checked exception raised when dynamic class compilation fails.
 */
public class EventClassCompilationException extends Exception {

    public enum Reason {
        VALIDATION,
        SCHEMA_MAP,
        UNSUPPORTED_TYPE,
        CODEGEN,
        LINKAGE
    }

    public record Violation(String field, String message) {
        public Violation {
            field = field == null ? "" : field;
            message = Objects.requireNonNullElse(message, "violation");
        }
    }

    private final Reason reason;
    private final GeneratedTypeKey key;
    private final ErrorCode errorCode;
    private final List<Violation> violations;

    public EventClassCompilationException(Reason reason,
                                          GeneratedTypeKey key,
                                          String message) {
        this(reason, key, message, null, List.of());
    }

    public EventClassCompilationException(Reason reason,
                                          GeneratedTypeKey key,
                                          String message,
                                          Throwable cause,
                                          List<Violation> violations) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.key = key;
        this.errorCode = mapReason(reason);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    private static ErrorCode mapReason(Reason reason) {
        return switch (reason) {
            case VALIDATION -> ErrorCode.EVENT_VALIDATION_ERROR;
            case SCHEMA_MAP, UNSUPPORTED_TYPE -> ErrorCode.EVENT_SCHEMA_MAPPING_ERROR;
            case CODEGEN -> ErrorCode.EVENT_CODEGEN_ERROR;
            case LINKAGE -> ErrorCode.EVENT_PROCESSING_ERROR;
        };
    }

    public Reason getReason() {
        return reason;
    }

    public GeneratedTypeKey getKey() {
        return key;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<Violation> getViolations() {
        return Collections.unmodifiableList(violations);
    }
}
