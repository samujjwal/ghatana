package com.ghatana.refactorer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.api.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MCP tool adapter that translates MCP tool calls to Polyfix gRPC service calls. Provides the
 * bridge between MCP runtime and Polyfix service.
 
 * @doc.type class
 * @doc.purpose Handles mcp tool adapter operations
 * @doc.layer core
 * @doc.pattern Adapter
*/
public final class McpToolAdapter {
    private static final Logger logger = LogManager.getLogger(McpToolAdapter.class);
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    private final ManagedChannel channel;
    private final PolyfixServiceGrpc.PolyfixServiceBlockingStub polyfixStub;

    /**
     * Creates a new MCP tool adapter.
     *
     * @param serverHost Polyfix server host
     * @param serverPort Polyfix server gRPC port
     */
    public McpToolAdapter(String serverHost, int serverPort) {
        this.channel =
                ManagedChannelBuilder.forAddress(serverHost, serverPort).usePlaintext().build();
        this.polyfixStub = PolyfixServiceGrpc.newBlockingStub(channel);

        logger.info("MCP adapter connected to Polyfix server at {}:{}", serverHost, serverPort);
    }

    /**
     * Handles the polyfix.run MCP tool call.
     *
     * @param arguments tool arguments from MCP
     * @return tool result
     */
    public McpToolResult handleRun(Map<String, Object> arguments) {
        try {
            logger.info("Handling polyfix.run MCP tool call");

            // Parse arguments
            String repoRoot = (String) arguments.get("repoRoot");
            @SuppressWarnings("unchecked")
            List<String> languages = (List<String>) arguments.getOrDefault("languages", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> policies =
                    (Map<String, Object>) arguments.getOrDefault("policies", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> budgets =
                    (Map<String, Object>) arguments.getOrDefault("budgets", Map.of());
            Boolean formatters = (Boolean) arguments.getOrDefault("formatters", true);
            String idempotencyKey = (String) arguments.get("idempotencyKey");

            // Build gRPC request
            DiagnoseRequest.Builder configBuilder =
                    DiagnoseRequest.newBuilder().setRepoRoot(repoRoot).setFormatters(formatters);

            // Add languages
            for (String lang : languages) {
                configBuilder.addLanguages(Language.newBuilder().setId(lang).build());
            }

            // Add policies
            for (Map.Entry<String, Object> entry : policies.entrySet()) {
                configBuilder.addPolicies(
                        PolicyKV.newBuilder()
                                .setKey(entry.getKey())
                                .setValue(String.valueOf(entry.getValue()))
                                .build());
            }

            // Add budget
            Budget.Builder budgetBuilder = Budget.newBuilder();
            if (budgets.containsKey("maxPasses")) {
                budgetBuilder.setMaxPasses(((Number) budgets.get("maxPasses")).intValue());
            }
            if (budgets.containsKey("maxEditsPerFile")) {
                budgetBuilder.setMaxEditsPerFile(
                        ((Number) budgets.get("maxEditsPerFile")).intValue());
            }
            if (budgets.containsKey("timeoutSeconds")) {
                budgetBuilder.setTimeoutSeconds(
                        ((Number) budgets.get("timeoutSeconds")).intValue());
            }
            configBuilder.setBudget(budgetBuilder.build());

            RunRequest request =
                    RunRequest.newBuilder()
                            .setConfig(configBuilder.build())
                            .setIdempotencyKey(idempotencyKey != null ? idempotencyKey : "")
                            .setDryRun(false)
                            .build();

            // Call Polyfix service
            JobId response = polyfixStub.run(request);

            logger.info("Polyfix job created: {}", response.getId());

            return new McpToolResult(
                    true, Map.of("jobId", response.getId()), "Job created successfully");

        } catch (Exception e) {
            logger.error("Error handling polyfix.run", e);
            return new McpToolResult(false, null, "Failed to create job: " + e.getMessage());
        }
    }

    /**
     * Handles the polyfix.status MCP tool call.
     *
     * @param arguments tool arguments from MCP
     * @return tool result
     */
    public McpToolResult handleStatus(Map<String, Object> arguments) {
        try {
            logger.info("Handling polyfix.status MCP tool call");

            String jobId = (String) arguments.get("jobId");
            if (jobId == null || jobId.isEmpty()) {
                return new McpToolResult(false, null, "jobId is required");
            }

            JobId request = JobId.newBuilder().setId(jobId).build();
            RunStatus response = polyfixStub.getStatus(request);

            Map<String, Object> result =
                    Map.of(
                            "jobId", response.getJobId(),
                            "state", response.getState(),
                            "pass", response.getPass(),
                            "startedAt", response.getStartedAt(),
                            "updatedAt", response.getUpdatedAt(),
                            "toolVersions", response.getToolVersionsMap(),
                            "errorMessage", response.getErrorMessage());

            return new McpToolResult(true, result, "Status retrieved successfully");

        } catch (Exception e) {
            logger.error("Error handling polyfix.status", e);
            return new McpToolResult(false, null, "Failed to get status: " + e.getMessage());
        }
    }

    /**
     * Handles the polyfix.report MCP tool call.
     *
     * @param arguments tool arguments from MCP
     * @return tool result
     */
    public McpToolResult handleReport(Map<String, Object> arguments) {
        try {
            logger.info("Handling polyfix.report MCP tool call");

            String jobId = (String) arguments.get("jobId");
            if (jobId == null || jobId.isEmpty()) {
                return new McpToolResult(false, null, "jobId is required");
            }

            JobId request = JobId.newBuilder().setId(jobId).build();
            Report response = polyfixStub.getReport(request);

            Map<String, Object> result =
                    Map.of(
                            "jobId", response.getJobId(),
                            "summaryJson", response.getSummaryJson(),
                            "generatedAt", response.getGeneratedAt()
                            // Note: Not including binary zip content in MCP response
                            );

            return new McpToolResult(true, result, "Report retrieved successfully");

        } catch (Exception e) {
            logger.error("Error handling polyfix.report", e);
            return new McpToolResult(false, null, "Failed to get report: " + e.getMessage());
        }
    }

    /**
     * Handles the polyfix.diagnose MCP tool call.
     *
     * @param arguments tool arguments from MCP
     * @return tool result
     */
    public McpToolResult handleDiagnose(Map<String, Object> arguments) {
        try {
            logger.info("Handling polyfix.diagnose MCP tool call");

            String repoRoot = (String) arguments.get("repoRoot");
            @SuppressWarnings("unchecked")
            List<String> languages = (List<String>) arguments.getOrDefault("languages", List.of());

            DiagnoseRequest.Builder requestBuilder =
                    DiagnoseRequest.newBuilder().setRepoRoot(repoRoot);

            // Add languages
            for (String lang : languages) {
                requestBuilder.addLanguages(Language.newBuilder().setId(lang).build());
            }

            DiagnoseRequest request = requestBuilder.build();
            DiagnoseResponse response = polyfixStub.diagnose(request);

            // Convert diagnostics to MCP-friendly format
            List<Map<String, Object>> diagnostics =
                    response.getDiagnosticsList().stream()
                            .map(
                                    d ->
                                            Map.<String, Object>of(
                                                    "tool", d.getTool(),
                                                    "rule", d.getRule(),
                                                    "message", d.getMessage(),
                                                    "file", d.getFile(),
                                                    "line", (Object) d.getLine(),
                                                    "column", (Object) d.getColumn(),
                                                    "severity", d.getSeverity()))
                            .toList();

            Map<String, Object> result =
                    Map.of(
                            "diagnostics", diagnostics,
                            "executionId", response.getExecutionId(),
                            "timestamp", response.getTimestamp());

            return new McpToolResult(true, result, "Diagnostics completed successfully");

        } catch (Exception e) {
            logger.error("Error handling polyfix.diagnose", e);
            return new McpToolResult(false, null, "Failed to run diagnostics: " + e.getMessage());
        }
    }

    /** Shuts down the adapter and closes connections. */
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            logger.info("MCP adapter shut down successfully");
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down MCP adapter", e);
            Thread.currentThread().interrupt();
        }
    }
}
