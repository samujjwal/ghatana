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
@DisplayName("Platform Exception Hierarchy — creation and propagation [GH-90000]")
class PlatformExceptionTest {

    // ── ServiceException ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("ServiceException [GH-90000]")
    class ServiceExceptionTests {

        @Test
        @DisplayName("default constructor uses SERVICE_ERROR code [GH-90000]")
        void defaultConstructor_usesServiceErrorCode() { // GH-90000
            ServiceException ex = new ServiceException(); // GH-90000
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ERROR); // GH-90000
            assertThat(ex.getMessage()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("message constructor preserves message [GH-90000]")
        void messageConstructor_preservesMessage() { // GH-90000
            ServiceException ex = new ServiceException("something went wrong [GH-90000]");
            assertThat(ex.getMessage()).contains("something went wrong [GH-90000]");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_ERROR); // GH-90000
        }

        @Test
        @DisplayName("cause constructor chains cause correctly [GH-90000]")
        void causeConstructor_chainsCause() { // GH-90000
            RuntimeException cause = new RuntimeException("root cause [GH-90000]");
            ServiceException ex = new ServiceException("wrapped", cause); // GH-90000
            assertThat(ex.getCause()).isSameAs(cause); // GH-90000
            assertThat(ex.getMessage()).contains("wrapped [GH-90000]");
        }

        @Test
        @DisplayName("forService() factory builds structured exception with metadata [GH-90000]")
        void forService_buildsStructuredExceptionWithMetadata() { // GH-90000
            RuntimeException cause = new RuntimeException("db down [GH-90000]");
            ServiceException ex = ServiceException.forService("UserService", "findById", cause); // GH-90000

            assertThat(ex.getMessage()).contains("UserService [GH-90000]").contains("findById [GH-90000]");
            assertThat(ex.getCause()).isSameAs(cause); // GH-90000
            assertThat(ex.getMetadata()).containsEntry("serviceName", "UserService"); // GH-90000
            assertThat(ex.getMetadata()).containsEntry("operation", "findById"); // GH-90000
        }

        @Test
        @DisplayName("serviceUnavailable() factory sets correct error code [GH-90000]")
        void serviceUnavailable_setsCorrectErrorCode() { // GH-90000
            ServiceException ex = ServiceException.serviceUnavailable("PaymentService [GH-90000]");

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE); // GH-90000
            assertThat(ex.getMessage()).contains("PaymentService [GH-90000]");
            assertThat(ex.getMetadata()).containsEntry("serviceName", "PaymentService"); // GH-90000
        }

        @Test
        @DisplayName("timeout() factory includes timing metadata [GH-90000]")
        void timeout_includesTimingMetadata() { // GH-90000
            ServiceException ex = ServiceException.timeout("CacheService", "get", 5000L); // GH-90000

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_TIMEOUT); // GH-90000
            assertThat(ex.getMessage()).contains("CacheService [GH-90000]").contains("5000 [GH-90000]");
            assertThat(ex.getMetadata()).containsEntry("timeoutMs", 5000L); // GH-90000
        }
    }

    // ── ValidationException ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationException [GH-90000]")
    class ValidationExceptionTests {

        @Test
        @DisplayName("uses VALIDATION_ERROR code [GH-90000]")
        void usesValidationErrorCode() { // GH-90000
            ValidationException ex = new ValidationException("field 'email' is required [GH-90000]");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR); // GH-90000
            assertThat(ex.getMessage()).contains("email [GH-90000]");
        }

        @Test
        @DisplayName("is an instance of BaseException [GH-90000]")
        void isInstanceOfBaseException() { // GH-90000
            ValidationException ex = new ValidationException("invalid [GH-90000]");
            assertThat(ex).isInstanceOf(BaseException.class); // GH-90000
        }
    }

    // ── ResourceNotFoundException ─────────────────────────────────────────────

    @Nested
    @DisplayName("ResourceNotFoundException [GH-90000]")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("uses RESOURCE_NOT_FOUND code [GH-90000]")
        void usesResourceNotFoundCode() { // GH-90000
            ResourceNotFoundException ex = ResourceNotFoundException.forResource("User", "42"); // GH-90000
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND); // GH-90000
            assertThat(ex.getMessage()).contains("User [GH-90000]").contains("42 [GH-90000]");
        }

        @Test
        @DisplayName("HTTP status is 404 [GH-90000]")
        void httpStatusIs404() { // GH-90000
            assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404); // GH-90000
        }
    }

    // ── ConfigurationException ────────────────────────────────────────────────

    @Nested
    @DisplayName("ConfigurationException [GH-90000]")
    class ConfigurationExceptionTests {

        @Test
        @DisplayName("uses CONFIGURATION_ERROR code [GH-90000]")
        void usesConfigurationErrorCode() { // GH-90000
            ConfigurationException ex = new ConfigurationException("missing DB_URL [GH-90000]");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFIGURATION_ERROR); // GH-90000
            assertThat(ex.getMessage()).contains("DB_URL [GH-90000]");
        }

        @Test
        @DisplayName("cause chained when provided [GH-90000]")
        void causeChainedWhenProvided() { // GH-90000
            RuntimeException cause = new RuntimeException("parse failure [GH-90000]");
            ConfigurationException ex = new ConfigurationException("bad config", cause); // GH-90000
            assertThat(ex.getCause()).isSameAs(cause); // GH-90000
        }
    }

    // ── Exception chaining across hierarchy ───────────────────────────────────

    @Nested
    @DisplayName("exception chaining and metadata [GH-90000]")
    class ExceptionChainingTests {

        @Test
        @DisplayName("metadata added via addMetadata() is retrievable [GH-90000]")
        void metadataAddedViaAddMetadata_isRetrievable() { // GH-90000
            ServiceException ex = new ServiceException("test [GH-90000]");
            ex.addMetadata("requestId", "req-123"); // GH-90000
            ex.addMetadata("userId", "user-456"); // GH-90000

            assertThat(ex.getMetadata()) // GH-90000
                    .containsEntry("requestId", "req-123") // GH-90000
                    .containsEntry("userId", "user-456"); // GH-90000
        }

        @Test
        @DisplayName("metadata constructor sets metadata directly [GH-90000]")
        void metadataConstructor_setsMetadataDirectly() { // GH-90000
            Map<String, Object> meta = Map.of("correlationId", "corr-789"); // GH-90000
            ServiceException ex = new ServiceException("with meta", new RuntimeException(), meta); // GH-90000
            assertThat(ex.getMetadata()).containsEntry("correlationId", "corr-789"); // GH-90000
        }

        @Test
        @DisplayName("exception is a runtime exception - need not be declared in throws [GH-90000]")
        void exceptionIsRuntimeException() { // GH-90000
            assertThat(ServiceException.class.getSuperclass().getName()) // GH-90000
                    .contains("Exception [GH-90000]");
        }
    }
}
