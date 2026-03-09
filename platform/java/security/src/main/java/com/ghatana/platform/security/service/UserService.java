package com.ghatana.platform.security.service;

import com.ghatana.platform.security.model.User;
import io.activej.promise.Promise;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/**
 * User service.
 *
 * <p>Default seed users are configured via environment variables:
 * <ul>
 *   <li>{@code ADMIN_USERNAME} / {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}</li>
 *   <li>{@code DEFAULT_USER_USERNAME} / {@code DEFAULT_USER_EMAIL} / {@code DEFAULT_USER_PASSWORD}</li>
 * </ul>
 * If these variables are not set, <b>no default users are created</b>, forcing
 * explicit provisioning in every environment.
 *
 * @doc.type class
 * @doc.purpose User management service with env-driven provisioning
 * @doc.layer core
 * @doc.pattern Service
 */

public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserService() {
        initializeSeedUsers();
    }

    /**
     * Seeds initial users <b>only</b> when the required environment variables
     * are present. This prevents hardcoded credentials from reaching any
     * deployment.
     */
    private void initializeSeedUsers() {
        seedUser(
            System.getenv("ADMIN_USERNAME"),
            System.getenv("ADMIN_EMAIL"),
            System.getenv("ADMIN_PASSWORD"),
            new HashSet<>(Arrays.asList("ADMIN", "USER")),
            "admin"
        );

        seedUser(
            System.getenv("DEFAULT_USER_USERNAME"),
            System.getenv("DEFAULT_USER_EMAIL"),
            System.getenv("DEFAULT_USER_PASSWORD"),
            new HashSet<>(Collections.singletonList("USER")),
            "default user"
        );
    }

    private void seedUser(String username, String email, String password,
                          java.util.Set<String> roles, String label) {
        if (username == null || username.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            log.info("Skipping {} seed — environment variables not set", label);
            return;
        }
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt(10));
        User user = new User(username, email, hashed, roles);
        users.put(user.getUsername(), user);
        log.info("Seeded {} user: {}", label, username);
    }

    public Optional<User> authenticate(String username, String password) {
        User user = users.get(username);
        if (user != null && BCrypt.checkpw(password, user.getPassword())) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    public Promise<User> register(User user) {
        if (users.containsKey(user.getUsername())) {
            return Promise.ofException(new RuntimeException("Username already exists"));
        }

        // Hash the password before storing
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt(10));
        User newUser = User.builder()
                .userId(UUID.randomUUID().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(hashedPassword)
                .roles(user.getRoles())
                .build();
                
        users.put(newUser.getUsername(), newUser);
        return Promise.of(newUser);
    }

    // Get all users (for testing/debugging)
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }
}
