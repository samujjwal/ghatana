package com.ghatana.platform.observability.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.observability.trace.TraceQuery;
import com.ghatana.platform.observability.trace.TraceQueryBuilder;
import com.ghatana.platform.observability.trace.TraceStatistics;
import com.ghatana.platform.observability.trace.TraceStorage;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP handler for trace statistics endpoint.
 * <p>
 * Handles:
 * <ul>
 *   <li>GET /api/v1/traces/stats - Get aggregated trace statistics</li>
 * </ul>
 * @doc.type class
 * @doc.purpose HTTP handler for trace statistics query and aggregation
 * @doc.layer core
 * @doc.pattern HTTP Handler, Servlet
 * </p>
 * <p>
 * Query parameters (same as QueryHandler for filtering):
 * <ul>
 *   <li>serviceName - Filter by service name</li>
 *   <li>operationName - Filter by operation name</li>
 *   <li>status - Filter by status (OK, ERROR, UNSET)</li>
 *   <li>minDuration - Minimum duration in milliseconds</li>
 *   <li>maxDuration - Maximum duration in milliseconds</li>
 *   <li>startTime - Start time (ISO-8601)</li>
 *   <li>endTime - End time (ISO-8601)</li>
 * </ul>
 * </p>
 * <p>
 * Returns aggregated statistics:
 * <ul>
 *   <li>Total trace count</li>
 *   <li>Error count and error rate</li>
 *   <li>Duration statistics (min, max, avg, p50, p95, p99)</li>
 *   <li>Span count statistics (min, max, avg)</li>
 * </ul>
 * </p>
 *
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class StatisticsHandler {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsHandler.class);

    private final TraceStorage storage;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a StatisticsHandler.
     *
     * @param storage       TraceStorage backend (not null)
     * @param objectMapper  Jackson ObjectMapper for JSON (not null)
     * @throws IllegalArgumentException if any parameter is null
     */
    public StatisticsHandler(TraceStorage storage, ObjectMapper objectMapper) {
        this.storage = Objects.requireNonNull(storage, "TraceStorage cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    /**
     * Handles GET /api/v1/traces/stats - get trace statistics.
     * <p>
     * Returns aggregated statistics for traces matching the query filters.
     * If no filters are provided, returns statistics for all traces.
     * </p>
     *
     * @param request  HTTP request with optional query parameters
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handleGetStatistics(HttpRequest request) {
        try {
            // Parse query parameters (reuse QueryHandler logic)
            TraceQuery query = parseQueryParameters(request);

            logger.debug("Computing statistics with filters: serviceName={}, status={}", 
                    query.getServiceName().orElse("*"), 
                    query.getStatus().orElse("*"));

            return storage.getStatistics(query)
                    .then(statistics -> {
                        logger.info("Computed statistics: totalTraces={}, errorCount={}, errorRate={}%", 
                                statistics.totalTraces(), 
                                statistics.errorCount(), 
                                statistics.errorRate());
                        
                        return Promise.of(createJsonResponse(200, statistics));
                    }, ex -> {
                        logger.error("Failed to compute statistics", ex);
                        ErrorResponse error = ErrorResponse.internalError(
                                "Failed to compute statistics: " + ex.getMessage(), 
                                request.getPath());
                        return Promise.of(createJsonResponse(500, error));
                    });

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid query parameters: {}", ex.getMessage());
            ErrorResponse error = ErrorResponse.badRequest(
                    "Invalid query parameters: " + ex.getMessage(), 
                    request.getPath());
            return Promise.of(createJsonResponse(400, error));
        }
    }

    /**
     * Parses query parameters from HTTP request into TraceQuery.
     * <p>
     * Note: Statistics endpoint does not use limit/offset (returns aggregated data).
     * </p>
     *
     * @param request  HTTP request with query parameters
     * @return TraceQuery with parsed filters
     * @throws IllegalArgumentException if parameters are invalid
     */
    private TraceQuery parseQueryParameters(HttpRequest request) {
        TraceQueryBuilder builder = TraceQuery.builder();

        // Optional filters
        getQueryParam(request, "serviceName").ifPresent(builder::withServiceName);
        getQueryParam(request, "operationName").ifPresent(builder::withOperationName);
        getQueryParam(request, "status").ifPresent(builder::withStatus);

        // Duration filters
        getQueryParam(request, "minDuration")
                .ifPresent(s -> builder.withMinDurationMs(parseLong(s, "minDuration")));
        getQueryParam(request, "maxDuration")
                .ifPresent(s -> builder.withMaxDurationMs(parseLong(s, "maxDuration")));

        // Time range filters
        getQueryParam(request, "startTime")
                .ifPresent(s -> builder.withStartTime(parseInstant(s, "startTime")));
        getQueryParam(request, "endTime")
                .ifPresent(s -> builder.withEndTime(parseInstant(s, "endTime")));

        // Tags (comma-separated key:value pairs)
        getQueryParam(request, "tags").ifPresent(tagsStr -> {
            String[] pairs = tagsStr.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    builder.withTag(kv[0].trim(), kv[1].trim());
                }
            }
        });

        // Statistics queries don't need pagination - set limit to max
        builder.withLimit(Integer.MAX_VALUE).withOffset(0);

        return builder.build();
    }

    /**
     * Gets a query parameter value from the request.
     *
     * @param request  HTTP request
     * @param name     Parameter name
     * @return Optional parameter value
     */
    private Optional<String> getQueryParam(HttpRequest request, String name) {
        String value = request.getQueryParameter(name);
        return (value != null && !value.isBlank()) ? Optional.of(value) : Optional.empty();
    }

    /**
     * Parses a string to long.
     *
     * @param value  String value
     * @param name   Parameter name (for error message)
     * @return Parsed long
     * @throws IllegalArgumentException if parsing fails
     */
    private long parseLong(String value, String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a valid long: " + value);
        }
    }

    /**
     * Parses a string to Instant (ISO-8601 format).
     *
     * @param value  String value
     * @param name   Parameter name (for error message)
     * @return Parsed Instant
     * @throws IllegalArgumentException if parsing fails
     */
    private Instant parseInstant(String value, String name) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    name + " must be a valid ISO-8601 timestamp: " + value);
        }
    }

    /**
     * Creates a JSON HTTP response with the given status code and body.
     *
     * @param statusCode  HTTP status code
     * @param body        Response body object (will be serialized to JSON)
     * @return HTTP response
     */
    private HttpResponse createJsonResponse(int statusCode, Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return HttpResponse.ofCode(statusCode)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json)
                    .build();
        } catch (Exception ex) {
            logger.error("Failed to serialize response to JSON", ex);
            return HttpResponse.ofCode(500)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody("{\"error\":\"Failed to serialize response\"}".getBytes())
                    .build();
        }
    }
}
