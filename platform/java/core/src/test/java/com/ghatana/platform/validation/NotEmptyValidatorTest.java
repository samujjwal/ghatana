package com.ghatana.platform.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotEmptyValidator.
 */
class NotEmptyValidatorTest {

    private final NotEmptyValidator validator = NotEmptyValidator.instance();

    @Test
    void testValidateNull() {
        ValidationResult result = validator.validate(null, "field");
        
        assertFalse(result.isValid());
        assertEquals("NOT_EMPTY", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateEmpty() {
        ValidationResult result = validator.validate("", "field");
        
        assertFalse(result.isValid());
        assertEquals("NOT_EMPTY", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateWhitespace() {
        ValidationResult result = validator.validate("   ", "field");
        
        assertFalse(result.isValid());
        assertEquals("NOT_EMPTY", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateNonEmpty() {
        ValidationResult result = validator.validate("value", "field");
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testGetType() {
        assertEquals("NOT_EMPTY", validator.getType());
    }
}
