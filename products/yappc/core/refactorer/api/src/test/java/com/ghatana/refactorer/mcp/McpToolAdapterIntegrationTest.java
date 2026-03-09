package com.ghatana.refactorer.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.refactorer.server.testutils.ServerTestHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests ensuring the MCP adapter drives the service-server gRPC facade correctly. 
 * @doc.type class
 * @doc.purpose Handles mcp tool adapter integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class McpToolAdapterIntegrationTest {

    private ServerTestHarness harness;
    private McpToolAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        harness = new ServerTestHarness().start();
        adapter = new McpToolAdapter("localhost", harness.getGrpcPort());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (adapter != null) {
            adapter.shutdown();
        }
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void runStatusReportAndDiagnoseFlow() {
        McpToolResult runResult =
                adapter.handleRun(
                        Map.of(
                                "repoRoot",
                                "/tmp/mcp-repo",
                                "languages",
                                List.of("java", "python"),
                                "formatters",
                                Boolean.TRUE,
                                "idempotencyKey",
                                "mcp-run-123"));

        assertThat(runResult.success()).isTrue();
        assertThat(runResult.data()).containsKey("jobId");
        String jobId = (String) runResult.data().get("jobId");

        McpToolResult statusResult = adapter.handleStatus(Map.of("jobId", jobId));
        assertThat(statusResult.success()).isTrue();
        assertThat(statusResult.data()).containsEntry("state", "QUEUED");
        assertThat(statusResult.data()).containsEntry("jobId", jobId);

        McpToolResult reportResult = adapter.handleReport(Map.of("jobId", jobId));
        assertThat(reportResult.success()).isTrue();
        assertThat(reportResult.data()).containsKey("summaryJson");

        McpToolResult diagnoseResult =
                adapter.handleDiagnose(
                        Map.of(
                                "repoRoot",
                                "/tmp/mcp-repo",
                                "languages",
                                List.of("java", "typescript")));
        assertThat(diagnoseResult.success()).isTrue();
        assertThat(diagnoseResult.data()).containsKey("diagnostics");
    }
}
