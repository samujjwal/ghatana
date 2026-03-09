package com.ghatana.pipeline.registry.session;

import com.ghatana.platform.security.session.RequestContext;
import com.ghatana.platform.security.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility methods for session management.
 *
 * <p>Purpose: Provides static helper methods for accessing session state,
 * user context, and tenant information from the current request context.
 * Simplifies session operations throughout the service layer.</p>
 *
 * @doc.type class
 * @doc.purpose Static utilities for session access and manipulation
 * @doc.layer product
 * @doc.pattern Utility
 * @since 2.0.0
 */
public final class SessionUtils {
    
    private static final Logger LOG = LoggerFactory.getLogger(SessionUtils.class);
    
    private SessionUtils() {
        // Utility class, no instances
    }
    
    /**
     * Get the current session, or empty if not available.
     */
    public static Optional<SessionState> getCurrentSession() {
        return RequestContext.currentOptional()
            .filter(RequestContext::hasSession)
            .map(RequestContext::getSession);
    }
    
    /**
     * Get the current user ID, or empty if not available.
     */
    public static Optional<String> getCurrentUserId() {
        return getCurrentSession()
            .map(SessionState::getUserId);
    }
    
    /**
     * Get the current tenant ID, or empty if not available.
     */
    public static Optional<String> getCurrentTenantId() {
        return getCurrentSession()
            .map(SessionState::getTenantId);
    }
    
    /**
     * Execute a task with the current session, or throw an exception if no session is available.
     */
    public static <T> T withSession(Function<SessionState, T> task) {
        SessionState session = getCurrentSession()
            .orElseThrow(() -> new IllegalStateException("No active session"));
        return task.apply(session);
    }
    
    /**
     * Execute a task with the current session, or return a default value if no session is available.
     */
    public static <T> T withSessionOrDefault(Function<SessionState, T> task, T defaultValue) {
        return getCurrentSession()
            .map(task)
            .orElse(defaultValue);
    }
    
    /**
     * Execute a task with the current session, or execute a default task if no session is available.
     */
    public static <T> T withSessionOrElse(Function<SessionState, T> task, Supplier<T> defaultTask) {
        return getCurrentSession()
            .map(task)
            .orElseGet(defaultTask);
    }
    
    /**
     * Execute a task with the current user ID, or throw an exception if no user ID is available.
     */
    public static <T> T withUserId(Function<String, T> task) {
        String userId = getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("No user ID in session"));
        return task.apply(userId);
    }
    
    /**
     * Execute a task with the current tenant ID, or throw an exception if no tenant ID is available.
     */
    public static <T> T withTenantId(Function<String, T> task) {
        String tenantId = getCurrentTenantId()
            .orElseThrow(() -> new IllegalStateException("No tenant ID in session"));
        return task.apply(tenantId);
    }
    
    /**
     * Execute a task with the current user ID and tenant ID, or throw an exception if either is not available.
     */
    public static <T> T withUserAndTenant(BiFunction<String, String, T> task) {
        SessionState session = getCurrentSession()
            .orElseThrow(() -> new IllegalStateException("No active session"));
        
        String userId = Optional.ofNullable(session.getUserId())
            .orElseThrow(() -> new IllegalStateException("No user ID in session"));
        
        String tenantId = Optional.ofNullable(session.getTenantId())
            .orElseThrow(() -> new IllegalStateException("No tenant ID in session"));
        
        return task.apply(userId, tenantId);
    }
    
    /**
     * Functional interface for a function that takes two arguments.
     */
    @FunctionalInterface
    public interface BiFunction<T, U, R> {
        R apply(T t, U u);
    }
}
