/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process AEP client that loads AEP classes from classpath or configured library JAR.
 
 * @doc.type class
 * @doc.purpose Handles aep library client operations
 * @doc.layer product
 * @doc.pattern Implementation
*/
public final class AepLibraryClient implements AepClient {

  private static final Logger LOG = LoggerFactory.getLogger(AepLibraryClient.class);
  private static final String DEFAULT_TENANT_ID = "default";
  private static final String AEP_CLASS = "com.ghatana.aep.Aep";
  private static final String AEP_EVENT_CLASS = "com.ghatana.aep.AepEngine$Event";

  private final ObjectMapper objectMapper;
  private final URLClassLoader managedClassLoader;
  private final ClassLoader aepClassLoader;
  private final Class<?> eventClass;
  private final Object engine;

  public AepLibraryClient(String libraryPath) throws AepException {
    this.objectMapper = JsonUtils.getDefaultMapper();
    this.managedClassLoader = createManagedClassLoader(libraryPath);
    this.aepClassLoader =
        managedClassLoader != null
            ? managedClassLoader
            : Thread.currentThread().getContextClassLoader();

    try {
      Class<?> aepClass = Class.forName(AEP_CLASS, true, aepClassLoader);
      this.eventClass = Class.forName(AEP_EVENT_CLASS, true, aepClassLoader);
      Method embedded = aepClass.getMethod("embedded");
      this.engine = embedded.invoke(null);
      LOG.info("Initialized AEP library client using classpath/JAR loading");
    } catch (ReflectiveOperationException e) {
      closeManagedClassLoader();
      throw new AepException(
          "Failed to initialize embedded AEP. Ensure AEP classes are present on classpath"
              + " or set AEP_LIBRARY_PATH to a valid JAR.",
          rootCause(e));
    }
  }

  @Override
  public String publishEvent(String eventType, String payload) throws AepException {
    Map<String, Object> payloadMap = asMap(parseJsonValue(payload, "payload"));
    String tenantId = extractTenantId(payloadMap);

    Object event = createEvent(eventType, payloadMap);
    Object result = awaitPromise(invokeEngine("process", new Class<?>[] {String.class, eventClass}, tenantId, event));

    String eventId = invokeRecordString(result, "eventId");
    boolean success = invokeRecordBoolean(result, "success");
    if (!success) {
      throw new AepException("AEP library failed to process event: " + eventType);
    }
    return eventId;
  }

  @Override
  public String queryEvents(String query) throws AepException {
    Map<String, Object> queryMap = asMap(parseJsonValue(query, "query"));
    String tenantId = extractTenantId(queryMap);

    Object patternsObj = awaitPromise(invokeEngine("listPatterns", new Class<?>[] {String.class}, tenantId));
    List<?> patterns = patternsObj instanceof List<?> list ? list : List.of();

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
      List<Object> events = toEvents(contextMap.get("events"));
      if (events.isEmpty()) {
        events = List.of(createEvent("action." + action, contextMap));
      }
      Object anomaliesObj =
          awaitPromise(invokeEngine("detectAnomalies", new Class<?>[] {String.class, List.class}, tenantId, events));
      List<?> anomalies = anomaliesObj instanceof List<?> list ? list : List.of();

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("action", action);
      response.put("status", "completed");
      response.put("count", anomalies.size());
      response.put("anomalies", anomalies);
      response.put("mode", "library");
      return writeJson(response);
    }

