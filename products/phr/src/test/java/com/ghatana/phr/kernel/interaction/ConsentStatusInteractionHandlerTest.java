package com.ghatana.phr.kernel.interaction;

import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
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
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler(new RecordingConsentService(
                ConsentService.ConsentAccessDecision.allow(
                        ConsentService.ReasonCode.EXPLICIT_GRANT,
                        "grant-1",
                        ConsentService.CacheStatus.MISS,
                        Instant.parse("2026-06-21T00:00:00Z"))));

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
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler(new RecordingConsentService(
                ConsentService.ConsentAccessDecision.deny(
                        ConsentService.ReasonCode.OUT_OF_SCOPE,
                        ConsentService.CacheStatus.MISS)));

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1", "ad-hoc-export")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.DENIED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.consent_missing");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("blocks missing tenant scope")
    void blocksMissingTenantScope() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler(new RecordingConsentService(
                ConsentService.ConsentAccessDecision.allow(
                        ConsentService.ReasonCode.EXPLICIT_GRANT,
                        "grant-1",
                        ConsentService.CacheStatus.MISS,
                        Instant.parse("2026-06-21T00:00:00Z"))));

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request(null, "campaign-activation")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
    }

    @Test
    @DisplayName("delegates consent decisions to the product consent service when provided")
    void delegatesConsentDecisionsToProductConsentService() {
        RecordingConsentService consentService = new RecordingConsentService(
                ConsentService.ConsentAccessDecision.allow(
                        ConsentService.ReasonCode.EXPLICIT_GRANT,
                        "grant-1",
                        ConsentService.CacheStatus.MISS,
                        Instant.parse("2026-06-21T00:00:00Z")));
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler(consentService);

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1", "campaign-activation")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().status()).isEqualTo("allowed");
        assertThat(consentService.lastRequest).isNotNull();
        assertThat(consentService.lastRequest.tenantId()).isEqualTo("tenant-1");
        assertThat(consentService.lastRequest.target().patientId()).isEqualTo("subject-1");
        assertThat(consentService.lastRequest.actor().actorId()).isEqualTo("digital-marketing");
    }

    @Test
    @DisplayName("returns denied interaction outcome when product consent service denies")
    void returnsDeniedInteractionOutcomeWhenProductConsentServiceDenies() {
        ConsentStatusInteractionHandler handler = new ConsentStatusInteractionHandler(new RecordingConsentService(
                ConsentService.ConsentAccessDecision.deny(
                        ConsentService.ReasonCode.OUT_OF_SCOPE,
                        ConsentService.CacheStatus.MISS)));

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                runPromise(() -> handler.handle(request("tenant-1", "ad-hoc-export")));

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.DENIED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.consent_missing");
        assertThat(outcome.payload()).isNull();
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

    private static final class RecordingConsentService implements ConsentService {
        private final ConsentAccessDecision decision;
        private ConsentCheckRequest lastRequest;

        private RecordingConsentService(ConsentAccessDecision decision) {
            this.decision = decision;
        }

        @Override
        public Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request) {
            lastRequest = request;
            return Promise.of(decision);
        }

        @Override
        public Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request) {
            return checkAccess(request);
        }

        @Override
        public Promise<Void> invalidatePatientAccessCache(CacheInvalidationRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<ConsentRevokeResult> revokeConsent(ConsentRevokeRequest request) {
            return Promise.of(new ConsentRevokeResult(true, request.target().resourceId()));
        }
    }
}
