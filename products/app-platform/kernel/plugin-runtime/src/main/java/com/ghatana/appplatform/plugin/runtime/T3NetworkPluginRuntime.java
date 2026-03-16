/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.runtime;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginTier;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Executes Tier-3 (network-capable) plugins by delegating to gRPC/HTTP subprocess stubs
 * running as sidecar containers in the same Kubernetes pod (STORY-K04-005).
 *
 * <p>T3 plugins are fully trusted external processes. They must declare all required
 * capabilities (including {@code EXECUTE_NETWORK}) and receive security-team approval
 * before activation. This runtime calls the local sidecar HTTP endpoint exposed by the
 * plugin container on a loopback socket and returns the result.
 *
 * <p>The stub is intentionally simple: it POSTs a JSON body and reads a JSON response.
 * In-pod loopback connections are guaranteed by the Kubernetes pod network namespace.
 *
 * @doc.type  class
 * @doc.purpose Delegates T3 plugin invocations to local sidecar process via HTTP stub (K04-005)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class T3NetworkPluginRuntime {

    private static final Logger log = LoggerFactory.getLogger(T3NetworkPluginRuntime.class);

    /** Default port assigned to T3 plugin sidecars (configurable per manifest via {@code sidecarPort}). */
    public static final int DEFAULT_SIDECAR_PORT = 7070;
    /** Maximum allowed execution time for a T3 plugin invocation. */
    public static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final Executor executor;

    public T3NetworkPluginRuntime(Executor executor) {
        this.executor   = Objects.requireNonNull(executor, "executor");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Invokes a T3 plugin by posting to its loopback sidecar endpoint.
     *
     * @param manifest   the plugin manifest (must declare tier = T3)
     * @param input      evaluation input to send as JSON body
     * @param sidecarPort port the sidecar is listening on (use {@link #DEFAULT_SIDECAR_PORT} if unknown)
     * @return promise resolving to the raw JSON response body
     */
    public Promise<String> invoke(PluginManifest manifest, Map<String, Object> input, int sidecarPort) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(input,    "input");

        return Promise.ofBlocking(executor, () -> {
            enforceT3Tier(manifest);

            URI endpoint = URI.create("http://127.0.0.1:" + sidecarPort + "/invoke");
            String requestBody = toJson(input);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .header("X-Plugin-Name",    manifest.name())
                    .header("X-Plugin-Version", manifest.version().toString())
                    .timeout(CALL_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new T3InvocationException(
                        "T3 sidecar returned HTTP " + response.statusCode()
                                + " for plugin=" + manifest.name() + ": " + response.body());
            }

            log.debug("T3 plugin invoked: name={} version={} port={}",
                    manifest.name(), manifest.version(), sidecarPort);
            return response.body();
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void enforceT3Tier(PluginManifest manifest) {
        if (manifest.tier() != PluginTier.T3) {
            throw new IllegalArgumentException(
                    "T3NetworkPluginRuntime only accepts T3 plugins, got: " + manifest.tier());
        }
    }

    /** Minimal JSON serialisation for a string-keyed map (avoids Jackson dependency). */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey().replace("\"", "\\\"")).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append('"').append(s.replace("\"", "\\\"")).append('"');
            } else if (v instanceof Boolean || v instanceof Number) {
                sb.append(v);
            } else if (v == null) {
                sb.append("null");
            } else {
                sb.append('"').append(v.toString().replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** Thrown when a T3 sidecar returns an error or is unreachable. */
    public static final class T3InvocationException extends RuntimeException {
        public T3InvocationException(String message) { super(message); }
        public T3InvocationException(String message, Throwable cause) { super(message, cause); }
    }
}
