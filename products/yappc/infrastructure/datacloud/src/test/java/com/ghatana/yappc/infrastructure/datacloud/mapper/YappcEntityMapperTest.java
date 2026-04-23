package com.ghatana.yappc.infrastructure.datacloud.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for YappcEntityMapper.
 */
@DisplayName("YappcEntityMapper Tests")
/**
 * @doc.type class
 * @doc.purpose Handles yappc entity mapper test operations
 * @doc.layer platform
 * @doc.pattern Test
 */
class YappcEntityMapperTest {

    private YappcEntityMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        mapper = new YappcEntityMapper(objectMapper); // GH-90000
    }

    @Test
    @DisplayName("Should convert simple object to entity data map")
    void shouldConvertToEntity() { // GH-90000
        TestEntity source = new TestEntity(UUID.randomUUID(), "Test", 42); // GH-90000

        Map<String, Object> result = mapper.toEntityData(source); // GH-90000

        assertThat(result).containsEntry("name", "Test"); // GH-90000
        assertThat(result).containsEntry("value", 42); // GH-90000
    }

    @Test
    @DisplayName("Should convert Entity back to domain object")
    void shouldConvertFromEntity() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
            id.toString(), "test_collection", // GH-90000
            Map.of("id", id.toString(), "name", "Test", "value", 42)); // GH-90000

        TestEntity result = mapper.fromEntity(entity, TestEntity.class); // GH-90000

        assertThat(result.name()).isEqualTo("Test");
        assertThat(result.value()).isEqualTo(42); // GH-90000
    }

    @Test
    @DisplayName("Should encrypt and restore project environment variables")
    void shouldEncryptAndRestoreProjectEnvironmentVariables() { // GH-90000
        YappcEntityMapper encryptedMapper = new YappcEntityMapper(objectMapper, createEncryptionService()); // GH-90000
        ProjectEntity project = new ProjectEntity("Encrypted Project", "Desc", "user-1"); // GH-90000
        project.setEnvironmentVariables(Map.of( // GH-90000
            "OPENAI_API_KEY", "sk-test-secret",
            "DATABASE_URL", "jdbc:postgresql://localhost/yappc"
        ));

        Map<String, Object> payload = encryptedMapper.toEntityData(project); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, String> storedEnvironmentVariables =
            (Map<String, String>) payload.get("environmentVariables");

        assertThat(storedEnvironmentVariables) // GH-90000
            .containsKeys("OPENAI_API_KEY", "DATABASE_URL"); // GH-90000
        assertThat(storedEnvironmentVariables.get("OPENAI_API_KEY"))
            .startsWith("enc::")
            .isNotEqualTo("sk-test-secret");

        DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
            project.getId().toString(), // GH-90000
            ProjectEntity.getCollectionName(), // GH-90000
            payload);

        ProjectEntity restored = encryptedMapper.fromEntity(entity, ProjectEntity.class); // GH-90000

        assertThat(restored.getEnvironmentVariables()) // GH-90000
            .containsEntry("OPENAI_API_KEY", "sk-test-secret") // GH-90000
            .containsEntry("DATABASE_URL", "jdbc:postgresql://localhost/yappc"); // GH-90000
    }

    @Test
    @DisplayName("Should reject persisting project environment variables without encryption service")
    void shouldRejectPersistingProjectEnvironmentVariablesWithoutEncryptionService() { // GH-90000
        ProjectEntity project = new ProjectEntity("Missing Key", "Desc", "user-1"); // GH-90000
        project.setEnvironmentVariables(Map.of("OPENAI_API_KEY", "sk-test-secret")); // GH-90000

        assertThatThrownBy(() -> mapper.toEntityData(project)) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("secret manager");
    }

    @Test
    @DisplayName("Should continue reading legacy plaintext project environment variables")
    void shouldContinueReadingLegacyPlaintextProjectEnvironmentVariables() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
            id.toString(), // GH-90000
            ProjectEntity.getCollectionName(), // GH-90000
            Map.of( // GH-90000
                "id", id.toString(), // GH-90000
                "name", "Legacy Project",
                "environmentVariables", Map.of("OPENAI_API_KEY", "legacy-secret") // GH-90000
            ));

        ProjectEntity restored = mapper.fromEntity(entity, ProjectEntity.class); // GH-90000

        assertThat(restored.getEnvironmentVariables()) // GH-90000
            .containsEntry("OPENAI_API_KEY", "legacy-secret"); // GH-90000
    }

    private EncryptionService createEncryptionService() { // GH-90000
        return new EncryptionService(Base64.getDecoder().decode(EncryptionService.generateKey())); // GH-90000
    }

    record TestEntity(UUID id, String name, int value) {} // GH-90000
}
