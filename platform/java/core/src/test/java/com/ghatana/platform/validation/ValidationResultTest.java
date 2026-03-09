package com.ghatana.platform.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationResult.
 */
class ValidationResultTest {

    @Test
    void testSuccess() {
        ValidationResult result = ValidationResult.success();
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testFailureWithSingleError() {
        ValidationError error = new ValidationError("CODE", "Message");
        ValidationResult result = ValidationResult.failure(error);
        
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("CODE", result.getErrors().get(0).getCode());
    }

    @Test
    void testFailureWithMultipleErrors() {
        ValidationError error1 = new ValidationError("CODE1", "Message 1");
        ValidationError error2 = new ValidationError("CODE2", "Message 2");
        ValidationResult result = ValidationResult.failure(error1, error2);
        
        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size());
    }

    @Test
    void testFailureWithList() {
        List<ValidationError> errors = List.of(
            new ValidationError("CODE1", "Message 1"),
            new ValidationError("CODE2", "Message 2")
        );
        ValidationResult result = ValidationResult.failure(errors);
        
        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size());
    }

    @Test
    void testToString() {
        ValidationResult result = ValidationResult.success();
        assertTrue(result.toString().contains("valid=true"));
        
        ValidationResult failure = ValidationResult.failure(new ValidationError("CODE", "Message"));
        assertTrue(failure.toString().contains("valid=false"));
    }
}
