package com.ghatana.refactorer.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.api.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * WebSocket handler for A2A (Agent-to-Agent) communication. Maps envelope messages to Polyfix gRPC
 * service calls and streams responses back.
 
 * @doc.type class
 * @doc.purpose Handles a2a web socket handler operations
 * @doc.layer core
 * @doc.pattern Handler
*/
public final class A2aWebSocketHandler {
    private static final Logger logger = LogManager.getLogger(A2aWebSocketHandler.class);
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    private final ManagedChannel channel;
    private final PolyfixServiceGrpc.PolyfixServiceBlockingStub polyfixStub;
    private final Map<String, String> activeJobs = new ConcurrentHashMap<>();

    /**
     * Creates a new A2A WebSocket handler.
     *
     * @param serverHost Polyfix server host
     * @param serverPort Polyfix server gRPC port
     */
    public A2aWebSocketHandler(String serverHost, int serverPort) {
        this.channel =
                ManagedChannelBuilder.forAddress(serverHost, serverPort).usePlaintext().build();
        this.polyfixStub = PolyfixServiceGrpc.newBlockingStub(channel);

        logger.info(
                "A2A WebSocket handler connected to Polyfix server at {}:{}",
                serverHost,
                serverPort);
    }

    /**
     * Handles incoming envelope messages from WebSocket clients.
     *
     * @param envelopeJson JSON representation of the envelope
     * @return response envelope as JSON
     */
    public String handleMessage(String envelopeJson) {
        try {
            Envelope envelope = objectMapper.readValue(envelopeJson, Envelope.class);
            logger.debug("Received A2A envelope: type={}, id={}", envelope.type(), envelope.id());

            return switch (envelope.type()) {
                case EnvelopeTypes.TASK_REQUEST -> handleTaskRequest(envelope);
                case EnvelopeTypes.CAPABILITIES -> handleCapabilities(envelope);
                case EnvelopeTypes.HEARTBEAT -> handleHeartbeat(envelope);
                default -> {
                    logger.warn("Unknown envelope type: {}", envelope.type());
                    yield createErrorResponse(
                            envelope, "Unknown envelope type: " + envelope.type());
                }
            };

        } catch (Exception e) {
            logger.error("Error handling A2A message", e);
            return createErrorResponse(null, "Failed to process message: " + e.getMessage());
        }
    }

    /** Handles task request envelopes. */
    private String handleTaskRequest(Envelope envelope) throws Exception {
        Map<String, Object> payload = envelope.payload();
        String operation = (String) payload.get("operation");

        return switch (operation) {
            case "run" -> handleRunRequest(envelope);
            case "diagnose" -> handleDiagnoseRequest(envelope);
            case "status" -> handleStatusRequest(envelope);
            case "report" -> handleReportRequest(envelope);
            default -> createErrorResponse(envelope, "Unknown operation: " + operation);
        };
    }

