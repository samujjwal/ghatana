package com.ghatana.stt.core.security;

import io.activej.promise.Promise;

import java.util.Set;

/**
 * Port interface for centralized security policy evaluation.
 *
 * <p>Defines the RBAC policy evaluation contract that the STT service needs.
 * The concrete implementation (from {@code products:security-gateway}) is
 * injected at runtime, keeping this module free of cross-product dependencies.
 *
 * @doc.type interface
 * @doc.purpose RBAC policy evaluation port for STT security
 * @doc.layer product
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface SecurityGateway {

    /**
     * Evaluates whether the given user with the specified roles is permitted to
     * perform the requested action on the given resource.
     *
     * @param userId     the authenticated user's identifier
     * @param roles      the user's role set
     * @param resource   the resource being accessed (e.g., "stt-service")
     * @param permission the permission required (e.g., "read", "write", "admin")
     * @return a promise resolving to {@code true} if access is allowed
     */
    Promise<Boolean> evaluatePolicy(String userId, Set<String> roles,
                                     String resource, String permission);
}
