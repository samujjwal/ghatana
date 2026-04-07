package com.ghatana.products.finance.bff;

import com.ghatana.finance.service.Transaction;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.finance.service.TransactionService;
import com.ghatana.kernel.context.KernelContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance BFF routes transaction processing to the registered transaction service
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
class FinanceBFFTest {

    @Mock
    private KernelContext context;

    @Mock
    private TransactionService transactionService;

    private FinanceBFF financeBff;

    @BeforeEach
    void setUp() {
        financeBff = new FinanceBFF(context);
    }

    @Test
    void routesTransactionProcessingToKernelRegisteredService() {
        Transaction transaction = createTransaction();
        TransactionResult expected = TransactionResult.approved();
        when(context.getDependency(TransactionService.class)).thenReturn(transactionService);
        when(transactionService.processTransaction(transaction)).thenReturn(expected);

        financeBff.start();

        assertSame(expected, financeBff.processTransaction(transaction));
    }

    @Test
    void rejectsTransactionProcessingBeforeStart() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> financeBff.processTransaction(createTransaction())
        );

        assertEquals("Finance BFF not started", exception.getMessage());
    }

    @Test
    void rejectsNullTransactionAtApiBoundary() {
        financeBff.start();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> financeBff.processTransaction(null)
        );

        assertEquals("transaction is required", exception.getMessage());
    }

    @Test
    void rejectsUnsafeTradeIdentifierAtApiBoundary() {
        financeBff.start();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> financeBff.getTradeData("advisor-1", "../trade")
        );

        assertTrue(exception.getMessage().contains("tradeId"));
    }

    @Test
    void rejectsBlankPortfolioUserIdentifierAtApiBoundary() {
        financeBff.start();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> financeBff.getPortfolioData("   ", "portfolio-1")
        );

        assertEquals("userId must not be blank", exception.getMessage());
    }

    private static Transaction createTransaction() {
        Transaction transaction = new Transaction();
        transaction.setId("txn-bff-1");
        transaction.setTenantId("tenant-1");
        transaction.setAmount(100.0);
        transaction.setCurrency("USD");
        transaction.setLocation("NEW_YORK");
        transaction.setMerchantCategory("RETAIL");
        transaction.setCounterpartyCountry("US");
        transaction.setPaymentMethod("CARD");
        transaction.setVelocity(1.0);
        transaction.setTimestamp(Instant.parse("2026-04-06T12:00:00Z"));
        transaction.setStatus("PENDING");
        return transaction;
    }
}