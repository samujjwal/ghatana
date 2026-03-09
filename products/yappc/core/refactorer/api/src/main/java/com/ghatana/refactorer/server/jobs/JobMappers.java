package com.ghatana.refactorer.server.jobs;

import com.ghatana.refactorer.api.v1.DiagnoseRequest;
import com.ghatana.refactorer.api.v1.Report;
import com.ghatana.refactorer.api.v1.RunRequest;
import com.ghatana.refactorer.api.v1.RunStatus;
import com.ghatana.refactorer.api.v1.UnifiedDiagnostic;
import com.ghatana.refactorer.server.auth.TenantContext;
import com.ghatana.refactorer.server.dto.RestModels;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapping helpers between REST/gRPC payloads and internal job models.
 *
 * @doc.type class
 * @doc.purpose Convert transport-layer objects into JobRecord/JobSubmission structures and vice versa.
 * @doc.layer product
 * @doc.pattern Mapper
 */
public final class JobMappers {
    private JobMappers() {}

    public static RestModels.RunStatus toRestStatus(JobRecord record) {
        return new RestModels.RunStatus(
                record.jobId(),
                record.state().name(),
                record.currentPass(),
                record.createdAt(),
                record.updatedAt(),
                record.attributes(),
                record.error().orElse(null));
    }

    public static RestModels.Report toRestReport(JobRecord record) {
        String summaryJson = String.format("{\"status\":\"%s\"}", record.state().name());
        return new RestModels.Report(record.jobId(), summaryJson, null, record.updatedAt());
    }

    public static RunStatus toProtoStatus(JobRecord record) {
        return RunStatus.newBuilder()
                .setJobId(record.jobId())
                .setState(record.state().name())
                .setPass(record.currentPass())
                .setStartedAt(String.valueOf(record.createdAt()))
                .setUpdatedAt(String.valueOf(record.updatedAt()))
                .putAllToolVersions(record.attributes())
                .setErrorMessage(record.error().orElse(""))
                .build();
    }

    public static Report toProtoReport(JobRecord record) {
        String summaryJson = String.format("{\"status\":\"%s\"}", record.state().name());
        return Report.newBuilder()
                .setJobId(record.jobId())
                .setSummaryJson(summaryJson)
                .setGeneratedAt(String.valueOf(record.updatedAt()))
                .build();
    }

    public static JobSubmission fromRestRunRequest(
            RestModels.RunRequest request, TenantContext context) {
        Objects.requireNonNull(request, "request must not be null");
        JobSubmission.Builder builder = JobSubmission.builder(context).dryRun(request.dryRun());
        RestModels.DiagnoseRequest config = request.config();
        if (config != null) {
            populateRestDiagnoseAttributes(builder, config);
        }
        builder.attribute("idempotencyKey", request.idempotencyKey());
        return builder.build();
    }

    public static JobSubmission fromProtoRunRequest(RunRequest request, TenantContext context) {
        Objects.requireNonNull(request, "request must not be null");
        JobSubmission.Builder builder = JobSubmission.builder(context).dryRun(request.getDryRun());
        if (request.hasConfig()) {
            DiagnoseRequest config = request.getConfig();
            builder.attribute("repoRoot", config.getRepoRoot());
            builder.attribute("tenantId", config.getTenantId());
            builder.attribute("formatters", Boolean.toString(config.getFormatters()));
            builder.attribute("languages", joinProtoLanguages(config));
        }
        builder.attribute("idempotencyKey", request.getIdempotencyKey());
        return builder.build();
    }

    public static JobSubmission fromRestDiagnoseRequest(
            RestModels.DiagnoseRequest request, TenantContext context) {
        Objects.requireNonNull(request, "request must not be null");
        JobSubmission.Builder builder = JobSubmission.builder(context).dryRun(true);
        populateRestDiagnoseAttributes(builder, request);
        return builder.build();
    }

