package com.ghatana.platform.observability.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.observability.trace.*;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP handler for trace query endpoints.
 * <p>
 * Handles:
 * <ul>
 *   <li>GET /api/v1/traces/{traceId} - Get a specific trace by ID</li>
 *   <li>GET /api/v1/traces - Search traces with filters</li>
 * </ul>
 * </p>
 * <p>
 * Query parameters for search:
 * <ul>
 *   <li>traceId - Filter by trace ID (exact match)</li>
 *   <li>serviceName - Filter by service name</li>
 *   <li>operationName - Filter by operation name</li>
 *   <li>status - Filter by status (OK, ERROR, UNSET)</li>
 *   <li>minDuration - Minimum duration in milliseconds</li>
 *   <li>maxDuration - Maximum duration in milliseconds</li>
 *   <li>startTime - Start time (ISO-8601)</li>
 *   <li>endTime - End time (ISO-8601)</li>
 *   <li>limit - Max results (default 100, max 1000)</li>
 *   <li>offset - Results offset (default 0)</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose HTTP handler for trace query endpoints (get and search operations)
 * @doc.layer observability
 * @doc.pattern Handler, ActiveJ HTTP Handler, Query Processor
 *
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class QueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(QueryHandler.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final TraceStorage storage;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a QueryHandler.
     *
     * @param storage       TraceStorage backend (not null)
     * @param objectMapper  Jackson ObjectMapper for JSON (not null)
     * @throws IllegalArgumentException if any parameter is null
     */
    public QueryHandler(TraceStorage storage, ObjectMapper objectMapper) {
        this.storage = Objects.requireNonNull(storage, "TraceStorage cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    /**
     * Handles GET /api/v1/traces/{traceId} - get trace by ID.
     * <p>
     * Returns a single TraceInfo if found, or 404 Not Found.
     * </p>
     *
     * @param request  HTTP request with traceId in path
     * @param traceId  Trace ID extracted from path parameter
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handleGetTraceById(HttpRequest request, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            ErrorResponse error = ErrorResponse.badRequest(
                    "traceId is required", request.getPath());
            return Promise.of(createJsonResponse(400, error));
        }

        logger.debug("Querying trace by ID: {}", traceId);

        // Build query - use tags to filter by traceId
        // Note: TraceQuery doesn't have direct traceId filter,
        // so we query all traces and filter client-side
        TraceQuery query = TraceQuery.builder()
                .withLimit(1000)  // Reasonable limit for filtering
                .build();

        return storage.queryTraces(query)
                .then(traces -> {
                    // Filter by traceId client-side
                    Optional<TraceInfo> matchingTrace = traces.stream()
                            .filter(trace -> trace.traceId().equals(traceId))
                            .findFirst();

                    if (matchingTrace.isEmpty()) {
                        logger.debug("Trace not found: {}", traceId);
                        ErrorResponse error = ErrorResponse.notFound(
                                "Trace not found: " + traceId, request.getPath());
                        return Promise.of(createJsonResponse(404, error));
                    }

                    TraceInfo trace = matchingTrace.get();
                    logger.debug("Found trace: traceId={}, spanCount={}", 
                            trace.traceId(), trace.spanCount());
                    return Promise.of(createJsonResponse(200, trace));
                }, ex -> {
                    logger.error("Failed to query trace: {}", traceId, ex);
                    ErrorResponse error = ErrorResponse.internalError(
                            "Failed to query trace: " + ex.getMessage(), 
                            request.getPath());
                    return Promise.of(createJsonResponse(500, error));
                });
    }

    /**
     * Handles GET /api/v1/traces - search traces with filters.
     * <p>
     * Returns a list of TraceInfo objects matching the query parameters.
     * Supports pagination via limit/offset.
     * </p>
     *
     * @param request  HTTP request with query parameters
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handleSearchTraces(HttpRequest request) {
        try {
            // Parse query parameters
            TraceQuery query = parseQueryParameters(request);

            logger.debug("Searching traces with filters: serviceName={}, status={}, limit={}", 
                    query.getServiceName().orElse("*"), 
                    query.getStatus().orElse("*"), 
                    query.getLimit());

            return storage.queryTraces(query)
                    .then(traces -> {
                        logger.info("Found {} traces matching query", traces.size());
                        
                        // Wrap results in response object
                        SearchResponse response = new SearchResponse(
                                traces.size(),
                                query.getLimit(),
                                query.getOffset(),
                                traces
                        );
                        
                        return Promise.of(createJsonResponse(200, response));
                    }, ex -> {
                        logger.error("Failed to search traces", ex);
                        ErrorResponse error = ErrorResponse.internalError(
                                "Failed to search traces: " + ex.getMessage(), 
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
     *
     * @param request  HTTP request with query parameters
     * @return TraceQuery with parsed filters
     * @throws IllegalArgumentException if parameters are invalid
     */
    private TraceQuery parseQueryParameters(HttpRequest request) {
        TraceQueryBuilder builder = TraceQuery.builder();

        // Optional filters
        getQueryParam(request, "traceId").ifPresent(id -> builder.withTag("traceId", id));
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

        // Pagination
        int limit = getQueryParam(request, "limit")
                .map(s -> parseInt(s, "limit"))
                .orElse(DEFAULT_LIMIT);
        
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit cannot exceed " + MAX_LIMIT);
        }
        
        int offset = getQueryParam(request, "offset")
                .map(s -> parseInt(s, "offset"))
                .orElse(0);

        builder.withLimit(limit).withOffset(offset);

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
     * Parses a string to int.
     *
     * @param value  String value
     * @param name   Parameter name (for error message)
     * @return Parsed int
     * @throws IllegalArgumentException if parsing fails
     */
    private int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a valid integer: " + value);
        }
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

    /**
     * Response wrapper for search results with pagination metadata.
     */
    public static class SearchResponse {
        private final int count;
        private final int limit;
        private final int offset;
        private final List<TraceInfo> traces;

        public SearchResponse(int count, int limit, int offset, List<TraceInfo> traces) {
            this.count = count;
            this.limit = limit;
            this.offset = offset;
            this.traces = traces;
        }

        public int getCount() {
            return count;
        }

        public int getLimit() {
            return limit;
        }

        public int getOffset() {
            return offset;
        }

        public List<TraceInfo> getTraces() {
            return traces;
        }
    }
}
