package com.ghatana.platform.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotNullValidator.
 */
class NotNullValidatorTest {

    private final NotNullValidator validator = NotNullValidator.instance();

    @Test
    void testValidateNull() {
        ValidationResult result = validator.validate(null, "field");
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("NOT_NULL", result.getErrors().get(0).getCode());
    }

    @Test
    void testValidateNonNull() {
        ValidationResult result = validator.validate("value", "field");
        
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testGetType() {
        assertEquals("NOT_NULL", validator.getType());
    }

    @Test
    void testSingleton() {
        NotNullValidator instance1 = NotNullValidator.instance();
        NotNullValidator instance2 = NotNullValidator.instance();
        
        assertSame(instance1, instance2);
    }
}
