/**
 * @doc.type test
 * @doc.purpose Tests for standardized error handling
 * @doc.layer platform
 */
package com.ghatana.media.error;

import com.ghatana.platform.core.exception.*;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Tests for CONS-006: Exception hierarchy consistency and AV-004: Error handling standardization
 */
class ErrorHandlerTest {

    @Test
    @DisplayName("Should classify timeout exceptions as retryable [GH-90000]")
    void testTimeoutRetryable() { // GH-90000
        assertTrue(ErrorHandler.isRetryable(new SocketTimeoutException("Connection timed out [GH-90000]")));
        assertTrue(ErrorHandler.isRetryable(new java.util.concurrent.TimeoutException("Operation timeout [GH-90000]")));
    }

    @Test
    @DisplayName("Should classify connect exceptions as retryable [GH-90000]")
    void testConnectRetryable() { // GH-90000
        assertTrue(ErrorHandler.isRetryable(new ConnectException("Connection refused [GH-90000]")));
    }

    @Test
    @DisplayName("Should classify validation exceptions as non-retryable [GH-90000]")
    void testValidationNonRetryable() { // GH-90000
        assertFalse(ErrorHandler.isRetryable(new ValidationException("Invalid input [GH-90000]")));
    }

    @Test
    @DisplayName("Should classify security exceptions as non-retryable [GH-90000]")
    void testSecurityNonRetryable() { // GH-90000
        assertFalse(ErrorHandler.isRetryable(new UnauthorizedException("Access denied [GH-90000]")));
    }

    @Test
    @DisplayName("Should get correct error category [GH-90000]")
    void testErrorCategories() { // GH-90000
        assertEquals("VALIDATION", ErrorHandler.getErrorCategory(new ValidationException("Invalid [GH-90000]")));
        assertEquals("NOT_FOUND", ErrorHandler.getErrorCategory(new ResourceNotFoundException("Not found [GH-90000]")));
        assertEquals("SECURITY", ErrorHandler.getErrorCategory(new UnauthorizedException("Denied [GH-90000]")));
        assertEquals("TIMEOUT", ErrorHandler.getErrorCategory(new SocketTimeoutException("Timeout [GH-90000]")));
        assertEquals("NETWORK", ErrorHandler.getErrorCategory(new ConnectException("Failed [GH-90000]")));
        assertEquals("UNKNOWN", ErrorHandler.getErrorCategory(new RuntimeException("Unknown [GH-90000]")));
    }

    @Test
    @DisplayName("Should translate exceptions to platform standards [GH-90000]")
    void testExceptionTranslation() { // GH-90000
        // IllegalArgumentException -> ValidationException
        BaseException translated = ErrorHandler.translate( // GH-90000
            new IllegalArgumentException("Bad argument [GH-90000]"), "test context"
        );
        assertInstanceOf(ValidationException.class, translated); // GH-90000
        assertTrue(translated.getMessage().contains("test context [GH-90000]"));

        // SocketTimeoutException -> TimeoutException
        translated = ErrorHandler.translate( // GH-90000
            new SocketTimeoutException("Connection timeout [GH-90000]"), "network call"
        );
        assertInstanceOf(TimeoutException.class, translated); // GH-90000

        // ConnectException -> ServiceUnavailableException
        translated = ErrorHandler.translate( // GH-90000
            new ConnectException("Connection refused [GH-90000]"), "service call"
        );
        assertInstanceOf(ServiceUnavailableException.class, translated); // GH-90000
    }

    @Test
    @DisplayName("Should pass through BaseException without translation [GH-90000]")
    void testBaseExceptionPassthrough() { // GH-90000
        ValidationException original = new ValidationException("Original [GH-90000]");
        BaseException translated = ErrorHandler.translate(original, "context"); // GH-90000
        assertSame(original, translated); // GH-90000
    }

    @Test
    @DisplayName("Should execute operation with handling [GH-90000]")
    void testExecuteWithHandling() { // GH-90000
        // Success case
        String result = ErrorHandler.executeWithHandling(() -> "success", "operation"); // GH-90000
        assertEquals("success", result); // GH-90000

        // Failure case
        BaseException exception = assertThrows(BaseException.class, () -> { // GH-90000
            ErrorHandler.executeWithHandling(() -> { // GH-90000
                throw new IllegalArgumentException("Bad input [GH-90000]");
            }, "test operation");
        });
        assertInstanceOf(ValidationException.class, exception); // GH-90000
    }

