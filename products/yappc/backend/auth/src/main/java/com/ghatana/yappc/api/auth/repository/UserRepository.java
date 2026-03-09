package com.ghatana.yappc.api.auth.repository;

import com.ghatana.yappc.api.auth.model.User;
import io.activej.promise.Promise;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user persistence operations.
 * 
 * @doc.type interface
 * @doc.purpose Data access layer for User entities with Data-Cloud backing
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface UserRepository {

    /**
     * Finds a user by username or email address.
     * 
     * @param usernameOrEmail username or email to search for
     * @return promise of optional user
     */
    Promise<Optional<User>> findByUsernameOrEmail(String usernameOrEmail);

    /**
     * Finds a user by unique identifier.
     * 
     * @param id user UUID
     * @return promise of optional user
     */
    Promise<Optional<User>> findById(UUID id);

    /**
     * Finds a user by password reset token.
     * 
     * @param token reset token
     * @return promise of optional user
     */
    Promise<Optional<User>> findByPasswordResetToken(String token);

    /**
     * Persists a user entity (create or update).
     * 
     * @param user user to save
     * @return promise of saved user
     */
    Promise<User> save(User user);

    /**
     * Checks if a username is already taken.
     * 
     * @param username username to check
     * @return promise of true if exists
     */
    Promise<Boolean> existsByUsername(String username);

    /**
     * Checks if an email is already registered.
     * 
     * @param email email to check
     * @return promise of true if exists
     */
    Promise<Boolean> existsByEmail(String email);
}
