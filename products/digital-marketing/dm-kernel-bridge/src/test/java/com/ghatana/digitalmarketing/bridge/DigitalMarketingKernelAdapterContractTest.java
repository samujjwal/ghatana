package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DigitalMarketingKernelAdapter contract")
class DigitalMarketingKernelAdapterContractTest extends EventloopTestBase {

    private final DigitalMarketingKernelAdapter adapter = new ContractOnlyAdapter();

    @Test
    @DisplayName("evaluateRisk default fails closed by returning maximum risk (1.0)")
    void shouldFailClosedForMissingRiskProvider() {
        double score = runPromise(() -> adapter.evaluateRisk(
            null,
            "campaign-1",
            "DM_CAMPAIGN_LAUNCH",
            Map.of("budget", 1000)
        ));
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("notifyUser default fails closed by completing silently")
    void shouldFailClosedForMissingNotificationProvider() {
        Void result = runPromise(() -> adapter.notifyUser(
            null,
            "user-1",
            "dmos.campaign.launched",
            Map.of("campaignName", "Launch")
        ));
        assertThat(result).isNull();
    }

    private static final class ContractOnlyAdapter implements DigitalMarketingKernelAdapter {
        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
            return Promise.of(Boolean.FALSE);
        }

        @Override
        public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
            return Promise.of(Boolean.FALSE);
        }

        @Override
        public Promise<String> requestApproval(
                DmOperationContext context,
                String operationType,
                String subjectId,
                String description) {
            return Promise.ofException(new UnsupportedOperationException("approval provider missing"));
        }

        @Override
        public Promise<String> recordAudit(
                DmOperationContext context,
                String entityId,
                String action,
                Map<String, Object> attributes) {
            return Promise.ofException(new UnsupportedOperationException("audit provider missing"));
        }
    }
}
