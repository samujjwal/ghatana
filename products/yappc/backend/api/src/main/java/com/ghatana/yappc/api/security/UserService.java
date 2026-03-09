/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - User Service
 *
 * Provides user management services including authentication, user creation,
 * and user data access. Integrates with the platform authentication system.
 */

package com.ghatana.yappc.api.security;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User service for authentication and user management.
 *
 * <p>All operations on the in-memory store are non-blocking {@link ConcurrentHashMap}
 * lookups and therefore return synchronous {@link Promise#of} values. No blocking
 * executor is required because no IO is involved.
 *
 * <p>Features:
 * <ul>
 *   <li>User authentication with password verification</li>
 *   <li>User creation and management</li>
 *   <li>Role and permission management</li>
 *   <li>Tenant-based user isolation</li>
 *   <li>Password hashing and validation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose User authentication and management service
 * @doc.layer product
 * @doc.pattern Service
 */
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    // In-memory user store (in production, replace with database via Promise.ofBlocking)
    private final Map<String, UserData> userStore = new ConcurrentHashMap<>();
    private final Map<String, String> emailToUserId = new ConcurrentHashMap<>();

    public UserService() {
        initializeDefaultUsers();
    }

    /**
     * Authenticates a user with email and password.
     *
     * @param email    user email
     * @param password plaintext password
     * @return Promise of UserContext on success, or {@code null} on failure
     */
    @NotNull
    public Promise<@Nullable UserContext> authenticate(@NotNull String email, @NotNull String password) {
        try {
            String userId = emailToUserId.get(email);
            if (userId == null) {
                LOG.warn("Authentication failed: user not found for email {}", email);
                return Promise.of(null);
            }

            UserData userData = userStore.get(userId);
            if (userData == null) {
                LOG.warn("Authentication failed: user data not found for userId {}", userId);
                return Promise.of(null);
            }

            if (!verifyPassword(password, userData.getPasswordHash())) {
                LOG.warn("Authentication failed: invalid password for email {}", email);
                return Promise.of(null);
            }

            UserContext userContext = UserContext.builder()
                    .userId(userData.getUserId())
                    .email(userData.getEmail())
                    .userName(userData.getUserName())
                    .tenantId(userData.getTenantId())
                    .roles(userData.getRoles())
                    .permissions(userData.getPermissions())
                    .build();

            LOG.debug("User {} authenticated successfully", userId);
            return Promise.of(userContext);

        } catch (Exception e) {
            LOG.error("Authentication error for email {}", email, e);
            return Promise.of(null);
        }
    }

    /**
     * Creates a new user.
     *
     * @param email    user email (must be unique)
     * @param password plaintext password
     * @param userName display name
     * @param tenantId tenant identifier
     * @param roles    list of role names
     * @return Promise of the created UserContext, or {@code null} if email already exists
     */
    @NotNull
    public Promise<@Nullable UserContext> createUser(@NotNull String email,
                                                     @NotNull String password,
                                                     @NotNull String userName,
                                                     @NotNull String tenantId,
                                                     @NotNull List<String> roles) {
        try {
            String userId = generateUserId();
            String passwordHash = hashPassword(password);

            UserData userData = new UserData(
                    userId, email, userName, passwordHash,
                    tenantId, roles, getDefaultPermissions(roles)
            );

            // Atomic check-and-insert: avoids TOCTOU race on duplicate emails
            String existingUserId = emailToUserId.putIfAbsent(email, userId);
            if (existingUserId != null) {
                LOG.warn("User creation failed: email {} already exists", email);
                return Promise.of(null);
            }

            userStore.put(userId, userData);

            UserContext userContext = UserContext.builder()
                    .userId(userId)
                    .email(email)
                    .userName(userName)
                    .tenantId(tenantId)
                    .roles(roles)
                    .permissions(userData.getPermissions())
                    .build();

            LOG.info("User {} created successfully with email {}", userId, email);
            return Promise.of(userContext);

        } catch (Exception e) {
            LOG.error("User creation error for email {}", email, e);
            return Promise.of(null);
        }
    }

    /**
     * Gets a user by ID.
     *
     * @param userId user identifier
     * @return Promise of UserContext, or {@code null} if not found
     */
    @NotNull
    public Promise<@Nullable UserContext> getUserById(@NotNull String userId) {
        try {
            UserData userData = userStore.get(userId);
            if (userData == null) {
                return Promise.of(null);
            }

            return Promise.of(UserContext.builder()
                    .userId(userData.getUserId())
                    .email(userData.getEmail())
                    .userName(userData.getUserName())
                    .tenantId(userData.getTenantId())
                    .roles(userData.getRoles())
                    .permissions(userData.getPermissions())
                    .build());

        } catch (Exception e) {
            LOG.error("Get user error for userId {}", userId, e);
            return Promise.of(null);
        }
    }

    /**
     * Gets a user by email.
     *
     * @param email user email
     * @return Promise of UserContext, or {@code null} if not found
     */
    @NotNull
    public Promise<@Nullable UserContext> getUserByEmail(@NotNull String email) {
        String userId = emailToUserId.get(email);
        if (userId == null) {
            return Promise.of(null);
        }
        return getUserById(userId);
    }

    /**
     * Updates user roles.
     *
     * @param userId   user identifier
     * @param newRoles replacement role list
     * @return Promise of {@code true} on success, {@code false} if user not found
     */
    @NotNull
    public Promise<Boolean> updateUserRoles(@NotNull String userId, @NotNull List<String> newRoles) {
        try {
            UserData userData = userStore.get(userId);
            if (userData == null) {
                return Promise.of(false);
            }

            // Atomic update: compute new permissions before mutating, then set both
            List<Permission> newPermissions = getDefaultPermissions(newRoles);
            userData.setRolesAndPermissions(newRoles, newPermissions);

            LOG.info("User {} roles updated to {}", userId, newRoles);
            return Promise.of(true);

        } catch (Exception e) {
            LOG.error("Update user roles error for userId {}", userId, e);
            return Promise.of(false);
        }
    }

    /**
     * Gets users by tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise of matching user list (never null, may be empty)
     */
    @NotNull
    public Promise<List<UserContext>> getUsersByTenant(@NotNull String tenantId) {
        try {
            List<UserContext> users = userStore.values().stream()
                    .filter(userData -> tenantId.equals(userData.getTenantId()))
                    .map(userData -> UserContext.builder()
                            .userId(userData.getUserId())
                            .email(userData.getEmail())
                            .userName(userData.getUserName())
                            .tenantId(userData.getTenantId())
                            .roles(userData.getRoles())
                            .permissions(userData.getPermissions())
                            .build())
                    .toList();
            return Promise.of(users);

        } catch (Exception e) {
            LOG.error("Get users by tenant error for tenantId {}", tenantId, e);
            return Promise.of(List.of());
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private List<Permission> getDefaultPermissions(@NotNull List<String> roles) {
        return roles.stream()
                .flatMap(role -> getRoleDefaultPermissions(role).stream())
                .distinct()
                .toList();
    }

    private List<Permission> getRoleDefaultPermissions(@NotNull String role) {
        return switch (role) {
            case "admin" -> List.of(
                    Permission.crud("/api/v1/**"),
                    Permission.crud("/admin/**")
            );
            case "tenant_admin" -> List.of(
                    Permission.crud("/api/v1/tenants/{tenantId}/**"),
                    Permission.crud("/api/v1/projects/**")
            );
            case "developer" -> List.of(
                    Permission.crud("/api/v1/projects/**"),
                    Permission.readOnly("/api/v1/agents/**")
            );
            case "analyst" -> List.of(
                    Permission.readOnly("/api/v1/projects/**"),
                    Permission.readOnly("/api/v1/agents/**")
            );
            case "viewer" -> List.of(
                    Permission.readOnly("/api/v1/projects/{projectId}")
            );
            default -> List.of();
        };
    }

    /**
     * Hashes a password.
     *
     * <p><b>WARNING: dev-only implementation.</b> Replace with BCrypt/Argon2
     * before production deployment. See {@code platform:java:security} for
     * the platform {@code PasswordHasher}.
     *
     * @param password plaintext password
     * @return hashed password (dev-only: {@code String.hashCode()}-based)
     */
    // NOTE: Dev-only hash — production deployments MUST replace with platform PasswordHasher (BCrypt/Argon2).
    private String hashPassword(@NotNull String password) {
        logger.warn("Using dev-only password hashing — NOT for production use");
        return "hashed_" + password.hashCode();
    }

    /**
     * Verifies a password against a hash.
     *
     * <p><b>WARNING: dev-only implementation.</b> See {@link #hashPassword}.
     */
    // NOTE: Dev-only verification — production deployments MUST replace with platform PasswordHasher.
    private boolean verifyPassword(@NotNull String password, @NotNull String hash) {
        return ("hashed_" + password.hashCode()).equals(hash);
    }

    private String generateUserId() {
        return "user_" + java.util.UUID.randomUUID();
    }

    private void initializeDefaultUsers() {
        String adminUserId = generateUserId();
        UserData adminUser = new UserData(
                adminUserId,
                "admin@yappc.local",
                "System Administrator",
                hashPassword("admin123"),
                "system",
                List.of("admin"),
                getDefaultPermissions(List.of("admin"))
        );

        userStore.put(adminUserId, adminUser);
        emailToUserId.put("admin@yappc.local", adminUserId);

        LOG.info("Default admin user initialized");
    }

    // ── Inner types ────────────────────────────────────────────────────

    /**
     * User data storage class.
     *
     * @doc.type class
     * @doc.purpose In-memory user data record
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    private static class UserData {
        private final String userId;
        private final String email;
        private final String userName;
        private final String passwordHash;
        private final String tenantId;
        private List<String> roles;
        private List<Permission> permissions;

        UserData(String userId, String email, String userName, String passwordHash,
                 String tenantId, List<String> roles, List<Permission> permissions) {
            this.userId = userId;
            this.email = email;
            this.userName = userName;
            this.passwordHash = passwordHash;
            this.tenantId = tenantId;
            this.roles = List.copyOf(roles);
            this.permissions = List.copyOf(permissions);
        }

        String getUserId() { return userId; }
        String getEmail() { return email; }
        String getUserName() { return userName; }
        String getPasswordHash() { return passwordHash; }
        String getTenantId() { return tenantId; }
        List<String> getRoles() { return roles; }
        List<Permission> getPermissions() { return permissions; }

        void setRoles(List<String> roles) { this.roles = List.copyOf(roles); }
        void setPermissions(List<Permission> permissions) { this.permissions = List.copyOf(permissions); }

        /**
         * Atomically updates roles and permissions together to avoid partial reads.
         */
        synchronized void setRolesAndPermissions(List<String> roles, List<Permission> permissions) {
            this.roles = List.copyOf(roles);
            this.permissions = List.copyOf(permissions);
        }
    }
}
