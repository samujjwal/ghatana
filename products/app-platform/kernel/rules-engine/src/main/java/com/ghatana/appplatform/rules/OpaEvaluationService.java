package com.ghatana.appplatform.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * REST client for Open Policy Agent (OPA) policy evaluation.
 *
 * <p>Calls the OPA Data API ({@code POST /v1/data/{policyPath}}) with a JSON
 * {@code input} document and returns an {@link OpaResult} that indicates whether
 * the policy allows the request plus any additional output fields.
 *
 * <p>All network I/O is executed on the supplied {@link Executor} and wrapped in
 * an ActiveJ {@link Promise} so it is safe to use from an ActiveJ event-loop.
 *
 * @doc.type class
 * @doc.purpose OPA REST API integration for rule/policy evaluation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class OpaEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(OpaEvaluationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Result of a single OPA policy evaluation.
     *
     * @param allow      whether the evaluated policy granted access
     * @param result     full JSON response body from OPA (may contain extra fields)
     * @param policyPath the OPA policy path that was evaluated
     */
    public record OpaResult(boolean allow, Map<String, Object> result, String policyPath) {}

    private final String opaBaseUrl;
    private final HttpClient httpClient;
    private final Executor executor;

    /**
     * Creates a service that talks to OPA at the given base URL.
     *
     * @param opaBaseUrl base URL of the OPA server, e.g. {@code http://localhost:8181}
     * @param executor   blocking executor for HTTP calls
     */
    public OpaEvaluationService(String opaBaseUrl, Executor executor) {
        this.opaBaseUrl = opaBaseUrl.endsWith("/") ? opaBaseUrl.substring(0, opaBaseUrl.length() - 1) : opaBaseUrl;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Evaluates an OPA policy against the given input document.
     *
     * <p>The OPA endpoint called is {@code POST {opaBaseUrl}/v1/data/{policyPath}}.
     * The request body is {@code {"input": <inputMap>}}.
     *
     * @param policyPath OPA path segments separated by slashes, e.g. {@code authz/allow}
     * @param input      input document passed to OPA as the {@code input} object
     * @return promise resolving to an {@link OpaResult}
     * @throws OpaEvaluationException if OPA returns a non-2xx status or the connection fails
     */
    public Promise<OpaResult> evaluate(String policyPath, Map<String, Object> input) {
        return Promise.ofBlocking(executor, () -> doEvaluate(policyPath, input));
    }

    @SuppressWarnings("unchecked")
    private OpaResult doEvaluate(String policyPath, Map<String, Object> input) throws Exception {
        String normalizedPath = policyPath.startsWith("/") ? policyPath.substring(1) : policyPath;
        String url = opaBaseUrl + "/v1/data/" + normalizedPath;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", input);
        String requestJson = MAPPER.writeValueAsString(requestBody);

        log.debug("OPA evaluate: POST {} with input keys={}", url, input.keySet());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpaEvaluationException(
                    "OPA returned HTTP " + response.statusCode() + " for policy=" + policyPath
                            + "; body=" + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode resultNode = root.path("result");

        boolean allow = false;
        Map<String, Object> resultMap = new HashMap<>();

        if (!resultNode.isMissingNode()) {
            // OPA returns the full result document under "result"
            resultMap = MAPPER.convertValue(resultNode, Map.class);
            // Conventionally the top-level allow field determines access
            Object allowVal = resultMap.get("allow");
            if (allowVal instanceof Boolean b) {
                allow = b;
            }
        }

        log.debug("OPA result: policy={} allow={}", policyPath, allow);
        return new OpaResult(allow, resultMap, policyPath);
    }

    /**
     * Thrown when OPA returns an error HTTP status or the request fails.
     *
     * @doc.type class
     * @doc.purpose Signals OPA evaluation failure
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public static final class OpaEvaluationException extends RuntimeException {
        public OpaEvaluationException(String message) {
            super(message);
        }

        public OpaEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
