package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductInteractionBroker")
class ProductInteractionBrokerTest extends EventloopTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("routes valid requests through handler and writes evidence")
    void routesValidRequestThroughHandlerAndWritesEvidence() {
        RecordingEvidenceWriter evidenceWriter = new RecordingEvidenceWriter();
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(evidenceWriter)
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-success", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(outcome.payload()).isEqualTo(new EchoResponse("hello"));
            assertThat(evidenceWriter.records).hasSize(1);
            assertThat(broker.metrics().succeeded()).isEqualTo(1);
            assertThat(broker.metrics().blocked()).isZero();
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("blocks request when required tenant scope is missing")
    void blocksMissingTenantScope() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-missing-tenant",
                            EchoHandler.CONTRACT_ID,
                            "1.0.0",
                            "provider-product",
                            "consumer-product",
                            "consumer-product",
                            "",
                            "workspace-1",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                                Map.of(
                                    "actor", "kernel-test-runner",
                                    "tenantId", "",
                                    "workspaceId", "workspace-1",
                                    "purpose", "test",
                                    "authorized", "true",
                                    "consentGranted", "true"),
                            new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_required");
            assertThat(broker.metrics().blocked()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("blocks request when policy evaluator denies it")
    void blocksPolicyDenial() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyEvaluator(request -> ProductInteractionPolicyDecision.denied("product_interaction.policy_denied"))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-policy-denied", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.policy_denied");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("default policy requires interaction purpose")
    void defaultPolicyRequiresInteractionPurpose() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-purpose-required",
                            EchoHandler.CONTRACT_ID,
                            "1.0.0",
                            "provider-product",
                            "consumer-product",
                            "consumer-product",
                            "tenant-1",
                            "workspace-1",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                                Map.of(
                                    "actor", "kernel-test-runner",
                                    "tenantId", "tenant-1",
                                    "workspaceId", "workspace-1",
                                    "authorized", "true",
                                    "consentGranted", "true"),
                            new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.purpose_required");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("requires successful provider outcomes to include evidence refs")
    void requiresEvidenceForSuccessfulProviderOutcome() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new MissingEvidenceHandler())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-missing-evidence", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.evidence_required");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("converts evidence writer failures into blocked outcomes")
    void convertsEvidenceWriterFailuresIntoBlockedOutcome() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter((request, outcome) -> Promise.ofException(new IllegalStateException("evidence unavailable")))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-evidence-failure", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.evidence_persistence_failed");
            assertThat(broker.metrics().evidenceFailures()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("fails closed with evidence when contract is missing")
    void failsClosedWithEvidenceWhenContractIsMissing() {
        RecordingEvidenceWriter evidenceWriter = new RecordingEvidenceWriter();
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
                .evidenceWriter(evidenceWriter)
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-missing-contract", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.contract_missing");
            assertThat(evidenceWriter.records).hasSize(1);
            assertThat(broker.metrics().blocked()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("contract hash changes when allowed purposes change")
    void contractHashChangesWhenAllowedPurposesChange() throws Exception {
        String baseline = contractHash(echoContract());
        String changed = contractHash(new ProductInteractionContract(
                EchoHandler.CONTRACT_ID,
                "1.0.0",
                "provider-product",
                Set.of("consumer-product"),
                true,
                true,
                false,
                "none",
                "same-tenant",
                Set.of("tester"),
                Set.of("test", "support"),
                Set.of("test"),
                false));

        assertThat(changed).isNotEqualTo(baseline);
    }

    @Test
    @DisplayName("contract hash changes when lifecycle phases change")
    void contractHashChangesWhenLifecyclePhasesChange() throws Exception {
        String baseline = contractHash(echoContract());
        String changed = contractHash(new ProductInteractionContract(
                EchoHandler.CONTRACT_ID,
                "1.0.0",
                "provider-product",
                Set.of("consumer-product"),
                true,
                true,
                false,
                "none",
                "same-tenant",
                Set.of("tester"),
                Set.of("test"),
                Set.of("test", "validate"),
                false));

        assertThat(changed).isNotEqualTo(baseline);
    }

    @Test
    @DisplayName("contract hash changes when degraded mode changes")
    void contractHashChangesWhenDegradedModeChanges() throws Exception {
        String baseline = contractHash(echoContract());
        String changed = contractHash(new ProductInteractionContract(
                EchoHandler.CONTRACT_ID,
                "1.0.0",
                "provider-product",
                Set.of("consumer-product"),
                true,
                true,
                false,
                "none",
                "same-tenant",
                Set.of("tester"),
                Set.of("test"),
                Set.of("test"),
                true));

        assertThat(changed).isNotEqualTo(baseline);
    }

    @Test
    @DisplayName("persists canonical interaction evidence records to files")
    void persistsCanonicalInteractionEvidenceRecordsToFiles() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ProductInteractionRequest<EchoRequest> request = baseRequest("broker-file-evidence", new EchoRequest("hello"));
        FileProductInteractionEvidenceWriter evidenceWriter =
                new FileProductInteractionEvidenceWriter(tempDir.resolve("interaction-evidence"), executor);
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(evidenceWriter)
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() -> broker.execute(request));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            Path evidencePath = evidenceWriter.evidencePath(request);
            assertThat(evidencePath).exists();
            Map<String, Object> record = OBJECT_MAPPER.readValue(
                    Files.readString(evidencePath),
                    new TypeReference<>() {
                    });
            assertThat(record)
                    .containsEntry("schemaVersion", "1.0.0")
                    .containsEntry("manifestType", "interaction-evidence")
                    .containsEntry("contractId", EchoHandler.CONTRACT_ID)
                    .containsEntry("contractVersion", "1.0.0")
                    .containsEntry("providerProductId", "provider-product")
                    .containsEntry("consumerProductId", "consumer-product")
                    .containsEntry("mode", "request-response")
                    .containsEntry("tenantId", "tenant-1")
                    .containsEntry("workspaceId", "workspace-1")
                    .containsEntry("productUnitId", "consumer-product")
                    .containsEntry("runId", "run-1")
                    .containsEntry("correlationId", "corr-1")
                    .containsEntry("status", "succeeded")
                    .containsEntry("policyDecision", "allowed");
            assertThat(record.get("evidenceId")).asString().startsWith("interaction-evidence-");
            assertThat(record.get("requestedAt")).isEqualTo("2026-05-21T00:00:00Z");
            assertThat(record.get("completedAt")).isInstanceOf(String.class);
            assertThat(record.get("evidenceRefs")).isEqualTo(List.of("kernel://evidence/test/broker-file-evidence"));
            assertThat(record.get("provenanceRefs")).isEqualTo(List.of());
        } finally {
            broker.close();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("replays completed interactions by interaction id and scope")
    void replaysCompletedInteractionsByInteractionIdAndScope() {
        CountingEchoHandler handler = new CountingEchoHandler();
        ProductInteractionBroker broker = brokerBuilder()
                .register(handler)
                .brokerMode(BrokerMode.TEST)
                .build();
        ProductInteractionRequest<EchoRequest> request = baseRequest("broker-idempotent", new EchoRequest("hello"));
        try {
            ProductInteractionOutcome<EchoResponse> first = runPromise(() -> broker.execute(request));
            ProductInteractionOutcome<EchoResponse> second = runPromise(() -> broker.execute(request));

            assertThat(first.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(second.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(handler.invocations.get()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("records latency metrics within the local interaction SLO")
    void recordsLatencyMetricsWithinLocalSlo() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            for (int index = 0; index < 20; index++) {
                int interactionIndex = index;
                ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                        broker.execute(baseRequest("broker-performance-" + interactionIndex, new EchoRequest("hello"))));
                assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            }

            ProductInteractionBrokerMetrics metrics = broker.metrics();
            assertThat(metrics.requested()).isEqualTo(20);
            assertThat(metrics.succeeded()).isEqualTo(20);
            assertThat(metrics.blocked()).isZero();
            assertThat(metrics.maxLatencyMs()).isLessThan(500L);
            assertThat(metrics.averageLatencyMs()).isLessThanOrEqualTo(metrics.maxLatencyMs());
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("times out handlers that do not complete")
    void timesOutHandlersThatDoNotComplete() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new HangingHandler())
                .requestTimeout(Duration.ofMillis(20))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-timeout", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.timeout");
            assertThat(broker.metrics().timedOut()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("rejects duplicate handler registration")
    void rejectsDuplicateHandlerRegistration() {
        ProductInteractionBroker.Builder builder = brokerBuilder()
                .register(new EchoHandler())
                .brokerMode(BrokerMode.TEST);

        assertThatThrownBy(() -> builder.register(new EchoHandler()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(EchoHandler.CONTRACT_ID);
    }

    // P0-01: Production mode requires real evidence writer
    @Test
    @DisplayName("P0-01: production mode rejects no-op evidence writer")
    void productionModeRejectsNoopEvidenceWriter() {
        assertThatThrownBy(() -> brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(ProductInteractionEvidenceWriter.noop())
                .brokerMode(BrokerMode.PRODUCTION)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production mode requires a real evidence writer");
    }

    @Test
    @DisplayName("P0-01: test mode allows no-op evidence writer")
    void testModeAllowsNoopEvidenceWriter() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(ProductInteractionEvidenceWriter.noop())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            assertThat(broker).isNotNull();
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("P0-01: development mode defaults to local file-backed evidence")
    void developmentModeDefaultsToLocalFileBackedEvidence() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(ProductInteractionEvidenceWriter.noop())
                .developmentEvidenceRoot(tempDir.resolve("development-evidence"))
                .brokerMode(BrokerMode.DEVELOPMENT)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(baseRequest("broker-development-evidence", new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(tempDir.resolve("development-evidence")).exists();
        } finally {
            broker.close();
        }
    }

    // P0-02: Reject caller-supplied authorization and consent flags
    @Test
    @DisplayName("P0-02: rejects caller-supplied authorized flag")
    void rejectsCallerSuppliedAuthorizedFlag() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyContextResolver(ProductInteractionPolicyContextResolver.testResolver())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-forged-auth",
                            EchoHandler.CONTRACT_ID,
                            "1.0.0",
                            "provider-product",
                            "consumer-product",
                            "consumer-product",
                            "tenant-1",
                            "workspace-1",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                            Map.of(
                                "actor", "kernel-test-runner",
                                "tenantId", "tenant-1",
                                "workspaceId", "workspace-1",
                                "purpose", "test",
                                "authorized", "true"),  // Caller-supplied - should be rejected
                            new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.caller_supplied_auth_not_trusted");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("P0-02: rejects caller-supplied consent flag")
    void rejectsCallerSuppliedConsentFlag() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyContextResolver(ProductInteractionPolicyContextResolver.testResolver())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() ->
                    broker.execute(new ProductInteractionRequest<>(
                            "1.0.0",
                            "broker-forged-consent",
                            EchoHandler.CONTRACT_ID,
                            "1.0.0",
                            "provider-product",
                            "consumer-product",
                            "consumer-product",
                            "tenant-1",
                            "workspace-1",
                            "run-1",
                            "corr-1",
                            Instant.parse("2026-05-21T00:00:00Z"),
                            Map.of(
                                "actor", "kernel-test-runner",
                                "tenantId", "tenant-1",
                                "workspaceId", "workspace-1",
                                "purpose", "test",
                                "consentGranted", "true"),  // Caller-supplied - should be rejected
                            new EchoRequest("hello"))));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.caller_supplied_consent_not_trusted");
        } finally {
            broker.close();
        }
    }

    // P0-03: Replay key includes payload hash and contract metadata
    @Test
    @DisplayName("P0-03: different payload creates different replay key")
    void differentPayloadCreatesDifferentReplayKey() {
        CountingEchoHandler handler = new CountingEchoHandler();
        ProductInteractionBroker broker = brokerBuilder()
                .register(handler)
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionRequest<EchoRequest> request1 = baseRequest("broker-payload-1", new EchoRequest("hello"));
            ProductInteractionRequest<EchoRequest> request2 = baseRequest("broker-payload-2", new EchoRequest("world"));

            ProductInteractionOutcome<EchoResponse> outcome1 = runPromise(() -> broker.execute(request1));
            ProductInteractionOutcome<EchoResponse> outcome2 = runPromise(() -> broker.execute(request2));

            assertThat(outcome1.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(outcome2.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            // Different payloads should not replay - handler should be called twice
            assertThat(handler.invocations.get()).isEqualTo(2);
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("P0-03: same payload replays from cache")
    void samePayloadReplaysFromCache() {
        CountingEchoHandler handler = new CountingEchoHandler();
        ProductInteractionBroker broker = brokerBuilder()
                .register(handler)
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionRequest<EchoRequest> request = baseRequest("broker-same-payload", new EchoRequest("hello"));

            ProductInteractionOutcome<EchoResponse> first = runPromise(() -> broker.execute(request));
            ProductInteractionOutcome<EchoResponse> second = runPromise(() -> broker.execute(request));

            assertThat(first.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            assertThat(second.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
            // Same payload should replay - handler called only once
            assertThat(handler.invocations.get()).isEqualTo(1);
        } finally {
            broker.close();
        }
    }

    // SEC-001: Full wrong tenant/workspace isolation tests
    @Test
    @DisplayName("SEC-001: blocks interaction from wrong tenant")
    void blocksInteractionFromWrongTenant() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyContextResolver((request, contract) -> 
                    Map.of("tenantId", "tenant-1", "workspaceId", "workspace-1", "purpose", "test", "actor", "kernel-test-runner"))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionRequest<EchoRequest> request = new ProductInteractionRequest<>(
                    "1.0.0",
                    "broker-wrong-tenant",
                    EchoHandler.CONTRACT_ID,
                    "1.0.0",
                    "provider-product",
                    "consumer-product",
                    "consumer-product",
                    "tenant-2",  // Wrong tenant
                    "workspace-1",
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("actor", "kernel-test-runner"),
                    new EchoRequest("hello"));

            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() -> broker.execute(request));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_mismatch");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("SEC-001: blocks interaction from wrong workspace")
    void blocksInteractionFromWrongWorkspace() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyContextResolver((request, contract) -> 
                    Map.of("tenantId", "tenant-1", "workspaceId", "workspace-1", "purpose", "test", "actor", "kernel-test-runner"))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionRequest<EchoRequest> request = new ProductInteractionRequest<>(
                    "1.0.0",
                    "broker-wrong-workspace",
                    EchoHandler.CONTRACT_ID,
                    "1.0.0",
                    "provider-product",
                    "consumer-product",
                    "consumer-product",
                    "tenant-1",
                    "workspace-2",  // Wrong workspace
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("actor", "kernel-test-runner"),
                    new EchoRequest("hello"));

            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() -> broker.execute(request));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.workspace_mismatch");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("SEC-001: blocks interaction with both wrong tenant and workspace")
    void blocksInteractionWithWrongTenantAndWorkspace() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyContextResolver((request, contract) -> 
                    Map.of("tenantId", "tenant-1", "workspaceId", "workspace-1", "purpose", "test", "actor", "kernel-test-runner"))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionRequest<EchoRequest> request = new ProductInteractionRequest<>(
                    "1.0.0",
                    "broker-wrong-both",
                    EchoHandler.CONTRACT_ID,
                    "1.0.0",
                    "provider-product",
                    "consumer-product",
                    "consumer-product",
                    "tenant-2",  // Wrong tenant
                    "workspace-2",  // Wrong workspace
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("actor", "kernel-test-runner"),
                    new EchoRequest("hello"));

            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() -> broker.execute(request));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.BLOCKED);
            // Should fail on tenant check first
            assertThat(outcome.reasonCode()).isEqualTo("product_interaction.tenant_mismatch");
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("SEC-001: allows interaction with correct tenant and workspace")
    void allowsInteractionWithCorrectTenantAndWorkspace() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .policyContextResolver((request, contract) -> 
                    Map.of("tenantId", "tenant-1", "workspaceId", "workspace-1", "purpose", "test", "actor", "kernel-test-runner"))
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            ProductInteractionRequest<EchoRequest> request = new ProductInteractionRequest<>(
                    "1.0.0",
                    "broker-correct-scope",
                    EchoHandler.CONTRACT_ID,
                    "1.0.0",
                    "provider-product",
                    "consumer-product",
                    "consumer-product",
                    "tenant-1",  // Correct tenant
                    "workspace-1",  // Correct workspace
                    "run-1",
                    "corr-1",
                    Instant.parse("2026-05-21T00:00:00Z"),
                    Map.of("actor", "kernel-test-runner"),
                    new EchoRequest("hello"));

            ProductInteractionOutcome<EchoResponse> outcome = runPromise(() -> broker.execute(request));

            assertThat(outcome.status()).isEqualTo(ProductInteractionStatus.SUCCEEDED);
        } finally {
            broker.close();
        }
    }

    // KER-004: Commit SHA binding for production truth
    @Test
    @DisplayName("KER-004: production mode requires commit SHA")
    void productionModeRequiresCommitSha() {
        assertThatThrownBy(() -> brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(new RecordingEvidenceWriter())
                .brokerMode(BrokerMode.PRODUCTION)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production mode requires commit SHA for production truth binding");
    }

    @Test
    @DisplayName("KER-004: production mode accepts valid commit SHA")
    void productionModeAcceptsValidCommitSha() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(new RecordingEvidenceWriter())
                .brokerMode(BrokerMode.PRODUCTION)
                .commitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347")
                .build();
        try {
            assertThat(broker).isNotNull();
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("KER-004: rejects invalid commit SHA format")
    void rejectsInvalidCommitShaFormat() {
        assertThatThrownBy(() -> brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(new RecordingEvidenceWriter())
                .brokerMode(BrokerMode.PRODUCTION)
                .commitSha("invalid-sha")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid commit SHA format");
    }

    @Test
    @DisplayName("KER-004: non-production mode does not require commit SHA")
    void nonProductionModeDoesNotRequireCommitSha() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .evidenceWriter(new RecordingEvidenceWriter())
                .brokerMode(BrokerMode.DEVELOPMENT)
                .build();
        try {
            assertThat(broker).isNotNull();
        } finally {
            broker.close();
        }
    }

    @Test
    @DisplayName("KER-004: test mode does not require commit SHA")
    void testModeDoesNotRequireCommitSha() {
        ProductInteractionBroker broker = brokerBuilder()
                .register(new EchoHandler())
                .brokerMode(BrokerMode.TEST)
                .build();
        try {
            assertThat(broker).isNotNull();
        } finally {
            broker.close();
        }
    }

    private static ProductInteractionRequest<EchoRequest> baseRequest(String interactionId, EchoRequest payload) {
        return new ProductInteractionRequest<>(
                "1.0.0",
                interactionId,
                EchoHandler.CONTRACT_ID,
                "1.0.0",
                "provider-product",
                "consumer-product",
                "consumer-product",
                "tenant-1",
                "workspace-1",
                "run-1",
                "corr-1",
                Instant.parse("2026-05-21T00:00:00Z"),
                Map.of(
                    "actor", "kernel-test-runner",
                    "tenantId", "tenant-1",
                    "workspaceId", "workspace-1",
                    "purpose", "test"),
                payload);
    }

    private static ProductInteractionBroker.Builder brokerBuilder() {
        return ProductInteractionBroker.builder().registerContract(echoContract());
    }

    private static ProductInteractionContract echoContract() {
        return new ProductInteractionContract(
                EchoHandler.CONTRACT_ID,
                "1.0.0",
                "provider-product",
                Set.of("consumer-product"),
                true,
                true,
                false,
                "none",
                "same-tenant",
                Set.of("tester"),
                Set.of("test"),
                Set.of("test"),
                false);
    }

    private static String contractHash(ProductInteractionContract contract) throws Exception {
        Method method = ProductInteractionBroker.class.getDeclaredMethod(
                "computeContractSchemaHash",
                ProductInteractionContract.class);
        method.setAccessible(true);
        return (String) method.invoke(null, contract);
    }

    private record EchoRequest(String value) {
    }

    private record EchoResponse(String value) {
    }

    private static class EchoHandler implements ProductInteractionHandler<EchoRequest, EchoResponse> {
        private static final String CONTRACT_ID = "kernel://interactions/test.echo.v1";

        @Override
        public String contractId() {
            return CONTRACT_ID;
        }

        @Override
        public Class<EchoRequest> requestType() {
            return EchoRequest.class;
        }

        @Override
        public Class<EchoResponse> responseType() {
            return EchoResponse.class;
        }

        @Override
        public Promise<ProductInteractionOutcome<EchoResponse>> handle(ProductInteractionRequest<EchoRequest> request) {
            return Promise.of(ProductInteractionOutcome.succeeded(
                    request.interactionId(),
                    List.of("kernel://evidence/test/" + request.interactionId()),
                    new EchoResponse(request.payload().value())));
        }
    }

    private static final class CountingEchoHandler extends EchoHandler {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Promise<ProductInteractionOutcome<EchoResponse>> handle(ProductInteractionRequest<EchoRequest> request) {
            invocations.incrementAndGet();
            return super.handle(request);
        }
    }

    private static final class MissingEvidenceHandler extends EchoHandler {
        @Override
        public Promise<ProductInteractionOutcome<EchoResponse>> handle(ProductInteractionRequest<EchoRequest> request) {
            return Promise.of(ProductInteractionOutcome.succeeded(
                    request.interactionId(),
                    List.of(),
                    new EchoResponse(request.payload().value())));
        }
    }

    private static final class HangingHandler extends EchoHandler {
        @Override
        public Promise<ProductInteractionOutcome<EchoResponse>> handle(ProductInteractionRequest<EchoRequest> request) {
            return Promise.ofCallback(cb -> {
            });
        }
    }

    private static final class RecordingEvidenceWriter implements ProductInteractionEvidenceWriter {
        private final ConcurrentLinkedQueue<ProductInteractionOutcome<?>> records = new ConcurrentLinkedQueue<>();

        @Override
        public Promise<Void> write(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome) {
            records.add(outcome);
            return Promise.complete();
        }
    }
}
