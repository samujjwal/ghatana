/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * Unit tests for {@link PIIScanner}.
 *
 * P3-18: Verify PII detection patterns work correctly.
 */
class PIIScannerTest {

    private final PIIScanner scanner = new PIIScanner();

    @Test
    void shouldDetectEmailAddresses() {
        String text = "Contact us at user@example.com for support";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertTrue(result.hasPII());
        assertEquals(1, result.items().size());
        assertEquals("email", result.items().get(0).type());
        assertTrue(result.items().get(0).matchedText().contains("user@example.com"));
    }

    @Test
    void shouldDetectPhoneNumbers() {
        String text = "Call us at 555-123-4567 or (555) 987-6543";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertTrue(result.hasPII());
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("phone")));
    }

    @Test
    void shouldDetectCreditCardNumbers() {
        String text = "Card number: 4111 1111 1111 1111";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertTrue(result.hasPII());
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("credit_card")));
    }

    @Test
    void shouldDetectSSN() {
        String text = "SSN: 123-45-6789";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertTrue(result.hasPII());
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("ssn")));
    }

    @Test
    void shouldDetectIPAddresses() {
        String text = "Server IP is 192.168.1.1";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertTrue(result.hasPII());
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("ip_address")));
    }

    @Test
    void shouldReturnNoPIIForCleanText() {
        String text = "This is a clean message with no sensitive data";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertFalse(result.hasPII());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void shouldHandleEmptyText() {
        PIIScanner.PIIResult result = scanner.scan("");
        
        assertFalse(result.hasPII());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void shouldHandleNullText() {
        PIIScanner.PIIResult result = scanner.scan(null);
        
        assertFalse(result.hasPII());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void shouldScanMapForPII() {
        Map<String, Object> data = Map.of(
            "email", "user@example.com",
            "message", "Call 555-123-4567 for help",
            "safe", "This is safe text"
        );
        
        PIIScanner.PIIResult result = scanner.scanMap(data);
        
        assertTrue(result.hasPII());
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("email")));
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("phone")));
    }

    @Test
    void shouldDetectMultiplePIITypesInSingleText() {
        String text = "Email: user@example.com, Phone: 555-123-4567, IP: 192.168.1.1";
        PIIScanner.PIIResult result = scanner.scan(text);
        
        assertTrue(result.hasPII());
        long distinctTypes = result.items().stream()
            .map(PIIScanner.PIIItem::type)
            .distinct()
            .count();
        assertTrue(distinctTypes >= 3);
    }
}
