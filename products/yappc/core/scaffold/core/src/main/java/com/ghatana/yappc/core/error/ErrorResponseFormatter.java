/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.yappc.core.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.core.exception.ErrorCodeMappers;
import com.ghatana.platform.core.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;

/**
 * Consistent error response formatting with correlation ID support and
 * structured output.
 *
 * <p>
 * Provides standardized error response formatting across all components with
 * proper correlation ID tracking, severity indication, and contextual
 * information.
 *
 * <p>
 * Week 10 Day 50: Phase 4 architectural improvements - Error Handling Strategy
 *
 * @doc.type class
 * @doc.purpose Consistent error response formatting with correlation ID support
 * and structured output.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ErrorResponseFormatter {

    private static final ObjectMapper objectMapper = createObjectMapper();

    /**
     * Standardized error response structure
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {

        private final String correlationId;
        private final String errorCode;
        private final String message;
        private final YappcException.ErrorSeverity severity;
        private final Instant timestamp;
        private final Map<String, Object> context;
        private final String stackTrace;

        public ErrorResponse(
                String correlationId,
                String errorCode,
                String message,
                YappcException.ErrorSeverity severity,
                Instant timestamp,
                Map<String, Object> context,
                String stackTrace) {
            this.correlationId = correlationId;
            this.errorCode = errorCode;
            this.message = message;
            this.severity = severity;
            this.timestamp = timestamp;
            this.context = context;
            this.stackTrace = stackTrace;
        }

        // Getters
        public String getCorrelationId() {
            return correlationId;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }

        public YappcException.ErrorSeverity getSeverity() {
            return severity;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public String getStackTrace() {
            return stackTrace;
        }
    }

    /**
     * Format YAPPC exception as structured error response
     */
    public static ErrorResponse formatError(YappcException exception, boolean includeStackTrace) {
        // Prefer canonical enum/name when possible. If the ErrorCode is the local BaseErrorCode
        // use our conservative toCanonicalName mapping; otherwise prefer enum.name() where
        // the implementation is an enum, and fall back to getCode() for foreign codes.
        String errorCodeName;
        if (exception.getErrorCode() instanceof YappcException.BaseErrorCode base) {
            errorCodeName = toCanonicalName(base);
        } else if (exception.getErrorCode() instanceof Enum) {
            errorCodeName = ((Enum<?>) exception.getErrorCode()).name();
        } else {
            // Attempt to map foreign/legacy codes to canonical names using shared mappers.
            errorCodeName = tryMapForeignCode(exception.getErrorCode().getCode());
        }

        return new ErrorResponse(
                exception.getCorrelationId(),
                errorCodeName,
                exception.getMessage(),
                exception.getSeverity(),
                exception.getTimestamp(),
                exception.getContext(),
                includeStackTrace ? getStackTraceAsString(exception) : null);
    }

    /**
     * Best-effort mapping from Yappc BaseErrorCode to canonical names used
     * elsewhere. This is intentionally small and conservative — expand mappings
     * as we consolidate.
     */
    public static String toCanonicalName(YappcException.BaseErrorCode base) {
        if (base == null) {
            return "UNKNOWN_ERROR";
        }
        return switch (base) {
            case INTERNAL_ERROR ->
                "INTERNAL_ERROR";
            case VALIDATION_FAILED ->
                "VALIDATION_ERROR";
            case RESOURCE_NOT_FOUND ->
                "RESOURCE_NOT_FOUND";
            default ->
                base.name();
        };
    }

    /**
     * Format generic exception as structured error response
     */
    public static ErrorResponse formatError(
            Exception exception, String correlationId, boolean includeStackTrace) {
        return new ErrorResponse(
                correlationId,
                toCanonicalName(YappcException.BaseErrorCode.INTERNAL_ERROR),
                exception.getMessage(),
                YappcException.ErrorSeverity.CRITICAL,
                Instant.now(),
                Map.of("exceptionType", exception.getClass().getSimpleName()),
                includeStackTrace ? getStackTraceAsString(exception) : null);
    }

    /**
     * Format error response as JSON string
     */
    public static String formatAsJson(ErrorResponse errorResponse) {
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            // Fallback to simple format if JSON serialization fails
            return String.format(
                    "{\"correlationId\":\"%s\",\"errorCode\":\"%s\",\"message\":\"%s\"}",
                    errorResponse.getCorrelationId(),
                    errorResponse.getErrorCode(),
                    errorResponse.getMessage().replace("\"", "\\\""));
        }
    }

    /**
     * Format error response as human-readable string
     */
    public static String formatAsString(ErrorResponse errorResponse) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error [")
                .append(errorResponse.getErrorCode())
                .append("]: ")
                .append(errorResponse.getMessage())
                .append(" (Correlation ID: ")
                .append(errorResponse.getCorrelationId())
                .append(")");

        if (errorResponse.getSeverity() != null) {
            sb.append(" [Severity: ").append(errorResponse.getSeverity()).append("]");
        }

        if (errorResponse.getContext() != null && !errorResponse.getContext().isEmpty()) {
            sb.append(" [Context: ").append(errorResponse.getContext()).append("]");
        }

        return sb.toString();
    }

    /**
     * Format CLI-friendly error message.
     */
    public static String formatForCli(YappcException exception) {
        StringBuilder sb = new StringBuilder();

        // Severity indicator
        String indicator
                = switch (exception.getSeverity()) {
            case CRITICAL ->
                "🚨";
            case HIGH ->
                "❌";
            case MEDIUM ->
                "⚠️";
            case LOW ->
                "ℹ️";
        };

        // Prefer canonical name when possible (BaseErrorCode -> toCanonicalName), otherwise use
        // enum name for enum-backed ErrorCodes, falling back to getCode().
        String cliErrorCode;
        if (exception.getErrorCode() instanceof YappcException.BaseErrorCode base) {
            cliErrorCode = toCanonicalName(base);
        } else if (exception.getErrorCode() instanceof Enum) {
            cliErrorCode = ((Enum<?>) exception.getErrorCode()).name();
        } else {
            // Attempt to map foreign/legacy codes to canonical names using shared mappers.
            cliErrorCode = tryMapForeignCode(exception.getErrorCode().getCode());
        }

        sb.append(indicator)
                .append(" ")
                .append(cliErrorCode)
                .append(": ")
                .append(exception.getMessage());

        if (!exception.getContext().isEmpty()) {
            sb.append("\n   Context: ").append(exception.getContext());
        }

        sb.append("\n   Correlation ID: ").append(exception.getCorrelationId());

        return sb.toString();
    }

    /**
     * Try mapping a foreign error code string to a canonical ErrorCode name. We
     * try the ingress/dcmaar/kg mappers in order and return the first
     * non-UNKNOWN mapping. If none match we fall back to the original code
     * string to preserve existing behaviour.
     */
    private static String tryMapForeignCode(String code) {
        if (code == null) {
            return ErrorCode.UNKNOWN_ERROR.name();
        }

        ErrorCode mapped = ErrorCodeMappers.fromIngress(code);
        if (mapped != ErrorCode.UNKNOWN_ERROR) {
            return mapped.name();
        }

        mapped = ErrorCodeMappers.fromDcmaar(code);
        if (mapped != ErrorCode.UNKNOWN_ERROR) {
            return mapped.name();
        }

        mapped = ErrorCodeMappers.fromKg(code);
        if (mapped != ErrorCode.UNKNOWN_ERROR) {
            return mapped.name();
        }

        return code;
    }

    /**
     * Format CLI-friendly error message for generic exceptions
     */
    public static String formatForCli(Exception exception, String correlationId) {
        return String.format(
                "🚨 %s: %s\n   Correlation ID: %s",
                YappcException.BaseErrorCode.INTERNAL_ERROR.name(),
                exception.getMessage(),
                correlationId);
    }

    private static String getStackTraceAsString(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = JsonUtils.getDefaultMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
