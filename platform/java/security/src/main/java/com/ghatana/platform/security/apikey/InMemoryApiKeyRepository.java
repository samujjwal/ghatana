package com.ghatana.platform.security.apikey;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the ApiKeyRepository interface.
 * This implementation stores API keys in memory.
 */
/**
 * In memory api key repository.
 *
 * @doc.type class
 * @doc.purpose In memory api key repository
 * @doc.layer core
 * @doc.pattern Repository
 */
@Slf4j
public class InMemoryApiKeyRepository implements ApiKeyRepository {

    private final Map<String, ApiKey> apiKeysById = new ConcurrentHashMap<>();
    private final Map<String, ApiKey> apiKeysByKey = new ConcurrentHashMap<>();

    @Override
    public Optional<ApiKey> findByKey(String key) {
        return Optional.ofNullable(apiKeysByKey.get(key));
    }

    @Override
    public Optional<ApiKey> findById(String id) {
        return Optional.ofNullable(apiKeysById.get(id));
    }

    @Override
    public List<ApiKey> findByOwner(String owner) {
        return apiKeysById.values().stream()
                .filter(apiKey -> owner.equals(apiKey.getOwner()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ApiKey> findAll() {
        return new ArrayList<>(apiKeysById.values());
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        if (apiKey == null) {
            throw new IllegalArgumentException("API key cannot be null");
        }
        
        // Remove old key mapping if it exists
        if (apiKey.getId() != null) {
            ApiKey existingApiKey = apiKeysById.get(apiKey.getId());
            if (existingApiKey != null && existingApiKey.getKey() != null) {
                apiKeysByKey.remove(existingApiKey.getKey());
            }
        }
        
        // Save the API key
        apiKeysById.put(apiKey.getId(), apiKey);
        apiKeysByKey.put(apiKey.getKey(), apiKey);
        
        log.debug("Saved API key: {}", apiKey.getId());
        
        return apiKey;
    }

    @Override
    public boolean deleteById(String id) {
        ApiKey apiKey = apiKeysById.remove(id);
        
        if (apiKey != null && apiKey.getKey() != null) {
            apiKeysByKey.remove(apiKey.getKey());
            log.debug("Deleted API key: {}", id);
            return true;
        }
        
        return false;
    }

    @Override
    public boolean deleteByKey(String key) {
        ApiKey apiKey = apiKeysByKey.remove(key);
        
        if (apiKey != null && apiKey.getId() != null) {
            apiKeysById.remove(apiKey.getId());
            log.debug("Deleted API key with value: {}", key);
            return true;
        }
        
        return false;
    }

    /**
     * Clears all API keys from the repository.
     */
    public void clear() {
        apiKeysById.clear();
        apiKeysByKey.clear();
        log.debug("Cleared all API keys");
    }
}
