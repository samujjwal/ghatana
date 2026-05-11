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
import com.ghatana.yappc.services.import_.ImportValidationService;
import com.ghatana.yappc.services.import_.ImportValidationResult;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;
import static io.activej.http.HttpResponse.*;

/**
 * Production-grade controller for governed import requests.
 */
public final class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ObjectMapper objectMapper;
    private final ImportValidationService validationService;

    public ImportController(
            @NotNull ObjectMapper objectMapper,
            @NotNull ImportValidationService validationService
    ) {
        this.objectMapper = objectMapper;
        this.validationService = validationService;
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

                    // Validate import source
                    ImportValidationResult validationResult = validationService.validateImportSource(req);
                    if (!validationResult.isValid()) {
                        log.warn("Import source validation failed: projectId={}, errors={}", 
                                req.projectId(), validationResult.errors());
                        return Promise.of(badRequest400("Import source validation failed: " + validationResult.errors()));
                    }

                    // Create import job
                    ImportJobResponse response = createImportJob(req, principal);

                    log.info("Import job created: jobId={}, projectId={}, sourceType={}", 
                            response.jobId(), req.projectId(), req.sourceType());

                    return Promise.of(ok200Json(objectMapper.writeValueAsString(response)));

                } catch (Exception e) {
                    log.error("Error creating import job", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Import job request failed", e));
    }

    public Promise<HttpResponse> getImportJobStatus(HttpRequest request) {
        String jobId = request.getQueryParameter("jobId");
        
        if (jobId == null || jobId.isBlank()) {
            return Promise.of(badRequest400("jobId is required"));
        }

        log.debug("Getting import job status: jobId={}", jobId);

        // Get job status
        ImportJobStatusResponse response = getJobStatus(jobId);

        try {
            return Promise.of(ok200Json(objectMapper.writeValueAsString(response)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error serializing job status", e);
            return Promise.of(badRequest400("Error serializing response"));
        }
    }

    private ImportJobResponse createImportJob(ImportRequest req, Principal principal) {
        String jobId = "import-" + java.util.UUID.randomUUID().toString();
        
        return new ImportJobResponse(
                jobId,
                req.projectId(),
                req.sourceType(),
                ImportJobStatus.PENDING,
                "Import job created",
                java.time.Instant.now().toString(),
                principal.getTenantId(),
                principal.getName()
        );
    }

    private ImportJobStatusResponse getJobStatus(String jobId) {
        // In production, this would query the actual job status from storage
        // For now, return a simulated response
        return new ImportJobStatusResponse(
                jobId,
                ImportJobStatus.PENDING,
                0,
                100,
                "Job is pending",
                java.time.Instant.now().toString()
        );
    }

    public record ImportRequest(
            String sourceType,
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
