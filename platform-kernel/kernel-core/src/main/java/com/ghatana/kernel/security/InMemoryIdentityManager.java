package com.ghatana.kernel.security;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * In-memory implementation of {@link IdentityManager} for testing.
 *
 * @doc.type class
 * @doc.purpose In-memory identity manager for testing (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class InMemoryIdentityManager implements IdentityManager {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryIdentityManager.class);

    private final Map<String, UserIdentity> identities = new ConcurrentHashMap<>();
    private final Map<String, String> passwords = new ConcurrentHashMap<>();
    private final Map<String, String> usernameToUserId = new ConcurrentHashMap<>();
    private final Executor executor;

    public InMemoryIdentityManager(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Optional<String>> authenticate(String tenantId, String username, String password) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenantId + ":" + username;
            String userId = usernameToUserId.get(key);
            if (userId == null) {
                LOG.info("[IDENTITY-MANAGER] Authentication failed: user not found tenant={} username={}", tenantId, username);
                return Optional.empty();
            }

            String storedPassword = passwords.get(userId);
            if (storedPassword == null || !storedPassword.equals(password)) {
                LOG.info("[IDENTITY-MANAGER] Authentication failed: invalid password tenant={} username={}", tenantId, username);
                return Optional.empty();
            }

            UserIdentity identity = identities.get(userId);
            if (identity != null && !identity.isActive()) {
                LOG.info("[IDENTITY-MANAGER] Authentication failed: user inactive tenant={} username={}", tenantId, username);
                return Optional.empty();
            }

            LOG.info("[IDENTITY-MANAGER] Authentication successful tenant={} username={}", tenantId, username);
            return Optional.of(userId);
        });
    }

    @Override
    public Promise<String> createIdentity(String tenantId, String username, String email, String password) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenantId + ":" + username;
            if (usernameToUserId.containsKey(key)) {
                throw new IllegalArgumentException("Username already exists: " + username);
            }

            String userId = UUID.randomUUID().toString();
            UserIdentity identity = UserIdentity.builder()
                .id(userId)
                .tenantId(tenantId)
                .username(username)
                .email(email)
                .build();

            identities.put(userId, identity);
            passwords.put(userId, password);
            usernameToUserId.put(key, userId);

            LOG.info("[IDENTITY-MANAGER] Created identity userId={} tenant={} username={}", userId, tenantId, username);
            return userId;
        });
    }

    @Override
    public Promise<Optional<UserIdentity>> getIdentity(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return Promise.ofBlocking(executor, () -> Optional.ofNullable(identities.get(userId)));
    }

    @Override
    public Promise<Void> updateIdentity(String userId, UserIdentity identity) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(identity, "identity must not be null");

        return Promise.ofBlocking(executor, () -> {
            if (!identities.containsKey(userId)) {
                throw new IllegalArgumentException("User identity not found: " + userId);
            }
            identities.put(userId, identity);
            LOG.info("[IDENTITY-MANAGER] Updated identity userId={}", userId);
            return null;
        });
    }

    @Override
    public Promise<Void> deleteIdentity(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return Promise.ofBlocking(executor, () -> {
            UserIdentity identity = identities.remove(userId);
            if (identity != null) {
                passwords.remove(userId);
                String key = identity.getTenantId() + ":" + identity.getUsername();
                usernameToUserId.remove(key);
                LOG.info("[IDENTITY-MANAGER] Deleted identity userId={}", userId);
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> usernameExists(String tenantId, String username) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(username, "username must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenantId + ":" + username;
            return usernameToUserId.containsKey(key);
        });
    }
}
