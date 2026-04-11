package com.ghatana.platform.security.apikey;

import com.ghatana.core.database.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing API keys.
 *
 * <p>Extends the canonical {@link Repository} from platform:database for standard CRUD
 * operations while adding API key-specific query methods.</p>
 *
 * @doc.type interface
 * @doc.purpose Repository for managing API keys with canonical Repository base
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface ApiKeyRepository extends Repository<ApiKey, String> {

    /**
     * Finds an API key by its key value.
     *
     * @param key The API key value
     * @return The API key, or empty if not found
     */
    Optional<ApiKey> findByKey(String key);

    /**
     * Finds all API keys for the specified owner.
     *
     * @param owner The owner
     * @return The API keys
     */
    List<ApiKey> findByOwner(String owner);

    /**
     * Deletes an API key by its key value.
     *
     * @param key The API key value
     * @return true if the API key was deleted, false otherwise
     */
    boolean deleteByKey(String key);
}
