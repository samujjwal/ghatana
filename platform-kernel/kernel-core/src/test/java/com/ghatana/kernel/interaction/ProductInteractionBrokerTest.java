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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
                .evidenceWriter(evidenceWriter)
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
                .policyEvaluator(request -> ProductInteractionPolicyDecision.denied("product_interaction.policy_denied"))
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new MissingEvidenceHandler())
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
                .evidenceWriter((request, outcome) -> Promise.ofException(new IllegalStateException("evidence unavailable")))
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
    @DisplayName("persists canonical interaction evidence records to files")
    void persistsCanonicalInteractionEvidenceRecordsToFiles() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ProductInteractionRequest<EchoRequest> request = baseRequest("broker-file-evidence", new EchoRequest("hello"));
        FileProductInteractionEvidenceWriter evidenceWriter =
                new FileProductInteractionEvidenceWriter(tempDir.resolve("interaction-evidence"), executor);
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
                .evidenceWriter(evidenceWriter)
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(handler)
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new EchoHandler())
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
        ProductInteractionBroker broker = ProductInteractionBroker.builder()
                .register(new HangingHandler())
                .requestTimeout(Duration.ofMillis(20))
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
        ProductInteractionBroker.Builder builder = ProductInteractionBroker.builder().register(new EchoHandler());

        assertThatThrownBy(() -> builder.register(new EchoHandler()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(EchoHandler.CONTRACT_ID);
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
                    "purpose", "test",
                    "authorized", "true",
                    "consentGranted", "true"),
                payload);
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
