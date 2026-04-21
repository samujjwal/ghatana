/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * {@link PolicyAsCodeEngine} that delegates to an external Open Policy Agent (OPA)
 * HTTP endpoint via the OPA REST API (POST /v1/data/{policyPath}).
 *
 * <p>Uses Jackson for proper JSON serialization and deserialization with typed
 * response records. Handles HTTP errors, malformed JSON, and missing fields explicitly.
 *
 * <p>All HTTP calls are wrapped in {@code Promise.ofBlocking} to avoid blocking
 * the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose OPA HTTP client adapter for policy-as-code evaluation
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class OpaClient implements PolicyAsCodeEngine {

    private static final Logger log = LoggerFactory.getLogger(OpaClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String opaBaseUrl;
    private final HttpClient httpClient;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    /**
     * Construct an OPA client.
     *
     * @param opaBaseUrl base URL of the OPA server (e.g. {@code http://opa:8181})
     * @param executor   blocking executor for HTTP calls (must not be the event-loop thread)
     */
    public OpaClient(String opaBaseUrl, Executor executor) {
        this.opaBaseUrl = opaBaseUrl.stripTrailing();
        this.executor = executor;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .build();
    }

    @Override
    public Promise<PolicyEvalResult> evaluate(
            String tenantId, String policyName, Map<String, Object> input) {
        return Promise.ofBlocking(executor, () -> {
            String url = opaBaseUrl + "/v1/data/" + policyName.replace('.', '/');
            
            try {
                String requestBody = objectMapper.writeValueAsString(Map.of("input", input));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                // Handle HTTP errors
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    String errorMsg = "OPA client error: HTTP " + response.statusCode();
                    log.warn("{} for policy {} tenant {}", errorMsg, policyName, tenantId);
                    return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
                }
                
                if (response.statusCode() >= 500) {
                    String errorMsg = "OPA server error: HTTP " + response.statusCode();
                    log.error("{} for policy {} tenant {}", errorMsg, policyName, tenantId);
                    return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
                }

                if (response.statusCode() != 200) {
                    String errorMsg = "OPA unexpected status: HTTP " + response.statusCode();
                    log.warn("{} for policy {} tenant {}", errorMsg, policyName, tenantId);
                    return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
                }

                // Parse OPA response
                OpaResponse opaResponse = objectMapper.readValue(response.body(), OpaResponse.class);
                
                if (opaResponse.result() == null) {
                    log.warn("OPA response missing result field for policy {} tenant {}", policyName, tenantId);
                    return PolicyEvalResult.deny(policyName, List.of("OPA response missing result"), 100);
                }

                boolean allowed = opaResponse.result().allow() != null && opaResponse.result().allow();
                
                if (allowed) {
                    return PolicyEvalResult.allow(policyName);
                } else {
                    // Extract reasons from OPA response if available
                    List<String> reasons = opaResponse.result().reasons();
                    if (reasons == null || reasons.isEmpty()) {
                        reasons = List.of("OPA policy denied the request");
                    }
                    return PolicyEvalResult.deny(policyName, reasons, 50);
                }

            } catch (java.net.ConnectException e) {
                String errorMsg = "Failed to connect to OPA server: " + e.getMessage();
                log.error("{} for policy {} tenant {}", errorMsg, policyName, tenantId, e);
                return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
            } catch (java.net.SocketTimeoutException e) {
                String errorMsg = "OPA request timed out: " + e.getMessage();
                log.error("{} for policy {} tenant {}", errorMsg, policyName, tenantId, e);
                return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String errorMsg = "Failed to parse OPA response: " + e.getMessage();
                log.error("{} for policy {} tenant {}", errorMsg, policyName, tenantId, e);
                return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
            } catch (Exception e) {
                String errorMsg = "Unexpected error evaluating policy: " + e.getMessage();
                log.error("{} for policy {} tenant {}", errorMsg, policyName, tenantId, e);
                return PolicyEvalResult.deny(policyName, List.of(errorMsg), 100);
            }
        });
    }

    /**
     * Typed OPA response structure.
     * OPA returns: {"result": {"allow": boolean, "reasons": [string]}}
     */
    record OpaResponse(OpaResult result) {
        record OpaResult(Boolean allow, List<String> reasons) {}
    }
}
