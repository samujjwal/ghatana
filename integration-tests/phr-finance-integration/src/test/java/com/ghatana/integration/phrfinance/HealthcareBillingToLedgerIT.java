package com.ghatana.integration.phrfinance;

import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.billing.LedgerPostingService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Cross-domain integration test for PHR encounter close to ledger posting contract
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("HealthcareBillingToLedgerIT")
class HealthcareBillingToLedgerIT extends EventloopTestBase {

    @Test
    @DisplayName("closing encounter emits CHARGE transaction with patient/provider accounts")
    void closingEncounterEmitsChargeTransaction() {
        PhrFinanceTestFixtures.StubDataCloudAdapter dataCloud = new PhrFinanceTestFixtures.StubDataCloudAdapter();
        CapturingLedgerPostingService ledger = new CapturingLedgerPostingService();

        BillingService billingService = new BillingService(
            PhrFinanceTestFixtures.createTestContext(dataCloud),
            ledger
        );

        runPromise(() -> billingService.start());

        BillingService.BillingEncounter created = runPromise(() -> billingService.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-cross-1",
                "provider-cross-1",
                "facility-cross-1",
                List.of(new BillingService.ServiceLine("99213", "Consultation", 1, new BigDecimal("1800.00"), "NPR")),
                new BigDecimal("1800.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ));

        BillingService.BillingEncounter closed = runPromise(() -> billingService.closeEncounter(created.id()));

        assertThat(closed.status()).isEqualTo(BillingService.EncounterStatus.CLOSED);
        assertThat(ledger.posted).hasSize(1);

        BillingTransaction posted = ledger.posted.getFirst();
        assertThat(posted.getTransactionId()).isEqualTo("enc:" + created.id());
        assertThat(posted.getType()).isEqualTo(BillingTransaction.TransactionType.CHARGE);
        assertThat(posted.getDebitAccount()).isEqualTo("PHR:AR:patient-cross-1");
        assertThat(posted.getCreditAccount()).isEqualTo("PHR:REVENUE:provider-cross-1");
    }

    private static final class CapturingLedgerPostingService implements LedgerPostingService {

        private final List<BillingTransaction> posted = new ArrayList<>();

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
            posted.add(transaction);
            return Promise.of("entry-" + posted.size());
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            return Promise.of("reversal-1");
        }

        @Override
        public Promise<PostingStatus> getPostingStatus(String transactionId) {
            boolean found = posted.stream().anyMatch(tx -> tx.getTransactionId().equals(transactionId));
            return Promise.of(found ? PostingStatus.POSTED : PostingStatus.NOT_FOUND);
        }
    }
}
