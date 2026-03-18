/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.filter;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.http.server.filter.FilterChain;
import com.ghatana.platform.security.annotation.RequiresPermission;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * {@link FilterChain.Filter} that enforces {@link RequiresPermission} annotations
 * declared on route handler classes or methods.
 *
 * <h2>Permission model</h2>
 * <p>Permission strings follow the {@code "resource:action:scope"} format defined by
 * {@link RequiresPermission}.  Wildcard segments ({@code *}) are supported:</p>
 * <ul>
 *   <li>{@code "event:read:all"} — exact match</li>
 *   <li>{@code "event:*:all"} — any action on {@code event} resources</li>
 *   <li>{@code "*:*:*"} — super-admin (matches everything)</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * <p>The filter is installed in the {@link FilterChain} <em>after</em> JWT validation so
 * that a valid {@link TenantContext} and {@link Principal} are always available.
 * Route handlers annotated with {@code @RequiresPermission} are looked up via a
 * {@link PermissionResolver} that callers provide — typically backed by a role-permission
 * table in the platform identity store.</p>
 *
 * <h2>Annotation scanning</h2>
 * <p>This filter does NOT perform runtime byte-code scanning (no AOP weaving required).
 * Instead the caller registers route-handler {@link Class} objects together with their
 * corresponding {@link PermissionEnforcerFilter}.  The filter builds a lookup map at
 * construction time and evaluates it on each matching request.</p>
 *
 * @doc.type class
 * @doc.purpose Enforces @RequiresPermission annotations on route handlers (K-12)
 * @doc.layer platform
 * @doc.pattern Filter, Security
 */
public final class PermissionEnforcerFilter implements FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(PermissionEnforcerFilter.class);

    /**
     * Resolves the set of permissions granted to the currently authenticated principal.
     *
     * <p>Implementations look up role-permission tables for the given principal and tenant.
     * A simple in-memory implementation is provided via {@link #ofStaticPrincipalPermissions}.
     */
    @FunctionalInterface
    public interface PermissionResolver {
        /**
         * Returns all permissions held by {@code principal} in {@code tenantId}.
         *
         * @param principal the authenticated user (never {@code null})
         * @param tenantId  the active tenant identifier
         * @return an unmodifiable set of permission strings (may be empty, never {@code null})
         */
        @NotNull
        Set<String> getPermissions(@NotNull Principal principal, @NotNull String tenantId);
    }

    /**
     * Maps route-handler class names to the {@link RequiresPermission} annotation
     * declared on them (class-level).  Per-method enforcement is handled by callers
     * registering method-level overrides via {@link #registerRoutePermission}.
     */
    private final Map<String, RequiresPermission> routePermissions = new ConcurrentHashMap<>();

    private final PermissionResolver permissionResolver;

    /**
     * Creates a filter that delegates permission resolution to the given resolver.
     *
     * @param permissionResolver strategy for loading granted permissions from the identity store
     */
    public PermissionEnforcerFilter(@NotNull PermissionResolver permissionResolver) {
        this.permissionResolver = Objects.requireNonNull(permissionResolver, "permissionResolver");
    }

    /**
     * Factory that creates an enforcer backed by a static (test/bootstrap) map of
     * {@code principal-name → granted-permissions}.
     *
     * @param staticGrants map from principal name to granted permission strings
     * @return a new {@link PermissionEnforcerFilter}
     */
    @NotNull
    public static PermissionEnforcerFilter ofStaticPrincipalPermissions(
            @NotNull Map<String, Set<String>> staticGrants) {
        Map<String, Set<String>> grants = Map.copyOf(staticGrants);
        PermissionResolver resolver = (principal, tenantId) ->
                grants.getOrDefault(principal.getName(), Collections.emptySet());
        return new PermissionEnforcerFilter(resolver);
    }

    /**
     * Registers a class-level {@link RequiresPermission} constraint for the given
     * route-handler class.  Call this during service initialisation for each servlet class
     * that carries the annotation.
     *
     * @param handlerClass the route-handler class to register
     */
    public void registerRouteClass(@NotNull Class<?> handlerClass) {
        RequiresPermission ann = handlerClass.getAnnotation(RequiresPermission.class);
        if (ann != null) {
            routePermissions.put(handlerClass.getName(), ann);
            log.debug("Registered permission '{}' for route class {}", ann.value(), handlerClass.getName());
        }
    }

    /**
     * Registers an explicit permission requirement for a route identified by {@code routeKey}
     * (e.g. the method reference string {@code "POST /api/v1/events"}).
     *
     * @param routeKey   a unique identifier for the route
     * @param annotation the permission annotation to enforce
     */
    public void registerRoutePermission(@NotNull String routeKey,
                                        @NotNull RequiresPermission annotation) {
        routePermissions.put(routeKey, Objects.requireNonNull(annotation, "annotation"));
        log.debug("Registered permission '{}' for route '{}'", annotation.value(), routeKey);
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {
        // Derive the route key used for permission look-up (method + path pattern)
        String routeKey = request.getMethod().name() + " " + request.getPath();

        RequiresPermission required = routePermissions.get(routeKey);
        if (required == null) {
            // No explicit constraint registered for this route — pass through
            return next.serve(request);
        }

        Principal principal = TenantContext.current().orElse(null);
        if (principal == null) {
            log.warn("Permission check for '{}' failed: no principal in TenantContext", routeKey);
            return Promise.of(HttpResponse.ofCode(401).build());
        }

        String tenantId = TenantContext.getCurrentTenantId();
        Set<String> granted = permissionResolver.getPermissions(principal, tenantId);

        if (!isAllowed(required, granted)) {
            log.warn("Access denied: principal={}, route={}, required={}",
                    principal.getName(), routeKey, required.value());
            return Promise.of(HttpResponse.ofCode(403).build());
        }

        return next.serve(request);
    }

    // ── Permission matching ────────────────────────────────────────────────────

    private static boolean isAllowed(@NotNull RequiresPermission required,
                                     @NotNull Set<String> granted) {
        // Collect all required permission strings
        String[] checks;
        if (required.anyOf().length > 0) {
            checks = required.anyOf();
        } else {
            checks = new String[]{ required.value() };
        }

        if (required.requireAll()) {
            // ALL of the specified permissions must be present
            for (String perm : checks) {
                if (!hasPermission(granted, perm)) return false;
            }
            return true;
        } else {
            // ANY one of the specified permissions is sufficient
            for (String perm : checks) {
                if (hasPermission(granted, perm)) return true;
            }
            return false;
        }
    }

    /**
     * Checks whether {@code granted} satisfies {@code required}, respecting wildcard segments.
     *
     * <p>Example: {@code "event:*:all"} in granted satisfies required {@code "event:read:all"}.
     */
    private static boolean hasPermission(@NotNull Set<String> granted,
                                         @NotNull String required) {
        if (granted.contains(required)) return true;
        if (granted.contains("*:*:*")) return true;

        String[] reqParts = required.split(":", 3);
        for (String g : granted) {
            String[] gParts = g.split(":", 3);
            if (gParts.length == 3 && reqParts.length == 3
                    && segmentMatches(gParts[0], reqParts[0])
                    && segmentMatches(gParts[1], reqParts[1])
                    && segmentMatches(gParts[2], reqParts[2])) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentMatches(String granted, String required) {
        return "*".equals(granted) || granted.equals(required);
    }
}
