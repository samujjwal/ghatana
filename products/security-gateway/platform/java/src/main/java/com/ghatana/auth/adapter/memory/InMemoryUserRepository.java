package com.ghatana.auth.adapter.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ghatana.auth.core.port.UserRepository;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;

import io.activej.promise.Promise;

/**
 * In-memory implementation of UserRepository for testing and development.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides ephemeral user storage using in-memory HashMap. Suitable for: - Unit
 * testing authentication flows - Local development without database -
 * Proof-of-concept implementations
 *
 * <p>
 * <b>Thread Safety</b><br>
 * NOT thread-safe. For testing only. Use JPA implementation for production.
 *
 * <p>
 * <b>Data Isolation</b><br>
 * Tenant-scoped queries: Returns only users matching tenant ID. Cross-tenant
 * lookups return Optional.empty().
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * UserRepository repo = new InMemoryUserRepository();
 *
 * User alice = new User("alice@example.com", "Alice");
 * repo.save(TenantId.of("tenant-1"), alice).whenComplete((saved, error) -> {
 *     if (error != null) {
 *         log.error("Save failed", error);
 *         return;
 *     }
 *     log.info("Saved user: {}", saved.getId());
 * });
 * }</pre>
 *
 * @see UserRepository for interface contract
 * @see com.ghatana.auth.adapter.jpa.JpaUserRepository for production
 * implementation
 * @doc.type class
 * @doc.purpose In-memory user repository adapter (testing only)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, Map<String, User>> tenantUsers = new HashMap<>();
    private final Map<String, String> emailToUserId = new HashMap<>();

    @Override
    public Promise<User> save(User user) {
        if (user == null) {
            return Promise.ofException(
                    new IllegalArgumentException("user must not be null")
            );
        }

        TenantId tenantId = user.getTenantId();
        String tenant = tenantId.value();
        String userId = user.getUserId().value().toString();
        String email = user.getEmail();

        // Check email not already used in tenant
        String key = tenant + ":" + email;
        if (emailToUserId.containsKey(key) && !emailToUserId.get(key).equals(userId)) {
            return Promise.ofException(
                    new IllegalArgumentException("Email already in use in this tenant")
            );
        }

        // Store user
        tenantUsers.computeIfAbsent(tenant, k -> new HashMap<>()).put(userId, user);
        emailToUserId.put(key, userId);

        return Promise.of(user);
    }

    @Override
    public Promise<Optional<User>> findByUserId(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (userId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("userId must not be null")
            );
        }

        String tenant = tenantId.value();
        String id = userId.value().toString();

        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        User user = users.get(id);

        return Promise.of(Optional.ofNullable(user));
    }

    @Override
    public Promise<Optional<User>> findByUsername(TenantId tenantId, String username) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (username == null || username.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("username must not be null")
            );
        }

        String tenant = tenantId.value();
        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());

        for (User user : users.values()) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return Promise.of(Optional.of(user));
            }
        }

        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<Optional<User>> findByEmail(TenantId tenantId, String email) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (email == null || email.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("email must not be null")
            );
        }

        String tenant = tenantId.value();
        String key = tenant + ":" + email;
        String userId = emailToUserId.get(key);

        if (userId == null) {
            return Promise.of(Optional.empty());
        }

        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        User user = users.get(userId);

        return Promise.of(Optional.ofNullable(user));
    }

    @Override
    public Promise<List<User>> findAllByTenant(TenantId tenantId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }

        String tenant = tenantId.value();
        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        return Promise.of(new java.util.ArrayList<>(users.values()));
    }

    @Override
    public Promise<Optional<User>> authenticate(TenantId tenantId, String username, String password) {
        if (tenantId == null || username == null || password == null) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId, username, and password must not be null")
            );
        }

        return findByUsername(tenantId, username)
                .then(optUser -> {
                    if (optUser.isPresent()) {
                        User user = optUser.get();
                        if (!user.canAuthenticate()) {
                            return Promise.of(Optional.<User>empty());
                        }
                        // Verify password against stored hash
                        Optional<String> storedHash = user.getPasswordHash();
                        if (storedHash.isEmpty() || !verifyPassword(password, storedHash.get())) {
                            return Promise.of(Optional.<User>empty());
                        }
                        return Promise.of(optUser);
                    }
                    return Promise.of(Optional.empty());
                });
    }

    @Override
    public Promise<Void> updatePassword(TenantId tenantId, UserId userId, String newPassword) {
        if (tenantId == null || userId == null || newPassword == null) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId, userId, and newPassword must not be null")
            );
        }

        String tenant = tenantId.value();
        String id = userId.value().toString();

        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        User user = users.get(id);

        if (user == null) {
            return Promise.ofException(
                    new IllegalArgumentException("User not found in tenant")
            );
        }

        // Hash the new password and rebuild the immutable User with updated hash
        String newHash = hashPassword(newPassword);
        User updated = User.builder()
                .tenantId(user.getTenantId())
                .userId(user.getUserId())
                .authenticationType(user.getAuthenticationType())
                .status(user.getStatus())
                .username(user.getUsername())
                .email(user.getEmail())
                .passwordHash(newHash)
                .displayName(user.getDisplayName())
                .roles(user.getRoles())
                .permissions(user.getPermissions())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .mfaEnabled(user.isMfaEnabled())
                .active(user.isActive())
                .locked(user.isLocked())
                .createdAt(user.getCreatedAt())
                .updatedAt(java.time.Instant.now())
                .metadata(user.getMetadata())
                .build();
        users.put(id, updated);
        return Promise.of(null);
    }

    @Override
    public Promise<Void> lockUser(TenantId tenantId, UserId userId) {
        if (tenantId == null || userId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId and userId must not be null")
            );
        }

        String tenant = tenantId.value();
        String id = userId.value().toString();

        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        User user = users.get(id);

        if (user == null) {
            return Promise.ofException(
                    new IllegalArgumentException("User not found in tenant")
            );
        }

        // Rebuild immutable user with locked=true
        User lockedUser = User.builder()
                .tenantId(user.getTenantId())
                .userId(user.getUserId())
                .authenticationType(user.getAuthenticationType())
                .status(user.getStatus())
                .username(user.getUsername())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash().orElse(null))
                .displayName(user.getDisplayName())
                .roles(user.getRoles())
                .permissions(user.getPermissions())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .mfaEnabled(user.isMfaEnabled())
                .active(user.isActive())
                .locked(true)
                .createdAt(user.getCreatedAt())
                .updatedAt(java.time.Instant.now())
                .metadata(user.getMetadata())
                .build();
        users.put(id, lockedUser);
        return Promise.of(null);
    }

    @Override
    public Promise<Void> unlockUser(TenantId tenantId, UserId userId) {
        if (tenantId == null || userId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId and userId must not be null")
            );
        }

        String tenant = tenantId.value();
        String id = userId.value().toString();

        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        User user = users.get(id);

        if (user == null) {
            return Promise.ofException(
                    new IllegalArgumentException("User not found in tenant")
            );
        }

        // Rebuild immutable user with locked=false
        User unlockedUser = User.builder()
                .tenantId(user.getTenantId())
                .userId(user.getUserId())
                .authenticationType(user.getAuthenticationType())
                .status(user.getStatus())
                .username(user.getUsername())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash().orElse(null))
                .displayName(user.getDisplayName())
                .roles(user.getRoles())
                .permissions(user.getPermissions())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .mfaEnabled(user.isMfaEnabled())
                .active(user.isActive())
                .locked(false)
                .createdAt(user.getCreatedAt())
                .updatedAt(java.time.Instant.now())
                .metadata(user.getMetadata())
                .build();
        users.put(id, unlockedUser);
        return Promise.of(null);
    }

    @Override
    public Promise<Void> delete(TenantId tenantId, UserId userId) {
        if (tenantId == null || userId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId and userId must not be null")
            );
        }

        String tenant = tenantId.value();
        String id = userId.value().toString();

        Map<String, User> users = tenantUsers.getOrDefault(tenant, new HashMap<>());
        User removed = users.remove(id);

        if (removed != null) {
            String key = tenant + ":" + removed.getEmail();
            emailToUserId.remove(key);
        }

        return Promise.of(null);
    }

    /**
     * Clear all users (for testing between test cases).
     */
    public void clear() {
        tenantUsers.clear();
        emailToUserId.clear();
    }

    // ─── Password Hashing Utilities ──────────────────────────────────────

    /**
     * Hashes a plaintext password using SHA-256 with a random salt.
     * Format: {@code $sha256$<base64-salt>$<base64-hash>}
     *
     * @param plaintext the raw password
     * @return hashed password string
     */
    private static String hashPassword(String plaintext) {
        try {
            java.security.SecureRandom random = new java.security.SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            String saltStr = Base64.getEncoder().encodeToString(salt);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hashed = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            String hashStr = Base64.getEncoder().encodeToString(hashed);

            return "$sha256$" + saltStr + "$" + hashStr;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored hash.
     *
     * @param plaintext  the raw password to check
     * @param storedHash the stored hash (format: {@code $sha256$salt$hash})
     * @return true if the password matches
     */
    private static boolean verifyPassword(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) {
            return false;
        }
        try {
            if (storedHash.startsWith("$sha256$")) {
                String[] parts = storedHash.split("\\$");
                if (parts.length < 4) return false;
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                String expectedHash = parts[3];

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(salt);
                byte[] hashed = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
                String actualHash = Base64.getEncoder().encodeToString(hashed);

                return MessageDigest.isEqual(
                        expectedHash.getBytes(StandardCharsets.UTF_8),
                        actualHash.getBytes(StandardCharsets.UTF_8));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
