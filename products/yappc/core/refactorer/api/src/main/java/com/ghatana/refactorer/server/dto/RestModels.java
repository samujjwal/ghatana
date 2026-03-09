package com.ghatana.refactorer.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * REST API data transfer objects that mirror the protobuf models for JSON serialization.
 *
 * @doc.type class
 * @doc.purpose Provide typed request/response records shared by controllers and clients.
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class RestModels {

    private RestModels() {
        // Utility class
    }

    public record Language(String id) {}

    public record PolicyKV(String key, String value) {}

    public record Budget(
            @JsonProperty("maxPasses") int maxPasses,
            @JsonProperty("maxEditsPerFile") int maxEditsPerFile,
            @JsonProperty("timeoutSeconds") int timeoutSeconds) {}

    public record DiagnoseRequest(
            @JsonProperty("repoRoot") String repoRoot,
            @JsonProperty("includeGlobs") List<String> includeGlobs,
            @JsonProperty("languages") List<Language> languages,
            @JsonProperty("policies") List<PolicyKV> policies,
            Budget budget,
            boolean formatters,
            @JsonProperty("tenantId") String tenantId) {}

    public record UnifiedDiagnostic(
            String tool,
            String rule,
            String message,
            String file,
            int line,
            int column,
            String severity,
            Map<String, String> meta,
            long timestamp) {}

    public record DiagnoseResponse(
            List<UnifiedDiagnostic> diagnostics,
            @JsonProperty("executionId") String executionId,
            long timestamp) {}

    public record RunRequest(
            DiagnoseRequest config,
            @JsonProperty("idempotencyKey") String idempotencyKey,
            @JsonProperty("dryRun") boolean dryRun) {}

    public record JobResponse(@JsonProperty("jobId") String jobId) {}

    public record RunStatus(
            @JsonProperty("jobId") String jobId,
            String state,
            int pass,
            @JsonProperty("startedAt") long startedAt,
            @JsonProperty("updatedAt") long updatedAt,
            @JsonProperty("toolVersions") Map<String, String> toolVersions,
            @JsonProperty("errorMessage") String errorMessage) {}

    public record Report(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("summaryJson") String summaryJson,
            @JsonProperty("reportZip") byte[] reportZip,
            @JsonProperty("generatedAt") long generatedAt) {}

    public record ProgressEvent(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("eventType") String eventType,
            String message,
            UnifiedDiagnostic diagnostic,
            @JsonProperty("currentPass") int currentPass,
            @JsonProperty("totalPasses") int totalPasses,
            long timestamp) {}

    public record ErrorResponse(
            String code,
            String message,
            String details,
            @JsonProperty("correlationId") String correlationId) {}

    public record HealthResponse(String status, Map<String, String> details, long timestamp) {}

    public record PatternSubmissionRequest(
            String name,
            String spec,
            int confidence,
            List<String> tags) {}

    public record PatternAnalysisRequest(
            @JsonProperty("timeWindowHours") int timeWindowHours,
            @JsonProperty("minSupport") double minSupport,
            @JsonProperty("eventTypes") List<String> eventTypes) {}

    public record PatternDiscoveryRequest(
            @JsonProperty("eventTypes") List<String> eventTypes,
            @JsonProperty("timeWindowHours") int timeWindowHours,
            @JsonProperty("minSupport") double minSupport) {}

    public record CorrelationAnalysisRequest(
            @JsonProperty("eventTypes") List<String> eventTypes,
            @JsonProperty("timeWindowMinutes") int timeWindowMinutes,
            @JsonProperty("minCorrelation") double minCorrelation) {}

    public record PatternListRequest(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("minConfidence") double minConfidence,
            int limit) {}
}