    @Test
    @DisplayName("Should execute with fallback on failure [GH-90000]")
    void testExecuteWithFallback() { // GH-90000
        String result = ErrorHandler.executeWithFallback( // GH-90000
            () -> { throw new RuntimeException("Primary failed [GH-90000]"); },
            () -> "fallback result", // GH-90000
            "primary operation"
        );
        assertEquals("fallback result", result); // GH-90000

        // Success case - fallback not used
        result = ErrorHandler.executeWithFallback( // GH-90000
            () -> "primary result", // GH-90000
            () -> "fallback result", // GH-90000
            "primary operation"
        );
        assertEquals("primary result", result); // GH-90000
    }

    @Test
    @DisplayName("Should wrap checked exceptions as runtime [GH-90000]")
    void testWrapAsRuntime() { // GH-90000
        Exception checked = new Exception("Checked exception [GH-90000]");
        RuntimeException runtime = ErrorHandler.wrapAsRuntime(checked); // GH-90000
        assertEquals(checked.getMessage(), runtime.getMessage()); // GH-90000
        assertSame(checked, runtime.getCause()); // GH-90000

        // Already runtime - should return same
        RuntimeException alreadyRuntime = new IllegalArgumentException("Already runtime [GH-90000]");
        RuntimeException result = ErrorHandler.wrapAsRuntime(alreadyRuntime); // GH-90000
        assertSame(alreadyRuntime, result); // GH-90000
    }

    @Test
    @DisplayName("Should provide user-friendly messages [GH-90000]")
    void testUserMessages() { // GH-90000
        assertTrue(ErrorHandler.getUserMessage(new ValidationException("Invalid [GH-90000]"))
            .contains("Invalid input [GH-90000]"));
        assertTrue(ErrorHandler.getUserMessage(new ResourceNotFoundException("Not found [GH-90000]"))
            .contains("Resource not found [GH-90000]"));
        assertTrue(ErrorHandler.getUserMessage(new TimeoutException("Timeout [GH-90000]"))
            .contains("timed out [GH-90000]"));
        assertTrue(ErrorHandler.getUserMessage(new ServiceUnavailableException("Down [GH-90000]"))
            .contains("temporarily unavailable [GH-90000]"));
        assertTrue(ErrorHandler.getUserMessage(new UnauthorizedException("Denied [GH-90000]"))
            .contains("Access denied [GH-90000]"));
    }

    @Test
    @DisplayName("Should determine correct severity levels [GH-90000]")
    void testSeverityLevels() { // GH-90000
        assertEquals(ErrorHandler.Severity.CRITICAL, // GH-90000
            ErrorHandler.getSeverity(new InternalException("Critical [GH-90000]")));
        assertEquals(ErrorHandler.Severity.HIGH, // GH-90000
            ErrorHandler.getSeverity(new ServiceUnavailableException("Down [GH-90000]")));
        assertEquals(ErrorHandler.Severity.MEDIUM, // GH-90000
            ErrorHandler.getSeverity(new TimeoutException("Timeout [GH-90000]")));
        assertEquals(ErrorHandler.Severity.LOW, // GH-90000
            ErrorHandler.getSeverity(new ValidationException("Invalid [GH-90000]")));
        assertEquals(ErrorHandler.Severity.LOW, // GH-90000
            ErrorHandler.getSeverity(new ResourceNotFoundException("Not found [GH-90000]")));
    }

    @Test
    @DisplayName("Should check nested causes for retryable [GH-90000]")
    void testNestedCauseRetryable() { // GH-90000
        // Wrap a retryable exception
        Exception wrapped = new RuntimeException("Wrapper", // GH-90000
            new SocketTimeoutException("Inner timeout [GH-90000]"));
        assertTrue(ErrorHandler.isRetryable(wrapped)); // GH-90000

        // Deeply nested
        Exception deep = new RuntimeException("Level 1", // GH-90000
            new RuntimeException("Level 2", // GH-90000
                new ConnectException("Level 3 [GH-90000]")));
        assertTrue(ErrorHandler.isRetryable(deep)); // GH-90000
    }
}
