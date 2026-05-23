package com.ghatana.integration.crossservice;

import com.ghatana.digitalmarketing.bridge.NotificationPreferenceInteractionHandler;
import com.ghatana.kernel.interaction.BrokerMode;
import com.ghatana.kernel.interaction.ProductInteractionBroker;
import com.ghatana.kernel.interaction.ProductInteractionContract;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionPolicyDecision;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.phr.kernel.consent.ConsentAccessDeniedException;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.interaction.ConsentStatusInteractionHandler;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cross-product interaction broker")
class ProductInteractionBrokerTest extends EventloopTestBase {

    @Test
    @DisplayName("routes valid request through registered provider handler")
    void routesValidRequestThroughRegisteredHandler() {
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(consentHandler())
                .build();
        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome = runPromise(() ->
                    broker.execute(baseConsentRequest("broker-ok", "campaign-activation")));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(outcome.payload()).isNotNull();
            assertThat(outcome.evidenceRefs()).isNotEmpty();
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("blocks unsupported contract version before dispatch")
    void blocksUnsupportedContractVersion() {
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(consentHandler())
                .build();
        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-unsupported-version",
                            ConsentStatusInteractionHandler.CONTRACT_ID,
                            "2.0.0",
                            "phr",
                            "digital-marketing",
                            "digital-marketing",
                            "tenant-1",
                            "workspace-1",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                            policyContext("campaign-activation"),
                            new ConsentStatusInteractionHandler.ConsentStatusRequest("subject-1", "campaign-activation"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.contract_version_unsupported");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("blocks request when workspace scope is missing")
    void blocksMissingWorkspace() {
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(consentHandler())
                .build();
        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-missing-workspace",
                            ConsentStatusInteractionHandler.CONTRACT_ID,
                            "1.0.0",
                            "phr",
                            "digital-marketing",
                            "digital-marketing",
                            "tenant-1",
                            "",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                            policyContext("campaign-activation"),
                            new ConsentStatusInteractionHandler.ConsentStatusRequest("subject-1", "campaign-activation"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.workspace_required");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("blocks request when tenant scope is missing")
    void blocksMissingTenant() {
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(consentHandler())
                .build();
        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-missing-tenant",
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
                            policyContext("campaign-activation"),
                            new ConsentStatusInteractionHandler.ConsentStatusRequest("subject-1", "campaign-activation"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_required");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("blocks on policy evaluator denial")
    void blocksOnPolicyDenial() {
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(consentHandler())
                .policyEvaluator(request -> ProductInteractionPolicyDecision.denied("product_interaction.policy_denied"))
                .build();
        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome = runPromise(() ->
                    broker.execute(baseConsentRequest("broker-policy-denied", "campaign-activation")));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("fails closed when no handler is registered for contract")
    void failsClosedWhenHandlerUnavailable() {
        ProductInteractionBroker broker = runtimeBrokerBuilder().build();
        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome = runPromise(() ->
                    broker.execute(baseConsentRequest("broker-handler-missing", "campaign-activation")));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.handler_unavailable");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("times out long-running handler and returns timeout reason")
    void timesOutLongRunningHandler() {
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(new HangingHandler())
                .requestTimeout(Duration.ofMillis(20))
                .build();
        try {
            ProductInteractionOutcome<HangingHandler.HangingResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-timeout",
                            HangingHandler.CONTRACT_ID,
                            "1.0.0",
                            "phr",
                            "digital-marketing",
                            "digital-marketing",
                            "tenant-1",
                            "workspace-1",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                            policyContext("campaign-activation"),
                            new HangingHandler.HangingRequest("subject-1"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.timeout");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("retries with same interaction id are idempotent")
    void retriesWithSameInteractionIdAreIdempotent() {
        CountingNotificationHandler handler = new CountingNotificationHandler();
        ProductInteractionBroker broker = runtimeBrokerBuilder()
                .register(handler)
                .build();
        try {
            ProductInteractionRequest<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> request =
                    new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-idempotent-retry",
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
                            policyContext("care-plan-notification"),
                            new NotificationPreferenceInteractionHandler.NotificationPreferenceRequest(
                                    "subject-1",
                                    "care-plan-notification"));

            ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> first =
                    runPromise(() -> broker.execute(request));
            ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> second =
                    runPromise(() -> broker.execute(request));

            assertThat(first.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(second.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(handler.invocations.get()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    private static ProductInteractionRequest<ConsentStatusInteractionHandler.ConsentStatusRequest> baseConsentRequest(
            String interactionId,
            String purpose) {
        return new ProductInteractionRequest<>(
                "1.0.0",
                interactionId,
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
                policyContext(purpose),
                new ConsentStatusInteractionHandler.ConsentStatusRequest("subject-1", purpose));
    }

    private static Map<String, String> policyContext(String purpose) {
        return Map.of(
                "purpose", purpose,
                "actor", "runtime-test-runner",
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "authorized", "true",
                "consentGranted", "true");
    }

    private static ProductInteractionBroker.Builder runtimeBrokerBuilder() {
        return ProductInteractionBroker.builder()
                .brokerMode(BrokerMode.TEST)
                .registerContract(phrConsentContract())
                .registerContract(dmosNotificationPreferenceContract())
                .registerContract(hangingContract());
    }

    private static ProductInteractionContract phrConsentContract() {
        return new ProductInteractionContract(
                ConsentStatusInteractionHandler.CONTRACT_ID,
                "1.0.0",
                "phr",
                Set.of("digital-marketing"),
                true,
                true,
                true,
                "healthcare-contact",
                "same-tenant",
                Set.of("lifecycle-runner"),
                Set.of("campaign-activation"),
                Set.of("validate", "test", "deploy", "verify"),
                false);
    }

    private static ProductInteractionContract dmosNotificationPreferenceContract() {
        return new ProductInteractionContract(
                NotificationPreferenceInteractionHandler.CONTRACT_ID,
                "1.0.0",
                "digital-marketing",
                Set.of("phr"),
                true,
                true,
                true,
                "customer-contact",
                "same-tenant",
                Set.of("lifecycle-runner"),
                Set.of("care-plan-notification"),
                Set.of("validate", "test", "deploy", "verify"),
                true);
    }

    private static ProductInteractionContract hangingContract() {
        return new ProductInteractionContract(
                HangingHandler.CONTRACT_ID,
                "1.0.0",
                "phr",
                Set.of("digital-marketing"),
                true,
                true,
                true,
                "healthcare-contact",
                "same-tenant",
                Set.of("lifecycle-runner"),
                Set.of("campaign-activation"),
                Set.of("test"),
                false);
    }

    private static ConsentStatusInteractionHandler consentHandler() {
        return new ConsentStatusInteractionHandler(new TestConsentService());
    }

    private static NotificationPreferenceInteractionHandler notificationPreferenceHandler() {
        return new NotificationPreferenceInteractionHandler(request -> Promise.of(
                new NotificationPreferenceInteractionHandler.NotificationPreferenceResponse(
                        request.payload().subjectId(),
                        true,
                        false,
                        "runtime-test-service")));
    }

    private static final class CountingNotificationHandler implements com.ghatana.kernel.interaction.ProductInteractionHandler<
            NotificationPreferenceInteractionHandler.NotificationPreferenceRequest,
            NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> {
        private final AtomicInteger invocations = new AtomicInteger();
        private final NotificationPreferenceInteractionHandler delegate = notificationPreferenceHandler();

        @Override
        public String contractId() {
            return NotificationPreferenceInteractionHandler.CONTRACT_ID;
        }

        @Override
        public Class<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> requestType() {
            return NotificationPreferenceInteractionHandler.NotificationPreferenceRequest.class;
        }

        @Override
        public Class<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> responseType() {
            return NotificationPreferenceInteractionHandler.NotificationPreferenceResponse.class;
        }

        @Override
        public Promise<ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse>> handle(
                ProductInteractionRequest<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> request) {
            invocations.incrementAndGet();
            return delegate.handle(request);
        }
    }

    private static final class HangingHandler implements com.ghatana.kernel.interaction.ProductInteractionHandler<
            HangingHandler.HangingRequest,
            HangingHandler.HangingResponse> {
        private static final String CONTRACT_ID = "kernel://interactions/broker.hanging.v1";

        @Override
        public String contractId() {
            return CONTRACT_ID;
        }

        @Override
        public Class<HangingRequest> requestType() {
            return HangingRequest.class;
        }

        @Override
        public Class<HangingResponse> responseType() {
            return HangingResponse.class;
        }

        @Override
        public Promise<ProductInteractionOutcome<HangingResponse>> handle(ProductInteractionRequest<HangingRequest> request) {
            return Promise.ofCallback(cb -> {
                // Intentionally never completes to trigger broker timeout.
            });
        }

        private record HangingRequest(String subjectId) {}

        private record HangingResponse(String status) {}
    }

    private static final class TestConsentService implements ConsentService {
        @Override
        public Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request) {
            boolean allowed = request.actor().scopes().contains("campaign-activation");
            if (allowed) {
                return Promise.of(ConsentAccessDecision.allow(
                        ReasonCode.EXPLICIT_GRANT,
                        "grant-runtime-test",
                        CacheStatus.MISS,
                        null));
            }
            return Promise.of(ConsentAccessDecision.deny(ReasonCode.OUT_OF_SCOPE, CacheStatus.MISS));
        }

        @Override
        public Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request) {
            return checkAccess(request).map(decision -> {
                if (!decision.allowed()) {
                    throw new ConsentAccessDeniedException(
                            request.requestId(),
                            request.tenantId(),
                            request.actor().actorId(),
                            request.target().patientId(),
                            decision);
                }
                return decision;
            });
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
