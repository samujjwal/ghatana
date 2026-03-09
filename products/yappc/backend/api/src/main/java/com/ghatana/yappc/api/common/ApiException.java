/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.common;

/**
 * Base exception for typed API errors with HTTP status codes.
 *
 * <p>Subclasses define specific HTTP behaviors:
 *
 * <ul>
 *   <li>{@link NotFoundException} → 404
 *   <li>{@link ConflictException} → 409
 *   <li>{@link ValidationException} → 422
 *   <li>{@link ServiceUnavailableException} → 503
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Typed API exception hierarchy
 * @doc.layer api
 * @doc.pattern Exception Hierarchy
 */
public class ApiException extends RuntimeException {

  private final int statusCode;
  private final String errorCode;

  public ApiException(int statusCode, String errorCode, String message) {
    super(message);
    this.statusCode = statusCode;
    this.errorCode = errorCode;
  }

  public ApiException(int statusCode, String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
    this.errorCode = errorCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getErrorCode() {
    return errorCode;
  }

  /** 404 Not Found. */
  public static class NotFoundException extends ApiException {
    public NotFoundException(String message) {
      super(404, "NOT_FOUND", message);
    }

    public NotFoundException(String entityType, Object id) {
      super(404, "NOT_FOUND", entityType + " not found: " + id);
    }
  }

  /** 409 Conflict. */
  public static class ConflictException extends ApiException {
    public ConflictException(String message) {
      super(409, "CONFLICT", message);
    }
  }

  /** 422 Unprocessable Entity. */
  public static class ValidationException extends ApiException {
    public ValidationException(String message) {
      super(422, "VALIDATION_ERROR", message);
    }
  }

  /** 503 Service Unavailable. */
  public static class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String message) {
      super(503, "SERVICE_UNAVAILABLE", message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
      super(503, "SERVICE_UNAVAILABLE", message, cause);
    }
  }
}
