package com.ghatana.integration.phrfinance;

import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.billing.LedgerPostingService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies patient identity continuity from PHR encounters to finance posting accounts
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("PatientIdentitySyncIT")
class PatientIdentitySyncIT extends EventloopTestBase {

    @Test
    @DisplayName("encounter close keeps patient identity in ledger debit account")
    void patientIdentityIsPreservedAcrossBoundary() {
        PhrFinanceTestFixtures.StubDataCloudAdapter dataCloud = new PhrFinanceTestFixtures.StubDataCloudAdapter();
        IdentityAssertingLedgerService ledger = new IdentityAssertingLedgerService("patient-identity-77");

        BillingService billingService = new BillingService(
            PhrFinanceTestFixtures.createTestContext(dataCloud),
            ledger
        );

        runPromise(() -> billingService.start());

        BillingService.BillingEncounter encounter = runPromise(() -> billingService.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-identity-77",
                "provider-identity-22",
                "facility-identity-11",
                List.of(new BillingService.ServiceLine("80053", "Metabolic panel", 1, new BigDecimal("950.00"), "NPR")),
                new BigDecimal("950.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ));

        runPromise(() -> billingService.closeEncounter(encounter.id()));

        assertThat(ledger.validated).isTrue();
    }

    private static final class IdentityAssertingLedgerService implements LedgerPostingService {

        private final String expectedPatientId;
        private boolean validated;

        private IdentityAssertingLedgerService(String expectedPatientId) {
            this.expectedPatientId = expectedPatientId;
        }

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
            assertThat(transaction.getDebitAccount()).isEqualTo("PHR:AR:" + expectedPatientId);
            assertThat(transaction.getExternalReferenceId()).isNotBlank();
            validated = true;
            return Promise.of("entry-identity");
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            return Promise.of("reversal-identity");
        }

        @Override
        public Promise<PostingStatus> getPostingStatus(String transactionId) {
            return Promise.of(validated ? PostingStatus.POSTED : PostingStatus.NOT_FOUND);
        }
    }
}
