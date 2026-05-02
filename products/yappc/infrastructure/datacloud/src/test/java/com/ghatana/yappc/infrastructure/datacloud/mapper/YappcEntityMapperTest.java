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
    void setUp() { 
        objectMapper = new ObjectMapper(); 
        objectMapper.registerModule(new JavaTimeModule()); 
        mapper = new YappcEntityMapper(objectMapper); 
    }

    @Test
    @DisplayName("Should convert simple object to entity data map")
    void shouldConvertToEntity() { 
        TestEntity source = new TestEntity(UUID.randomUUID(), "Test", 42); 

        Map<String, Object> result = mapper.toEntityData(source); 

        assertThat(result).containsEntry("name", "Test"); 
        assertThat(result).containsEntry("value", 42); 
    }

    @Test
    @DisplayName("Should convert Entity back to domain object")
    void shouldConvertFromEntity() { 
        UUID id = UUID.randomUUID(); 
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( 
            id.toString(), "test_collection", 
            Map.of("id", id.toString(), "name", "Test", "value", 42)); 

        TestEntity result = mapper.fromEntity(entity, TestEntity.class); 

        assertThat(result.name()).isEqualTo("Test");
        assertThat(result.value()).isEqualTo(42); 
    }

    @Test
    @DisplayName("Should encrypt and restore project environment variables")
    void shouldEncryptAndRestoreProjectEnvironmentVariables() { 
        YappcEntityMapper encryptedMapper = new YappcEntityMapper(objectMapper, createEncryptionService()); 
        ProjectEntity project = new ProjectEntity("Encrypted Project", "Desc", "user-1"); 
        project.setEnvironmentVariables(Map.of( 
            "OPENAI_API_KEY", "sk-test-secret",
            "DATABASE_URL", "jdbc:postgresql://localhost/yappc"
        ));

        Map<String, Object> payload = encryptedMapper.toEntityData(project); 
        @SuppressWarnings("unchecked")
        Map<String, String> storedEnvironmentVariables =
            (Map<String, String>) payload.get("environmentVariables");

        assertThat(storedEnvironmentVariables) 
            .containsKeys("OPENAI_API_KEY", "DATABASE_URL"); 
        assertThat(storedEnvironmentVariables.get("OPENAI_API_KEY"))
            .startsWith("enc::")
            .isNotEqualTo("sk-test-secret");

        DataCloudClient.Entity entity = DataCloudClient.Entity.of( 
            project.getId().toString(), 
            ProjectEntity.getCollectionName(), 
            payload);

        ProjectEntity restored = encryptedMapper.fromEntity(entity, ProjectEntity.class); 

        assertThat(restored.getEnvironmentVariables()) 
            .containsEntry("OPENAI_API_KEY", "sk-test-secret") 
            .containsEntry("DATABASE_URL", "jdbc:postgresql://localhost/yappc"); 
    }

    @Test
    @DisplayName("Should reject persisting project environment variables without encryption service")
    void shouldRejectPersistingProjectEnvironmentVariablesWithoutEncryptionService() { 
        ProjectEntity project = new ProjectEntity("Missing Key", "Desc", "user-1"); 
        project.setEnvironmentVariables(Map.of("OPENAI_API_KEY", "sk-test-secret")); 

        assertThatThrownBy(() -> mapper.toEntityData(project)) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("secret manager");
    }

    @Test
    @DisplayName("Should continue reading legacy plaintext project environment variables")
    void shouldContinueReadingLegacyPlaintextProjectEnvironmentVariables() { 
        UUID id = UUID.randomUUID(); 
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( 
            id.toString(), 
            ProjectEntity.getCollectionName(), 
            Map.of( 
                "id", id.toString(), 
                "name", "Legacy Project",
                "environmentVariables", Map.of("OPENAI_API_KEY", "legacy-secret") 
            ));

        ProjectEntity restored = mapper.fromEntity(entity, ProjectEntity.class); 

        assertThat(restored.getEnvironmentVariables()) 
            .containsEntry("OPENAI_API_KEY", "legacy-secret"); 
    }

    private EncryptionService createEncryptionService() { 
        return new EncryptionService(Base64.getDecoder().decode(EncryptionService.generateKey())); 
    }

    record TestEntity(UUID id, String name, int value) {} 
}
