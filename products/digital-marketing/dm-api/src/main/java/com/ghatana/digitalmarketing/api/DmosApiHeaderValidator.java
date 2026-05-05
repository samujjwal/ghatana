package com.ghatana.digitalmarketing.api;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API header validator for DMOS servlets.
 *
 * <p>Enforces mandatory headers and fails closed in production environments
 * when required headers are missing (P0-1).</p>
 *
 * @doc.type class
 * @doc.purpose API header validation with production-safe defaults
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class DmosApiHeaderValidator {

    private static final Logger LOG = LoggerFactory.getLogger(DmosApiHeaderValidator.class);

    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String PRINCIPAL_ID_HEADER = "X-Principal-ID";
    private static final String SESSION_ID_HEADER = "X-Session-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private static final String PRODUCTION_ENV = "production";
    private static final String DMOS_ENV = "DMOS_ENV";

    private DmosApiHeaderValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that all mandatory headers are present.
     *
     * <p>In production environments, missing mandatory headers cause an exception.
     * In development/test environments, defaults are provided for convenience.</p>
     *
     * @param request the HTTP request
     * @throws IllegalArgumentException if mandatory headers are missing in production
     */
    public static void validateMandatoryHeaders(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of(TENANT_ID_HEADER));
        String principalId = request.getHeader(HttpHeaders.of(PRINCIPAL_ID_HEADER));
        String sessionId = request.getHeader(HttpHeaders.of(SESSION_ID_HEADER));

        if (isProduction()) {
            // Fail closed in production
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException(
                    "Missing required header: " + TENANT_ID_HEADER + " (mandatory in production)"
                );
            }
            if (principalId == null || principalId.isBlank()) {
                throw new IllegalArgumentException(
                    "Missing required header: " + PRINCIPAL_ID_HEADER + " (mandatory in production)"
                );
            }
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException(
                    "Missing required header: " + SESSION_ID_HEADER + " (mandatory in production)"
                );
            }
        } else {
            // Log warnings in development/test
            String remoteAddress = resolveRemoteAddress(request);
            if (tenantId == null || tenantId.isBlank()) {
                LOG.warn("[{}] Missing header: {} - using default for development",
                    remoteAddress, TENANT_ID_HEADER);
            }
            if (principalId == null || principalId.isBlank()) {
                LOG.warn("[{}] Missing header: {} - using default for development",
                    remoteAddress, PRINCIPAL_ID_HEADER);
            }
            if (sessionId == null || sessionId.isBlank()) {
                LOG.warn("[{}] Missing header: {} - using default for development",
                    remoteAddress, SESSION_ID_HEADER);
            }
        }
    }

    private static String resolveRemoteAddress(HttpRequest request) {
        try {
            return String.valueOf(request.getRemoteAddress());
        } catch (NullPointerException ex) {
            return "unknown";
        }
    }

    /**
     * Extracts the tenant ID header, returning a default in non-production environments.
     *
     * @param request the HTTP request
     * @return tenant ID or default
     */
    public static String getTenantId(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of(TENANT_ID_HEADER));
        if ((tenantId == null || tenantId.isBlank()) && !isProduction()) {
            return "default-tenant-dev";
        }
        return tenantId;
    }

    /**
     * Extracts the principal ID header, returning a default in non-production environments.
     *
     * @param request the HTTP request
     * @return principal ID or default
     */
    public static String getPrincipalId(HttpRequest request) {
        String principalId = request.getHeader(HttpHeaders.of(PRINCIPAL_ID_HEADER));
        if ((principalId == null || principalId.isBlank()) && !isProduction()) {
            return "default-principal-dev";
        }
        return principalId;
    }

    /**
     * Extracts the session ID header, returning a default in non-production environments.
     *
     * @param request the HTTP request
     * @return session ID or default
     */
    public static String getSessionId(HttpRequest request) {
        String sessionId = request.getHeader(HttpHeaders.of(SESSION_ID_HEADER));
        if ((sessionId == null || sessionId.isBlank()) && !isProduction()) {
            return "default-session-dev";
        }
        return sessionId;
    }

    /**
     * Extracts the correlation ID header, generating a new one if missing.
     *
     * @param request the HTTP request
     * @return correlation ID (never null)
     */
    public static String getCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(HttpHeaders.of(CORRELATION_ID_HEADER));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Returns {@code true} if the current environment is production.
     *
     * @return {@code true} if production
     */
    private static boolean isProduction() {
        String env = System.getProperty(DMOS_ENV);
        if (env == null || env.isBlank()) {
            env = System.getenv(DMOS_ENV);
        }
        if (env == null || env.isBlank()) {
            return false;
        }
        return PRODUCTION_ENV.equalsIgnoreCase(env.trim());
    }
}
