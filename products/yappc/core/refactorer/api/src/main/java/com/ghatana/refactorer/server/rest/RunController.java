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
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * REST controller for run operations. Handles POST /v1/run requests.
 *
 * @doc.type class
 * @doc.purpose Handle HTTP endpoints for run workflows and delegate to
 * service-layer collaborators.
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class RunController {

    private static final Logger logger = LogManager.getLogger(RunController.class);
    private final JobService jobService;
    private final AccessPolicy accessPolicy;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public RunController(
            JobService jobService, AccessPolicy accessPolicy, ValidationService validationService) {
        this.jobService = jobService;
        this.accessPolicy = accessPolicy;
        this.validationService = validationService;
    }

    /**
     * Handles run requests.
     *
     * <p>
     * Implements PR 3.2: Exception Handling with canonical ErrorCode patterns.
     *
     * <p>
     * Flow: 1. Parse request body 2. Validate via core/validation (PR 3.1) 3.
     * Resolve tenant via TenantContext 4. Submit job 5. Return 201 Created
     * response
     *
     * <p>
     * Errors: Validation failures (400), Authentication (401), Authorization
     * (403), Service errors (500) - all mapped to canonical ErrorCode enum.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> run(HttpRequest request) {
        String correlationId = "req-" + System.currentTimeMillis();

        return request.loadBody()
                .then(
                        body -> {
                            try {
                                logger.info("Received run request [{}]", correlationId);

                                // Parse JSON body
                                RestModels.RunRequest runRequest
                                = objectMapper.readValue(
                                        body.getArray(), RestModels.RunRequest.class);

                                // Step 1: Validate request using core/validation abstraction (PR 3.1)
                                return validationService
                                        .validateEvent(runRequest)
                                        .map(
                                                validationResult -> {
                                                    if (!validationResult.isValid()) {
                                                        logger.warn(
                                                                "RunRequest validation failed [{}]: {}",
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
                                                        = JobMappers.tenantContextFromRest(
                                                                runRequest);
                                                    }

                                                    // Step 3: Submit job
                                                    JobSubmission submission
                                                    = JobMappers.fromRestRunRequest(
                                                            runRequest, tenantContext);
                                                    JobRecord record = jobService.submit(
                                                            submission);
                                                    RestModels.JobResponse response
                                                    = new RestModels.JobResponse(
                                                            record.jobId());

                                                    logger.info(
                                                            "Successfully created job [{}] for request [{}]",
                                                            record.jobId(),
                                                            correlationId);

                                                    return ResponseBuilder.created()
                                                            .json(response)
                                                            .build();
                                                });

                            } catch (Exception e) {
                                // Use centralized ExceptionHandler with ErrorCode mapping (PR 3.2)
                                logger.error(
                                        "Error processing run request [{}]", correlationId, e);
                                return Promise.of(
                                        ExceptionHandler.handle(e, correlationId));
                            }
                        });
    }
}
