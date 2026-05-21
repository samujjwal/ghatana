package com.ghatana.integration.crossservice;

import com.ghatana.digitalmarketing.bridge.NotificationPreferenceInteractionHandler;
import com.ghatana.kernel.interaction.FileProductInteractionEvidenceWriter;
import com.ghatana.kernel.interaction.ProductInteractionBroker;
import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionPolicyDecision;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.phr.kernel.interaction.ConsentStatusInteractionHandler;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR and DMOS product interaction contracts")
class PhrDmosProductInteractionContractTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DMOS can consume PHR consent status through Kernel interaction broker")
    void dmosConsumesPhrConsentStatusThroughKernelInteractionBroker() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
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
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().status()).isEqualTo("allowed");
        assertThat(outcome.evidenceRefs())
                .contains("products/phr/lifecycle/gate-packs/consent.yaml");
    }

    @Test
    @DisplayName("PHR can consume DMOS notification preferences through Kernel interaction broker")
    void phrConsumesDmosNotificationPreferencesThroughKernelInteractionBroker() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new NotificationPreferenceInteractionHandler())
                .build();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
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
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().smsEnabled()).isTrue();
        assertThat(outcome.evidenceRefs())
                .contains("products/digital-marketing/lifecycle/evidence/notification-preference.yaml");
    }

    @Test
    @DisplayName("unscoped cross-product interaction fails closed without tenant scope")
    void unscopedCrossProductInteractionFailsClosedWithoutTenantScope() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
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
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_required");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("wrong tenant scope is denied by policy before dispatch")
    void wrongTenantScopeIsDeniedByPolicyBeforeDispatch() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .policyEvaluator(request -> {
                    Object requestedTenant = request.policyContext().get("tenantId");
                    if (requestedTenant != null && !requestedTenant.equals(request.tenantId())) {
                        return ProductInteractionPolicyDecision.denied("product_interaction.tenant_mismatch");
                    }
                    return ProductInteractionPolicyDecision.allow();
                })
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-wrong-tenant",
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
                    Map.of("purpose", "campaign-activation", "tenantId", "tenant-2"),
                    new ConsentStatusInteractionHandler.ConsentStatusRequest(
                            "subject-1",
                            "campaign-activation"))));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_mismatch");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("PHR and DMOS interaction blocks unsupported contract version")
    void blocksUnsupportedContractVersion() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .register(new NotificationPreferenceInteractionHandler())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-unsupported-version",
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
                    Map.of("purpose", "campaign-activation"),
                    new ConsentStatusInteractionHandler.ConsentStatusRequest(
                            "subject-1",
                            "campaign-activation"))));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.contract_version_unsupported");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("cross-product interaction blocks missing workspace scope")
    void crossProductInteractionBlocksMissingWorkspaceScope() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-missing-workspace",
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
                    Map.of("purpose", "campaign-activation"),
                    new ConsentStatusInteractionHandler.ConsentStatusRequest(
                            "subject-1",
                            "campaign-activation"))));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.workspace_required");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("cross-product interaction blocks when policy evaluator denies")
    void crossProductInteractionBlocksOnPolicyDenial() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .policyEvaluator(request -> ProductInteractionPolicyDecision.denied("product_interaction.policy_denied"))
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(baseConsentRequest(
                    "interaction-consent-policy-denied",
                    "tenant-1",
                    "workspace-1",
                    "corr-1",
                    "campaign-activation")));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("cross-product interaction blocks unsupported purpose")
    void crossProductInteractionBlocksUnsupportedPurpose() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(baseConsentRequest(
                    "interaction-consent-unsupported-purpose",
                    "tenant-1",
                    "workspace-1",
                    "corr-1",
                    "unsupported-purpose")));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.DENIED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.consent_missing");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    @DisplayName("cross-product interaction times out long-running handler")
    void crossProductInteractionTimesOutLongRunningHandler() {
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new HangingConsentHandler())
                .requestTimeout(Duration.ofMillis(20))
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome;
        try {
            outcome = runPromise(() -> broker.execute(baseConsentRequest(
                    "interaction-consent-timeout",
                    "tenant-1",
                    "workspace-1",
                    "corr-1",
                    "campaign-activation")));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.timeout");
    }

    @Test
    @DisplayName("cross-product interaction persists durable evidence with tenant workspace and correlation")
    void crossProductInteractionPersistsDurableEvidence() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        FileProductInteractionEvidenceWriter evidenceWriter =
                new FileProductInteractionEvidenceWriter(tempDir.resolve("interaction-evidence"), executor);

        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new ConsentStatusInteractionHandler())
                .evidenceWriter(evidenceWriter)
                .build();

        ProductInteractionRequest<ConsentStatusInteractionHandler.ConsentStatusRequest> request = baseConsentRequest(
                "interaction-consent-durable-evidence",
                "tenant-42",
                "workspace-7",
                "corr-99",
                "campaign-activation");

        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse> outcome =
                    runPromise(() -> broker.execute(request));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);

            Path evidencePath = evidenceWriter.evidencePath(request);
            assertThat(evidencePath).exists();
            String evidence = Files.readString(evidencePath);
            assertThat(evidence).contains("\"tenantId\" : \"tenant-42\"");
            assertThat(evidence).contains("\"workspaceId\" : \"workspace-7\"");
            assertThat(evidence).contains("\"correlationId\" : \"corr-99\"");
            assertThat(evidence).contains("products/phr/lifecycle/gate-packs/consent.yaml");
        } finally {
            broker.close();
            executor.shutdownNow();
        }
    }

    private static ProductInteractionRequest<ConsentStatusInteractionHandler.ConsentStatusRequest> baseConsentRequest(
            String interactionId,
            String tenantId,
            String workspaceId,
            String correlationId,
            String purpose) {
        return new ProductInteractionRequest<>(
                "1.0.0",
                interactionId,
                ConsentStatusInteractionHandler.CONTRACT_ID,
                "1.0.0",
                "phr",
                "digital-marketing",
                "digital-marketing",
                tenantId,
                workspaceId,
                "run-1",
                correlationId,
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of("purpose", purpose),
                new ConsentStatusInteractionHandler.ConsentStatusRequest("subject-1", purpose));
    }

    private static final class HangingConsentHandler implements ProductInteractionHandler<
            ConsentStatusInteractionHandler.ConsentStatusRequest,
            ConsentStatusInteractionHandler.ConsentStatusResponse> {

        @Override
        public String contractId() {
            return ConsentStatusInteractionHandler.CONTRACT_ID;
        }

        @Override
        public Class<ConsentStatusInteractionHandler.ConsentStatusRequest> requestType() {
            return ConsentStatusInteractionHandler.ConsentStatusRequest.class;
        }

        @Override
        public Class<ConsentStatusInteractionHandler.ConsentStatusResponse> responseType() {
            return ConsentStatusInteractionHandler.ConsentStatusResponse.class;
        }

        @Override
        public Promise<ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatusResponse>> handle(
                ProductInteractionRequest<ConsentStatusInteractionHandler.ConsentStatusRequest> request) {
            return Promise.ofCallback(callback -> {
                // Intentionally never completes to verify broker timeout behavior.
            });
        }
    }
}
