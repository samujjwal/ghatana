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
@DisplayName("ApiKeyService [GH-90000]")
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
    @DisplayName("createApiKey [GH-90000]")
    class CreateApiKey {

        @Test
        @DisplayName("should create an API key with generated key value [GH-90000]")
        void shouldCreateWithGeneratedKey() { // GH-90000
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS); // GH-90000
            ApiKey result = service.createApiKey("Test Key", "A test key", "owner-1", expiry); // GH-90000

            assertThat(result.getKey()).startsWith("ak_ [GH-90000]");
            assertThat(result.getName()).isEqualTo("Test Key [GH-90000]");
            assertThat(result.getDescription()).isEqualTo("A test key [GH-90000]");
            assertThat(result.getOwner()).isEqualTo("owner-1 [GH-90000]");
            assertThat(result.getExpiresAt()).isEqualTo(expiry); // GH-90000
        }

        @Test
        @DisplayName("should persist the created API key [GH-90000]")
        void shouldPersist() { // GH-90000
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            service.createApiKey("Key", "Desc", "owner", null); // GH-90000

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class); // GH-90000
            verify(apiKeyRepository).save(captor.capture()); // GH-90000
            assertThat(captor.getValue().getKey()).startsWith("ak_ [GH-90000]");
        }
    }

    @Nested
    @DisplayName("getApiKeyByKey [GH-90000]")
    class GetApiKeyByKey {

        @Test
        @DisplayName("should return API key when found [GH-90000]")
        void shouldReturnWhenFound() { // GH-90000
            ApiKey expected = ApiKey.builder().key("ak_test123 [GH-90000]").name("Existing [GH-90000]").build();
            when(apiKeyRepository.findByKey("ak_test123 [GH-90000]")).thenReturn(Optional.of(expected));

            ApiKey result = service.getApiKeyByKey("ak_test123 [GH-90000]");
            assertThat(result.getName()).isEqualTo("Existing [GH-90000]");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found [GH-90000]")
        void shouldThrowWhenNotFound() { // GH-90000
            when(apiKeyRepository.findByKey(anyString())).thenReturn(Optional.empty()); // GH-90000

            assertThatThrownBy(() -> service.getApiKeyByKey("nonexistent [GH-90000]"))
                    .isInstanceOf(ResourceNotFoundException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateApiKey [GH-90000]")
    class ValidateApiKey {

        @Test
        @DisplayName("should return valid API key and update lastUsed [GH-90000]")
        void shouldReturnValidKey() { // GH-90000
            ApiKey validKey = ApiKey.builder() // GH-90000
                    .key("ak_valid [GH-90000]")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findByKey("ak_valid [GH-90000]")).thenReturn(Optional.of(validKey));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.validateApiKey("ak_valid [GH-90000]");

            assertThat(result.getLastUsedAt()).isNotNull(); // GH-90000
            verify(apiKeyRepository).save(validKey); // GH-90000
        }

        @Test
        @DisplayName("should throw ServiceException for invalid key string [GH-90000]")
        void shouldThrowForInvalidKey() { // GH-90000
            when(apiKeyRepository.findByKey("bad [GH-90000]")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateApiKey("bad [GH-90000]"))
                    .isInstanceOf(ServiceException.class) // GH-90000
                    .hasMessage("Invalid API key [GH-90000]");
        }

        @Test
        @DisplayName("should throw ServiceException for expired key [GH-90000]")
        void shouldThrowForExpiredKey() { // GH-90000
            ApiKey expired = ApiKey.builder() // GH-90000
                    .key("ak_expired [GH-90000]")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findByKey("ak_expired [GH-90000]")).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.validateApiKey("ak_expired [GH-90000]"))
                    .isInstanceOf(ServiceException.class) // GH-90000
                    .hasMessage("API key is not valid [GH-90000]");
        }

        @Test
        @DisplayName("should throw ServiceException for disabled key [GH-90000]")
        void shouldThrowForDisabledKey() { // GH-90000
            ApiKey disabled = ApiKey.builder() // GH-90000
                    .key("ak_disabled [GH-90000]")
                    .enabled(false) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findByKey("ak_disabled [GH-90000]")).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> service.validateApiKey("ak_disabled [GH-90000]"))
                    .isInstanceOf(ServiceException.class) // GH-90000
                    .hasMessage("API key is not valid [GH-90000]");
        }
    }

    @Nested
    @DisplayName("updateApiKey [GH-90000]")
    class UpdateApiKey {

        @Test
        @DisplayName("should update only non-null fields [GH-90000]")
        void shouldUpdateNonNullFields() { // GH-90000
            ApiKey existing = ApiKey.builder() // GH-90000
                    .id("id-1 [GH-90000]")
                    .key("ak_test [GH-90000]")
                    .name("Old Name [GH-90000]")
                    .description("Old Desc [GH-90000]")
                    .enabled(true) // GH-90000
                    .build(); // GH-90000
            when(apiKeyRepository.findById("id-1 [GH-90000]")).thenReturn(Optional.of(existing));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.updateApiKey("id-1", "New Name", null, null, false); // GH-90000

            assertThat(result.getName()).isEqualTo("New Name [GH-90000]");
            assertThat(result.getDescription()).isEqualTo("Old Desc [GH-90000]");
            assertThat(result.isEnabled()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("deleteApiKey [GH-90000]")
    class DeleteApiKey {

        @Test
        @DisplayName("should delete when key exists [GH-90000]")
        void shouldDeleteExisting() { // GH-90000
            when(apiKeyRepository.existsById("id-1 [GH-90000]")).thenReturn(true);

            service.deleteApiKey("id-1 [GH-90000]");

            verify(apiKeyRepository).existsById("id-1 [GH-90000]");
            verify(apiKeyRepository).deleteById("id-1 [GH-90000]");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when key not found [GH-90000]")
        void shouldThrowWhenNotFound() { // GH-90000
            when(apiKeyRepository.existsById("nonexistent [GH-90000]")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteApiKey("nonexistent [GH-90000]"))
                    .isInstanceOf(ResourceNotFoundException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("addRole / addPermission [GH-90000]")
    class AddRolePermission {

        @Test
        @DisplayName("should add role to existing key [GH-90000]")
        void shouldAddRole() { // GH-90000
            ApiKey key = ApiKey.builder().id("id-1 [GH-90000]").key("ak_test [GH-90000]").build();
            when(apiKeyRepository.findById("id-1 [GH-90000]")).thenReturn(Optional.of(key));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.addRole("id-1", "ADMIN"); // GH-90000

            assertThat(result.getRoles()).contains("ADMIN [GH-90000]");
        }

        @Test
        @DisplayName("should add permission to existing key [GH-90000]")
        void shouldAddPermission() { // GH-90000
            ApiKey key = ApiKey.builder().id("id-1 [GH-90000]").key("ak_test [GH-90000]").build();
            when(apiKeyRepository.findById("id-1 [GH-90000]")).thenReturn(Optional.of(key));
            when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0)); // GH-90000

            ApiKey result = service.addPermission("id-1", "event:read:all"); // GH-90000

            assertThat(result.getPermissions()).contains("event:read:all [GH-90000]");
        }
    }

    @Nested
    @DisplayName("getApiKeysByOwner [GH-90000]")
    class GetByOwner {

        @Test
        @DisplayName("should return all keys for owner [GH-90000]")
        void shouldReturnForOwner() { // GH-90000
            ApiKey k1 = ApiKey.builder().key("ak_1 [GH-90000]").owner("alice [GH-90000]").build();
            ApiKey k2 = ApiKey.builder().key("ak_2 [GH-90000]").owner("alice [GH-90000]").build();
            when(apiKeyRepository.findByOwner("alice [GH-90000]")).thenReturn(List.of(k1, k2));

            List<ApiKey> result = service.getApiKeysByOwner("alice [GH-90000]");

            assertThat(result).hasSize(2); // GH-90000
        }
    }
}
