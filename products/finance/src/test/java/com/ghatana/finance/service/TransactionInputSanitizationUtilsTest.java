package com.ghatana.finance.service;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance transaction boundary validation and sanitization helpers
 * @doc.layer product
 * @doc.pattern Test
 */
class TransactionInputSanitizationUtilsTest {

    @Test
    void acceptsSafeIdentifier() {
        assertEquals("txn-001", TransactionInputSanitizationUtils.requireSafeIdentifier(" txn-001 ", "id"));
    }

    @Test
    void rejectsUnsafeCode() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionInputSanitizationUtils.requireSafeCode("<script>", "currency")
        );
    }

    @Test
    void enforcesAllowedValues() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionInputSanitizationUtils.requireAllowedValue("UNKNOWN", "status", Set.of("PENDING"))
        );
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionInputSanitizationUtils.requirePositiveAmount(0, "amount")
        );
    }
}