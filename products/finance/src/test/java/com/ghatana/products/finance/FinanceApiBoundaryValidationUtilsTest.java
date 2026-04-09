package com.ghatana.products.finance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance product API boundary validation helpers reject unsafe identifiers and missing payloads
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceApiBoundaryValidationUtilsTest {

    @Test
    void acceptsSafePrincipalIdentifier() {
        assertEquals("advisor-01", FinanceApiBoundaryValidationUtils.requirePrincipalId(" advisor-01 ", "userId"));
    }

    @Test
    void rejectsUnsafeResourceIdentifier() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FinanceApiBoundaryValidationUtils.requireResourceId("../trade", "tradeId")
        );
    }

    @Test
    void rejectsMissingPayload() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FinanceApiBoundaryValidationUtils.requirePayload(null, "tradeRequest")
        );
    }

    @Test
    void returnsProvidedPayload() {
        Object payload = new Object();

        assertSame(payload, FinanceApiBoundaryValidationUtils.requirePayload(payload, "portfolioRequest"));
    }
}
