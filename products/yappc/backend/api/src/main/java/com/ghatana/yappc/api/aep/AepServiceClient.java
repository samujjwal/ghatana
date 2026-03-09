/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP client for external AEP service mode.
 
 * @doc.type class
 * @doc.purpose Handles aep service client operations
 * @doc.layer product
 * @doc.pattern Implementation
*/
public final class AepServiceClient implements AepClient {

  private static final Logger LOG = LoggerFactory.getLogger(AepServiceClient.class);
  private static final String DEFAULT_TENANT_ID = "default";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final URI baseUri;
  private final Duration timeout;

  public AepServiceClient(AepConfig config) {
    Objects.requireNonNull(config, "config");
    this.objectMapper = JsonUtils.getDefaultMapper();
    this.baseUri = URI.create(config.getServiceUrl());
    this.timeout = Duration.ofMillis(Math.max(config.getServiceTimeoutMs(), 1000));
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    LOG.info("Initialized AEP service client for {}", config.getServiceUrl());
  }

  @Override
  public String publishEvent(String eventType, String payload) throws AepException {
    Map<String, Object> payloadMap = asMap(parseJsonValue(payload, "payload"));
    String tenantId = extractTenantId(payloadMap);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("tenantId", tenantId);
    requestBody.put("type", eventType);
    requestBody.put("payload", payloadMap);

    HttpResponse<String> response =
        sendJson("POST", "/api/v1/events", requestBody, tenantId, "publish event");
    Map<String, Object> responseBody = parseJsonMap(response.body(), "publish event response");
    Object eventId = responseBody.get("eventId");
    if (eventId == null) {
      throw new AepException("AEP service response missing eventId");
    }
    return String.valueOf(eventId);
  }

  @Override
  public String queryEvents(String query) throws AepException {
    Map<String, Object> queryMap = asMap(parseJsonValue(query, "query"));
    String tenantId = extractTenantId(queryMap);

    StringBuilder path = new StringBuilder("/api/v1/patterns?tenantId=");
    path.append(urlEncode(tenantId));

    Object status = queryMap.get("status");
    if (status != null) {
      path.append("&status=").append(urlEncode(String.valueOf(status)));
    }

    HttpResponse<String> response = sendGet(path.toString(), tenantId, "query events");
    return response.body();
  }

  @Override
  public String executeAction(String action, String context) throws AepException {
    Map<String, Object> contextMap = asMap(parseJsonValue(context, "context"));
    String tenantId = extractTenantId(contextMap);

    if ("detect-patterns".equalsIgnoreCase(action)) {
      Map<String, Object> requestBody = new LinkedHashMap<>();
      requestBody.put("tenantId", tenantId);
      Object rawEvents = contextMap.get("events");
      if (rawEvents instanceof List<?>) {
        requestBody.put("events", rawEvents);
      } else {
        requestBody.put("events", List.of(actionEvent(action, contextMap)));
      }
      HttpResponse<String> response =
          sendJson(
              "POST",
              "/api/v1/analytics/anomalies",
              requestBody,
              tenantId,
              "execute action " + action);
      return response.body();
    }

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("tenantId", tenantId);
    requestBody.put("type", "action." + action);
    requestBody.put("payload", contextMap);

    HttpResponse<String> response =
        sendJson("POST", "/api/v1/events", requestBody, tenantId, "execute action " + action);
    Map<String, Object> responseBody = new LinkedHashMap<>(parseJsonMap(response.body(), "action response"));
    responseBody.putIfAbsent("action", action);
    responseBody.putIfAbsent("status", "submitted");
    return toJson(responseBody);
  }

  @Override
  public String healthCheck() throws AepException {
    HttpResponse<String> response = sendGet("/health", null, "health check");
    Map<String, Object> body = parseJsonMap(response.body(), "health response");
    String status = String.valueOf(body.getOrDefault("status", "UNKNOWN"));
    if ("UP".equalsIgnoreCase(status)
        || "READY".equalsIgnoreCase(status)
        || "LIVE".equalsIgnoreCase(status)
        || "healthy".equalsIgnoreCase(status)) {
      return "healthy";
    }
    return "unhealthy";
  }

  @Override
  public void close() {
    // java.net.http.HttpClient has no explicit close lifecycle.
  }

  private HttpResponse<String> sendGet(String path, String tenantId, String operation)
      throws AepException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(resolve(path))
            .timeout(timeout)
            .header("Accept", "application/json")
            .GET();
    if (tenantId != null && !tenantId.isBlank()) {
      builder.header("X-Tenant-Id", tenantId);
    }
    return send(builder.build(), operation);
  }

  private HttpResponse<String> sendJson(
      String method, String path, Object body, String tenantId, String operation)
      throws AepException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(resolve(path))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
    if (tenantId != null && !tenantId.isBlank()) {
      builder.header("X-Tenant-Id", tenantId);
    }

    String json = toJson(body);
    if ("POST".equalsIgnoreCase(method)) {
      builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    } else if ("PUT".equalsIgnoreCase(method)) {
      builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    } else {
      builder.method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    }
    return send(builder.build(), operation);
  }

  private HttpResponse<String> send(HttpRequest request, String operation) throws AepException {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int code = response.statusCode();
      if (code < 200 || code >= 300) {
        throw new AepException(
            "AEP service "
                + operation
                + " failed with status "
                + code
                + " and body: "
                + response.body());
      }
      return response;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AepException("AEP service " + operation + " interrupted", e);
    } catch (IOException e) {
      throw new AepException("AEP service " + operation + " failed", e);
    }
  }

  private URI resolve(String path) {
    if (path.startsWith("/")) {
      return baseUri.resolve(path);
    }
    return baseUri.resolve("/" + path);
  }

  private Object parseJsonValue(String json, String fieldName) throws AepException {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      throw new AepException("Invalid JSON for " + fieldName, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJsonMap(String json, String operation) throws AepException {
    try {
      Object value = objectMapper.readValue(json, Object.class);
      if (value instanceof Map<?, ?> rawMap) {
        return toStringObjectMap(rawMap);
      }
      return Map.of("value", value);
    } catch (JsonProcessingException e) {
      throw new AepException("Invalid JSON received from AEP during " + operation, e);
    }
  }

  private Map<String, Object> asMap(Object value) {
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map<?, ?> rawMap) {
      return toStringObjectMap(rawMap);
    }
    return Map.of("value", value);
  }

  private Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      if (entry.getKey() != null) {
        map.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    return map;
  }

  private Map<String, Object> actionEvent(String action, Map<String, Object> context) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "action." + action);
    event.put("payload", context);
    return event;
  }

  private String extractTenantId(Map<String, Object> payload) {
    Object tenantId = payload.get("tenantId");
    if (tenantId == null) {
      tenantId = payload.get("tenant_id");
    }
    if (tenantId instanceof String tenant && !tenant.isBlank()) {
      return tenant;
    }
    return DEFAULT_TENANT_ID;
  }

  private String toJson(Object body) throws AepException {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new AepException("Failed to serialize AEP request body", e);
    }
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
