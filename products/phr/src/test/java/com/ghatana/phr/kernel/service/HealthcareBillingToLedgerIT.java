package com.ghatana.phr.kernel.service;

import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.billing.LedgerPostingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end integration test for PHR billing close -> platform ledger posting.
 *
 * @doc.type class
 * @doc.purpose Verifies healthcare encounter closure emits correct BillingTransaction
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("HealthcareBillingToLedgerIT")
class HealthcareBillingToLedgerIT extends EventloopTestBase {

    @Test
    @DisplayName("closing encounter posts CHARGE transaction with patient/provider accounts")
    void encounterClosePostsCharge() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
            new PhrTestInfrastructure.StubDataCloudAdapter();
        CapturingLedgerPostingService ledger = new CapturingLedgerPostingService();

        BillingService service = new BillingService(
            PhrTestInfrastructure.createTestContext(dataCloud), ledger);
        runPromise(() -> service.start());

        BillingService.BillingEncounter created = runPromise(() -> service.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-int-1",
                "provider-int-1",
                "facility-int-1",
                List.of(new BillingService.ServiceLine("99213", "Visit", 1, new BigDecimal("1500.00"), "NPR")),
                new BigDecimal("1500.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ));

        BillingService.BillingEncounter closed = runPromise(() -> service.closeEncounter(created.id()));
        assertEquals(BillingService.EncounterStatus.CLOSED, closed.status());
        assertNotNull(closed.closedAt());

        assertEquals(1, ledger.posted.size());
        BillingTransaction posted = ledger.posted.getFirst();
        assertEquals("enc:" + created.id(), posted.getTransactionId());
        assertEquals(BillingTransaction.TransactionType.CHARGE, posted.getType());
        assertEquals("PHR:AR:patient-int-1", posted.getDebitAccount());
        assertEquals("PHR:REVENUE:provider-int-1", posted.getCreditAccount());
        assertEquals("NPR", posted.getCurrency());
    }

    static final class CapturingLedgerPostingService implements LedgerPostingService {

        final List<BillingTransaction> posted = new ArrayList<>();

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
            posted.add(transaction);
            return Promise.of("entry-" + posted.size());
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            return Promise.of("rev-1");
        }

        @Override
        public Promise<PostingStatus> getPostingStatus(String transactionId) {
            boolean found = posted.stream().anyMatch(t -> t.getTransactionId().equals(transactionId));
            return Promise.of(found ? PostingStatus.POSTED : PostingStatus.NOT_FOUND);
        }
    }
}
