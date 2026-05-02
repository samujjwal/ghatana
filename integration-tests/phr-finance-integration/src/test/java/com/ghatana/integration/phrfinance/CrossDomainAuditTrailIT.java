package com.ghatana.integration.phrfinance;

import com.ghatana.plugin.ledger.LedgerPlugin;
import com.ghatana.plugin.ledger.LedgerTransaction;
import com.ghatana.finance.kernel.service.BillingLedgerAdapter;
import com.ghatana.finance.kernel.service.LedgerManagementService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies cross-domain ledger posting carries auditable source and reference metadata
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("CrossDomainAuditTrailIT")
class CrossDomainAuditTrailIT extends EventloopTestBase {

    @Test
    @DisplayName("finance adapter preserves source product and external reference in ledger description")
    void adapterPreservesCrossDomainAuditAttributes() {
        StubLedgerManagementService ledgerService = new StubLedgerManagementService();
        BillingLedgerAdapter adapter = new BillingLedgerAdapter(ledgerService);

        LedgerTransaction tx = LedgerTransaction.builder()
            .transactionId("tx-audit-1")
            .sourceId("phr")
            .debitAccount("PHR:AR:patient-audit-1")
            .creditAccount("PHR:REVENUE:provider-audit-2")
            .amount(new BigDecimal("450.00"))
            .currency("NPR")
            .type(LedgerTransaction.TransactionType.CHARGE)
            .description("Encounter closure")
            .externalReferenceId("enc-audit-9")
            .tenantId("tenant-audit")
            .occurredAt(Instant.now())
            .build();

        String entryId = runPromise(() -> adapter.postTransaction(tx));

        assertThat(entryId).isEqualTo("ledger-1");
        assertThat(ledgerService.lastDescription)
            .contains("Encounter closure")
            .contains("src=phr")
            .contains("ref=enc-audit-9");

        LedgerPlugin.PostingStatus status = runPromise(() -> adapter.getPostingStatus("tx-audit-1"));
        assertThat(status).isEqualTo(LedgerPlugin.PostingStatus.POSTED);
    }

    private static final class StubLedgerManagementService extends LedgerManagementService {

        private String lastDescription;

        private StubLedgerManagementService() {
            super(PhrFinanceTestFixtures.createTestContext(new PhrFinanceTestFixtures.StubDataCloudAdapter()));
        }

        @Override
        public Promise<LedgerEntry> postEntry(LedgerEntryRequest request) {
            lastDescription = request.getDescription();
            return Promise.of(new LedgerEntry(
                "ledger-1",
                request.getDebitAccount(),
                request.getCreditAccount(),
                request.getAmount(),
                request.getCurrency(),
                request.getDescription(),
                request.getTransactionType(),
                Instant.now(),
                "POSTED"
            ));
        }

        @Override
        public Promise<Optional<LedgerEntry>> getEntry(String entryId) {
            return Promise.of(Optional.of(new LedgerEntry(
                "ledger-1",
                "PHR:REVENUE:provider-audit-2",
                "PHR:AR:patient-audit-1",
                new BigDecimal("450.00"),
                "NPR",
                "existing",
                "CHARGE",
                Instant.now(),
                "POSTED"
            )));
        }
    }
}
