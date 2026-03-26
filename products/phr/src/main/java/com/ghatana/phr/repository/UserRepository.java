package com.ghatana.phr.repository;

import com.ghatana.phr.model.PHRUser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data access layer for User
 *
 * @doc.type class
 * @doc.purpose Data access layer for User
 * @doc.layer product
 * @doc.pattern Repository
 */
public class UserRepository {
    private final Map<String, PHRUser> users = new HashMap<>();

    public PHRUser findById(String userId) {
        return users.get(userId);
    }

    public Optional<PHRUser> findByUsername(String username) {
        return users.values().stream()
            .filter(user -> user.getUsername().equals(username))
            .findFirst();
    }

    public void save(PHRUser user) {
        users.put(user.getUserId(), user);
    }

    public void delete(String userId) {
        users.remove(userId);
    }

    public boolean exists(String userId) {
        return users.containsKey(userId);
    }
}
