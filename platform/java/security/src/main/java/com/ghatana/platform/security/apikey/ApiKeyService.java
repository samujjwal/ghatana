package com.ghatana.platform.security.apikey;

import com.ghatana.platform.core.exception.ResourceNotFoundException;
import com.ghatana.platform.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing API keys.
 */
/**
 * Api key service.
 *
 * @doc.type class
 * @doc.purpose Api key service
 * @doc.layer core
 * @doc.pattern Service
 */
@Slf4j
public class ApiKeyService {

    private static final int API_KEY_LENGTH = 32;
    private static final String API_KEY_PREFIX = "ak_";
    
    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom;

    /**
     * Creates a new ApiKeyService with the specified repository.
     *
     * @param apiKeyRepository The API key repository
     */
    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Creates a new API key.
     *
     * @param name The API key name
     * @param description The API key description
     * @param owner The API key owner
     * @param expiresAt The API key expiration timestamp
     * @return The created API key
     */
    public ApiKey createApiKey(String name, String description, String owner, Instant expiresAt) {
        String key = generateApiKey();
        
        ApiKey apiKey = ApiKey.builder()
                .key(key)
                .name(name)
                .description(description)
                .owner(owner)
                .expiresAt(expiresAt)
                .build();
        
        return apiKeyRepository.save(apiKey);
    }

    /**
     * Gets an API key by its key value.
     *
     * @param key The API key value
     * @return The API key
     * @throws ResourceNotFoundException If the API key is not found
     */
    public ApiKey getApiKeyByKey(String key) {
        return apiKeyRepository.findByKey(key)
                .orElseThrow(() -> ResourceNotFoundException.forResource("ApiKey", key));
    }

    /**
     * Gets an API key by its ID.
     *
     * @param id The API key ID
     * @return The API key
     * @throws ResourceNotFoundException If the API key is not found
     */
    public ApiKey getApiKeyById(String id) {
        return apiKeyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forResource("ApiKey", id));
    }

    /**
     * Gets all API keys for the specified owner.
     *
     * @param owner The owner
     * @return The API keys
     */
    public List<ApiKey> getApiKeysByOwner(String owner) {
        return apiKeyRepository.findByOwner(owner);
    }

    /**
     * Gets all API keys.
     *
     * @return All API keys
     */
    public List<ApiKey> getAllApiKeys() {
        return apiKeyRepository.findAll();
    }

    /**
     * Updates an API key.
     *
     * @param id The API key ID
     * @param name The API key name
     * @param description The API key description
     * @param expiresAt The API key expiration timestamp
     * @param enabled Whether the API key is enabled
     * @return The updated API key
     * @throws ResourceNotFoundException If the API key is not found
     */
    public ApiKey updateApiKey(String id, String name, String description, Instant expiresAt, Boolean enabled) {
        ApiKey apiKey = getApiKeyById(id);
        
        if (name != null) {
            apiKey.setName(name);
        }
        
        if (description != null) {
            apiKey.setDescription(description);
        }
        
        if (expiresAt != null) {
            apiKey.setExpiresAt(expiresAt);
        }
        
        if (enabled != null) {
            apiKey.setEnabled(enabled);
        }
        
        return apiKeyRepository.save(apiKey);
    }

    /**
     * Deletes an API key by its ID.
     *
     * @param id The API key ID
     * @throws ResourceNotFoundException If the API key is not found
     */
    public void deleteApiKey(String id) {
        if (!apiKeyRepository.deleteById(id)) {
            throw ResourceNotFoundException.forResource("ApiKey", id);
        }
    }

    /**
     * Validates an API key.
     *
     * @param key The API key value
     * @return The API key if valid
     * @throws ServiceException If the API key is invalid
     */
    public ApiKey validateApiKey(String key) {
        Optional<ApiKey> optionalApiKey = apiKeyRepository.findByKey(key);
        
        if (optionalApiKey.isEmpty()) {
            throw new ServiceException("Invalid API key");
        }
        
        ApiKey apiKey = optionalApiKey.get();
        
        if (!apiKey.isValid()) {
            throw new ServiceException("API key is not valid");
        }
        
        apiKey.updateLastUsed();
        apiKeyRepository.save(apiKey);
        
        return apiKey;
    }

    /**
     * Adds a role to an API key.
     *
     * @param id The API key ID
     * @param role The role to add
     * @return The updated API key
     * @throws ResourceNotFoundException If the API key is not found
     */
    public ApiKey addRole(String id, String role) {
        ApiKey apiKey = getApiKeyById(id);
        apiKey.addRole(role);
        return apiKeyRepository.save(apiKey);
    }

    /**
     * Adds a permission to an API key.
     *
     * @param id The API key ID
     * @param permission The permission to add
     * @return The updated API key
     * @throws ResourceNotFoundException If the API key is not found
     */
    public ApiKey addPermission(String id, String permission) {
        ApiKey apiKey = getApiKeyById(id);
        apiKey.addPermission(permission);
        return apiKeyRepository.save(apiKey);
    }

    /**
     * Generates a random API key.
     *
     * @return The generated API key
     */
    private String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
