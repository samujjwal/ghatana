package com.ghatana.finance.contracts;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance contract runner output and validation flow without invoking System.exit in tests
 * @doc.layer product
 * @doc.pattern Test
 */
class ContractValidationRunnerTest {

    @Test
    void returnsSuccessAndPrintsSummaryWhenContractsAreValid() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = ContractValidationRunner.run(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exitCode);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Validating 7 Finance contracts"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("All contracts validated successfully"));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }
}
