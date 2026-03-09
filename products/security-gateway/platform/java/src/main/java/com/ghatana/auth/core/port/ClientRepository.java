package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.Client;
import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OAuth client persistence and retrieval.
 *
 * <p><b>Purpose</b><br>
 * Port interface (hexagonal architecture) for client management operations.
 * Infrastructure layer provides adapters (JPA, Redis, etc.).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Save a client
 * Client client = Client.builder()
 *     .tenantId(tenantId)
 *     .clientId(ClientId.random())
 *     .clientName("Mobile App")
 *     .build();
 *
 * clientRepository.save(client)
 *     .thenApply(saved -> {
 *         // Client persisted
 *         return saved;
 *     });
 *
 * // Lookup by client ID
 * clientRepository.findByClientId(tenantId, clientId)
 *     .thenApply(maybeClient -> {
 *         if (maybeClient.isPresent()) {
 *             // Client found
 *         }
 *         return maybeClient;
 *     });
 * }</pre>
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All operations scoped by TenantId - clients are isolated per tenant.
 *
 * <p><b>Implementation Notes</b><br>
 * - Infrastructure adapters MUST enforce tenant isolation
 * - Client secrets MUST be hashed before storage (bcrypt, argon2)
 * - Lookups MUST be indexed on (tenantId, clientId) for performance
 * - Consider caching for frequently accessed clients
 *
 * @doc.type interface
 * @doc.purpose OAuth client repository port
 * @doc.layer core
 * @doc.pattern Port
 */
public interface ClientRepository {
    
    /**
     * Saves or updates a client.
     *
     * @param client client to save
     * @return Promise of saved client (may have generated fields)
     */
    Promise<Client> save(Client client);
    
    /**
     * Finds a client by tenant and client ID.
     *
     * @param tenantId tenant identifier
     * @param clientId client identifier
     * @return Promise of Optional client (empty if not found)
     */
    Promise<Optional<Client>> findByClientId(TenantId tenantId, ClientId clientId);
    
    /**
     * Lists all active clients for a tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise of list of active clients
     */
    Promise<List<Client>> findAllByTenant(TenantId tenantId);
    
    /**
     * Deletes a client (soft delete recommended).
     *
     * @param tenantId tenant identifier
     * @param clientId client identifier
     * @return Promise of void
     */
    Promise<Void> delete(TenantId tenantId, ClientId clientId);
    
    /**
     * Validates client credentials (client_id + client_secret).
     *
     * @param tenantId tenant identifier
     * @param clientId client identifier
     * @param clientSecret client secret (plaintext, will be hashed for comparison)
     * @return Promise of true if credentials are valid, false otherwise
     */
    Promise<Boolean> validateCredentials(TenantId tenantId, ClientId clientId, String clientSecret);
}
