/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.port;

import com.ghatana.appplatform.iam.domain.ClientCredentials;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for persisting and querying OAuth 2.0 client credentials.
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>Constant-time secret comparison (handled by callers via BCrypt).</li>
 *   <li>Atomic {@code updateLastUsed} (best-effort write; never block a token response).</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Client credential store port for OAuth 2.0 client_credentials (K01-002)
 * @doc.layer core
 * @doc.pattern Port
 */
public interface ClientCredentialStore {

    /**
     * Looks up a client by its human-readable string ID (e.g. {@code "svc-ledger"}).
     *
     * @param clientIdStr the string client identifier
     * @return the credentials wrapped in Optional; empty if not found
     */
    Promise<Optional<ClientCredentials>> getByClientIdStr(String clientIdStr);

    /**
     * Resolves all permissions granted to the given roles for a tenant.
     * Pass {@code null} tenantId to load platform-wide role permissions only.
     *
     * @param roles    role names (e.g. {@code "service:ledger"})
     * @param tenantId the tenant scope; {@code null} for global roles
     * @return flat list of permission strings (e.g. {@code "ledger:read"})
     */
    Promise<List<String>> loadPermissionsForRoles(List<String> roles, UUID tenantId);

    /**
     * Updates the {@code last_used_at} timestamp for auditing purposes.
     * Fire-and-forget — failures are logged but do not fail token issuance.
     *
     * @param clientId the internal UUID of the client
     */
    Promise<Void> updateLastUsed(UUID clientId);
}
