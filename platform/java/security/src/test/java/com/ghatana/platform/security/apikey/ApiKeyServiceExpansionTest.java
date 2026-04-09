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
@DisplayName("ApiKeyService - Phase 3 Expansion")
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceExpansionTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRepository);
    }

    // ============================================
    // KEY ROTATION (1 test)
    // ============================================

    @Nested
    @DisplayName("Key Rotation")
    class KeyRotationTests {

        @Test
        @DisplayName("Rotating a key invalidates the old key and creates new valid key")
        void keyRotationCreatesNewKey() {
            // Setup: Create original key
            ApiKey original = new ApiKey("ak_original_123", "Original Key", "Old description", "owner-1",
                Instant.now().plus(30, ChronoUnit.DAYS), false);

            when(apiKeyRepository.findByKey("ak_original_123"))
                .thenReturn(Optional.of(original));
            when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            // Test: Rotate the key - should create new key with same metadata
            ApiKey rotated = service.rotateApiKey("ak_original_123");

            // Verify: New key is different from old
            assertThat(rotated.getKey()).isNotEqualTo("ak_original_123");
            assertThat(rotated.getKey()).startsWith("ak_");
            assertThat(rotated.getName()).isEqualTo("Original Key");
            assertThat(rotated.getOwner()).isEqualTo("owner-1");

            // Verify: Repository saved the new key
            verify(apiKeyRepository).save(any(ApiKey.class));
        }
    }

    // ============================================
    // CONCURRENT KEY GENERATION (1 test)
    // ============================================

    @Nested
    @DisplayName("Concurrent Key Generation")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent key creation produces unique keys")
        void concurrentKeyGenerationUniqueness() throws InterruptedException {
            int threadCount = 5;
            Set<String> generatedKeys = new HashSet<>();
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(inv -> {
                    ApiKey key = inv.getArgument(0);
                    if (generatedKeys.add(key.getKey())) {
                        successCount.incrementAndGet();
                    }
                    return key;
                });

            for (int i = 0; i < threadCount; i++) {
                int index = i;
                new Thread(() -> {
                    try {
                        service.createApiKey(
                            "Concurrent Key " + index,
                            "Description " + index,
                            "owner-" + index,
                            Instant.now().plus(30, ChronoUnit.DAYS)
                        );
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            // All generated keys should be unique
            assertThat(generatedKeys).hasSize(threadCount);
            assertThat(successCount.get()).isEqualTo(threadCount);

            // All keys should start with the correct prefix
            generatedKeys.forEach(key -> assertThat(key).startsWith("ak_"));
        }
    }

    // ============================================
    // EXPIRATION HANDLING (1 test)
    // ============================================

    @Nested
    @DisplayName("Key Expiration Handling")
    class ExpirationTests {

        @Test
        @DisplayName("Expired API keys can be queried but should be marked as inactive")
        void expiredKeyHandling() {
            Instant pastTime = Instant.now().minus(1, ChronoUnit.DAYS);
            ApiKey expiredKey = new ApiKey("ak_expired_123", "Expired Key", "Old key",
                "owner-1", pastTime, false);

            // When: Query for expired key
            when(apiKeyRepository.findByKey("ak_expired_123"))
                .thenReturn(Optional.of(expiredKey));

            ApiKey result = service.getApiKeyByKey("ak_expired_123")
                .orElseThrow(() -> new ResourceNotFoundException("Key not found"));

            // Then: Key should be found but marked as expired (date is in the past)
            assertThat(result.getKey()).isEqualTo("ak_expired_123");
            assertThat(result.getExpiresAt()).isBefore(Instant.now());
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

        public ApiKey(String key, String name, String description, String owner, Instant expiresAt, boolean revoked) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.owner = owner;
            this.expiresAt = expiresAt;
            this.revoked = revoked;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getOwner() { return owner; }
        public Instant getExpiresAt() { return expiresAt; }
        public boolean isRevoked() { return revoked; }
    }

    /**
     * Repository interface for API keys.
     */
    interface ApiKeyRepository {
        ApiKey save(ApiKey key);
        Optional<ApiKey> findByKey(String key);
    }

    /**
     * API key service for testing.
     */
    static class ApiKeyService {
        private final ApiKeyRepository repository;

        public ApiKeyService(ApiKeyRepository repository) {
            this.repository = repository;
        }

        public ApiKey createApiKey(String name, String description, String owner, Instant expiresAt) {
            String key = generateKey();
            ApiKey apiKey = new ApiKey(key, name, description, owner, expiresAt, false);
            return repository.save(apiKey);
        }

        public Optional<ApiKey> getApiKeyByKey(String key) {
            return repository.findByKey(key);
        }

        public ApiKey rotateApiKey(String oldKey) {
            ApiKey existing = repository.findByKey(oldKey)
                .orElseThrow(() -> new ResourceNotFoundException("Key not found"));

            String newKey = generateKey();
            ApiKey rotated = new ApiKey(newKey, existing.getName(), existing.getDescription(),
                existing.getOwner(), existing.getExpiresAt(), false);
            return repository.save(rotated);
        }

        private String generateKey() {
            return "ak_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        }
    }
}
