package com.ghatana.digitalmarketing.bridge;

import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationPreferenceInteractionHandler")
class NotificationPreferenceInteractionHandlerTest extends EventloopTestBase {

    @Test
    @DisplayName("returns tenant-scoped notification preferences with evidence")
    void returnsTenantScopedNotificationPreferencesWithEvidence() {
        NotificationPreferenceInteractionHandler handler = new NotificationPreferenceInteractionHandler();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.evidenceRefs())
                .contains("products/digital-marketing/lifecycle/evidence/notification-preference.yaml");
        assertThat(outcome.payload().smsEnabled()).isTrue();
        assertThat(outcome.payload().emailEnabled()).isFalse();
    }

    @Test
    @DisplayName("blocks missing tenant scope without fake success")
    void blocksMissingTenantScopeWithoutFakeSuccess() {
        NotificationPreferenceInteractionHandler handler = new NotificationPreferenceInteractionHandler();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> outcome =
                runPromise(() -> handler.handle(request(null)));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("delegates preference lookup to the product preference service when provided")
    void delegatesPreferenceLookupToProductPreferenceService() {
        RecordingPreferenceService preferenceService = new RecordingPreferenceService(
                new NotificationPreferenceInteractionHandler.NotificationPreferenceResponse(
                        "subject-1",
                        false,
                        true,
                        "contact-preference-service"));
        NotificationPreferenceInteractionHandler handler =
                new NotificationPreferenceInteractionHandler(preferenceService);

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().smsEnabled()).isFalse();
        assertThat(outcome.payload().emailEnabled()).isTrue();
        assertThat(outcome.payload().source()).isEqualTo("contact-preference-service");
        assertThat(preferenceService.lastRequest).isNotNull();
        assertThat(preferenceService.lastRequest.tenantId()).isEqualTo("tenant-1");
        assertThat(preferenceService.lastRequest.payload().purpose()).isEqualTo("care-plan-notification");
    }

    private static ProductInteractionRequest<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> request(
            String tenantId) {
        return new ProductInteractionRequest<>(
                "1.0.0",
                "interaction-1",
                NotificationPreferenceInteractionHandler.CONTRACT_ID,
                "1.0.0",
                "digital-marketing",
                "phr",
                "digital-marketing",
                tenantId,
                "workspace-1",
                "run-1",
                "corr-1",
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of("purpose", "care-plan-notification"),
                new NotificationPreferenceInteractionHandler.NotificationPreferenceRequest(
                        "subject-1",
                        "care-plan-notification"));
    }

    private static final class RecordingPreferenceService
            implements NotificationPreferenceInteractionHandler.NotificationPreferenceService {
        private final NotificationPreferenceInteractionHandler.NotificationPreferenceResponse response;
        private ProductInteractionRequest<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> lastRequest;

        private RecordingPreferenceService(
                NotificationPreferenceInteractionHandler.NotificationPreferenceResponse response) {
            this.response = response;
        }

        @Override
        public Promise<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> lookup(
                ProductInteractionRequest<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> request) {
            lastRequest = request;
            return Promise.of(response);
        }
    }
}
