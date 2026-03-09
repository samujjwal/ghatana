/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;

/**
 * API-layer JSON helpers for request parsing and typed JSON conversion.
 
 * @doc.type class
 * @doc.purpose Handles json utils operations
 * @doc.layer product
 * @doc.pattern Utility
*/
public final class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER =
      com.ghatana.platform.core.util.JsonUtils.getDefaultMapper();

  private JsonUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static ObjectMapper getDefaultMapper() {
    return OBJECT_MAPPER;
  }

  public static <T> Promise<T> parseBody(HttpRequest request, Class<T> bodyType) {
    return request.loadBody().then(
        body -> {
          String json = body != null ? body.getString(StandardCharsets.UTF_8) : null;
          if (json == null || json.isBlank()) {
            return Promise.ofException(new BadRequestException("Request body is required"));
          }

          try {
            return Promise.of(fromJson(json, bodyType));
          } catch (JsonProcessingException e) {
            return Promise.ofException(new BadRequestException("Invalid JSON request body", e));
          }
        });
  }

  public static <T> T fromJson(String json, Class<T> bodyType) throws JsonProcessingException {
    return OBJECT_MAPPER.readValue(json, bodyType);
  }

  public static String toJson(Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize JSON payload", e);
    }
  }

  public static byte[] toJsonBytes(Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize JSON payload", e);
    }
  }

  public static final class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
      super(message);
    }

    public BadRequestException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
