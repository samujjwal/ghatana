/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.platform.core.util.JsonUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process AEP client that calls the embedded {@link AepEngine} via direct typed API.
 *
 * <p>The {@code :products:aep:platform} module is always on the YAPPC API compile classpath; no
 * class-loader tricks or reflection are needed. Because {@link AepEngine} implementations return
 * pre-resolved {@code Promise.of()} values, calling {@code .getResult()} is safe here — it
 * returns immediately without scheduling work on an event-loop.
 *
 * @doc.type class
 * @doc.purpose In-process AEP client using direct typed API (no reflection)
 * @doc.layer product
 * @doc.pattern Client, Adapter
 */
public final class AepLibraryClient implements AepClient {

  private static final Logger LOG = LoggerFactory.getLogger(AepLibraryClient.class);
  private static final String DEFAULT_TENANT_ID = "default";

  private final ObjectMapper objectMapper;
  private final AepEngine engine;

  /**
   * Creates an {@link AepLibraryClient} backed by an embedded {@link AepEngine}.
   *
   * <p>The {@code libraryPath} parameter is accepted for backward API compatibility but is
   * intentionally ignored — AEP classes are always loaded from the standard classpath.
   *
   * @param libraryPath ignored; retained for compatibility with {@link AepClientFactory}
   */
  public AepLibraryClient(@SuppressWarnings("unused") String libraryPath) {
    this.objectMapper = JsonUtils.getDefaultMapper();
    this.engine = Aep.embedded();
    LOG.info("Initialized AEP library client (embedded, direct typed API)");
  }

  @Override
  public String publishEvent(String eventType, String payload) throws AepException {
    Map<String, Object> payloadMap = asMap(parseJsonValue(payload, "payload"));
    String tenantId = extractTenantId(payloadMap);

    AepEngine.Event event = new AepEngine.Event(eventType, payloadMap, Map.of(), Instant.now());
    AepEngine.ProcessingResult result = engine.process(tenantId, event).getResult();

    if (result == null || !result.success()) {
      throw new AepException("AEP library failed to process event: " + eventType);
    }
    return result.eventId();
  }

  @Override
  public String queryEvents(String query) throws AepException {
    Map<String, Object> queryMap = asMap(parseJsonValue(query, "query"));
    String tenantId = extractTenantId(queryMap);

    List<AepEngine.Pattern> patterns = engine.listPatterns(tenantId).getResult();
    if (patterns == null) patterns = List.of();

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("tenantId", tenantId);
    response.put("count", patterns.size());
    response.put("patterns", patterns);
    response.put("mode", "library");
    return writeJson(response);
  }

  @Override
  public String executeAction(String action, String context) throws AepException {
    Map<String, Object> contextMap = asMap(parseJsonValue(context, "context"));
    String tenantId = extractTenantId(contextMap);

    if ("detect-patterns".equalsIgnoreCase(action)) {
      List<AepEngine.Event> events = toEvents(contextMap.get("events"));
      if (events.isEmpty()) {
        events = List.of(new AepEngine.Event("action." + action, contextMap, Map.of(), Instant.now()));
      }
      List<AepEngine.Anomaly> anomalies = engine.detectAnomalies(tenantId, events).getResult();
      if (anomalies == null) anomalies = List.of();

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("action", action);
      response.put("status", "completed");
      response.put("count", anomalies.size());
      response.put("anomalies", anomalies);
      response.put("mode", "library");
      return writeJson(response);
    }

    AepEngine.Event event = new AepEngine.Event("action." + action, contextMap, Map.of(), Instant.now());
    AepEngine.ProcessingResult result = engine.process(tenantId, event).getResult();
    boolean success = result != null && result.success();
    int detectionCount = result != null ? result.detections().size() : 0;
    String eventId = result != null ? result.eventId() : null;

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("action", action);
    response.put("status", success ? "submitted" : "failed");
    response.put("eventId", eventId);
    response.put("detections", detectionCount);
    response.put("mode", "library");
    return writeJson(response);
  }

  @Override
  public String healthCheck() throws AepException {
    return engine.eventCloud() != null ? "healthy" : "unhealthy";
  }

  @Override
  public void close() {
    try {
      engine.close();
    } catch (Exception e) {
      LOG.warn("Error closing embedded AEP engine", e);
    }
  }

  // ==================== Private Helpers ====================

  private List<AepEngine.Event> toEvents(Object rawEvents) {
    if (!(rawEvents instanceof List<?> eventList)) {
      return List.of();
    }

    List<AepEngine.Event> events = new ArrayList<>();
    for (Object eventObj : eventList) {
      if (!(eventObj instanceof Map<?, ?> rawEventMap)) {
        continue;
      }
      Map<String, Object> eventMap = toStringObjectMap(rawEventMap);
      String type = String.valueOf(eventMap.getOrDefault("type", "unknown"));
      Map<String, Object> payload = asMap(eventMap.get("payload"));
      events.add(new AepEngine.Event(type, payload, Map.of(), Instant.now()));
    }
    return events;
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

  private String writeJson(Object value) throws AepException {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new AepException("Failed to serialize AEP response", e);
    }
  }
}
