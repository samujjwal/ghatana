/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.common;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts tenant and user context from HTTP requests.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized context extraction for multi-tenancy: - Extract tenant ID from JWT token or
 * header - Extract user ID and persona from token - Validate tenant access
 *
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * RequestContext ctx = TenantContextExtractor.extract(request);
 * String tenantId = ctx.tenantId();
 * String userId = ctx.userId();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tenant context extraction
 * @doc.layer api
 * @doc.pattern Middleware, Strategy
 */
public class TenantContextExtractor {

  private static final Logger logger = LoggerFactory.getLogger(TenantContextExtractor.class);

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String TENANT_ID_HEADER = "X-Tenant-Id";
  private static final String PERSONA_HEADER = "X-Persona";
  private static final String BEARER_PREFIX = "Bearer ";

  /** Request context containing extracted values. */
  public record RequestContext(
      String tenantId,
      String userId,
      String userEmail,
      String persona,
      String role,
      boolean authenticated) {
    public static RequestContext anonymous() {
      return new RequestContext(null, null, null, null, null, false);
    }

    public static RequestContext of(String tenantId, String userId, String persona) {
      return new RequestContext(tenantId, userId, null, persona, null, true);
    }
  }

  /**
   * Extracts context from HTTP request.
   *
   * @param request the HTTP request
   * @return request context
   */
  public static RequestContext extract(HttpRequest request) {
    try {
      // Try Authorization header (JWT token)
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
        return extractFromJwt(authHeader.substring(BEARER_PREFIX.length()));
      }

      // Try explicit headers (for development/testing)
      String tenantId = request.getHeader(HttpHeaders.of(TENANT_ID_HEADER));
      String persona = request.getHeader(HttpHeaders.of(PERSONA_HEADER));

      if (tenantId != null) {
        // Development mode: use headers
        logger.debug("Using header-based context: tenantId={}", tenantId);
        return new RequestContext(
            tenantId, "dev-user", null, persona != null ? persona : "DEVELOPER", "MEMBER", true);
      }

      // No authentication found
      logger.debug("No authentication context found");
      return RequestContext.anonymous();

    } catch (Exception e) {
      logger.warn("Failed to extract context: {}", e.getMessage());
      return RequestContext.anonymous();
    }
  }

  /**
   * Extracts context from JWT token.
   *
   * @param token JWT token string
   * @return request context
   */
  private static RequestContext extractFromJwt(String token) {
    try {
      // JWT format: header.payload.signature
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        logger.warn("Invalid JWT format");
        return RequestContext.anonymous();
      }

      // Decode payload (base64url)
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

      // Parse JSON manually (to avoid Jackson dependency in this utility)
      String tenantId = extractJsonField(payload, "tenant_id");
      String userId = extractJsonField(payload, "sub");
      String email = extractJsonField(payload, "email");
      String persona = extractJsonField(payload, "persona");
      String role = extractJsonField(payload, "role");

      if (tenantId == null || userId == null) {
        logger.warn("JWT missing required claims: tenant_id or sub");
        return RequestContext.anonymous();
      }

      logger.debug(
          "Extracted context from JWT: tenantId={}, userId={}, persona={}",
          tenantId,
          userId,
          persona);

      return new RequestContext(tenantId, userId, email, persona, role, true);

    } catch (Exception e) {
      logger.warn("Failed to parse JWT: {}", e.getMessage());
      return RequestContext.anonymous();
    }
  }

  /** Simple JSON field extraction (avoids Jackson dependency). */
  private static String extractJsonField(String json, String field) {
    String search = "\"" + field + "\":\"";
    int start = json.indexOf(search);
    if (start == -1) {
      // Try without quotes (for non-string values)
      search = "\"" + field + "\":";
      start = json.indexOf(search);
      if (start == -1) return null;
      start += search.length();
      int end = json.indexOf(",", start);
      if (end == -1) end = json.indexOf("}", start);
      if (end == -1) return null;
      return json.substring(start, end).trim().replace("\"", "");
    }
    start += search.length();
    int end = json.indexOf("\"", start);
    if (end == -1) return null;
    return json.substring(start, end);
  }

  /**
   * Validates that request has valid tenant context.
   *
   * @param request HTTP request
   * @return Promise of context (fails if not authenticated)
   */
  public static Promise<RequestContext> requireAuthenticated(HttpRequest request) {
    RequestContext ctx = extract(request);
    if (!ctx.authenticated()) {
      return Promise.ofException(new UnauthorizedException("Authentication required"));
    }
    return Promise.of(ctx);
  }

  /**
   * Validates that user has access to specified tenant.
   *
   * @param ctx request context
   * @param tenantId required tenant ID
   * @return Promise of context (fails if tenant mismatch)
   */
  public static Promise<RequestContext> requireTenant(RequestContext ctx, String tenantId) {
    if (!ctx.authenticated()) {
      return Promise.ofException(new UnauthorizedException("Authentication required"));
    }
    if (!tenantId.equals(ctx.tenantId())) {
      return Promise.ofException(
          new ForbiddenException("User does not have access to tenant: " + tenantId));
    }
    return Promise.of(ctx);
  }

  /** Exception for unauthorized access. */
  public static class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
      super(message);
    }
  }

  /** Exception for forbidden access. */
  public static class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
      super(message);
    }
  }
}
