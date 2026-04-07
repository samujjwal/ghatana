package com.ghatana.products.finance.shell;

import com.ghatana.kernel.context.KernelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance product shell validates request identifiers and payload presence at the API boundary
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
class FinanceProductShellTest {

    @Mock
    private KernelContext context;

    private FinanceProductShell shell;

    @BeforeEach
    void setUp() {
        shell = new FinanceProductShell(context);
    }

    @Test
    void rejectsTradeExecutionBeforeStart() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> shell.handleTradeExecution("advisor-1", new Object())
        );

        assertEquals("Finance product shell not started", exception.getMessage());
    }

    @Test
    void rejectsUnsafeUserIdentifierAtApiBoundary() {
        shell.start();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shell.handleTradeExecution("../advisor", new Object())
        );

        assertEquals("userId must contain only safe identifier characters", exception.getMessage());
    }

    @Test
    void rejectsMissingTradePayloadAtApiBoundary() {
        shell.start();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shell.handleTradeExecution("advisor-1", null)
        );

        assertEquals("tradeRequest is required", exception.getMessage());
    }

    @Test
    void rejectsMissingPortfolioPayloadAtApiBoundary() {
        shell.start();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shell.handlePortfolioManagement("advisor-1", null)
        );

        assertEquals("portfolioRequest is required", exception.getMessage());
    }
}