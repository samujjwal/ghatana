/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import io.activej.promise.Promise;
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
 * <p>This is a stub implementation. The response parsing assumes OPA returns:
 * <pre>{"result": {"allow": true}}</pre>
 * A production implementation should deserialise the full OPA result using Jackson.
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

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String opaBaseUrl;
    private final HttpClient httpClient;
    private final Executor executor;

    /**
     * Construct an OPA client.
     *
     * @param opaBaseUrl base URL of the OPA server (e.g. {@code http://opa:8181})
     * @param executor   blocking executor for HTTP calls (must not be the event-loop thread)
     */
    public OpaClient(String opaBaseUrl, Executor executor) {
        this.opaBaseUrl = opaBaseUrl.stripTrailing();
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .build();
    }

    @Override
    public Promise<PolicyEvalResult> evaluate(
            String tenantId, String policyName, Map<String, Object> input) {
        // TODO: serialise input with Jackson and parse the OPA JSON response properly.
        // This stub always allows — replace before production use.
        return Promise.ofBlocking(executor, () -> {
            String url = opaBaseUrl + "/v1/data/" + policyName.replace('.', '/');
            String body = """
                {"input": %s}
                """.formatted(mapToJsonUnsafe(input));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return PolicyEvalResult.deny(policyName,
                    List.of("OPA returned HTTP " + response.statusCode()), 100);
            }

            // Minimal parsing: check if response body contains '"allow":true'
            boolean allowed = response.body().contains("\"allow\":true");
            return allowed
                ? PolicyEvalResult.allow(policyName)
                : PolicyEvalResult.deny(policyName,
                    List.of("OPA policy denied the request"), 50);
        });
    }

    /**
     * Minimal, injection-safe JSON serialisation for string-keyed maps.
     * Only handles String, Number, and Boolean values. Other values are omitted.
     * A production implementation MUST use Jackson or Gson.
     */
    private static String mapToJsonUnsafe(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            // Escape key to prevent injection
            sb.append('"').append(e.getKey().replace("\"", "\\\"")).append('"').append(":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("null");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
