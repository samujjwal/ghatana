package com.ghatana.platform.observability.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.observability.http.models.*;
import com.ghatana.platform.observability.trace.SpanData;
import com.ghatana.platform.observability.trace.TraceStorage;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HTTP handler for span ingestion endpoints.
 * <p>
 * Handles:
 * <ul>
 *   <li>POST /api/v1/traces/spans - Ingest a single span</li>
 *   <li>POST /api/v1/traces/spans/batch - Ingest multiple spans</li>
 * </ul>
 * @doc.type class
 * @doc.purpose HTTP handler for single and batch span ingestion
 * @doc.layer core
 * @doc.pattern HTTP Handler, Servlet
 * </p>
 * <p>
 * This handler:
 * <ol>
 *   <li>Parses JSON request body to {@link SpanRequest} or {@link BatchSpanRequest}</li>
 *   <li>Validates required fields (spanId, traceId, operationName, serviceName, startTime)</li>
 *   <li>Maps HTTP models to domain models using {@link SpanMapper}</li>
 *   <li>Stores spans via {@link TraceStorage}</li>
 *   <li>Returns success/failure responses with appropriate HTTP status codes</li>
 * </ol>
 * </p>
 *
 * @doc.author Ghatana Platform Team
 * @doc.created 2025-01-10
 * @doc.updated 2025-01-10
 * @doc.version 1.0.0
 * @doc.purpose HTTP handler for span ingestion (single and batch) - validates, maps, and persists spans
 * @doc.responsibility Parse JSON requests, validate required fields, map to domain, delegate storage, return HTTP responses
 * @doc.dependencies {@link TraceStorage}, {@link SpanMapper}, Jackson ObjectMapper, ActiveJ HTTP
 * @doc.usage Instantiated by TraceHttpService, wired to POST /api/v1/traces/spans endpoints
 * @doc.examples See handleSingleSpan() and handleBatchSpans() method docs for request/response formats
 * @doc.testing Test with IngestHandlerTest (unit tests with mock TraceStorage)
 * @doc.notes Stateless handler (thread-safe), uses Promise-based async I/O, supports partial batch success (207 Multi-Status)
 * 
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class IngestHandler {

    /**
     * Logger for ingestion operations and error tracking.
     */
    private static final Logger logger = LoggerFactory.getLogger(IngestHandler.class);

    /**
     * Trace storage backend for persisting span data.
     */
    private final TraceStorage storage;
    
    /**
     * Jackson ObjectMapper for JSON serialization/deserialization.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructs an IngestHandler with required dependencies.
     * <p>
     * Validates all parameters are non-null (fail-fast initialization).
     * </p>
     *
     * @param storage       TraceStorage backend for span persistence (must not be null)
     * @param objectMapper  Jackson ObjectMapper for JSON operations (must not be null)
     * @throws NullPointerException if any parameter is null
     * @doc.thread-safety Constructor is thread-safe; resulting instance is immutable and stateless
     */
    public IngestHandler(TraceStorage storage, ObjectMapper objectMapper) {
        this.storage = Objects.requireNonNull(storage, "TraceStorage cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    /**
     * Handles POST /api/v1/traces/spans - single span ingestion.
     * <p>
     * Request body: {@link SpanRequest} JSON
     * <pre>{@code
     * {
     *   "spanId": "abc123",
     *   "traceId": "trace-001",
     *   "parentSpanId": "parent-001",  // optional
     *   "operationName": "http.request",
     *   "serviceName": "api-gateway",
     *   "startTime": "2025-01-10T10:00:00Z",
     *   "endTime": "2025-01-10T10:00:01Z",  // optional
     *   "tags": {"http.method": "GET"},  // optional
     *   "logs": [...]  // optional
     * }
     * }</pre>
     * </p>
     * <p>
     * Success response: 201 Created with {@link IngestResponse}
     * <pre>{@code
     * {
     *   "success": true,
     *   "spanId": "abc123",
     *   "traceId": "trace-001"
     * }
     * }</pre>
     * </p>
     * <p>
     * Error responses:
     * <ul>
     *   <li>400 Bad Request - Invalid JSON or missing required fields</li>
     *   <li>500 Internal Server Error - Storage failure or unexpected error</li>
     * </ul>
     * </p>
     *
     * @param request  HTTP request with span JSON in body
     * @return Promise of HTTP response (201/400/500)
     * @doc.async Returns Promise for ActiveJ eventloop execution
     * @doc.error-handling Catches parse errors (400), validation errors (400), storage errors (500)
     */
    public Promise<HttpResponse> handleSingleSpan(HttpRequest request) {
        return request.loadBody()
                .then(body -> {
                    try {
                        // Parse JSON to SpanRequest
                        SpanRequest spanRequest = objectMapper.readValue(
                                body.asArray(), SpanRequest.class);

                        // Validate required fields
                        validateSpanRequest(spanRequest);

                        // Map to domain model
                        SpanData spanData = SpanMapper.toDomain(spanRequest);

                        // Store span
                        return storage.storeSpan(spanData)
                                .then(unused -> {
                                    logger.info("Ingested span: spanId={}, traceId={}", 
                                            spanData.spanId(), spanData.traceId());
                                    
                                    IngestResponse response = IngestResponse.success(
                                            spanData.spanId(), spanData.traceId());
                                    
                                    return Promise.of(createJsonResponse(201, response));
                                }, ex -> {
                                    logger.error("Failed to store span: spanId={}, traceId={}", 
                                            spanData.spanId(), spanData.traceId(), ex);
                                    ErrorResponse error = ErrorResponse.internalError(
                                            "Failed to store span: " + ex.getMessage(), 
                                            request.getPath());
                                    return Promise.of(createJsonResponse(500, error));
                                });

                    } catch (Exception ex) {
                        logger.error("Failed to parse span request", ex);
                        ErrorResponse error = ErrorResponse.badRequest(
                                "Invalid span data: " + ex.getMessage(), 
                                request.getPath());
                        return Promise.of(createJsonResponse(400, error));
                    }
                }, ex -> {
                    logger.error("Internal error during span ingestion", ex);
                    ErrorResponse error = ErrorResponse.internalError(
                            "Internal server error: " + ex.getMessage(), 
                            request.getPath());
                    return Promise.of(createJsonResponse(500, error));
                });
    }

    /**
     * Handles POST /api/v1/traces/spans/batch - batch span ingestion.
     * <p>
     * Request body: {@link BatchSpanRequest} JSON (array of spans)
     * <pre>{@code
     * {
     *   "spans": [
     *     {"spanId": "abc123", "traceId": "trace-001", ...},
     *     {"spanId": "def456", "traceId": "trace-002", ...}
     *   ]
     * }
     * }</pre>
     * </p>
     * <p>
     * Success response: 201 Created (all succeeded) or 207 Multi-Status (partial success)
     * <pre>{@code
     * {
     *   "totalReceived": 2,
     *   "successCount": 2,
     *   "failureCount": 0,
     *   "results": [
     *     {"success": true, "spanId": "abc123", "traceId": "trace-001"},
     *     {"success": true, "spanId": "def456", "traceId": "trace-002"}
     *   ]
     * }
     * }</pre>
     * </p>
     * <p>
     * Batch Processing:
     * <ul>
     *   <li>Validates each span individually</li>
     *   <li>Collects all valid spans for batch storage</li>
     *   <li>Returns per-span success/failure results</li>
     *   <li>Uses 207 Multi-Status if any span fails validation</li>
     *   <li>Uses 201 Created if all spans succeed</li>
     * </ul>
     * </p>
     * <p>
     * Error responses:
     * <ul>
     *   <li>400 Bad Request - Invalid JSON, empty batch, or malformed spans</li>
     *   <li>500 Internal Server Error - Storage failure (all spans failed)</li>
     * </ul>
     * </p>
     *
     * @param request  HTTP request with batch span JSON in body
     * @return Promise of HTTP response (201/207/400/500)
     * @doc.async Returns Promise for ActiveJ eventloop execution
     * @doc.error-handling Per-span validation (captures failures in results), global storage errors (500)
     * @doc.partial-success Returns 207 Multi-Status with detailed per-span results on partial failures
     */
    public Promise<HttpResponse> handleBatchSpans(HttpRequest request) {
        return request.loadBody()
                .then(body -> {
                    try {
                        // Parse JSON to BatchSpanRequest
                        BatchSpanRequest batchRequest = objectMapper.readValue(
                                body.asArray(), BatchSpanRequest.class);

                        if (batchRequest.size() == 0) {
                            ErrorResponse error = ErrorResponse.badRequest(
                                    "Batch cannot be empty", request.getPath());
                            return Promise.of(createJsonResponse(400, error));
                        }

                        logger.info("Processing batch of {} spans", batchRequest.size());

                        // Convert all spans to domain models
                        List<SpanData> spanDataList = new ArrayList<>();
                        List<IngestResponse> results = new ArrayList<>();

                        for (SpanRequest spanRequest : batchRequest.getSpans()) {
                            try {
                                validateSpanRequest(spanRequest);
                                SpanData spanData = SpanMapper.toDomain(spanRequest);
                                spanDataList.add(spanData);
                                results.add(IngestResponse.success(
                                        spanData.spanId(), spanData.traceId()));
                            } catch (Exception ex) {
                                logger.warn("Invalid span in batch: {}", ex.getMessage());
                                results.add(IngestResponse.failure(
                                        spanRequest.getSpanId(), 
                                        spanRequest.getTraceId(), 
                                        ex.getMessage()));
                            }
                        }

                        // Store all valid spans
                        return storage.storeSpans(spanDataList)
                                .then(unused -> {
                                    logger.info("Batch ingestion complete: {} spans", 
                                            spanDataList.size());
                                    
                                    BatchIngestResponse response = 
                                            BatchIngestResponse.fromResults(results);
                                    
                                    int statusCode = response.isFullSuccess() ? 201 : 207;
                                    return Promise.of(createJsonResponse(statusCode, response));
                                }, ex -> {
                                    logger.error("Failed to store batch of spans", ex);
                                    ErrorResponse error = ErrorResponse.internalError(
                                            "Failed to store batch: " + ex.getMessage(), 
                                            request.getPath());
                                    return Promise.of(createJsonResponse(500, error));
                                });

                    } catch (Exception ex) {
                        logger.error("Failed to parse batch span request", ex);
                        ErrorResponse error = ErrorResponse.badRequest(
                                "Invalid batch data: " + ex.getMessage(), 
                                request.getPath());
                        return Promise.of(createJsonResponse(400, error));
                    }
                }, ex -> {
                    logger.error("Internal error during batch span ingestion", ex);
                    ErrorResponse error = ErrorResponse.internalError(
                            "Internal server error: " + ex.getMessage(), 
                            request.getPath());
                    return Promise.of(createJsonResponse(500, error));
                });
    }

    /**
     * Validates a SpanRequest has all required fields.
     * <p>
     * Required fields:
     * <ul>
     *   <li>spanId - Non-null, non-blank unique span identifier</li>
     *   <li>traceId - Non-null, non-blank trace identifier</li>
     *   <li>operationName - Non-null, non-blank operation/method name</li>
     *   <li>serviceName - Non-null, non-blank service/component name</li>
     *   <li>startTime - Non-null timestamp (ISO-8601 format)</li>
     * </ul>
     * Optional fields: parentSpanId, endTime, tags, logs
     * </p>
     *
     * @param request  SpanRequest to validate
     * @throws IllegalArgumentException if any required field is null/blank, with descriptive message
     * @doc.validation Validates presence and non-emptiness; does not validate format/semantics
     */
    private void validateSpanRequest(SpanRequest request) {
        if (request.getSpanId() == null || request.getSpanId().isBlank()) {
            throw new IllegalArgumentException("spanId is required");
        }
        if (request.getTraceId() == null || request.getTraceId().isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        if (request.getOperationName() == null || request.getOperationName().isBlank()) {
            throw new IllegalArgumentException("operationName is required");
        }
        if (request.getServiceName() == null || request.getServiceName().isBlank()) {
            throw new IllegalArgumentException("serviceName is required");
        }
        if (request.getStartTime() == null) {
            throw new IllegalArgumentException("startTime is required");
        }
    }

    /**
     * Creates a JSON HTTP response with the given status code and body.
     * <p>
     * Serializes body object to JSON via Jackson ObjectMapper.
     * Sets Content-Type: application/json header.
     * Falls back to 500 error response if serialization fails.
     * </p>
     *
     * @param statusCode  HTTP status code (e.g., 200, 201, 400, 500)
     * @param body        Response body object (serializable via Jackson)
     * @return HttpResponse with JSON body and Content-Type header
     * @doc.error-handling Returns 500 error response if JSON serialization fails
     * @doc.content-type Always returns application/json (even on serialization failures)
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
