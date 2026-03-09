package com.ghatana.pipeline.registry.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.session.RequestContext;
import com.ghatana.platform.security.session.SessionManager;
import com.ghatana.platform.security.session.SessionState;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;

/**
 * HTTP controller for session management operations.
 *
 * <p>Purpose: Provides REST endpoints for inspecting and managing user sessions.
 * Supports session information retrieval, invalidation, and attribute management
 * for the Pipeline Registry service.</p>
 *
 * @doc.type class
 * @doc.purpose REST endpoints for user session management
 * @doc.layer product
 * @doc.pattern Controller
 * @since 2.0.0
 */
public class SessionController {

    private static final Logger LOG = LoggerFactory.getLogger(SessionController.class);
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    private HttpResponse createJsonResponse(int statusCode, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return ResponseBuilder.status(statusCode)
                    .header("Content-Type", "application/json")
                    .rawJson(json)
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to serialize response", e);
            return ResponseBuilder.status(500)
                    .header("Content-Type", "application/json")
                    .rawJson("{\"error\":\"Failed to serialize response\"}")
                    .build();
        }
    }

    private final SessionManager sessionManager;

    @Inject
    public SessionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Get information about the current session.
     */
    public Promise<HttpResponse> getCurrentSession(HttpRequest request) {
        Optional<RequestContext> context = RequestContext.currentOptional();

        if (context.isEmpty() || !context.get().hasSession()) {
            return Promise.of(ResponseBuilder.status(404)
                    .header("Content-Type", "application/json")
                    .rawJson("{\"error\":\"No active session\"}")
                    .build());
        }

        SessionState session = context.get().getSession();

        Map<String, Object> response = new HashMap<>();
        response.put("id", session.getId());
        response.put("createdAt", session.getCreatedAt().toString());
        response.put("lastAccessedAt", session.getLastAccessedAt().toString());
        response.put("userId", session.getUserId());
        response.put("tenantId", session.getTenantId());
        response.put("maxInactiveInterval", session.getMaxInactiveInterval());
        response.put("attributes", session.getAttributeNames());

        return Promise.of(createJsonResponse(200, response));
    }

    /**
     * Invalidate the current session.
     */
    public Promise<HttpResponse> invalidateSession(HttpRequest request) {
        Optional<RequestContext> context = RequestContext.currentOptional();

        if (context.isEmpty() || !context.get().hasSession()) {
            return Promise.of(ResponseBuilder.status(404)
                    .header("Content-Type", "application/json")
                    .rawJson("{\"error\":\"No active session\"}")
                    .build());
        }

        SessionState session = context.get().getSession();
        String sessionId = session.getId();

        return sessionManager.deleteSession(sessionId)
                .map(deleted -> {
                    if (deleted) {
                        context.get().setSession(null);
                        return ResponseBuilder.ok()
                                .header("Content-Type", "application/json")
                                .rawJson("{\"success\":true,\"message\":\"Session invalidated\"}")
                                .build();
                    } else {
                        return ResponseBuilder.status(500)
                                .header("Content-Type", "application/json")
                                .rawJson("{\"error\":\"Failed to invalidate session\"}")
                                .build();
                    }
                });
    }

    /**
     * Get all sessions for a user.
     */
    public Promise<HttpResponse> getUserSessions(HttpRequest request) {
        String userId = request.getQueryParameter("userId");

        if (userId == null || userId.isEmpty()) {
            return Promise.of(ResponseBuilder.status(400)
                    .header("Content-Type", "application/json")
                    .rawJson("{\"error\":\"User ID is required\"}")
                    .build());
        }

        return sessionManager.findSessionsByUserId(userId)
                .map(sessions -> createJsonResponse(200, Map.of("userId", userId, "sessions", sessions)));
    }

    /**
     * Get all sessions for a tenant.
     */
    public Promise<HttpResponse> getTenantSessions(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");

        if (tenantId == null || tenantId.isEmpty()) {
            return Promise.of(ResponseBuilder.status(400)
                    .header("Content-Type", "application/json")
                    .rawJson("{\"error\":\"Tenant ID is required\"}")
                    .build());
        }

        return sessionManager.findSessionsByTenantId(tenantId)
                .map(sessions -> createJsonResponse(200, Map.of("tenantId", tenantId, "sessions", sessions)));
    }

    /**
     * Clean up expired sessions.
     */
    public Promise<HttpResponse> cleanupSessions(HttpRequest request) {
        return sessionManager.deleteExpiredSessions()
                .map(count -> createJsonResponse(200, Map.of("deletedCount", count)));
    }
}
