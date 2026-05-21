package com.ghatana.integration.crossservice;

import com.ghatana.digitalmarketing.bridge.NotificationPreferenceInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.phr.kernel.interaction.ConsentStatusInteractionHandler;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR and DMOS product interaction contracts")
class PhrDmosProductInteractionContractTest extends EventloopTestBase {

    @Test
    @DisplayName("DMOS can consume PHR consent status through Kernel interaction handler")
    void dmosConsumesPhrConsentStatusThroughKernelInteractionHandler() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(new ProductInteractionRequest<>(
                        "1.0.0",
                        "interaction-consent-1",
                        ConsentStatusInteractionHandler.CONTRACT_ID,
                        "1.0.0",
                        "phr",
                        "digital-marketing",
                        "digital-marketing",
                        "tenant-1",
                        "workspace-1",
                        "run-1",
                        "corr-1",
                        Instant.parse("2026-05-21T00:00:00Z"),
                        Map.of("purpose", "campaign-activation"),
                        new ConsentStatusInteractionHandler.ConsentStatusRequest(
                                "subject-1",
                                "campaign-activation"))));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().status()).isEqualTo("allowed");
        assertThat(outcome.evidenceRefs())
                .contains("products/phr/lifecycle/gate-packs/consent.yaml");
    }

    @Test
    @DisplayName("PHR can consume DMOS notification preferences through Kernel interaction handler")
    void phrConsumesDmosNotificationPreferencesThroughKernelInteractionHandler() {
        NotificationPreferenceInteractionHandler handler = new NotificationPreferenceInteractionHandler();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> outcome =
                runPromise(() -> handler.handle(new ProductInteractionRequest<>(
                        "1.0.0",
                        "interaction-notification-1",
                        NotificationPreferenceInteractionHandler.CONTRACT_ID,
                        "1.0.0",
                        "digital-marketing",
                        "phr",
                        "phr",
                        "tenant-1",
                        "workspace-1",
                        "run-1",
                        "corr-1",
                        Instant.parse("2026-05-21T00:00:00Z"),
                        Map.of("purpose", "care-plan-notification"),
                        new NotificationPreferenceInteractionHandler.NotificationPreferenceRequest(
                                "subject-1",
                                "care-plan-notification"))));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().smsEnabled()).isTrue();
        assertThat(outcome.evidenceRefs())
                .contains("products/digital-marketing/lifecycle/evidence/notification-preference.yaml");
    }

    @Test
    @DisplayName("unsafe cross-product interaction fails closed without tenant scope")
    void unsafeCrossProductInteractionFailsClosedWithoutTenantScope() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(new ProductInteractionRequest<>(
                        "1.0.0",
                        "interaction-consent-2",
                        ConsentStatusInteractionHandler.CONTRACT_ID,
                        "1.0.0",
                        "phr",
                        "digital-marketing",
                        "digital-marketing",
                        null,
                        "workspace-1",
                        "run-1",
                        "corr-1",
                        Instant.parse("2026-05-21T00:00:00Z"),
                        Map.of("purpose", "campaign-activation"),
                        new ConsentStatusInteractionHandler.ConsentStatusRequest(
                                "subject-1",
                                "campaign-activation"))));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
        assertThat(outcome.payload()).isNull();
    }
}
