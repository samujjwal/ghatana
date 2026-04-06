package com.ghatana.kernel.test.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PHR-Finance Integration Tests.
 *
 * <p>Disabled pending product modules: {@code com.ghatana.phr.kernel} and
 * {@code com.ghatana.finance.kernel} do not exist in kernel scope.
 * Product-specific modules should not be kernel dependencies.</p>
 *
 * @doc.type test
 * @doc.purpose Integration tests for PHR-Finance cross-product workflows
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@Disabled("Requires product modules outside kernel scope")
@DisplayName("PHR-Finance Integration Tests")
class PhrFinanceIntegrationTest {

    @Test
    @Disabled
    void placeholder() {
        // All tests disabled — phr/finance kernel modules are product concerns
    }
}
