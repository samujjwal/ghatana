package com.ghatana.platform.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationError.
 */
class ValidationErrorTest {

    @Test
    void testConstructorWithCodeAndMessage() {
        ValidationError error = new ValidationError("CODE", "Message");
        
        assertEquals("CODE", error.getCode());
        assertEquals("Message", error.getMessage());
        assertNull(error.getPath());
        assertNull(error.getRejectedValue());
    }

    @Test
    void testConstructorWithAllFields() {
        ValidationError error = new ValidationError("CODE", "Message", "/field", "value");
        
        assertEquals("CODE", error.getCode());
        assertEquals("Message", error.getMessage());
        assertEquals("/field", error.getPath());
        assertEquals("value", error.getRejectedValue());
    }

    @Test
    void testToString() {
        ValidationError error = new ValidationError("CODE", "Message", "/field", null);
        String str = error.toString();
        
        assertTrue(str.contains("CODE"));
        assertTrue(str.contains("Message"));
        assertTrue(str.contains("/field"));
    }
}
