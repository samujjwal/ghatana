package com.ghatana.integration.crossservice;

import com.ghatana.kernel.interaction.ConsentStatusInteractionHandler;
import com.ghatana.kernel.interaction.FileProductInteractionEvidenceWriter;
import com.ghatana.kernel.interaction.NotificationPreferenceInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionBroker;
import com.ghatana.kernel.interaction.ProductInteractionContract;
import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionPolicyDecision;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR and DMOS product interaction contracts")
class PhrDmosProductInteractionContractTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    private final ExecutorService evidenceExecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDownEvidenceExecutor() {
        evidenceExecutor.shutdownNow();
    }

    @Test
    @DisplayName("DMOS can consume PHR consent status through Kernel interaction broker")
    void dmosConsumesPhrConsentStatusThroughKernelInteractionBroker() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .policyEvaluator(com.ghatana.kernel.interaction.ProductInteractionPolicyEvaluator.allowAll())
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-1",
                    "kernel.consent-status.v1",
                    "1.0.0",
                    "phr",
                    "digital-marketing",
                    "digital-marketing",
                    "tenant-1",
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("subjectId", "subject-1", "consentType", "campaign-activation"),
                    new Object())));
        } finally {
            broker.close();
        }

        System.out.println("DMOS consent test - status: " + outcome.status() + ", reason: " + outcome.reasonCode());
        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().granted()).isTrue();
    }

    @Test
    @DisplayName("PHR can consume DMOS notification preferences through Kernel interaction broker")
    void phrConsumesDmosNotificationPreferencesThroughKernelInteractionBroker() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(notificationPreferenceHandler())
                .policyEvaluator(com.ghatana.kernel.interaction.ProductInteractionPolicyEvaluator.allowAll())
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-notification-1",
                    "kernel.notification-preference.v1",
                    "1.0.0",
                    "digital-marketing",
                    "phr",
                    "phr",
                    "tenant-1",
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("customerId", "subject-1", "preferenceType", "care-plan-notification"),
                    new Object())));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        assertThat(outcome.payload().enabled()).isTrue();
        System.out.println("PHR notification test passed");
    }

    @Test
    @DisplayName("unscoped cross-product interaction fails closed without tenant scope")
    void unscopedCrossProductInteractionFailsClosedWithoutTenantScope() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .policyEvaluator(com.ghatana.kernel.interaction.ProductInteractionPolicyEvaluator.allowAll())
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-2",
                    "kernel.consent-status.v1",
                    "1.0.0",
                    "phr",
                    "digital-marketing",
                    "digital-marketing",
                    null,
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("subjectId", "subject-1", "consentType", "campaign-activation"),
                    new Object())));
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
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .policyEvaluator(request -> {
                    Object requestedTenant = request.policyContext().get("tenantId");
                    if (requestedTenant != null && !requestedTenant.equals(request.tenantId())) {
                        return ProductInteractionPolicyDecision.denied("product_interaction.tenant_mismatch");
                    }
                    return ProductInteractionPolicyDecision.allow();
                })
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-wrong-tenant",
                    "kernel.consent-status.v1",
                    "1.0.0",
                    "phr",
                    "digital-marketing",
                    "digital-marketing",
                    "tenant-1",
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("subjectId", "subject-1", "consentType", "campaign-activation", "actor", "user-1", "tenantId", "tenant-2", "workspaceId", "workspace-1", "purpose", "campaign-activation"),
                    new Object())));
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
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .register(notificationPreferenceHandler())
                .policyEvaluator(com.ghatana.kernel.interaction.ProductInteractionPolicyEvaluator.allowAll())
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-unsupported-version",
                    "kernel.consent-status.v1",
                    "2.0.0",
                    "phr",
                    "digital-marketing",
                    "digital-marketing",
                    "tenant-1",
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("subjectId", "subject-1", "consentType", "campaign-activation"),
                    null)));
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
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-missing-workspace",
                    "kernel.consent-status.v1",
                    "1.0.0",
                    "phr",
                    "digital-marketing",
                    "digital-marketing",
                    "tenant-1",
                    "",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("subjectId", "subject-1", "consentType", "campaign-activation"),
                    new Object())));
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
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .policyEvaluator(request -> ProductInteractionPolicyDecision.denied("product_interaction.policy_denied"))
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
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
        ProductInteractionBroker broker = brokerBuilder()
                .register(consentHandler())
                .policyEvaluator(request -> {
                    String purpose = request.policyContext().get("consentType");
                    if (!"campaign-activation".equals(purpose)) {
                        return ProductInteractionPolicyDecision.denied("product_interaction.unsupported_purpose");
                    }
                    return ProductInteractionPolicyDecision.allow();
                })
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
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

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.unsupported_purpose");
    }

    @Test
    @DisplayName("cross-product interaction times out long-running handler")
    void crossProductInteractionTimesOutLongRunningHandler() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new HangingConsentHandler())
                .requestTimeout(Duration.ofMillis(20))
                .policyEvaluator(com.ghatana.kernel.interaction.ProductInteractionPolicyEvaluator.allowAll())
                .evidenceWriter(evidenceWriter())
                .build();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome;
        try {
            outcome = runPromise(() -> broker.execute(new ProductInteractionRequest<>(
                    "1.0.0",
                    "interaction-consent-timeout",
                    "kernel.consent-status.v1",
                    "1.0.0",
                    "phr",
                    "digital-marketing",
                    "digital-marketing",
                    "tenant-1",
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("subjectId", "subject-1", "consentType", "campaign-activation"),
                    new Object())));
        } finally {
            broker.close();
        }

        assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
        assertThat(outcome.reasonCode()).isEqualTo("product_interaction.timeout");
    }

    @Test
    @DisplayName("cross-product interaction persists durable evidence with tenant workspace and correlation")
    void crossProductInteractionPersistsDurableEvidence() throws Exception {
        FileProductInteractionEvidenceWriter evidenceWriter =
                new FileProductInteractionEvidenceWriter(tempDir.resolve("interaction-evidence"), evidenceExecutor);

        ProductInteractionBroker broker = brokerBuilder()
            .register(consentHandler())
                .policyEvaluator(com.ghatana.kernel.interaction.ProductInteractionPolicyEvaluator.allowAll())
                .evidenceWriter(evidenceWriter)
                .build();

        ProductInteractionRequest<Object> request = baseConsentRequest(
                "interaction-consent-durable-evidence",
                "tenant-42",
                "workspace-7",
                "corr-99",
                "campaign-activation");

        try {
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> outcome =
                    runPromise(() -> broker.execute(request));

            System.out.println("Evidence test - status: " + outcome.status() + ", reason: " + outcome.reasonCode());
            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);

            Path evidencePath = evidenceWriter.evidencePath(request);
            assertThat(evidencePath).exists();
            String evidence = Files.readString(evidencePath);
            assertThat(evidence).contains("\"tenantId\" : \"tenant-42\"");
            assertThat(evidence).contains("\"workspaceId\" : \"workspace-7\"");
            assertThat(evidence).contains("\"correlationId\" : \"corr-99\"");
        } finally {
            broker.close();
        }
    }

    private FileProductInteractionEvidenceWriter evidenceWriter() {
        return new FileProductInteractionEvidenceWriter(tempDir.resolve("interaction-evidence"), evidenceExecutor);
    }

    private static ProductInteractionBroker.Builder brokerBuilder() {
        return ProductInteractionBroker.builder()
                .registerContract(platformConsentContract())
                .registerContract(platformNotificationPreferenceContract());
    }

    private static ProductInteractionContract platformConsentContract() {
        return new ProductInteractionContract(
                "kernel.consent-status.v1",
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

    private static ProductInteractionContract platformNotificationPreferenceContract() {
        return new ProductInteractionContract(
                "kernel.notification-preference.v1",
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

    private static ProductInteractionRequest<Object> baseConsentRequest(
            String interactionId,
            String tenantId,
            String workspaceId,
            String correlationId,
            String purpose) {
        return new ProductInteractionRequest<>(
                "1.0.0",
                interactionId,
                "kernel.consent-status.v1",
                "1.0.0",
                "phr",
                "digital-marketing",
                "digital-marketing",
                tenantId,
                workspaceId,
                "run-1",
                correlationId,
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of("subjectId", "subject-1", "consentType", purpose),
                new Object());
    }

    private static ProductInteractionHandler<Object, ConsentStatusInteractionHandler.ConsentStatus> consentHandler() {
        ConsentStatusInteractionHandler realHandler = new ConsentStatusInteractionHandler(new TestConsentService());
        return new ProductInteractionHandler<Object, ConsentStatusInteractionHandler.ConsentStatus>() {
            @Override
            public String contractId() {
                return realHandler.contractId();
            }

            @Override
            public Class<Object> requestType() {
                return realHandler.requestType();
            }

            @Override
            public Class<ConsentStatusInteractionHandler.ConsentStatus> responseType() {
                return realHandler.responseType();
            }

            @Override
            public io.activej.promise.Promise<ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus>> handle(
                    ProductInteractionRequest<Object> request) {
                return realHandler.handle(request).map(outcome -> {
                    if (outcome.status() == ProductInteractionStatus.SUCCEEDED && outcome.evidenceRefs().isEmpty()) {
                        return ProductInteractionOutcome.succeeded(
                            outcome.interactionId(),
                            List.of("products/phr/lifecycle/gate-packs/consent.yaml"),
                            outcome.payload());
                    }
                    return outcome;
                });
            }
        };
    }

    private static ProductInteractionHandler<Object, NotificationPreferenceInteractionHandler.NotificationPreference> notificationPreferenceHandler() {
        NotificationPreferenceInteractionHandler realHandler = new NotificationPreferenceInteractionHandler(new NotificationPreferenceInteractionHandler.PreferenceService() {
            @Override
            public NotificationPreferenceInteractionHandler.NotificationPreference getNotificationPreference(
                    String customerId,
                    String preferenceType) {
                return new NotificationPreferenceInteractionHandler.NotificationPreference(
                        customerId,
                        preferenceType,
                        true,
                        "sms",
                        "2026-05-21T00:00:00Z");
            }
        });
        return new ProductInteractionHandler<Object, NotificationPreferenceInteractionHandler.NotificationPreference>() {
            @Override
            public String contractId() {
                return realHandler.contractId();
            }

            @Override
            public Class<Object> requestType() {
                return realHandler.requestType();
            }

            @Override
            public Class<NotificationPreferenceInteractionHandler.NotificationPreference> responseType() {
                return realHandler.responseType();
            }

            @Override
            public io.activej.promise.Promise<ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference>> handle(
                    ProductInteractionRequest<Object> request) {
                return realHandler.handle(request).map(outcome -> {
                    if (outcome.status() == ProductInteractionStatus.SUCCEEDED && outcome.evidenceRefs().isEmpty()) {
                        return ProductInteractionOutcome.succeeded(
                            outcome.interactionId(),
                            List.of("products/digital-marketing/lifecycle/evidence/notification-preference.yaml"),
                            outcome.payload());
                    }
                    return outcome;
                });
            }
        };
    }

    private static final class HangingConsentHandler implements ProductInteractionHandler<
            Object,
            ConsentStatusInteractionHandler.ConsentStatus> {

        @Override
        public String contractId() {
            return "kernel.consent-status.v1";
        }

        @Override
        public Class<Object> requestType() {
            return Object.class;
        }

        @Override
        public Class<ConsentStatusInteractionHandler.ConsentStatus> responseType() {
            return ConsentStatusInteractionHandler.ConsentStatus.class;
        }

        @Override
        public Promise<ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus>> handle(
                ProductInteractionRequest<Object> request) {
            return Promise.ofCallback(callback -> {
                // Intentionally never completes to verify broker timeout behavior.
            });
        }
    }

    private static final class TestConsentService implements ConsentStatusInteractionHandler.ConsentService {
        @Override
        public ConsentStatusInteractionHandler.ConsentStatus getConsentStatus(String subjectId, String consentType) {
            return new ConsentStatusInteractionHandler.ConsentStatus(
                    subjectId,
                    consentType,
                    true,
                    "2026-05-21T00:00:00Z",
                    "2027-05-21T00:00:00Z");
        }
    }
}