    public static JobSubmission fromProtoDiagnoseRequest(
            DiagnoseRequest request, TenantContext context) {
        Objects.requireNonNull(request, "request must not be null");
        JobSubmission.Builder builder = JobSubmission.builder(context).dryRun(true);
        builder.attribute("repoRoot", request.getRepoRoot());
        builder.attribute("tenantId", request.getTenantId());
        builder.attribute("formatters", Boolean.toString(request.getFormatters()));
        builder.attribute("languages", joinProtoLanguages(request));
        builder.attribute("budget.maxPasses", String.valueOf(request.getBudget().getMaxPasses()));
        builder.attribute(
                "budget.maxEditsPerFile", String.valueOf(request.getBudget().getMaxEditsPerFile()));
        builder.attribute(
                "budget.timeoutSeconds", String.valueOf(request.getBudget().getTimeoutSeconds()));
        request.getPoliciesList()
                .forEach(
                        policy ->
                                builder.attribute("policy." + policy.getKey(), policy.getValue()));
        return builder.build();
    }

    public static TenantContext tenantContextFromProto(RunRequest request) {
        DiagnoseRequest config = request.getConfig();
        String tenantId = config != null ? config.getTenantId() : "default";
        return TenantContext.of(tenantId, "grpc-client", Set.of(), Map.of());
    }

    public static TenantContext tenantContextFromRest(RestModels.RunRequest request) {
        RestModels.DiagnoseRequest config = request.config();
        String tenantId = config != null ? config.tenantId() : "default";
        return TenantContext.of(tenantId, "rest-client", Set.of(), Map.of());
    }

    public static TenantContext tenantContextFromRest(RestModels.DiagnoseRequest request) {
        String tenantId = request.tenantId() != null ? request.tenantId() : "default";
        return TenantContext.of(tenantId, "rest-client", Set.of(), Map.of());
    }

    public static TenantContext tenantContextFromProto(DiagnoseRequest request) {
        String tenantId = request.getTenantId().isBlank() ? "default" : request.getTenantId();
        return TenantContext.of(tenantId, "grpc-client", Set.of(), Map.of());
    }

    public static UnifiedDiagnostic toProtoDiagnostic(RestModels.UnifiedDiagnostic diagnostic) {
        return UnifiedDiagnostic.newBuilder()
                .setTool(diagnostic.tool())
                .setRule(diagnostic.rule())
                .setMessage(diagnostic.message())
                .setFile(diagnostic.file())
                .setLine(diagnostic.line())
                .setColumn(diagnostic.column())
                .setSeverity(diagnostic.severity())
                .setTimestamp(diagnostic.timestamp())
                .build();
    }

    private static void populateRestDiagnoseAttributes(
            JobSubmission.Builder builder, RestModels.DiagnoseRequest request) {
        builder.attribute("repoRoot", request.repoRoot());
        builder.attribute("tenantId", request.tenantId());
        builder.attribute("formatters", Boolean.toString(request.formatters()));
        builder.attribute("languages", joinRestLanguages(request.languages()));
        if (request.budget() != null) {
            builder.attribute("budget.maxPasses", String.valueOf(request.budget().maxPasses()));
            builder.attribute(
                    "budget.maxEditsPerFile", String.valueOf(request.budget().maxEditsPerFile()));
            builder.attribute(
                    "budget.timeoutSeconds", String.valueOf(request.budget().timeoutSeconds()));
        }
        if (request.policies() != null) {
            request.policies()
                    .forEach(policy -> builder.attribute("policy." + policy.key(), policy.value()));
        }
    }

    private static String joinRestLanguages(java.util.List<RestModels.Language> languages) {
        if (languages == null) {
            return "";
        }
        return languages.stream()
                .map(RestModels.Language::id)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.joining(","));
    }

    private static String joinProtoLanguages(DiagnoseRequest request) {
        return request.getLanguagesList().stream()
                .map(lang -> lang.getId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.joining(","));
    }
}
