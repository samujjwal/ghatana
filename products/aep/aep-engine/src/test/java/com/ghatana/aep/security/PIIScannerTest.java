/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private final PIIScanner scanner = new PIIScanner(); // GH-90000

    @Test
    void shouldDetectEmailAddresses() { // GH-90000
        String text = "Contact us at user@example.com for support";
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        assertEquals(1, result.items().size()); // GH-90000
        assertEquals("email", result.items().get(0).type()); // GH-90000
        assertTrue(result.items().get(0).matchedText().contains("user@example.com"));
    }

    @Test
    void shouldDetectPhoneNumbers() { // GH-90000
        String text = "Call us at 555-123-4567 or (555) 987-6543"; // GH-90000
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("phone")));
    }

    @Test
    void shouldDetectCreditCardNumbers() { // GH-90000
        String text = "Card number: 4111 1111 1111 1111";
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("credit_card")));
    }

    @Test
    void shouldDetectSSN() { // GH-90000
        String text = "SSN: 123-45-6789";
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("ssn")));
    }

    @Test
    void shouldDetectIPAddresses() { // GH-90000
        String text = "Server IP is 192.168.1.1";
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("ip_address")));
    }

    @Test
    void shouldReturnNoPIIForCleanText() { // GH-90000
        String text = "This is a clean message with no sensitive data";
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertFalse(result.hasPII()); // GH-90000
        assertTrue(result.items().isEmpty()); // GH-90000
    }

    @Test
    void shouldHandleEmptyText() { // GH-90000
        PIIScanner.PIIResult result = scanner.scan("");
        
        assertFalse(result.hasPII()); // GH-90000
        assertTrue(result.items().isEmpty()); // GH-90000
    }

    @Test
    void shouldHandleNullText() { // GH-90000
        PIIScanner.PIIResult result = scanner.scan(null); // GH-90000
        
        assertFalse(result.hasPII()); // GH-90000
        assertTrue(result.items().isEmpty()); // GH-90000
    }

    @Test
    void shouldScanMapForPII() { // GH-90000
        Map<String, Object> data = Map.of( // GH-90000
            "email", "user@example.com",
            "message", "Call 555-123-4567 for help",
            "safe", "This is safe text"
        );
        
        PIIScanner.PIIResult result = scanner.scanMap(data); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("email")));
        assertTrue(result.items().stream().anyMatch(item -> item.type().equals("phone")));
    }

    @Test
    void shouldDetectMultiplePIITypesInSingleText() { // GH-90000
        String text = "Email: user@example.com, Phone: 555-123-4567, IP: 192.168.1.1";
        PIIScanner.PIIResult result = scanner.scan(text); // GH-90000
        
        assertTrue(result.hasPII()); // GH-90000
        long distinctTypes = result.items().stream() // GH-90000
            .map(PIIScanner.PIIItem::type) // GH-90000
            .distinct() // GH-90000
            .count(); // GH-90000
        assertTrue(distinctTypes >= 3); // GH-90000
    }
}
