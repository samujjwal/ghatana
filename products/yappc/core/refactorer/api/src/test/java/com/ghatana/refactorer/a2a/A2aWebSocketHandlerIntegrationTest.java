package com.ghatana.refactorer.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.refactorer.server.testutils.ServerTestHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration coverage for the A2A adapter against the service-server gRPC layer. 
 * @doc.type class
 * @doc.purpose Handles a2a web socket handler integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class A2aWebSocketHandlerIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServerTestHarness harness;
    private A2aWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        harness = new ServerTestHarness().start();
        handler = new A2aWebSocketHandler("localhost", harness.getGrpcPort());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (handler != null) {
            handler.shutdown();
        }
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void runStatusReportFlow() throws Exception {
        String correlationId = "corr-a2a";
        Envelope runEnvelope =
                Envelope.request(
                        "req-run",
                        correlationId,
                        Map.of(
                                "operation",
                                "run",
                                "repoRoot",
                                "/tmp/a2a-repo",
                                "languages",
                                List.of("java"),
                                "formatters",
                                Boolean.TRUE));

        String runResponseJson =
                handler.handleMessage(objectMapper.writeValueAsString(runEnvelope));
        Envelope runResponse = objectMapper.readValue(runResponseJson, Envelope.class);
        assertThat(runResponse.type()).isEqualTo(EnvelopeTypes.TASK_RESULT);
        @SuppressWarnings("unchecked")
        Map<String, Object> runPayload = (Map<String, Object>) runResponse.payload();
        assertThat(runPayload.get("status")).isEqualTo("ACCEPTED");
        String jobId = (String) runPayload.get("jobId");
        assertThat(jobId).isNotBlank();

        Envelope statusEnvelope =
                Envelope.request("req-status", correlationId, Map.of("operation", "status"));
        String statusJson = handler.handleMessage(objectMapper.writeValueAsString(statusEnvelope));
        Envelope statusResponse = objectMapper.readValue(statusJson, Envelope.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> statusPayload = (Map<String, Object>) statusResponse.payload();
        assertThat(statusPayload.get("jobId")).isEqualTo(jobId);
        assertThat(statusPayload.get("state")).isEqualTo("QUEUED");

        Envelope reportEnvelope =
                Envelope.request("req-report", correlationId, Map.of("operation", "report"));
        String reportJson = handler.handleMessage(objectMapper.writeValueAsString(reportEnvelope));
        Envelope reportResponse = objectMapper.readValue(reportJson, Envelope.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> reportPayload = (Map<String, Object>) reportResponse.payload();
        assertThat(reportPayload.get("jobId")).isEqualTo(jobId);
        assertThat(reportPayload.get("summaryJson").toString()).contains("QUEUED");
    }

    @Test
    void diagnoseOperationProducesDiagnostics() throws Exception {
        Envelope diagnoseEnvelope =
                Envelope.request(
                        "req-diagnose",
                        "corr-diagnose",
                        Map.of(
                                "operation", "diagnose",
                                "repoRoot", "/tmp/a2a-repo",
                                "languages", List.of("java", "typescript")));

        String responseJson =
                handler.handleMessage(objectMapper.writeValueAsString(diagnoseEnvelope));
        Envelope response = objectMapper.readValue(responseJson, Envelope.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.payload();
        assertThat(payload.get("status")).isEqualTo("COMPLETED");
        assertThat(payload).containsKey("diagnostics");
    }
}
