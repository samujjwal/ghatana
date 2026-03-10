/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.infrastructure.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.governance.PolicyEngine;
import io.activej.promise.Promise;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OPA REST API–backed {@link PolicyEngine}.
 *
 * <p><b>Purpose</b><br>
 * Evaluates Rego policies by calling the OPA REST API
 * ({@code POST /v1/data/{package}/deny}). A deny result with a non-empty array
 * means the request is rejected; an empty array means it is allowed.
 *
 * <p><b>Configuration</b><br>
 * <ul>
 *   <li>{@code OPA_ENDPOINT} — base URL of the running OPA instance,
 *       e.g. {@code http://opa:8181}. Mandatory for this engine.
 *   <li>{@code OPA_TIMEOUT_MS} — HTTP call timeout in ms. Default: 3 000.
 * </ul>
 *
 * <p><b>Rego convention</b><br>
 * YAPPC policies live in package {@code yappc.policies.core} and expose a
 * {@code deny[msg]} rule. This engine sends
 * {@code POST /v1/data/yappc/policies/core/deny} and checks whether the
 * result array is empty.
 *
 * <p><b>Thread Safety</b><br>
 * {@link HttpClient} is thread-safe. All state is immutable after construction.
 *
 * @doc.type class
 * @doc.purpose OPA REST API adapter for runtime Rego policy evaluation
 * @doc.layer api
 * @doc.pattern Adapter (Infrastructure)
 */
public final class OpaRestPolicyEngine implements PolicyEngine {

  private static final Logger logger = LoggerFactory.getLogger(OpaRestPolicyEngine.class);

  private final String opaEndpoint;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Duration timeout;

  /**
   * Creates the engine.
   *
   * @param opaEndpoint  base OPA URL (e.g. {@code http://opa:8181})
   * @param objectMapper Jackson mapper for JSON serialization
   */
  public OpaRestPolicyEngine(@NotNull String opaEndpoint, @NotNull ObjectMapper objectMapper) {
    this.opaEndpoint = Objects.requireNonNull(opaEndpoint, "opaEndpoint").stripTrailing();
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

    String timeoutMs = System.getenv("OPA_TIMEOUT_MS");
    this.timeout = Duration.ofMillis(timeoutMs != null ? Long.parseLong(timeoutMs) : 3_000);

    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .build();

    logger.info("OpaRestPolicyEngine initialised — endpoint={} timeout={}ms",
        opaEndpoint, timeout.toMillis());
  }

  /**
   * {@inheritDoc}
   *
   * <p>POSTs to {@code /v1/data/yappc/policies/core/deny} with
   * {@code {"input": context}}.  The call returns {@code true} (allow) when
   * OPA produces zero deny messages, and {@code false} (deny) otherwise.
   * Network errors are logged and treated as an allow to avoid blocking
   * operations when OPA is temporarily unavailable.
   */
  @Override
  @NotNull
  public Promise<Boolean> evaluate(
      @NotNull String policyName, @NotNull Map<String, Object> context) {
    // Derive OPA path from policyName (e.g. "core.deny" → /yappc/policies/core/deny)
    String packagePath = policyName.replace('.', '/');
    String url = opaEndpoint + "/v1/data/yappc/policies/" + packagePath;

    try {
      Map<String, Object> body = Map.of("input", context);
      String json = objectMapper.writeValueAsString(body);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(timeout)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

        Object resultField = result.get("result");
        boolean denied;
        if (resultField instanceof List<?> list) {
          denied = !list.isEmpty();
        } else {
          denied = Boolean.TRUE.equals(resultField);
        }

        if (denied) {
          logger.warn("[OPA] Policy '{}' DENIED for context keys={}", policyName, context.keySet());
          return Promise.of(Boolean.FALSE);
        }
        logger.debug("[OPA] Policy '{}' ALLOWED for context keys={}", policyName, context.keySet());
        return Promise.of(Boolean.TRUE);
      }

      logger.warn("[OPA] Non-200 response {} from {} — defaulting to ALLOW", response.statusCode(), url);
      return Promise.of(Boolean.TRUE);

    } catch (Exception e) {
      logger.error("[OPA] Failed to evaluate policy '{}' via {}: {} — defaulting to ALLOW",
          policyName, url, e.getMessage());
      // Fail open: allow the operation so OPA downtime doesn't block users
      return Promise.of(Boolean.TRUE);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Checks whether the OPA package path resolves to a non-null result. Returns
   * {@code true} when OPA responds 200 with a non-null {@code result} field.
   */
  @Override
  @NotNull
  public Promise<Boolean> policyExists(@NotNull String policyName) {
    String packagePath = policyName.replace('.', '/');
    String url = opaEndpoint + "/v1/data/yappc/policies/" + packagePath;

    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(timeout)
          .GET()
          .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
        return Promise.of(result.containsKey("result") && result.get("result") != null);
      }
      return Promise.of(Boolean.FALSE);
    } catch (Exception e) {
      logger.error("[OPA] Failed to check policy existence '{}': {}", policyName, e.getMessage());
      return Promise.of(Boolean.FALSE);
    }
  }
}
