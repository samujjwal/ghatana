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

    private final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
    private ServerTestHarness harness;
    private A2aWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        harness = new ServerTestHarness().start(); // GH-90000
        handler = new A2aWebSocketHandler("localhost", harness.getGrpcPort()); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (handler != null) { // GH-90000
            handler.shutdown(); // GH-90000
        }
        if (harness != null) { // GH-90000
            harness.close(); // GH-90000
        }
    }

    @Test
    void runStatusReportFlow() throws Exception { // GH-90000
        String correlationId = "corr-a2a";
        Envelope runEnvelope =
                Envelope.request( // GH-90000
                        "req-run",
                        correlationId,
                        Map.of( // GH-90000
                                "operation",
                                "run",
                                "repoRoot",
                                "/tmp/a2a-repo",
                                "languages",
                                List.of("java"),
                                "formatters",
                                Boolean.TRUE));

        String runResponseJson =
                handler.handleMessage(objectMapper.writeValueAsString(runEnvelope)); // GH-90000
        Envelope runResponse = objectMapper.readValue(runResponseJson, Envelope.class); // GH-90000
        assertThat(runResponse.type()).isEqualTo(EnvelopeTypes.TASK_RESULT); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> runPayload = (Map<String, Object>) runResponse.payload(); // GH-90000
        assertThat(runPayload.get("status")).isEqualTo("ACCEPTED");
        String jobId = (String) runPayload.get("jobId");
        assertThat(jobId).isNotBlank(); // GH-90000

        Envelope statusEnvelope =
                Envelope.request("req-status", correlationId, Map.of("operation", "status")); // GH-90000
        String statusJson = handler.handleMessage(objectMapper.writeValueAsString(statusEnvelope)); // GH-90000
        Envelope statusResponse = objectMapper.readValue(statusJson, Envelope.class); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> statusPayload = (Map<String, Object>) statusResponse.payload(); // GH-90000
        assertThat(statusPayload.get("jobId")).isEqualTo(jobId);
        assertThat(statusPayload.get("state")).isEqualTo("QUEUED");

        Envelope reportEnvelope =
                Envelope.request("req-report", correlationId, Map.of("operation", "report")); // GH-90000
        String reportJson = handler.handleMessage(objectMapper.writeValueAsString(reportEnvelope)); // GH-90000
        Envelope reportResponse = objectMapper.readValue(reportJson, Envelope.class); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> reportPayload = (Map<String, Object>) reportResponse.payload(); // GH-90000
        assertThat(reportPayload.get("jobId")).isEqualTo(jobId);
        assertThat(reportPayload.get("summaryJson").toString()).contains("QUEUED");
    }

    @Test
    void diagnoseOperationProducesDiagnostics() throws Exception { // GH-90000
        Envelope diagnoseEnvelope =
                Envelope.request( // GH-90000
                        "req-diagnose",
                        "corr-diagnose",
                        Map.of( // GH-90000
                                "operation", "diagnose",
                                "repoRoot", "/tmp/a2a-repo",
                                "languages", List.of("java", "typescript"))); // GH-90000

        String responseJson =
                handler.handleMessage(objectMapper.writeValueAsString(diagnoseEnvelope)); // GH-90000
        Envelope response = objectMapper.readValue(responseJson, Envelope.class); // GH-90000

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.payload(); // GH-90000
        assertThat(payload.get("status")).isEqualTo("COMPLETED");
        assertThat(payload).containsKey("diagnostics");
    }
}
