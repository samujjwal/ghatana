package com.ghatana.security.auth.impl;

import com.ghatana.platform.security.auth.AuthenticationProvider;
import com.ghatana.platform.security.auth.Credentials;
import com.ghatana.platform.security.auth.impl.UsernamePasswordCredentials;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.service.UserService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AuthenticationProvider for demonstration and testing.
 * In a production environment, this would be replaced with a database-backed implementation.
 
 *
 * @doc.type class
 * @doc.purpose In memory user service
 * @doc.layer core
 * @doc.pattern Service
*/
public class InMemoryUserService extends UserService implements AuthenticationProvider {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryUserService.class);

    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, String> passwordHashes = new ConcurrentHashMap<>();

    /**
     * Create a new in-memory user service with some default users.
     */
    public InMemoryUserService() {
        // Add some default users for testing
        addUser(createUser("admin", "admin123", Set.of("ADMIN")));
        addUser(createUser("user", "user123", Set.of("USER")));
        addUser(createUser("auditor", "auditor123", Set.of("AUDITOR")));
    }

    @Override
    public Promise<Optional<User>> authenticate(Credentials credentials) {
        if (!(credentials instanceof UsernamePasswordCredentials)) {
            return Promise.of(Optional.empty());
        }

        UsernamePasswordCredentials creds = (UsernamePasswordCredentials) credentials;
        String password = new String(creds.getPassword());
        String username = creds.getUsername();

        // In a real application, you would verify the password hash
        User user = usersByUsername.get(username);
        if (user != null && verifyPassword(user.getUserId(), password)) {
            logger.debug("User authenticated: {}", username);
            return Promise.of(Optional.of(user));
        }

        logger.debug("Authentication failed for user: {}", username);
        return Promise.of(Optional.empty());
    }

    public Promise<Optional<User>> getUserById(String userId) {
        return Promise.of(Optional.ofNullable(usersById.get(userId)));
    }

    @Override
    public boolean supports(String type) {
        return "basic".equalsIgnoreCase(type);
    }

    /**
     * Add a new user to the in-memory store.
     */
    public void addUser(User user) {
        usersById.put(user.getUserId(), user);
        usersByUsername.put(user.getUsername(), user);
    }

    /**
     * Remove a user from the in-memory store.
     */
    public boolean removeUser(String userId) {
        User user = usersById.remove(userId);
        if (user != null) {
            usersByUsername.remove(user.getUsername());
            passwordHashes.remove(userId);
            return true;
        }
        return false;
    }

    /**
     * List all users in the system.
     */
    public List<User> listUsers() {
        return new ArrayList<>(usersById.values());
    }

    /**
     * Update a user's roles.
     */
    public boolean updateUserRoles(String userId, Set<String> roles) {
        User user = usersById.get(userId);
        if (user != null) {
            user.setRoles(roles);
            return true;
        }
        return false;
    }

    private boolean verifyPassword(String userId, String password) {
        // In a real application, use proper password hashing
        String storedPassword = passwordHashes.get(userId);
        return storedPassword != null && storedPassword.equals(password);
    }

    private User createUser(String username, String password, Set<String> roles) {
        String userId = UUID.randomUUID().toString();
        passwordHashes.put(userId, password);
        return new User(userId, username, roles);
    }
}
