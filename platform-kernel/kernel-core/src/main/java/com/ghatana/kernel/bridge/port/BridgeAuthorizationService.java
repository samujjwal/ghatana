/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.bridge.port;

/**
 * Kernel-owned authorization port for bridge calls.
 *
 * <p>The kernel defines this port; the runtime product wiring binds it to a concrete
 * security implementation (e.g. OPA, RBAC store, JWT claims). Bridge adapters must
 * not depend directly on concrete security libraries — they call this port instead.</p>
 *
 * <p>Usage in a bridge adapter:</p>
 * <pre>{@code
 * authService.isAuthorized(context, "collection:write", "create")
 *     .whenResult(allowed -> {
 *         if (!allowed) throw new SecurityException("Not authorized");
 *     });
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Kernel-owned authorization port for bridge call security checks
 * @doc.layer core
 * @doc.pattern Port
 * @author Ghatana Kernel Team
 * @since 1.3.0
 */
public interface BridgeAuthorizationService {

    /**
     * Checks whether the caller in the given {@code context} is authorized to
     * perform {@code action} on {@code resource}.
     *
     * @param context  the bridge call context carrying tenant and principal identity
     * @param resource the resource being accessed (e.g. {@code "collection:read"})
     * @param action   the action being attempted (e.g. {@code "read"}, {@code "create"})
     * @return a {@link Promise} resolving to {@code true} when the call is authorized,
     *         {@code false} otherwise; never a failed Promise for a mere denial
     */
    io.activej.promise.Promise<Boolean> isAuthorized(BridgeContext context, String resource, String action);
}
