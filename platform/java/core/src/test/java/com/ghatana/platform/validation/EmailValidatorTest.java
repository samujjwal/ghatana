package com.ghatana.platform.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailValidator.
 */
class EmailValidatorTest {

    private final EmailValidator validator = EmailValidator.instance();

    @Test
    void testValidateNull() {
        ValidationResult result = validator.validate(null, "email");
        
        assertFalse(result.isValid());
        assertEquals("NOT_NULL", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateEmpty() {
        ValidationResult result = validator.validate("", "email");
        
        assertFalse(result.isValid());
        assertEquals("NOT_EMPTY", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateInvalidEmail() {
        ValidationResult result = validator.validate("not-an-email", "email");
        
        assertFalse(result.isValid());
        assertEquals("INVALID_EMAIL", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateValidEmail() {
        ValidationResult result = validator.validate("test@example.com", "email");
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testValidateValidEmailWithDots() {
        ValidationResult result = validator.validate("user.name@example.co.uk", "email");
        
        assertTrue(result.isValid());
    }

    @Test
    void testGetType() {
        assertEquals("EMAIL", validator.getType());
    }
}
