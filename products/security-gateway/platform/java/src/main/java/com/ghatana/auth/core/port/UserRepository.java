package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for user persistence and authentication.
 *
 * <p><b>Purpose</b><br>
 * Port interface (hexagonal architecture) for user management operations.
 * Infrastructure layer provides adapters (JPA, LDAP, etc.).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Create user
 * User user = User.builder()
 *     .tenantId(tenantId)
 *     .userId(UserId.random())
 *     .username("john.doe")
 *     .email("john@example.com")
 *     .build();
 *
 * userRepository.save(user)
 *     .thenApply(saved -> {
 *         // User persisted
 *         return saved;
 *     });
 *
 * // Authenticate
 * userRepository.authenticate(tenantId, "john.doe", "password123")
 *     .thenApply(maybeUser -> {
 *         if (maybeUser.isPresent()) {
 *             // Authentication successful
 *         }
 *         return maybeUser;
 *     });
 * }</pre>
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All operations scoped by TenantId - users are isolated per tenant.
 *
 * <p><b>Security</b><br>
 * - Passwords MUST be hashed before storage (bcrypt, argon2, PBKDF2)
 * - Password hashes NEVER returned in User objects
 * - MFA secrets managed separately (MfaStore)
 * - Implement rate limiting for authentication attempts
 *
 * @doc.type interface
 * @doc.purpose User repository port
 * @doc.layer core
 * @doc.pattern Port
 */
public interface UserRepository {
    
    /**
     * Saves or updates a user.
     *
     * @param user user to save
     * @return Promise of saved user
     */
    Promise<User> save(User user);
    
    /**
     * Finds a user by tenant and user ID.
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @return Promise of Optional user (empty if not found)
     */
    Promise<Optional<User>> findByUserId(TenantId tenantId, UserId userId);
    
    /**
     * Finds a user by tenant and username.
     *
     * @param tenantId tenant identifier
     * @param username username (case-insensitive)
     * @return Promise of Optional user (empty if not found)
     */
    Promise<Optional<User>> findByUsername(TenantId tenantId, String username);
    
    /**
     * Finds a user by tenant and email.
     *
     * @param tenantId tenant identifier
     * @param email email address (case-insensitive)
     * @return Promise of Optional user (empty if not found)
     */
    Promise<Optional<User>> findByEmail(TenantId tenantId, String email);
    
    /**
     * Lists all active users for a tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise of list of active users
     */
    Promise<List<User>> findAllByTenant(TenantId tenantId);
    
    /**
     * Authenticates a user with username and password.
     *
     * @param tenantId tenant identifier
     * @param username username
     * @param password plaintext password (will be hashed for comparison)
     * @return Promise of Optional user (empty if authentication failed)
     */
    Promise<Optional<User>> authenticate(TenantId tenantId, String username, String password);
    
    /**
     * Updates user password (hashes password before storage).
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @param newPassword plaintext new password
     * @return Promise of void
     */
    Promise<Void> updatePassword(TenantId tenantId, UserId userId, String newPassword);
    
    /**
     * Locks a user account (prevents authentication).
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @return Promise of void
     */
    Promise<Void> lockUser(TenantId tenantId, UserId userId);
    
    /**
     * Unlocks a user account.
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @return Promise of void
     */
    Promise<Void> unlockUser(TenantId tenantId, UserId userId);
    
    /**
     * Deletes a user (soft delete recommended).
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @return Promise of void
     */
    Promise<Void> delete(TenantId tenantId, UserId userId);
}
