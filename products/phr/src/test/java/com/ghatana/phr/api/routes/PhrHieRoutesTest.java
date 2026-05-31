package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.hie.HieIntegrationContract;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract validation tests for {@link PhrHieRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies HIE route operations are backed by the kernel HIE contract
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrHieRoutes contract validation")
class PhrHieRoutesTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private CapturingHieContract hieContract;
    private PhrPolicyEvaluator policyEvaluator;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        hieContract = new CapturingHieContract();
        policyEvaluator = mock(PhrPolicyEvaluator.class);
        when(policyEvaluator.canAccessPhiResourceAsync(any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        servlet = new PhrHieRoutes(eventloop(), hieContract, policyEvaluator).getServlet();
    }

    @Test
    @DisplayName("202 - export submits explicit export operation through contract")
    void exportUsesHieContract() throws Exception {
        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.POST, "/export")));

        assertThat(response.getCode()).isEqualTo(202);
        JsonNode body = parseBody(response);
        assertThat(body.path("operation").asText()).isEqualTo("EXPORT");
        assertThat(body.path("contractId").asText()).isEqualTo("test-hie-contract");
        assertThat(body.has("patientId")).isFalse();
        assertThat(hieContract.lastRequest.operation()).isEqualTo(HieIntegrationContract.Operation.EXPORT);
        assertThat(hieContract.lastRequest.patientId()).isEqualTo("patient-1");
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(),
            eq("patient-1"),
            eq("hie-integration"),
            eq("EXPORT"),
            eq("tenant-1"),
            nullable(String.class)
        );
    }

    @Test
    @DisplayName("403 - export denied by policy before contract submit")
    void exportDeniedByPolicyBeforeContractSubmit() throws Exception {
        when(policyEvaluator.canAccessPhiResourceAsync(any(), anyString(), anyString(), eq("EXPORT"), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("HIE_EXPORT_DENIED", "denied")));

        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.POST, "/export")));

        assertThat(response.getCode()).isEqualTo(403);
        JsonNode body = parseBody(response);
        assertThat(body.path("error").asText()).isEqualTo("HIE_EXPORT_DENIED");
        assertThat(hieContract.lastRequest).isNull();
    }

    @Test
    @DisplayName("409 - unsupported import is contract rejected instead of fake accepted")
    void unsupportedImportRejectedByContract() throws Exception {
        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.POST, "/import")));

        assertThat(response.getCode()).isEqualTo(409);
        JsonNode body = parseBody(response);
        assertThat(body.path("error").asText()).isEqualTo("HIE_OPERATION_NOT_SUPPORTED");
        assertThat(hieContract.lastRequest.operation()).isEqualTo(HieIntegrationContract.Operation.IMPORT);
    }

    @Test
    @DisplayName("200 - status delegates to contract status")
    void statusDelegatesToContract() throws Exception {
        HttpResponse response = runPromise(() -> servlet.serve(contextRequest(HttpMethod.GET, "/status/hie-1")));

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = parseBody(response);
        assertThat(body.path("requestId").asText()).isEqualTo("hie-1");
        assertThat(body.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(body.path("reasonCode").asText()).isEqualTo("HIE_ACCEPTED");
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(),
            eq("patient-1"),
            eq("hie-integration"),
            eq("READ"),
            eq("tenant-1"),
            nullable(String.class)
        );
    }

    private static HttpRequest contextRequest(HttpMethod method, String path) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .withHeader(HttpHeaders.of("X-Persona"), "patient")
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .build();
    }

    private JsonNode parseBody(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
    }

    private static final class CapturingHieContract implements HieIntegrationContract {
        private HieIntegrationRequest lastRequest;

        @Override
        public String contractId() {
            return "test-hie-contract";
        }

        @Override
        public Set<Operation> supportedOperations() {
            return Set.of(Operation.EXPORT, Operation.SYNC);
        }

        @Override
        public Promise<HieIntegrationResult> submit(HieIntegrationRequest request) {
            lastRequest = request;
            boolean supported = supportedOperations().contains(request.operation());
            return Promise.of(new HieIntegrationResult(
                "hie-1",
                request.operation(),
                contractId(),
                supported ? "ACCEPTED" : "REJECTED",
                supported,
                supported ? "HIE_ACCEPTED" : "HIE_OPERATION_NOT_SUPPORTED",
                supported ? "accepted" : "operation is not supported by configured HIE contract"
            ));
        }

        @Override
        public Promise<HieIntegrationStatus> getStatus(String requestId, String correlationId) {
            return Promise.of(new HieIntegrationStatus(
                requestId,
                Operation.EXPORT,
                contractId(),
                "ACCEPTED",
                "HIE_ACCEPTED",
                "accepted"
            ));
        }
    }
}
