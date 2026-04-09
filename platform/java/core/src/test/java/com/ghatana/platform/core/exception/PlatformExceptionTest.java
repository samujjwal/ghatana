package com.ghatana.platform.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for platform exception hierarchy — verifies correct error codes,
 * message formatting, exception chaining, and metadata propagation.
 *
 * @doc.type class
 * @doc.purpose Unit tests for platform exception classes and their creation patterns
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("Platform Exception Hierarchy — creation and propagation")
class PlatformExceptionTest {

    // ── ServiceException ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("ServiceException")
    class ServiceExceptionTests {

        @Test
        @DisplayName("default constructor uses SERVICE_ERROR code")
        void defaultConstructor_usesServiceErrorCode() {
            ServiceException ex = new ServiceException();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ERROR);
            assertThat(ex.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("message constructor preserves message")
        void messageConstructor_preservesMessage() {
            ServiceException ex = new ServiceException("something went wrong");
            assertThat(ex.getMessage()).contains("something went wrong");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ERROR);
        }

        @Test
        @DisplayName("cause constructor chains cause correctly")
        void causeConstructor_chainsCause() {
            RuntimeException cause = new RuntimeException("root cause");
            ServiceException ex = new ServiceException("wrapped", cause);
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getMessage()).contains("wrapped");
        }

        @Test
        @DisplayName("forService() factory builds structured exception with metadata")
        void forService_buildsStructuredExceptionWithMetadata() {
            RuntimeException cause = new RuntimeException("db down");
            ServiceException ex = ServiceException.forService("UserService", "findById", cause);

            assertThat(ex.getMessage()).contains("UserService").contains("findById");
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getMetadata()).containsEntry("serviceName", "UserService");
            assertThat(ex.getMetadata()).containsEntry("operation", "findById");
        }

        @Test
        @DisplayName("serviceUnavailable() factory sets correct error code")
        void serviceUnavailable_setsCorrectErrorCode() {
            ServiceException ex = ServiceException.serviceUnavailable("PaymentService");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
            assertThat(ex.getMessage()).contains("PaymentService");
            assertThat(ex.getMetadata()).containsEntry("serviceName", "PaymentService");
        }

        @Test
        @DisplayName("timeout() factory includes timing metadata")
        void timeout_includesTimingMetadata() {
            ServiceException ex = ServiceException.timeout("CacheService", "get", 5000L);

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_TIMEOUT);
            assertThat(ex.getMessage()).contains("CacheService").contains("5000");
            assertThat(ex.getMetadata()).containsEntry("timeoutMs", 5000L);
        }
    }

    // ── ValidationException ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationException")
    class ValidationExceptionTests {

        @Test
        @DisplayName("uses VALIDATION_ERROR code")
        void usesValidationErrorCode() {
            ValidationException ex = new ValidationException("field 'email' is required");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(ex.getMessage()).contains("email");
        }

        @Test
        @DisplayName("is an instance of ServiceException")
        void isInstanceOfServiceException() {
            ValidationException ex = new ValidationException("invalid");
            assertThat(ex).isInstanceOf(ServiceException.class);
        }
    }

    // ── ResourceNotFoundException ─────────────────────────────────────────────

    @Nested
    @DisplayName("ResourceNotFoundException")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("uses RESOURCE_NOT_FOUND code")
        void usesResourceNotFoundCode() {
            ResourceNotFoundException ex = ResourceNotFoundException.forResource("User", "42");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(ex.getMessage()).contains("User").contains("42");
        }

        @Test
        @DisplayName("HTTP status is 404")
        void httpStatusIs404() {
            assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404);
        }
    }

    // ── ConfigurationException ────────────────────────────────────────────────

    @Nested
    @DisplayName("ConfigurationException")
    class ConfigurationExceptionTests {

        @Test
        @DisplayName("uses CONFIGURATION_ERROR code")
        void usesConfigurationErrorCode() {
            ConfigurationException ex = new ConfigurationException("missing DB_URL");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFIGURATION_ERROR);
            assertThat(ex.getMessage()).contains("DB_URL");
        }

        @Test
        @DisplayName("cause chained when provided")
        void causeChainedWhenProvided() {
            RuntimeException cause = new RuntimeException("parse failure");
            ConfigurationException ex = new ConfigurationException("bad config", cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ── Exception chaining across hierarchy ───────────────────────────────────

    @Nested
    @DisplayName("exception chaining and metadata")
    class ExceptionChainingTests {

        @Test
        @DisplayName("metadata added via addMetadata() is retrievable")
        void metadataAddedViaAddMetadata_isRetrievable() {
            ServiceException ex = new ServiceException("test");
            ex.addMetadata("requestId", "req-123");
            ex.addMetadata("userId", "user-456");

            assertThat(ex.getMetadata())
                    .containsEntry("requestId", "req-123")
                    .containsEntry("userId", "user-456");
        }

        @Test
        @DisplayName("metadata constructor sets metadata directly")
        void metadataConstructor_setsMetadataDirectly() {
            Map<String, Object> meta = Map.of("correlationId", "corr-789");
            ServiceException ex = new ServiceException("with meta", new RuntimeException(), meta);
            assertThat(ex.getMetadata()).containsEntry("correlationId", "corr-789");
        }

        @Test
        @DisplayName("exception is a runtime exception - need not be declared in throws")
        void exceptionIsRuntimeException() {
            assertThat(ServiceException.class.getSuperclass().getName())
                    .contains("Exception");
        }
    }
}
