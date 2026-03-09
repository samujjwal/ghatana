package com.ghatana.refactorer.server.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.refactorer.server.dto.RestModels;
import com.ghatana.refactorer.server.jobs.JobMappers;
import com.ghatana.refactorer.server.jobs.JobService;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.ghatana.platform.core.exception.ErrorCodeMappers;

/**
 * REST controller for job operations. Handles GET /v1/jobs/{id}/status, GET
 * /v1/jobs/{id}/report,
 *
 * DELETE /v1/jobs/{id} requests.
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Handle HTTP endpoints for jobs workflows and delegate to
 * service-layer collaborators.
 *
 * @doc.layer product
 *
 * @doc.pattern Controller
 *
 */
public final class JobsController {

    private static final Logger logger = LogManager.getLogger(JobsController.class);
    private final JobService jobService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    private static final HttpHeader HEADER_ACCEPT
            = HttpHeaders.ACCEPT != null ? HttpHeaders.ACCEPT : HttpHeaders.of("Accept");
    private static final HttpHeader HEADER_CONTENT_DISPOSITION
            = HttpHeaders.CONTENT_DISPOSITION != null
                    ? HttpHeaders.CONTENT_DISPOSITION
                    : HttpHeaders.of("Content-Disposition");

    public JobsController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Handles job status requests.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> status(HttpRequest request) {
        try {
            String jobId = extractJobId(request);
            logger.info("Received status request for job: {}", jobId);

            return jobService
                    .get(jobId)
                    .map(
                            jobRecord -> {
                                RestModels.RunStatus response = JobMappers.toRestStatus(jobRecord);
                                return ResponseBuilder.ok()
                                        .json(response)
                                        .build();
                            })
                    .map(Promise::of)
                    .orElseGet(() -> Promise.of(notFound(jobId)));

        } catch (Exception e) {
            logger.error("Error getting job status", e);
            return Promise.of(
                    createErrorResponse(
                            500, ErrorCodeMappers.fromIngress("INTERNAL_ERROR").name(), "Failed to get job status", e.getMessage()));
        }
    }

    /**
     * Handles job report requests.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> report(HttpRequest request) {
        try {
            String jobId = extractJobId(request);
            logger.info("Received report request for job: {}", jobId);
            String acceptHeader = request.getHeader(HEADER_ACCEPT);

            return jobService
                    .get(jobId)
                    .map(
                            jobRecord -> {
                                if ("application/zip".equals(acceptHeader)) {
                                    byte[] mockZipContent
                                    = ("report for " + jobId)
                                            .getBytes(StandardCharsets.UTF_8);
                                    return Promise.of(
                                            ResponseBuilder.ok()
                                                    .bytes(mockZipContent, "application/zip")
                                                    .header(
                                                            HEADER_CONTENT_DISPOSITION,
                                                            "attachment; filename=\"report-"
                                                            + jobId
                                                            + ".zip\"")
                                                    .build());
                                }

                                RestModels.Report response = JobMappers.toRestReport(jobRecord);
                                return Promise.of(ResponseBuilder.ok().json(response).build());
                            })
                    .orElseGet(() -> Promise.of(notFound(jobId)));
        } catch (Exception e) {
            logger.error("Error getting job report", e);
            return Promise.of(
                    createErrorResponse(
                            500, ErrorCodeMappers.fromIngress("INTERNAL_ERROR").name(), "Failed to get job report", e.getMessage()));
        }
    }

    /**
     * Handles job cancellation requests.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> cancel(HttpRequest request) {
        try {
            String jobId = extractJobId(request);
            logger.info("Received cancel request for job: {}", jobId);

            return jobService
                    .cancel(jobId)
                    .map(
                            record -> {
                                RestModels.RunStatus response = JobMappers.toRestStatus(record);
                                return Promise.of(
                                        ResponseBuilder.ok().json(response).build());
                            })
                    .orElseGet(() -> Promise.of(notFound(jobId)));
        } catch (Exception e) {
            logger.error("Error cancelling job", e);
            return Promise.of(
                    createErrorResponse(
                            500, ErrorCodeMappers.fromIngress("INTERNAL_ERROR").name(), "Failed to cancel job", e.getMessage()));
        }
    }

    private String extractJobId(HttpRequest request) {
        String path = request.getPath();
        logger.debug("Extracting job ID from path: {}", path);

        // Remove any query parameters
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }

        String[] segments = path.split("/");

        // Look for 'jobs' or 'events' segment and return the next segment
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (("jobs".equals(segment) || "events".equals(segment)) && i + 1 < segments.length) {
                String jobId = segments[i + 1];
                logger.debug("Extracted job ID: {} from path: {}", jobId, path);
                return jobId;
            }
        }

        // If we get here, try to get the last segment as the ID
        if (segments.length > 0) {
            String lastSegment = segments[segments.length - 1];
            if (!lastSegment.isEmpty()) {
                logger.debug("Using last path segment as job ID: {}", lastSegment);
                return lastSegment;
            }
        }

        String errorMsg = "Could not extract job ID from path: " + request.getPath();
        logger.error(errorMsg);
        throw new IllegalArgumentException(errorMsg);
    }

    private HttpResponse createErrorResponse(
            int status, String code, String message, String details) {
        RestModels.ErrorResponse error
                = new RestModels.ErrorResponse(
                        code, message, details, "req-" + System.currentTimeMillis());
        return ResponseBuilder.status(status).json(error).build();
    }

    private HttpResponse notFound(String jobId) {
        return ResponseBuilder.notFound()
                .json(Map.of(
                        "code", "NOT_FOUND",
                        "message", "Job not found: " + jobId))
                .build();
    }
}
