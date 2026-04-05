/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link AuthorizationService} implementation using in-memory RBAC.
 *
 * <p>Production systems should integrate with the governance module for external
 * policy evaluation and caching of authorization decisions.
 *
 * @doc.type class
 * @doc.purpose Default RBAC enforcement with in-memory policies
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultAuthorizationService implements AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthorizationService.class);

    private final IdentityService identityService;
    // Map: "$tenantId:$principal" → Set of scopes
    private final Map<String, Set<String>> principalScopes = new ConcurrentHashMap<>();

    public DefaultAuthorizationService(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public Promise<Boolean> isAuthorized(String tenantId, String principal, String resource) {
        return identityService.resolve(tenantId, principal)
            .map(optIdentity -> {
                if (optIdentity.isEmpty()) {
                    log.debug("Authorization check: unknown principal {}/{}", tenantId, principal);
                    return false;
                }

                AgentIdentity identity = optIdentity.get();
                // Parse resource as "category:action" (e.g., "collection:read")
                String[] parts = resource.split(":", 2);
                if (parts.length < 2) {
                    log.warn("Invalid resource format: {}", resource);
                    return false;
                }

                String scope = parts[0] + ":" + parts[1];
                boolean authorized = identity.hasScope(scope) || identity.hasScope("*");

                if (authorized) {
                    log.debug("Authorization granted: {}/{} -> {}", tenantId, principal, resource);
                } else {
                    log.debug("Authorization denied: {}/{} -> {}", tenantId, principal, resource);
                }

                return authorized;
            });
    }

    @Override
    public Promise<Boolean> hasScope(String tenantId, String principal, String requiredScope) {
        return identityService.resolve(tenantId, principal)
            .map(optIdentity -> {
                if (optIdentity.isEmpty()) {
                    log.debug("Scope check: unknown principal {}/{}", tenantId, principal);
                    return false;
                }

                AgentIdentity identity = optIdentity.get();
                boolean has = identity.hasScope(requiredScope) || identity.hasScope("*");

                if (has) {
                    log.debug("Principal has scope: {}/{} -> {}", tenantId, principal, requiredScope);
                } else {
                    log.debug("Principal lacks scope: {}/{} -> {}", tenantId, principal, requiredScope);
                }

                return has;
            });
    }

    @Override
    public Promise<Void> enforce(String tenantId, String principal, String resource) {
        return isAuthorized(tenantId, principal, resource)
            .then(authorized -> {
                if (authorized) {
                    return Promise.complete();
                } else {
                    throw new AuthorizationDeniedException(principal, resource, tenantId);
                }
            });
    }

    /**
     * Register scope grants for a principal (for testing).
     */
    public void grantScopes(String tenantId, String principal, Set<String> scopes) {
        String key = tenantId + ":" + principal;
        principalScopes.put(key, Set.copyOf(scopes));
    }
}