    Object event = createEvent("action." + action, contextMap);
    Object result = awaitPromise(invokeEngine("process", new Class<?>[] {String.class, eventClass}, tenantId, event));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("action", action);
    response.put("status", invokeRecordBoolean(result, "success") ? "submitted" : "failed");
    response.put("eventId", invokeRecordString(result, "eventId"));
    response.put("detections", invokeRecordListSize(result, "detections"));
    response.put("mode", "library");
    return writeJson(response);
  }

  @Override
  public String healthCheck() throws AepException {
    Object eventCloud = invokeEngine("eventCloud", new Class<?>[0]);
    return eventCloud != null ? "healthy" : "unhealthy";
  }

  @Override
  public void close() {
    try {
      invokeEngine("close", new Class<?>[0]);
    } catch (Exception e) {
      LOG.warn("Error closing embedded AEP engine", e);
    } finally {
      closeManagedClassLoader();
    }
  }

  private URLClassLoader createManagedClassLoader(String libraryPath) throws AepException {
    if (libraryPath == null || libraryPath.isBlank()) {
      return null;
    }

    Path path = Path.of(libraryPath).toAbsolutePath().normalize();
    if (!Files.exists(path)) {
      LOG.warn("AEP library path does not exist: {} (falling back to classpath)", path);
      return null;
    }

    try {
      LOG.info("Loading AEP classes from {}", path);
      return new URLClassLoader(new java.net.URL[] {path.toUri().toURL()}, Thread.currentThread().getContextClassLoader());
    } catch (MalformedURLException e) {
      throw new AepException("Invalid AEP library path: " + path, e);
    }
  }

  private Object createEvent(String type, Map<String, Object> payload) throws AepException {
    try {
      return eventClass
          .getConstructor(String.class, Map.class, Map.class, Instant.class)
          .newInstance(type, payload, Map.of(), Instant.now());
    } catch (ReflectiveOperationException e) {
      throw new AepException("Failed to create AEP Event instance", rootCause(e));
    }
  }

  private List<Object> toEvents(Object rawEvents) throws AepException {
    if (!(rawEvents instanceof List<?> eventList)) {
      return List.of();
    }

    List<Object> events = new ArrayList<>();
    for (Object eventObj : eventList) {
      if (!(eventObj instanceof Map<?, ?> rawEventMap)) {
        continue;
      }
      Map<String, Object> eventMap = toStringObjectMap(rawEventMap);
      String type = String.valueOf(eventMap.getOrDefault("type", "unknown"));
      Map<String, Object> payload = asMap(eventMap.get("payload"));
      events.add(createEvent(type, payload));
    }
    return events;
  }

  private Object invokeEngine(String methodName, Class<?>[] paramTypes, Object... args)
      throws AepException {
    try {
      Method method = engine.getClass().getMethod(methodName, paramTypes);
      return method.invoke(engine, args);
    } catch (ReflectiveOperationException e) {
      throw new AepException("AEP library call failed: " + methodName, rootCause(e));
    }
  }

  private Object awaitPromise(Object promise) throws AepException {
    try {
      Method getResult = promise.getClass().getMethod("getResult");
      return getResult.invoke(promise);
    } catch (ReflectiveOperationException e) {
      throw new AepException("Failed to resolve AEP promise result", rootCause(e));
    }
  }

  private String invokeRecordString(Object record, String method) throws AepException {
    try {
      Object value = record.getClass().getMethod(method).invoke(record);
      return value != null ? String.valueOf(value) : null;
    } catch (ReflectiveOperationException e) {
      throw new AepException("Failed to read AEP response field: " + method, rootCause(e));
    }
  }

  private boolean invokeRecordBoolean(Object record, String method) throws AepException {
    try {
      Object value = record.getClass().getMethod(method).invoke(record);
      return value instanceof Boolean b && b;
    } catch (ReflectiveOperationException e) {
      throw new AepException("Failed to read AEP response field: " + method, rootCause(e));
    }
  }

  private int invokeRecordListSize(Object record, String method) throws AepException {
    try {
      Object value = record.getClass().getMethod(method).invoke(record);
      if (value instanceof List<?> list) {
        return list.size();
      }
      return 0;
    } catch (ReflectiveOperationException e) {
      throw new AepException("Failed to read AEP response field: " + method, rootCause(e));
    }
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

  private void closeManagedClassLoader() {
    if (managedClassLoader != null) {
      try {
        managedClassLoader.close();
      } catch (IOException e) {
        LOG.warn("Failed to close AEP library classloader", e);
      }
    }
  }

  private Throwable rootCause(ReflectiveOperationException e) {
    if (e instanceof InvocationTargetException ite && ite.getCause() != null) {
      return ite.getCause();
    }
    return e;
  }
}
