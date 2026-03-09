package com.ghatana.yappc.api.auth.repository;

import com.ghatana.yappc.api.auth.model.User;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UserRepository implementation.
 *
 * <p><b>Note</b>: This is currently an in-memory implementation used to keep the module compiling
 * while Data-Cloud integration is being aligned to the new Data-Cloud application APIs.
 *
 * @doc.type class
 * @doc.purpose User persistence for YAPPC API
 * @doc.layer product
 * @doc.pattern Repository
 */
public class DataCloudUserRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(DataCloudUserRepository.class);

    private final ConcurrentHashMap<UUID, User> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> usernameIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> emailIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> passwordResetTokenIndex = new ConcurrentHashMap<>();

    @Inject
    public DataCloudUserRepository() {
        // No-op
    }

    @Override
    public Promise<Optional<User>> findByUsernameOrEmail(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return Promise.of(Optional.empty());
        }

        String key = usernameOrEmail.trim();
        UUID id = usernameIndex.get(key);
        if (id == null) {
            id = emailIndex.get(key.toLowerCase());
        }

        return Promise.of(Optional.ofNullable(id).map(usersById::get));
    }

    @Override
    public Promise<Optional<User>> findById(UUID id) {
        if (id == null) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.ofNullable(usersById.get(id)));
    }

    @Override
    public Promise<Optional<User>> findByPasswordResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Promise.of(Optional.empty());
        }
        UUID id = passwordResetTokenIndex.get(token);
        return Promise.of(Optional.ofNullable(id).map(usersById::get));
    }

    @Override
    public Promise<User> save(User user) {
        if (user == null) {
            return Promise.ofException(new IllegalArgumentException("user is required"));
        }
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
        }

        usersById.put(user.getId(), user);

        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            usernameIndex.put(user.getUsername(), user.getId());
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailIndex.put(user.getEmail().toLowerCase(), user.getId());
        }

        // Keep reset token index up to date
        passwordResetTokenIndex.entrySet().removeIf(entry -> entry.getValue().equals(user.getId()));
        if (user.getPasswordResetToken() != null && !user.getPasswordResetToken().isBlank()) {
            passwordResetTokenIndex.put(user.getPasswordResetToken(), user.getId());
        }

        logger.debug("Saved user id={}, username={}", user.getId(), user.getUsername());
        return Promise.of(user);
    }

    @Override
    public Promise<Boolean> existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Promise.of(false);
        }
        return Promise.of(usernameIndex.containsKey(username));
    }

    @Override
    public Promise<Boolean> existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Promise.of(false);
        }
        return Promise.of(emailIndex.containsKey(email.toLowerCase()));
    }
}
