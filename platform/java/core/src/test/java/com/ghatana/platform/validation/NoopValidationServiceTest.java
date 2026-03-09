package com.ghatana.platform.validation;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NoopValidationService.
 */
class NoopValidationServiceTest extends EventloopTestBase {

    private final NoopValidationService service = new NoopValidationService();

    @Test
    void testValidateEvent() {
        ValidationResult result = runPromise(() -> service.validateEvent(new Object()));
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testValidatePayload() {
        ValidationResult result = runPromise(() -> service.validatePayload("type", "{}"));
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testValidateSchema() {
        ValidationResult result = runPromise(() -> service.validateSchema("{}"));
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testCompileSchema() {
        String result = runPromise(() -> service.compileSchema("{}"));
        
        assertEquals("noop", result);
    }
}
