package com.ghatana.yappc.api;

import com.ghatana.yappc.common.JsonMapper;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for creating HTTP responses with common patterns.

 * @doc.type class
 * @doc.purpose Handles http responses operations
 * @doc.layer core
 * @doc.pattern ValueObject
* @doc.gaa.lifecycle perceive
*/
public final class HttpResponses {

    private static final String PROBLEM_CONTENT_TYPE = "application/problem+json; charset=utf-8";

    private HttpResponses() {}

    /**
     * Creates a 200 OK response with JSON content from String.
     *
     * @param jsonString JSON string
     * @return HTTP response
     */
    public static HttpResponse ok200Json(String jsonString) {
        return HttpResponse.ok200().withJson(jsonString).build();
    }

    /**
     * Creates a 200 OK response with JSON content from bytes.
     *
     * @param jsonBytes JSON bytes
     * @return HTTP response
     */
    public static HttpResponse ok200Json(byte[] jsonBytes) {
        return HttpResponse.ok200().withJson(new String(jsonBytes)).build();
    }

    /**
     * Creates a 500 error response using the canonical RFC-7807 problem envelope.
     *
     * @param message Error message
     * @return HTTP response
     */
    public static HttpResponse error500(String message) {
        return problem(500, "internal-server-error", "Internal Server Error", message);
    }

    /**
     * Creates a 400 bad request response using the canonical RFC-7807 problem envelope.
     *
     * @param message Error message
     * @return HTTP response
     */
    public static HttpResponse badRequest400(String message) {
        return problem(400, "bad-request", "Bad Request", message);
    }

    /**
     * Creates an RFC-7807 problem response for YAPPC API errors.
     *
     * @param status HTTP status code
     * @param typeSlug stable problem type slug
     * @param title short problem title
     * @param detail safe problem detail
     * @return HTTP problem response
     */
    public static HttpResponse problem(int status, String typeSlug, String title, String detail) {
        String safeTypeSlug = isBlank(typeSlug) ? "internal-server-error" : typeSlug;
        String safeTitle = isBlank(title) ? "Request failed" : title;
        String safeDetail = isBlank(detail) ? safeTitle : detail;
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://yappc.ghatana.com/problems/" + safeTypeSlug);
        problem.put("title", safeTitle);
        problem.put("status", status);
        problem.put("detail", safeDetail);
        problem.put("correlationId", correlationId);
        problem.put("error", safeDetail);
        try {
            return HttpResponse.ofCode(status)
                    .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(problem).getBytes(StandardCharsets.UTF_8)))
                    .withHeader(HttpHeaders.CONTENT_TYPE, PROBLEM_CONTENT_TYPE)
                    .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(status)
                    .withBody(ByteBuf.wrapForReading(("{\"type\":\"https://yappc.ghatana.com/problems/internal-server-error\",\"title\":\"Request failed\",\"status\":500,\"detail\":\"Unable to serialize error response\",\"correlationId\":\"" + correlationId + "\",\"error\":\"Unable to serialize error response\"}").getBytes(StandardCharsets.UTF_8)))
                    .withHeader(HttpHeaders.CONTENT_TYPE, PROBLEM_CONTENT_TYPE)
                    .build();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
