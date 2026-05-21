package com.ghatana.phr.kernel.interaction;

import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsentStatusInteractionHandler")
class ConsentStatusInteractionHandlerTest extends EventloopTestBase {

    @Test
    @DisplayName("allows campaign activation consent checks with audit evidence")
    void allowsCampaignActivationConsentChecksWithAuditEvidence() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1", "campaign-activation")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.evidenceRefs())
                .contains(
                        "products/phr/lifecycle/gate-packs/consent.yaml",
                        "products/phr/lifecycle/gate-packs/audit-evidence.yaml");
        assertThat(outcome.payload().status()).isEqualTo("allowed");
    }

    @Test
    @DisplayName("denies unsupported consent purpose with reason code")
    void deniesUnsupportedConsentPurposeWithReasonCode() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1", "ad-hoc-export")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.DENIED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.consent_missing");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("blocks missing tenant scope")
    void blocksMissingTenantScope() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request(null, "campaign-activation")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
    }

    private static ProductInteractionRequest<ConsentStatusInteractionHandler.ConsentStatusRequest> request(
            String tenantId,
            String purpose) {
        return new ProductInteractionRequest<>(
                "1.0.0",
                "interaction-1",
                ConsentStatusInteractionHandler.CONTRACT_ID,
                "1.0.0",
                "phr",
                "digital-marketing",
                "phr",
                tenantId,
                "workspace-1",
                "run-1",
                "corr-1",
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of("purpose", purpose),
                new ConsentStatusInteractionHandler.ConsentStatusRequest("subject-1", purpose));
    }
}
