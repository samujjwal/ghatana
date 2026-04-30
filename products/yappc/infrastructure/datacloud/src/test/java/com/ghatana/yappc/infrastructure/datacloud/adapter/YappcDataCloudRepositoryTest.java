package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import com.ghatana.yappc.domain.Identifiable;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for YappcDataCloudRepository.
 */
@DisplayName("YappcDataCloudRepository Tests")
/**
 * @doc.type class
 * @doc.purpose Handles yappc data cloud repository test operations
 * @doc.layer platform
 * @doc.pattern Test
 */
class YappcDataCloudRepositoryTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private YappcEntityMapper mapper;
    private YappcDataCloudRepository<TestEntity> repository;

    @AfterEach
    void tearDown() { // GH-90000
        runBlocking(TenantContext::clear); // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000

        ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        mapper = new YappcEntityMapper(objectMapper); // GH-90000

        repository = new YappcDataCloudRepository<>( // GH-90000
            client,
            mapper,
            "test_collection",
            TestEntity.class
        );
    }

    @Test
    @DisplayName("Should save entity through data-cloud")
    void shouldSaveEntity() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        TestEntity entity = new TestEntity(id, "Test", 42); // GH-90000

        DataCloudClient.Entity savedEntity = DataCloudClient.Entity.of( // GH-90000
            id.toString(), "test_collection", // GH-90000
            Map.of("id", id.toString(), "name", "Test", "value", 42)); // GH-90000

        when(client.save(anyString(), anyString(), any())) // GH-90000
            .thenReturn(Promise.of(savedEntity)); // GH-90000

        Promise<TestEntity> result = repository.save(entity); // GH-90000

        verify(client).save(anyString(), anyString(), any()); // GH-90000
    }

    @Test
    @DisplayName("Should find entity by ID")
    void shouldFindById() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        DataCloudClient.Entity entity = DataCloudClient.Entity.of( // GH-90000
            id.toString(), "test_collection", // GH-90000
            Map.of("id", id.toString(), "name", "Test", "value", 42)); // GH-90000

        when(client.findById(anyString(), anyString(), anyString())) // GH-90000
            .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

        Promise<Optional<TestEntity>> result = repository.findById(id); // GH-90000

        verify(client).findById(anyString(), anyString(), anyString()); // GH-90000
    }

    @Test
    @DisplayName("Should find all entities")
    void shouldFindAll() { // GH-90000
        when(client.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
            .thenReturn(Promise.of(List.of())); // GH-90000

        Promise<List<TestEntity>> result = repository.findAll(); // GH-90000

        verify(client).query(anyString(), anyString(), any(DataCloudClient.Query.class)); // GH-90000
    }

    @Test
    @DisplayName("Should delete entity by ID")
    void shouldDeleteById() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000

        when(client.delete(anyString(), anyString(), anyString())) // GH-90000
            .thenReturn(Promise.of(null)); // GH-90000

        Promise<Void> result = repository.deleteById(id); // GH-90000

        verify(client).delete(anyString(), anyString(), anyString()); // GH-90000
    }

    @Test
    @DisplayName("Should encrypt project environment variables before saving")
    void shouldEncryptProjectEnvironmentVariablesBeforeSaving() { // GH-90000
        ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        YappcEntityMapper encryptedMapper = new YappcEntityMapper( // GH-90000
            objectMapper,
            new EncryptionService(Base64.getDecoder().decode(EncryptionService.generateKey())) // GH-90000
        );
        YappcDataCloudRepository<ProjectEntity> projectRepository = new YappcDataCloudRepository<>( // GH-90000
            client,
            encryptedMapper,
            ProjectEntity.getCollectionName(), // GH-90000
            ProjectEntity.class
        );
        ProjectEntity project = new ProjectEntity("Secure Project", "Desc", "user-1"); // GH-90000
        project.setTenantId("tenant-alpha");
        project.setEnvironmentVariables(Map.of("OPENAI_API_KEY", "sk-live-secret")); // GH-90000

        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));

        AtomicReference<Map<String, Object>> savedPayloadRef = new AtomicReference<>(); // GH-90000
        when(client.save(anyString(), anyString(), any())) // GH-90000
            .thenAnswer(invocation -> { // GH-90000
                Map<String, Object> payload = invocation.getArgument(2); // GH-90000
                savedPayloadRef.set(payload); // GH-90000
                return Promise.of(DataCloudClient.Entity.of( // GH-90000
                    project.getId().toString(), // GH-90000
                    ProjectEntity.getCollectionName(), // GH-90000
                    payload
                ));
            });

        ProjectEntity saved = runPromise(() -> projectRepository.save(project)); // GH-90000

        verify(client).save(eq("tenant-alpha"), eq(ProjectEntity.getCollectionName()), any());
        Map<String, Object> savedPayload = savedPayloadRef.get(); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, String> persistedEnvironmentVariables =
            (Map<String, String>) savedPayload.get("environmentVariables");

        assertThat(persistedEnvironmentVariables.get("OPENAI_API_KEY"))
            .startsWith("enc::")
            .isNotEqualTo("sk-live-secret");
        assertThat(saved.getEnvironmentVariables()) // GH-90000
            .containsEntry("OPENAI_API_KEY", "sk-live-secret"); // GH-90000
    }

    record TestEntity(UUID id, String name, int value) implements Identifiable<UUID> { // GH-90000
        @Override public UUID getId() { return id; } // GH-90000
    }
}
