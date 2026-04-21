package com.ghatana.products.finance.shell;

import com.ghatana.finance.kernel.service.PortfolioManagementService;
import com.ghatana.finance.service.Transaction;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.finance.service.TransactionService;
import com.ghatana.kernel.context.KernelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    @Mock
    private TransactionService transactionService;
    @Mock
    private PortfolioManagementService portfolioManagementService;

    private FinanceProductShell shell;

    @BeforeEach
    void setUp() {
        shell = new FinanceProductShell(context, transactionService, portfolioManagementService);
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

    @Test
    void usesTransactionServiceForTradeExecutionWhenAvailable() {
        shell.start();
        
        Transaction tx = new Transaction();
        tx.setId("txn-123");
        tx.setTenantId("tenant-1");
        tx.setAmount(100.0);
        
        TransactionResult result = TransactionResult.approved();
        
        when(transactionService.processTransaction(any(Transaction.class))).thenReturn(result);
        
        var executionResult = shell.handleTradeExecution("advisor-1", tx);
        
        assertTrue(executionResult != null);
    }

    @Test
    void usesPortfolioManagementServiceForPortfolioManagementWhenAvailable() {
        shell.start();
        
        when(portfolioManagementService.createPortfolio(any())).thenAnswer(invocation -> {
            PortfolioManagementService.PortfolioRequest request = invocation.getArgument(0);
            return io.activej.promise.Promise.of(new PortfolioManagementService.Portfolio(
                "pf-123", request.getAccountId(), request.getName(), request.getDescription(),
                request.getCurrency(), java.math.BigDecimal.ZERO, "ACTIVE",
                java.time.Instant.now(), java.time.Instant.now()
            ));
        });
        
        var portfolioResult = shell.handlePortfolioManagement("advisor-1", new Object());
        
        assertTrue(portfolioResult != null);
    }

    @Test
    void throwsErrorWhenTransactionServiceUnavailable() {
        shell = new FinanceProductShell(context, null, portfolioManagementService);
        shell.start();
        
        Transaction tx = new Transaction();
        tx.setId("txn-123");
        tx.setTenantId("tenant-1");
        tx.setAmount(100.0);
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> shell.handleTradeExecution("advisor-1", tx)
        );
        
        assertEquals("TransactionService is not available. Cannot execute trade without transaction runtime.", exception.getMessage());
    }

    @Test
    void throwsErrorWhenPortfolioServiceUnavailable() {
        shell = new FinanceProductShell(context, transactionService, null);
        shell.start();
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> shell.handlePortfolioManagement("advisor-1", new Object())
        );
        
        assertEquals("PortfolioManagementService is not available. Cannot manage portfolio without portfolio service.", exception.getMessage());
    }

    @Test
    void throwsErrorForInvalidTradeRequestType() {
        shell.start();
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> shell.handleTradeExecution("advisor-1", "not a transaction")
        );
        
        assertEquals("tradeRequest must be an instance of Transaction", exception.getMessage());
    }
}
