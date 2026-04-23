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
    void setUp() throws Exception { // GH-90000
        harness = new ServerTestHarness().start(); // GH-90000
        adapter = new McpToolAdapter("localhost", harness.getGrpcPort()); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (adapter != null) { // GH-90000
            adapter.shutdown(); // GH-90000
        }
        if (harness != null) { // GH-90000
            harness.close(); // GH-90000
        }
    }

    @Test
    void runStatusReportAndDiagnoseFlow() { // GH-90000
        McpToolResult runResult =
                adapter.handleRun( // GH-90000
                        Map.of( // GH-90000
                                "repoRoot",
                                "/tmp/mcp-repo",
                                "languages",
                                List.of("java", "python"), // GH-90000
                                "formatters",
                                Boolean.TRUE,
                                "idempotencyKey",
                                "mcp-run-123"));

        assertThat(runResult.success()).isTrue(); // GH-90000
        assertThat(runResult.data()).containsKey("jobId");
        String jobId = (String) runResult.data().get("jobId");

        McpToolResult statusResult = adapter.handleStatus(Map.of("jobId", jobId)); // GH-90000
        assertThat(statusResult.success()).isTrue(); // GH-90000
        assertThat(statusResult.data()).containsEntry("state", "QUEUED"); // GH-90000
        assertThat(statusResult.data()).containsEntry("jobId", jobId); // GH-90000

        McpToolResult reportResult = adapter.handleReport(Map.of("jobId", jobId)); // GH-90000
        assertThat(reportResult.success()).isTrue(); // GH-90000
        assertThat(reportResult.data()).containsKey("summaryJson");

        McpToolResult diagnoseResult =
                adapter.handleDiagnose( // GH-90000
                        Map.of( // GH-90000
                                "repoRoot",
                                "/tmp/mcp-repo",
                                "languages",
                                List.of("java", "typescript"))); // GH-90000
        assertThat(diagnoseResult.success()).isTrue(); // GH-90000
        assertThat(diagnoseResult.data()).containsKey("diagnostics");
    }
}
