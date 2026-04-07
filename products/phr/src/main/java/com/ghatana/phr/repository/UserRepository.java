package com.ghatana.phr.repository;

import com.ghatana.phr.model.PHRUser;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data access layer for User
 *
 * @doc.type class
 * @doc.purpose Data access layer for User
 * @doc.layer product
 * @doc.pattern Repository
 */
public class UserRepository {
    private static final String SELECT_BY_ID_SQL = """
        SELECT user_id, username, email, provider_id, access_level, roles_json, permissions_json,
               active, password_hash, failed_login_attempts, lockout_until
        FROM phr_users
        WHERE user_id = ?
        """;

    private static final String SELECT_BY_USERNAME_SQL = """
        SELECT user_id, username, email, provider_id, access_level, roles_json, permissions_json,
               active, password_hash, failed_login_attempts, lockout_until
        FROM phr_users
        WHERE username = ?
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO phr_users (user_id, username, email, provider_id, access_level, roles_json, permissions_json,
                               active, password_hash, failed_login_attempts, lockout_until)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
            username = EXCLUDED.username,
            email = EXCLUDED.email,
            provider_id = EXCLUDED.provider_id,
            access_level = EXCLUDED.access_level,
            roles_json = EXCLUDED.roles_json,
            permissions_json = EXCLUDED.permissions_json,
            active = EXCLUDED.active,
            password_hash = EXCLUDED.password_hash,
            failed_login_attempts = EXCLUDED.failed_login_attempts,
            lockout_until = EXCLUDED.lockout_until
        """;

    private static final String DELETE_SQL = "DELETE FROM phr_users WHERE user_id = ?";
    private static final String EXISTS_SQL = "SELECT 1 FROM phr_users WHERE user_id = ?";

    private final DataSource dataSource;
    private final Map<String, PHRUser> users = new ConcurrentHashMap<>();

    public UserRepository() {
        this.dataSource = null;
    }

    public UserRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
        PhrPersistenceSupport.migrate(dataSource);
    }

    public PHRUser findById(String userId) {
        if (dataSource != null) {
            return findByIdJdbc(userId);
        }
        return users.get(userId);
    }

    public Optional<PHRUser> findByUsername(String username) {
        if (dataSource != null) {
            return Optional.ofNullable(findByUsernameJdbc(username));
        }
        return users.values().stream()
            .filter(user -> user.getUsername().equals(username))
            .findFirst();
    }

    public void save(PHRUser user) {
        if (user.getUserId() == null) {
            user.setUserId(UUID.randomUUID().toString());
        }
        if (dataSource != null) {
            saveJdbc(user);
            return;
        }
        users.put(user.getUserId(), user);
    }

    public void delete(String userId) {
        if (dataSource != null) {
            deleteJdbc(userId);
            return;
        }
        users.remove(userId);
    }

    public boolean exists(String userId) {
        if (dataSource != null) {
            return existsJdbc(userId);
        }
        return users.containsKey(userId);
    }

    private PHRUser findByIdJdbc(String userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapUser(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load user: " + userId, exception);
        }
    }

    private PHRUser findByUsernameJdbc(String username) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_USERNAME_SQL)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapUser(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load user by username: " + username, exception);
        }
    }

    private void saveJdbc(PHRUser user) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, user.getUserId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getEmail());
            statement.setString(4, user.getProviderId());
            statement.setString(5, user.getAccessLevel());
            statement.setString(6, PhrPersistenceSupport.writeStringSet(user.getRoles()));
            statement.setString(7, PhrPersistenceSupport.writeStringSet(user.getPermissions()));
            statement.setBoolean(8, user.isActive());
            statement.setString(9, user.getPasswordHash());
            statement.setInt(10, user.getFailedLoginAttempts());
            statement.setTimestamp(11, PhrPersistenceSupport.toTimestamp(user.getLockoutUntil()));
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save user: " + user.getUserId(), exception);
        }
    }

    private void deleteJdbc(String userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, userId);
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete user: " + userId, exception);
        }
    }

    private boolean existsJdbc(String userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check user existence: " + userId, exception);
        }
    }

    private static PHRUser mapUser(ResultSet resultSet) throws SQLException {
        PHRUser user = new PHRUser();
        user.setUserId(resultSet.getString("user_id"));
        user.setUsername(resultSet.getString("username"));
        user.setEmail(resultSet.getString("email"));
        user.setProviderId(resultSet.getString("provider_id"));
        user.setAccessLevel(resultSet.getString("access_level"));
        user.setRoles(PhrPersistenceSupport.readStringSet(resultSet.getString("roles_json")));
        user.setPermissions(PhrPersistenceSupport.readStringSet(resultSet.getString("permissions_json")));
        user.setActive(resultSet.getBoolean("active"));
        user.setPasswordHash(resultSet.getString("password_hash"));
        user.setFailedLoginAttempts(resultSet.getInt("failed_login_attempts"));
        user.setLockoutUntil(PhrPersistenceSupport.toInstant(resultSet.getTimestamp("lockout_until")));
        return user;
    }
}
