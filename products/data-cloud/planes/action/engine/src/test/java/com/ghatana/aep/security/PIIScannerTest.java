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
 * F-031: Verify PII enforcement policy defaults to BLOCK (fail-closed).
 */
class PIIScannerTest {

    private final PIIScanner scanner = new PIIScanner(); 

    // -------------------------------------------------------------------------
    // F-031: PiiEnforcementPolicy.resolve() defaults to BLOCK
    // -------------------------------------------------------------------------

    @Test
    void resolveDefaultsToBlockWhenEnvVarIsAbsent() {
        // Calls the testable overload with null (simulates an unset env var).
        assertEquals(PIIScanner.PiiEnforcementPolicy.BLOCK,
            PIIScanner.PiiEnforcementPolicy.resolve(null),
            "BLOCK must be the fail-closed default when AEP_PII_ENFORCEMENT is absent");
    }

    @Test
    void resolveDefaultsToBlockWhenEnvVarIsBlank() {
        assertEquals(PIIScanner.PiiEnforcementPolicy.BLOCK,
            PIIScanner.PiiEnforcementPolicy.resolve("   "),
            "BLOCK must be the fail-closed default when AEP_PII_ENFORCEMENT is blank");
    }

    @Test
    void resolveReturnsBlockForUnrecognisedValue() {
        assertEquals(PIIScanner.PiiEnforcementPolicy.BLOCK,
            PIIScanner.PiiEnforcementPolicy.resolve("UNKNOWN_POLICY"),
            "Unrecognised values must fall back to BLOCK, not throw");
    }

    @Test
    void resolveRecognisesExplicitBlockValue() {
        assertEquals(PIIScanner.PiiEnforcementPolicy.BLOCK,
            PIIScanner.PiiEnforcementPolicy.resolve("BLOCK"));
    }

    @Test
    void resolveRecognisesExplicitBlockValueCaseInsensitive() {
        assertEquals(PIIScanner.PiiEnforcementPolicy.BLOCK,
            PIIScanner.PiiEnforcementPolicy.resolve("block"));
    }

    @Test
    void resolveRecognisesExplicitLogValue() {
        assertEquals(PIIScanner.PiiEnforcementPolicy.LOG,
            PIIScanner.PiiEnforcementPolicy.resolve("LOG"));
    }

    @Test
    void resolveRecognisesExplicitRedactValue() {
        assertEquals(PIIScanner.PiiEnforcementPolicy.REDACT,
            PIIScanner.PiiEnforcementPolicy.resolve("REDACT"));
    }

    @Test
    void resolveThrowsForUnknownPolicyName() {
        // valueOf with an unrecognised name should throw IllegalArgumentException —
        // the resolve() method catches this and falls back to BLOCK.
        assertThrows(IllegalArgumentException.class,
            () -> PIIScanner.PiiEnforcementPolicy.valueOf("UNKNOWN_POLICY"));
    }

    // -------------------------------------------------------------------------
    // Detection tests
    // -------------------------------------------------------------------------

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

