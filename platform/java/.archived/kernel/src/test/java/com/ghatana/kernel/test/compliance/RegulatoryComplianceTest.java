package com.ghatana.kernel.test.compliance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regulatory Compliance Tests.
 *
 * <p>Disabled pending extension modules: {@code com.ghatana.finance.extension}
 * and {@code com.ghatana.phr.extension} do not exist in kernel scope.
 * Compliance tests should live in product modules.</p>
 *
 * @doc.type test
 * @doc.purpose Regulatory compliance validation tests
 * @doc.layer test
 * @doc.pattern Compliance Test
 */
@Disabled("Requires product extension modules outside kernel scope")
@DisplayName("Regulatory Compliance Tests")
class RegulatoryComplianceTest {

    @Test
    @Disabled
    void placeholder() {
        // All tests disabled — finance/phr extension modules are product concerns
    }
}
