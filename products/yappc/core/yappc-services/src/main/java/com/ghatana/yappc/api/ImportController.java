/**
 * Import Controller
 * 
 * HTTP API controller for governed import-source requests.
 * Handles import requests with governance, validation, and job tracking.
 * 
 * @doc.type class
 * @doc.purpose Governed import HTTP API
 * @doc.layer product
 * @doc.pattern Controller
 */

package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.services.compiler.ArtifactCompileJobService;
import com.ghatana.yappc.services.import_.ImportValidationService;
import com.ghatana.yappc.services.import_.ImportValidationResult;
import com.ghatana.yappc.services.import_.SourceImportJobRequest;
import com.ghatana.yappc.services.import_.SourceImportJobService;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;

/**
 * Production-grade controller for governed import requests.
 */
public final class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ObjectMapper objectMapper;
    private final ImportValidationService validationService;
    private final SourceImportJobService sourceImportJobService;
    private final ArtifactCompileJobService artifactCompileJobService;

    public ImportController(
            @NotNull ObjectMapper objectMapper,
            @NotNull ImportValidationService validationService,
            @NotNull SourceImportJobService sourceImportJobService,
            @NotNull ArtifactCompileJobService artifactCompileJobService
    ) {
        this.objectMapper = objectMapper;
        this.validationService = validationService;
        this.sourceImportJobService = Objects.requireNonNull(sourceImportJobService, "sourceImportJobService must not be null");
        this.artifactCompileJobService = Objects.requireNonNull(artifactCompileJobService, "artifactCompileJobService must not be null");
    }

    public Promise<HttpResponse> createImportJob(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    ImportRequest req = objectMapper.readValue(body.getArray(), ImportRequest.class);

                    // Validate required fields
                    if (req.sourceType() == null || req.sourceType().isBlank()) {
                        return Promise.of(badRequest400("sourceType is required"));
                    }
                    if (req.projectId() == null || req.projectId().isBlank()) {
                        return Promise.of(badRequest400("projectId is required"));
                    }

                    // Extract principal for authorization context
                    Principal principal = request.getAttachment(Principal.class);
                    if (principal == null) {
                        return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"Unauthenticated\"}")
                            .build());
                    }

                    String tenantId = principal.getTenantId();
                    String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
                    String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
                    if (workspaceId == null || workspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
                        return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                            .build());
                    }
                    if (!scopedProjectId.equals(req.projectId())) {
                        return Promise.of(HttpResponse.ofCode(403)
                            .withJson("{\"error\":\"Forbidden: project scope mismatch\"}")
                            .build());
                    }

                    // Validate import source
                    ImportValidationResult validationResult = validationService.validateImportSource(req);
                    if (!validationResult.isValid()) {
                        log.warn("Import source validation failed: projectId={}, errors={}", 
                                req.projectId(), validationResult.errors());
                        return Promise.of(badRequest400("Import source validation failed: " + validationResult.errors()));
                    }

                    SourceImportJobRequest jobRequest = new SourceImportJobRequest(
                        scopedProjectId,
                        workspaceId,
                        tenantId,
                        sourceLocatorValue(req),
                        req.sourceType(),
                        principal.getName(),
                        Map.of("correlationId", req.correlationId() == null ? "" : req.correlationId())
                    );

                    return sourceImportJobService.submitJob(jobRequest)
                        .map(jobId -> {
                            triggerAsyncCompile(jobId, principal, tenantId, workspaceId, scopedProjectId, req);
                            ImportJobResponse response = new ImportJobResponse(
                                jobId,
                                scopedProjectId,
                                req.sourceType(),
                                ImportJobStatus.PENDING,
                                "Import job submitted",
                                java.time.Instant.now().toString(),
                                tenantId,
                                principal.getName()
                            );
                            log.info("Import job created: jobId={}, projectId={}, sourceType={}",
                                response.jobId(), scopedProjectId, req.sourceType());
                            return ok200Json(objectMapper.writeValueAsString(response));
                        });

                } catch (Exception e) {
                    log.error("Error creating import job", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Import job request failed", e));
    }

    public Promise<HttpResponse> getImportJobStatus(HttpRequest request) {
        String jobId = request.getPathParameter("jobId");
        if (jobId == null || jobId.isBlank()) {
            jobId = request.getQueryParameter("jobId");
        }
        
        if (jobId == null || jobId.isBlank()) {
            return Promise.of(badRequest400("jobId is required"));
        }

        // P0: Require scope headers for job status lookup to prevent cross-scope access
        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            return Promise.of(HttpResponse.ofCode(401)
                .withJson("{\"error\":\"Unauthenticated\"}")
                .build());
        }

        String tenantId = principal.getTenantId();
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String scopedProjectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
        if (workspaceId == null || workspaceId.isBlank() || scopedProjectId == null || scopedProjectId.isBlank()) {
            return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"Bad Request: missing X-Workspace-ID or X-Project-ID scope header\"}")
                .build());
        }

        log.debug("Getting import job status with scope: jobId={}, tenantId={}, workspaceId={}, projectId={}", 
            jobId, tenantId, workspaceId, scopedProjectId);

        return sourceImportJobService.getJobStatus(jobId, tenantId, workspaceId, scopedProjectId)
            .map(job -> {
                if (job == null) {
                    return HttpResponse.ofCode(404)
                        .withJson("{\"error\":\"Source import job not found\"}")
                        .build();
                }
                ImportJobStatusResponse response = new ImportJobStatusResponse(
                    job.jobId(),
                    mapStatus(job.status()),
                    (int) Math.round(job.progress().percentage()),
                    job.progress().totalSteps(),
                    job.progress().currentPhase(),
                    java.time.Instant.now().toString()
                );
                try {
                    return ok200Json(objectMapper.writeValueAsString(response));
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.error("Error serializing job status", e);
                    return badRequest400("Error serializing response");
                }
            });
    }

    private void triggerAsyncCompile(
        String jobId,
        Principal principal,
        String tenantId,
        String workspaceId,
        String projectId,
        ImportRequest req
    ) {
        sourceImportJobService.updateStatus(jobId, SourceImportJob.JobStatus.VALIDATING)
            .then(ignored -> sourceImportJobService.updateProgress(jobId, 1, 5, 20, "VALIDATING"))
            .then(ignored -> artifactCompileJobService.compile(new ArtifactCompileJobService.CompileJobRequest(
                jobId,
                tenantId,
                workspaceId,
                projectId,
                principal.getName(),
                SourceLocator.builder()
                    .provider(req.sourceType())
                    .repoId(sourceLocatorValue(req))
                    .tenantId(tenantId)
                    .workspaceId(workspaceId)
                    .projectId(projectId)
                    .build()
            )))
            .then(result -> {
                if (result.success()) {
                    return sourceImportJobService.updateProgress(jobId, 5, 5, 100, "COMPLETED")
                        .then(ignored -> sourceImportJobService.updateStatus(jobId, SourceImportJob.JobStatus.COMPLETED));
                }
                return sourceImportJobService.updateStatus(jobId, SourceImportJob.JobStatus.FAILED);
            })
            .whenException(error -> {
                log.error("Source import compile failed: jobId={}", jobId, error);
                sourceImportJobService.updateStatus(jobId, SourceImportJob.JobStatus.FAILED)
                    .whenException(statusError -> log.error("Failed to mark job as failed: jobId={}", jobId, statusError));
            });
    }

    private static String sourceLocatorValue(ImportRequest req) {
        if (req.source() != null && !req.source().isBlank()) {
            return req.source();
        }
        if (req.sourceUrl() != null && !req.sourceUrl().isBlank()) {
            return req.sourceUrl();
        }
        if (req.sourceData() != null && !req.sourceData().isBlank()) {
            return req.sourceData();
        }
        throw new IllegalArgumentException("Either source, sourceUrl, or sourceData must be provided");
    }

    private static ImportJobStatus mapStatus(SourceImportJob.JobStatus status) {
        return switch (status) {
            case SUBMITTED -> ImportJobStatus.PENDING;
            case VALIDATING -> ImportJobStatus.VALIDATING;
            case DECOMPILING, MAPPING -> ImportJobStatus.IMPORTING;
            case RESIDUAL_REVIEW_REQUIRED -> ImportJobStatus.MAPPING;
            case COMPLETED -> ImportJobStatus.COMPLETED;
            case FAILED -> ImportJobStatus.FAILED;
            case CANCELLED -> ImportJobStatus.CANCELLED;
        };
    }

    public record ImportRequest(
            String sourceType,
            String source,
            String projectId,
            String workspaceId,
            String sourceUrl,
            String sourceData,
            Map<String, String> options,
            String correlationId
    ) {}

    public record ImportJobResponse(
            String jobId,
            String projectId,
            String sourceType,
            ImportJobStatus status,
            String message,
            String createdAt,
            String tenantId,
            String createdBy
    ) {}

    public record ImportJobStatusResponse(
            String jobId,
            ImportJobStatus status,
            int progress,
            int totalSteps,
            String message,
            String updatedAt
    ) {}

    public enum ImportJobStatus {
        PENDING,
        VALIDATING,
        IMPORTING,
        MAPPING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
