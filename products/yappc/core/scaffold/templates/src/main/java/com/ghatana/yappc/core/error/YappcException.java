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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base exception for all YAPPC-specific errors with correlation ID and
 * contextual information.
 *
 * <p>
 * Provides a unified exception hierarchy with consistent error handling,
 * correlation ID support, and structured error information for proper
 * observability and debugging.
 *
 * <p>
 * Week 10 Day 50: Phase 4 architectural improvements - Error Handling Strategy
 *
 * @doc.type class
 * @doc.purpose Base exception for all YAPPC-specific errors with correlation ID
 * and contextual information.
 * @doc.layer platform
 * @doc.pattern Component
 */
public abstract class YappcException extends Exception {

    private final String correlationId;
    private final YappcErrorCode errorCode;
    private final Map<String, Object> context;
    private final Instant timestamp;
    private final ErrorSeverity severity;

    protected YappcException(YappcErrorCode errorCode, String message, ErrorSeverity severity) {
        this(errorCode, message, severity, null, Map.of());
    }

    protected YappcException(
            YappcErrorCode errorCode, String message, ErrorSeverity severity, Throwable cause) {
        this(errorCode, message, severity, cause, Map.of());
    }

    protected YappcException(
            YappcErrorCode errorCode,
            String message,
            ErrorSeverity severity,
            Throwable cause,
            Map<String, Object> context) {
        super(message, cause);
        this.correlationId = generateCorrelationId();
        this.errorCode = errorCode;
        this.severity = severity;
        this.context = Map.copyOf(context);
        this.timestamp = Instant.now();
    }

    protected YappcException(
            YappcErrorCode errorCode,
            String message,
            ErrorSeverity severity,
            Throwable cause,
            Map<String, Object> context,
            String correlationId) {
        super(message, cause);
        this.correlationId = correlationId != null ? correlationId : generateCorrelationId();
        this.errorCode = errorCode;
        this.severity = severity;
        this.context = Map.copyOf(context);
        this.timestamp = Instant.now();
    }

    private static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public YappcErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public ErrorSeverity getSeverity() {
        return severity;
    }

    public String getFormattedMessage() {
        return String.format(
                "[%s] [%s] %s (Correlation ID: %s)",
                errorCode.getCode(), severity, getMessage(), correlationId);
    }

    /**
     * Structured error codes for consistent error identification
     */
    public interface YappcErrorCode {

        String getCode();

        String getDescription();

        ErrorSeverity getDefaultSeverity();
    }

    /**
     * Error severity levels for prioritization and alerting
     */
    public enum ErrorSeverity {
        LOW, // Informational, doesn't affect functionality
        MEDIUM, // Affects some functionality, workaround available
        HIGH, // Significant impact, requires attention
        CRITICAL  // System-level failure, immediate action required
    }

    /**
     * Context information for detailed error diagnosis
     */
    public static class ErrorContext {

        private final Map<String, Object> metadata;
        private final String componentId;
        private final String operationName;

        public ErrorContext(String componentId, String operationName, Map<String, Object> metadata) {
            this.componentId = componentId;
            this.operationName = operationName;
            this.metadata = Map.copyOf(metadata);
        }

        public String getComponentId() {
            return componentId;
        }

        public String getOperationName() {
            return operationName;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private String componentId;
            private String operationName;
            private Map<String, Object> metadata = Map.of();

            public Builder componentId(String componentId) {
                this.componentId = componentId;
                return this;
            }

            public Builder operationName(String operationName) {
                this.operationName = operationName;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public ErrorContext build() {
                return new ErrorContext(componentId, operationName, metadata);
            }
        }
    }

    /**
     * Base error codes for common categories
     */
    public enum BaseErrorCode implements YappcErrorCode {
        // Configuration errors
        CONFIG_VALIDATION_FAILED("CFG001", "Configuration validation failed", ErrorSeverity.HIGH),
        CONFIG_LOAD_FAILED("CFG002", "Configuration loading failed", ErrorSeverity.HIGH),
        CONFIG_RELOAD_FAILED("CFG003", "Configuration hot reload failed", ErrorSeverity.MEDIUM),
        // Build system errors
        BUILD_GENERATION_FAILED(
                "BLD001", "Build configuration generation failed", ErrorSeverity.HIGH),
        BUILD_VALIDATION_FAILED(
                "BLD002", "Build specification validation failed", ErrorSeverity.MEDIUM),
        BUILD_TOOL_NOT_SUPPORTED("BLD003", "Build tool not supported", ErrorSeverity.MEDIUM),
        // Cache errors
        CACHE_ACCESS_FAILED("CCH001", "Cache access failed", ErrorSeverity.MEDIUM),
        CACHE_EVICTION_FAILED("CCH002", "Cache eviction failed", ErrorSeverity.LOW),
        // Telemetry errors
        TELEMETRY_COLLECTION_FAILED("TEL001", "Telemetry collection failed", ErrorSeverity.LOW),
        TELEMETRY_EXPORT_FAILED("TEL002", "Telemetry export failed", ErrorSeverity.LOW),
        // Template errors
        TEMPLATE_PROCESSING_FAILED("TPL001", "Template processing failed", ErrorSeverity.HIGH),
        TEMPLATE_NOT_FOUND("TPL002", "Template not found", ErrorSeverity.HIGH),
        // Dependency injection errors
        INJECTION_FAILED("DI001", "Dependency injection failed", ErrorSeverity.CRITICAL),
        BEAN_CREATION_FAILED("DI002", "Bean creation failed", ErrorSeverity.HIGH),
        // General system errors
        INTERNAL_ERROR("SYS001", "Internal system error", ErrorSeverity.CRITICAL),
        RESOURCE_NOT_FOUND("SYS002", "Resource not found", ErrorSeverity.MEDIUM),
        INVALID_OPERATION("SYS003", "Invalid operation", ErrorSeverity.MEDIUM),
        VALIDATION_FAILED("SYS004", "Validation failed", ErrorSeverity.MEDIUM);

        private final String code;
        private final String description;
        private final ErrorSeverity defaultSeverity;

        BaseErrorCode(String code, String description, ErrorSeverity defaultSeverity) {
            this.code = code;
            this.description = description;
            this.defaultSeverity = defaultSeverity;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public ErrorSeverity getDefaultSeverity() {
            return defaultSeverity;
        }
    }
}
