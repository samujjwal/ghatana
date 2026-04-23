package com.ghatana.platform.security.apikey;

import com.ghatana.platform.core.exception.ResourceNotFoundException;
import com.ghatana.platform.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ApiKeyService}.
 *
 * @doc.type class
 * @doc.purpose ApiKeyService unit tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("ApiKeyService")
@ExtendWith(MockitoExtension.class) // GH-90000
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new ApiKeyService(apiKeyRepository); // GH-90000
    }

    @Nested
    @DisplayName("createApiKey")
    class CreateApiKey {

        @Test
        @DisplayName("should create an API key with generated key value")
        void shouldCreateWithGeneratedKey() { // GH-90000
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS); // GH-90000
            ApiKey result = service.createApiKey("Test Key", "A test key", "owner-1", expiry); // GH-90000

            assertThat(result.getKey()).startsWith("ak_");
            assertThat(result.getName()).isEqualTo("Test Key");
            assertThat(result.getDescription()).isEqualTo("A test key");
            assertThat(result.getOwner()).isEqualTo("owner-1");
            assertThat(result.getExpiresAt()).isEqualTo(expiry); // GH-90000
        }

        @Test
        @DisplayName("should persist the created API key")
        void shouldPersist() { // GH-90000
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            service.createApiKey("Key", "Desc", "owner", null); // GH-90000

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class); // GH-90000
            verify(apiKeyRepository).save(captor.capture()); // GH-90000
            assertThat(captor.getValue().getKey()).startsWith("ak_");
        }
    }

    @Nested
    @DisplayName("getApiKeyByKey")
    class GetApiKeyByKey {

        @Test
        @DisplayName("should return API key when found")
        void shouldReturnWhenFound() { // GH-90000
            ApiKey expected = ApiKey.builder().key("ak_test123").name("Existing").build();
            when(apiKeyRepository.findByKey("ak_test123")).thenReturn(Optional.of(expected));

            ApiKey result = service.getApiKeyByKey("ak_test123");
            assertThat(result.getName()).isEqualTo("Existing");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() { // GH-90000
            when(apiKeyRepository.findByKey(anyString())).thenReturn(Optional.empty()); // GH-90000

            assertThatThrownBy(() -> service.getApiKeyByKey("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateApiKey")
    class ValidateApiKey {

        @Test
        @DisplayName("should return valid API key and update lastUsed")
        void shouldReturnValidKey() { // GH-90000
            ApiKey validKey = ApiKey.builder() // GH-90000
                    .key("ak_valid")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findByKey("ak_valid")).thenReturn(Optional.of(validKey));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.validateApiKey("ak_valid");

            assertThat(result.getLastUsedAt()).isNotNull(); // GH-90000
            verify(apiKeyRepository).save(validKey); // GH-90000
        }

        @Test
        @DisplayName("should throw ServiceException for invalid key string")
        void shouldThrowForInvalidKey() { // GH-90000
            when(apiKeyRepository.findByKey("bad")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateApiKey("bad"))
                    .isInstanceOf(ServiceException.class) // GH-90000
                    .hasMessage("Invalid API key");
        }

        @Test
        @DisplayName("should throw ServiceException for expired key")
        void shouldThrowForExpiredKey() { // GH-90000
            ApiKey expired = ApiKey.builder() // GH-90000
                    .key("ak_expired")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findByKey("ak_expired")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.validateApiKey("ak_expired"))
                    .isInstanceOf(ServiceException.class) // GH-90000
                    .hasMessage("API key is not valid");
        }

        @Test
        @DisplayName("should throw ServiceException for disabled key")
        void shouldThrowForDisabledKey() { // GH-90000
            ApiKey disabled = ApiKey.builder() // GH-90000
                    .key("ak_disabled")
                    .enabled(false) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findByKey("ak_disabled")).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> service.validateApiKey("ak_disabled"))
                    .isInstanceOf(ServiceException.class) // GH-90000
                    .hasMessage("API key is not valid");
        }
    }

    @Nested
    @DisplayName("updateApiKey")
    class UpdateApiKey {

        @Test
        @DisplayName("should update only non-null fields")
        void shouldUpdateNonNullFields() { // GH-90000
            ApiKey existing = ApiKey.builder() // GH-90000
                    .id("id-1")
                    .key("ak_test")
                    .name("Old Name")
                    .description("Old Desc")
                    .enabled(true) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findById("id-1")).thenReturn(Optional.of(existing));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.updateApiKey("id-1", "New Name", null, null, false); // GH-90000

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("Old Desc");
            assertThat(result.isEnabled()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("deleteApiKey")
    class DeleteApiKey {

        @Test
        @DisplayName("should delete when key exists")
        void shouldDeleteExisting() { // GH-90000
            when(apiKeyRepository.existsById("id-1")).thenReturn(true);

            service.deleteApiKey("id-1");

            verify(apiKeyRepository).existsById("id-1");
            verify(apiKeyRepository).deleteById("id-1");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when key not found")
        void shouldThrowWhenNotFound() { // GH-90000
            when(apiKeyRepository.existsById("nonexistent")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteApiKey("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("addRole / addPermission")
    class AddRolePermission {

        @Test
        @DisplayName("should add role to existing key")
        void shouldAddRole() { // GH-90000
            ApiKey key = ApiKey.builder().id("id-1").key("ak_test").build();
            when(apiKeyRepository.findById("id-1")).thenReturn(Optional.of(key));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.addRole("id-1", "ADMIN"); // GH-90000

            assertThat(result.getRoles()).contains("ADMIN");
        }

        @Test
        @DisplayName("should add permission to existing key")
        void shouldAddPermission() { // GH-90000
            ApiKey key = ApiKey.builder().id("id-1").key("ak_test").build();
            when(apiKeyRepository.findById("id-1")).thenReturn(Optional.of(key));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.addPermission("id-1", "event:read:all"); // GH-90000

            assertThat(result.getPermissions()).contains("event:read:all");
        }
    }

    @Nested
    @DisplayName("getApiKeysByOwner")
    class GetByOwner {

        @Test
        @DisplayName("should return all keys for owner")
        void shouldReturnForOwner() { // GH-90000
            ApiKey k1 = ApiKey.builder().key("ak_1").owner("alice").build();
            ApiKey k2 = ApiKey.builder().key("ak_2").owner("alice").build();
            when(apiKeyRepository.findByOwner("alice")).thenReturn(List.of(k1, k2));

            List<ApiKey> result = service.getApiKeysByOwner("alice");

            assertThat(result).hasSize(2); // GH-90000
        }
    }
}
