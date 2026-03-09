package com.ghatana.platform.security.apikey;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing API keys.
 
 *
 * @doc.type interface
 * @doc.purpose Api key repository
 * @doc.layer core
 * @doc.pattern Repository
*/
public interface ApiKeyRepository {

    /**
     * Finds an API key by its key value.
     *
     * @param key The API key value
     * @return The API key, or empty if not found
     */
    Optional<ApiKey> findByKey(String key);

    /**
     * Finds an API key by its ID.
     *
     * @param id The API key ID
     * @return The API key, or empty if not found
     */
    Optional<ApiKey> findById(String id);

    /**
     * Finds all API keys for the specified owner.
     *
     * @param owner The owner
     * @return The API keys
     */
    List<ApiKey> findByOwner(String owner);

    /**
     * Finds all API keys.
     *
     * @return All API keys
     */
    List<ApiKey> findAll();

    /**
     * Saves an API key.
     *
     * @param apiKey The API key to save
     * @return The saved API key
     */
    ApiKey save(ApiKey apiKey);

    /**
     * Deletes an API key by its ID.
     *
     * @param id The API key ID
     * @return true if the API key was deleted, false otherwise
     */
    boolean deleteById(String id);

    /**
     * Deletes an API key by its key value.
     *
     * @param key The API key value
     * @return true if the API key was deleted, false otherwise
     */
    boolean deleteByKey(String key);
}
