package com.ghatana.refactorer.server.sse;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.refactorer.server.jobs.JobProgressStreamer;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-Sent Events handler for streaming job progress. Handles GET
 * /v1/jobs/{id}/events requests.
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Stream server-sent events so clients can observe real-time job
 * lifecycle updates.
 *
 * @doc.layer product
 *
 * @doc.pattern SSE Adapter
 *
 */
public final class EventsSseHandler {

    private static final Logger logger = LogManager.getLogger(EventsSseHandler.class);
    private final JobProgressStreamer progressStreamer;

    public EventsSseHandler(JobProgressStreamer progressStreamer) {
        this.progressStreamer = progressStreamer;
    }

    /**
     * Handles SSE requests for job events.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handle(HttpRequest request) {
        try {
            String jobId = extractJobId(request);
            logger.info("Starting SSE stream for job: {}", jobId);

            return progressStreamer
                    .openStream(jobId)
                    .map(
                            supplier
                            -> ResponseBuilder.ok()
                                    .header(
                                            HttpHeaders.CONTENT_TYPE, "text/event-stream")
                                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                                    .header(HttpHeaders.CONNECTION, "keep-alive")
                                    .header(
                                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                                    .rawBuilder()
                                    .withBodyStream(supplier)
                                    .build())
                    .map(Promise::of)
                    .orElseGet(
                            ()
                            -> Promise.of(
                                    ResponseBuilder.notFound()
                                            .text("Job not found: " + jobId)
                                            .build()));

        } catch (Exception e) {
            logger.error("Error handling SSE request", e);
            HttpResponse errorResponse
                    = ResponseBuilder.internalServerError()
                            .text("Failed to stream events")
                            .build();
            return Promise.of(errorResponse);
        }
    }

    private String extractJobId(HttpRequest request) {
        String path = request.getPath();
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            if ("jobs".equals(segment) && i + 1 < segments.length) {
                return segments[i + 1];
            }
            if ("events".equals(segment) && i + 1 < segments.length) {
                return segments[i + 1];
            }
        }
        throw new IllegalArgumentException("Invalid job path: " + path);
    }
}
