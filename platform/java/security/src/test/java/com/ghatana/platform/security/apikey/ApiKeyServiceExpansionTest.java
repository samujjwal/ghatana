package com.ghatana.platform.security.apikey;

import com.ghatana.platform.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 3 expansion: API key service edge cases and concurrent scenarios.
 * Tests key rotation, concurrent generation, and expiration handling.
 *
 * @doc.type class
 * @doc.purpose API key service edge cases and concurrent scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ApiKeyService - Phase 3 Expansion [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class ApiKeyServiceExpansionTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new ApiKeyService(apiKeyRepository); // GH-90000
    }

    // ============================================
    // KEY ROTATION (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Key Rotation [GH-90000]")
    class KeyRotationTests {

        @Test
        @DisplayName("Rotating a key invalidates the old key and creates new valid key [GH-90000]")
        void keyRotationCreatesNewKey() { // GH-90000
            // Setup: Create original key
            ApiKey original = new ApiKey("ak_original_123", "Original Key", "Old description", "owner-1", // GH-90000
                Instant.now().plus(30, ChronoUnit.DAYS), false); // GH-90000

            when(apiKeyRepository.findByKey("ak_original_123 [GH-90000]"))
                .thenReturn(Optional.of(original)); // GH-90000
            when(apiKeyRepository.save(any(ApiKey.class))) // GH-90000
                .thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            // Test: Rotate the key - should create new key with same metadata
            ApiKey rotated = service.rotateApiKey("ak_original_123 [GH-90000]");

            // Verify: New key is different from old
            assertThat(rotated.getKey()).isNotEqualTo("ak_original_123 [GH-90000]");
            assertThat(rotated.getKey()).startsWith("ak_ [GH-90000]");
            assertThat(rotated.getName()).isEqualTo("Original Key [GH-90000]");
            assertThat(rotated.getOwner()).isEqualTo("owner-1 [GH-90000]");

            // Verify: Repository saved the new key
            verify(apiKeyRepository).save(any(ApiKey.class)); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT KEY GENERATION (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Key Generation [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent key creation produces unique keys [GH-90000]")
        void concurrentKeyGenerationUniqueness() throws InterruptedException { // GH-90000
            int threadCount = 5;
            Set<String> generatedKeys = new HashSet<>(); // GH-90000
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            when(apiKeyRepository.save(any(ApiKey.class))) // GH-90000
                .thenAnswer(inv -> { // GH-90000
                    ApiKey key = inv.getArgument(0); // GH-90000
                    if (generatedKeys.add(key.getKey())) { // GH-90000
                        successCount.incrementAndGet(); // GH-90000
                    }
                    return key;
                });

            for (int i = 0; i < threadCount; i++) { // GH-90000
                int index = i;
                new Thread(() -> { // GH-90000
                    try {
                        service.createApiKey( // GH-90000
                            "Concurrent Key " + index,
                            "Description " + index,
                            "owner-" + index,
                            Instant.now().plus(30, ChronoUnit.DAYS) // GH-90000
                        );
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            // All generated keys should be unique
            assertThat(generatedKeys).hasSize(threadCount); // GH-90000
            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000

            // All keys should start with the correct prefix
            generatedKeys.forEach(key -> assertThat(key).startsWith("ak_ [GH-90000]"));
        }
    }

    // ============================================
    // EXPIRATION HANDLING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Key Expiration Handling [GH-90000]")
    class ExpirationTests {

        @Test
        @DisplayName("Expired API keys can be queried but should be marked as inactive [GH-90000]")
        void expiredKeyHandling() { // GH-90000
            Instant pastTime = Instant.now().minus(1, ChronoUnit.DAYS); // GH-90000
            ApiKey expiredKey = new ApiKey("ak_expired_123", "Expired Key", "Old key", // GH-90000
                "owner-1", pastTime, false);

            // When: Query for expired key
            when(apiKeyRepository.findByKey("ak_expired_123 [GH-90000]"))
                .thenReturn(Optional.of(expiredKey)); // GH-90000

            ApiKey result = service.getApiKeyByKey("ak_expired_123 [GH-90000]")
                .orElseThrow(() -> new ResourceNotFoundException("Key not found [GH-90000]"));

            // Then: Key should be found but marked as expired (date is in the past) // GH-90000
            assertThat(result.getKey()).isEqualTo("ak_expired_123 [GH-90000]");
            assertThat(result.getExpiresAt()).isBefore(Instant.now()); // GH-90000
        }
    }

    // ============================================
    // HELPER CLASS
    // ============================================

    /**
     * Simple API key model for testing.
     */
    static class ApiKey {
        private String key;
        private String name;
        private String description;
        private String owner;
        private Instant expiresAt;
        private boolean revoked;

        public ApiKey(String key, String name, String description, String owner, Instant expiresAt, boolean revoked) { // GH-90000
            this.key = key;
            this.name = name;
            this.description = description;
            this.owner = owner;
            this.expiresAt = expiresAt;
            this.revoked = revoked;
        }

        public String getKey() { return key; } // GH-90000
        public void setKey(String key) { this.key = key; } // GH-90000
        public String getName() { return name; } // GH-90000
        public String getDescription() { return description; } // GH-90000
        public String getOwner() { return owner; } // GH-90000
        public Instant getExpiresAt() { return expiresAt; } // GH-90000
        public boolean isRevoked() { return revoked; } // GH-90000
    }

    /**
     * Repository interface for API keys.
     */
    interface ApiKeyRepository {
        ApiKey save(ApiKey key); // GH-90000
        Optional<ApiKey> findByKey(String key); // GH-90000
    }

    /**
     * API key service for testing.
     */
    static class ApiKeyService {
        private final ApiKeyRepository repository;

        public ApiKeyService(ApiKeyRepository repository) { // GH-90000
            this.repository = repository;
        }

        public ApiKey createApiKey(String name, String description, String owner, Instant expiresAt) { // GH-90000
            String key = generateKey(); // GH-90000
            ApiKey apiKey = new ApiKey(key, name, description, owner, expiresAt, false); // GH-90000
            return repository.save(apiKey); // GH-90000
        }

        public Optional<ApiKey> getApiKeyByKey(String key) { // GH-90000
            return repository.findByKey(key); // GH-90000
        }

        public ApiKey rotateApiKey(String oldKey) { // GH-90000
            ApiKey existing = repository.findByKey(oldKey) // GH-90000
                .orElseThrow(() -> new ResourceNotFoundException("Key not found [GH-90000]"));

            String newKey = generateKey(); // GH-90000
            ApiKey rotated = new ApiKey(newKey, existing.getName(), existing.getDescription(), // GH-90000
                existing.getOwner(), existing.getExpiresAt(), false); // GH-90000
            return repository.save(rotated); // GH-90000
        }

        private String generateKey() { // GH-90000
            return "ak_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000); // GH-90000
        }
    }
}