    /** Handles run operation requests. */
    private String handleRunRequest(Envelope envelope) throws Exception {
        Map<String, Object> payload = envelope.payload();

        // Extract request parameters
        String repoRoot = (String) payload.get("repoRoot");
        @SuppressWarnings("unchecked")
        List<String> languages = (List<String>) payload.getOrDefault("languages", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> budgets =
                (Map<String, Object>) payload.getOrDefault("budgets", Map.of());
        Boolean formatters = (Boolean) payload.getOrDefault("formatters", true);

        // Build gRPC request
        DiagnoseRequest.Builder configBuilder =
                DiagnoseRequest.newBuilder().setRepoRoot(repoRoot).setFormatters(formatters);

        for (String lang : languages) {
            configBuilder.addLanguages(Language.newBuilder().setId(lang).build());
        }

        // Add budget constraints
        Budget.Builder budgetBuilder = Budget.newBuilder();
        if (budgets.containsKey("maxPasses")) {
            budgetBuilder.setMaxPasses(((Number) budgets.get("maxPasses")).intValue());
        }
        configBuilder.setBudget(budgetBuilder.build());

        RunRequest request =
                RunRequest.newBuilder().setConfig(configBuilder.build()).setDryRun(false).build();

        // Call Polyfix service
        JobId response = polyfixStub.run(request);

        // Store job mapping
        activeJobs.put(envelope.correlationId(), response.getId());

        logger.info(
                "A2A job created: {} for correlation: {}",
                response.getId(),
                envelope.correlationId());

        Envelope responseEnvelope =
                Envelope.response(
                        envelope.id(),
                        envelope.correlationId(),
                        Map.of("status", "ACCEPTED", "jobId", response.getId()));

        return objectMapper.writeValueAsString(responseEnvelope);
    }

    /** Handles diagnose operation requests. */
    private String handleDiagnoseRequest(Envelope envelope) throws Exception {
        Map<String, Object> payload = envelope.payload();

        String repoRoot = (String) payload.get("repoRoot");
        @SuppressWarnings("unchecked")
        List<String> languages = (List<String>) payload.getOrDefault("languages", List.of());

        DiagnoseRequest.Builder requestBuilder = DiagnoseRequest.newBuilder().setRepoRoot(repoRoot);

        for (String lang : languages) {
            requestBuilder.addLanguages(Language.newBuilder().setId(lang).build());
        }

        DiagnoseRequest request = requestBuilder.build();
        DiagnoseResponse response = polyfixStub.diagnose(request);

        // Convert diagnostics to A2A format
        List<Map<String, Object>> diagnostics =
                response.getDiagnosticsList().stream()
                        .map(
                                d ->
                                        Map.<String, Object>of(
                                                "tool", d.getTool(),
                                                "rule", d.getRule(),
                                                "message", d.getMessage(),
                                                "file", d.getFile(),
                                                "line", d.getLine(),
                                                "column", d.getColumn(),
                                                "severity", d.getSeverity()))
                        .toList();

        Envelope responseEnvelope =
                Envelope.response(
                        envelope.id(),
                        envelope.correlationId(),
                        Map.of(
                                "status",
                                "COMPLETED",
                                "diagnostics",
                                diagnostics,
                                "executionId",
                                response.getExecutionId()));

        return objectMapper.writeValueAsString(responseEnvelope);
    }

    /** Handles status operation requests. */
    private String handleStatusRequest(Envelope envelope) throws Exception {
        String jobId = activeJobs.get(envelope.correlationId());
        if (jobId == null) {
            return createErrorResponse(envelope, "No active job found for correlation ID");
        }

        JobId request = JobId.newBuilder().setId(jobId).build();
        RunStatus response = polyfixStub.getStatus(request);

        Envelope responseEnvelope =
                Envelope.response(
                        envelope.id(),
                        envelope.correlationId(),
                        Map.of(
                                "jobId", response.getJobId(),
                                "state", response.getState(),
                                "pass", response.getPass(),
                                "startedAt", response.getStartedAt(),
                                "updatedAt", response.getUpdatedAt()));

        return objectMapper.writeValueAsString(responseEnvelope);
    }

    /** Handles report operation requests. */
    private String handleReportRequest(Envelope envelope) throws Exception {
        String jobId = activeJobs.get(envelope.correlationId());
        if (jobId == null) {
            return createErrorResponse(envelope, "No active job found for correlation ID");
        }

        JobId request = JobId.newBuilder().setId(jobId).build();
        Report response = polyfixStub.getReport(request);

        Envelope responseEnvelope =
                Envelope.response(
                        envelope.id(),
                        envelope.correlationId(),
                        Map.of(
                                "jobId", response.getJobId(),
                                "summaryJson", response.getSummaryJson(),
                                "generatedAt", response.getGeneratedAt()));

        return objectMapper.writeValueAsString(responseEnvelope);
    }

    /** Handles capabilities requests. */
    private String handleCapabilities(Envelope envelope) throws Exception {
        Map<String, Object> capabilities =
                Map.of(
                        "operations", List.of("run", "diagnose", "status", "report"),
                        "languages", List.of("java", "typescript", "python", "bash", "rust"),
                        "version", "1.0.0");

        Envelope responseEnvelope = Envelope.capabilities(envelope.id(), capabilities);
        return objectMapper.writeValueAsString(responseEnvelope);
    }

    /** Handles heartbeat messages. */
    private String handleHeartbeat(Envelope envelope) throws Exception {
        Envelope responseEnvelope =
                Envelope.create(
                        EnvelopeTypes.ACK,
                        envelope.id(),
                        envelope.correlationId(),
                        Map.of("timestamp", System.currentTimeMillis()));

        return objectMapper.writeValueAsString(responseEnvelope);
    }

    /** Creates an error response envelope. */
    private String createErrorResponse(Envelope originalEnvelope, String errorMessage) {
        try {
            Envelope errorEnvelope =
                    Envelope.error(
                            originalEnvelope != null ? originalEnvelope.id() : "unknown",
                            originalEnvelope != null ? originalEnvelope.correlationId() : null,
                            errorMessage);

            return objectMapper.writeValueAsString(errorEnvelope);

        } catch (Exception e) {
            logger.error("Failed to create error response", e);
            return "{\"type\":\"polyfix.task.error\",\"message\":\"Internal error\"}";
        }
    }

    /** Shuts down the handler and closes connections. */
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            logger.info("A2A WebSocket handler shut down successfully");
        } catch (InterruptedException e) {
            logger.warn("Interrupted while shutting down A2A handler", e);
            Thread.currentThread().interrupt();
        }
    }
}
