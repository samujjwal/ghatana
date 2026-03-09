package com.ghatana.refactorer.server.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.core.exception.ErrorCode;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.validation.ValidationService;
import com.ghatana.refactorer.server.auth.AccessPolicy;
import com.ghatana.refactorer.server.auth.TenantContext;
import com.ghatana.refactorer.server.auth.TenantResolver;
import com.ghatana.refactorer.server.dto.RestModels;
import com.ghatana.refactorer.server.error.ExceptionHandler;
import com.ghatana.refactorer.server.jobs.JobMappers;
import com.ghatana.refactorer.server.jobs.JobRecord;
import com.ghatana.refactorer.server.jobs.JobService;
import com.ghatana.refactorer.server.jobs.JobSubmission;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * REST controller for diagnose operations. Handles POST /v1/diagnose requests.
 *
 * @doc.type class
 * @doc.purpose Handle HTTP endpoints for diagnose workflows and delegate to
 * service-layer collaborators.
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class DiagnoseController {

    private static final Logger logger = LogManager.getLogger(DiagnoseController.class);
    private final JobService jobService;
    private final AccessPolicy accessPolicy;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public DiagnoseController(
            JobService jobService, AccessPolicy accessPolicy, ValidationService validationService) {
        this.jobService = jobService;
        this.accessPolicy = accessPolicy;
        this.validationService = validationService;
    }

    /**
     * Handles diagnose requests.
     *
     * <p>
     * Implements PR 3.2: Exception Handling with canonical ErrorCode patterns.
     *
     * <p>
     * Flow: 1. Parse request body 2. Validate via core/validation (PR 3.1) 3.
     * Resolve tenant via TenantContext 4. Submit job 5. Return success response
     *
     * <p>
     * Errors: Validation failures (400), Authentication (401), Authorization
     * (403), Service errors (500) - all mapped to canonical ErrorCode enum.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> diagnose(HttpRequest request) {
        String correlationId = "req-" + System.currentTimeMillis();

        return request.loadBody()
                .then(
                        body -> {
                            try {
                                logger.info("Received diagnose request [{}]", correlationId);

                                // Parse JSON body
                                RestModels.DiagnoseRequest diagnoseRequest
                                = objectMapper.readValue(
                                        body.getArray(), RestModels.DiagnoseRequest.class);

                                // Step 1: Validate request using core/validation abstraction (PR 3.1)
                                return validationService
                                        .validateEvent(diagnoseRequest)
                                        .map(
                                                validationResult -> {
                                                    if (!validationResult.isValid()) {
                                                        logger.warn(
                                                                "DiagnoseRequest validation failed [{}]: {}",
                                                                correlationId,
                                                                validationResult.getErrors());

                                                        // Map validation errors to canonical ErrorCode
                                                        // (PR 3.2)
                                                        String errorDetails
                                                        = validationResult
                                                                .getErrors()
                                                                .toString();
                                                        RestModels.ErrorResponse error
                                                        = new RestModels.ErrorResponse(
                                                                ErrorCode.VALIDATION_ERROR
                                                                        .getCode(),
                                                                "Request validation failed",
                                                                errorDetails,
                                                                correlationId);

                                                        return ResponseBuilder.status(
                                                                ErrorCode.VALIDATION_ERROR
                                                                        .getHttpStatus())
                                                                .json(error)
                                                                .build();
                                                    }

                                                    // Step 2: Resolve tenant context
                                                    TenantContext tenantContext
                                                    = TenantResolver.get(request);
                                                    if (tenantContext == null) {
                                                        if (accessPolicy.isAuthRequired()) {
                                                            // Map to AUTHENTICATION_ERROR
                                                            // (ErrorCode enum)
                                                            throw new ExceptionHandler.AuthenticationException(
                                                                    "Authentication required");
                                                        }
                                                        tenantContext
                                                        = JobMappers
                                                                .tenantContextFromRest(
                                                                        diagnoseRequest);
                                                    }

                                                    // Step 3: Submit job
                                                    JobSubmission submission
                                                    = JobMappers.fromRestDiagnoseRequest(
                                                            diagnoseRequest,
                                                            tenantContext);
                                                    JobRecord record = jobService.submit(
                                                            submission);

                                                    // Step 4: Build response
                                                    RestModels.UnifiedDiagnostic mockDiagnostic
                                                    = new RestModels.UnifiedDiagnostic(
                                                            "mock-tool",
                                                            "mock-rule",
                                                            "Mock diagnostic for testing",
                                                            diagnoseRequest.repoRoot()
                                                            + "/test.java",
                                                            1,
                                                            1,
                                                            "INFO",
                                                            Map.of(
                                                                    "jobId",
                                                                    record.jobId()),
                                                            System.currentTimeMillis());

                                                    RestModels.DiagnoseResponse response
                                                    = new RestModels.DiagnoseResponse(
                                                            List.of(mockDiagnostic),
                                                            record.jobId(),
                                                            System.currentTimeMillis());

                                                    logger.info(
                                                            "Successfully processed diagnose request [{}] for repo: {}",
                                                            correlationId,
                                                            diagnoseRequest.repoRoot());

                                                    return ResponseBuilder.ok()
                                                            .json(response)
                                                            .build();
                                                });

                            } catch (Exception e) {
                                // Use centralized ExceptionHandler with ErrorCode mapping (PR 3.2)
                                logger.error(
                                        "Error processing diagnose request [{}]",
                                        correlationId,
                                        e);
                                return Promise.of(
                                        ExceptionHandler.handle(e, correlationId));
                            }
                        });
    }

    /**
     * Returns a small config summary for debugging (GET /v1/config).
     *
     * @param request HTTP request
     * @return Promise of HTTP response containing minimal config info
     */
    public Promise<HttpResponse> getConfig(HttpRequest request) {
        try {
            // Minimal config exposure for debug purposes
            Map<String, Object> cfg = Map.of(
                    "service", "refactorer",
                    "version", "1.0.0",
                    "timestamp", System.currentTimeMillis());

            return Promise.of(ResponseBuilder.ok().json(cfg).build());
        } catch (Exception e) {
            logger.error("Failed to return config", e);
            return Promise.of(ResponseBuilder.serverError()
                    .json(Map.of("error", "Failed to fetch config", "details", e.getMessage()))
                    .build());
        }
    }
}
