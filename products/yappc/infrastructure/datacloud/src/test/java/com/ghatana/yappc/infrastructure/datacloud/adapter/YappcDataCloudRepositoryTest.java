package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import com.ghatana.products.yappc.domain.Identifiable;
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
    void tearDown() {
        runBlocking(TenantContext::clear);
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mapper = new YappcEntityMapper(objectMapper);

        repository = new YappcDataCloudRepository<>(
            client,
            mapper,
            "test_collection",
            TestEntity.class
        );
    }

    @Test
    @DisplayName("Should save entity through data-cloud")
    void shouldSaveEntity() {
        UUID id = UUID.randomUUID();
        TestEntity entity = new TestEntity(id, "Test", 42);

        DataCloudClient.Entity savedEntity = DataCloudClient.Entity.of(
            id.toString(), "test_collection",
            Map.of("id", id.toString(), "name", "Test", "value", 42));

        when(client.save(anyString(), anyString(), any()))
            .thenReturn(Promise.of(savedEntity));

        Promise<TestEntity> result = repository.save(entity);

        verify(client).save(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should find entity by ID")
    void shouldFindById() {
        UUID id = UUID.randomUUID();
        DataCloudClient.Entity entity = DataCloudClient.Entity.of(
            id.toString(), "test_collection",
            Map.of("id", id.toString(), "name", "Test", "value", 42));

        when(client.findById(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(entity)));

        Promise<Optional<TestEntity>> result = repository.findById(id);

        verify(client).findById(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should find all entities")
    void shouldFindAll() {
        when(client.query(anyString(), anyString(), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));

        Promise<List<TestEntity>> result = repository.findAll();

        verify(client).query(anyString(), anyString(), any(DataCloudClient.Query.class));
    }

    @Test
    @DisplayName("Should delete entity by ID")
    void shouldDeleteById() {
        UUID id = UUID.randomUUID();

        when(client.delete(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(null));

        Promise<Void> result = repository.deleteById(id);

        verify(client).delete(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should encrypt project environment variables before saving")
    void shouldEncryptProjectEnvironmentVariablesBeforeSaving() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        YappcEntityMapper encryptedMapper = new YappcEntityMapper(
            objectMapper,
            new EncryptionService(Base64.getDecoder().decode(EncryptionService.generateKey()))
        );
        YappcDataCloudRepository<ProjectEntity> projectRepository = new YappcDataCloudRepository<>(
            client,
            encryptedMapper,
            ProjectEntity.getCollectionName(),
            ProjectEntity.class
        );
        ProjectEntity project = new ProjectEntity("Secure Project", "Desc", "user-1");
        project.setTenantId("tenant-alpha");
        project.setEnvironmentVariables(Map.of("OPENAI_API_KEY", "sk-live-secret"));

        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));

        AtomicReference<Map<String, Object>> savedPayloadRef = new AtomicReference<>();
        when(client.save(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                Map<String, Object> payload = invocation.getArgument(2);
                savedPayloadRef.set(payload);
                return Promise.of(DataCloudClient.Entity.of(
                    project.getId().toString(),
                    ProjectEntity.getCollectionName(),
                    payload
                ));
            });

        ProjectEntity saved = runPromise(() -> projectRepository.save(project));

        verify(client).save(eq("tenant-alpha"), eq(ProjectEntity.getCollectionName()), any());
        Map<String, Object> savedPayload = savedPayloadRef.get();
        @SuppressWarnings("unchecked")
        Map<String, String> persistedEnvironmentVariables =
            (Map<String, String>) savedPayload.get("environmentVariables");

        assertThat(persistedEnvironmentVariables.get("OPENAI_API_KEY"))
            .startsWith("enc::")
            .isNotEqualTo("sk-live-secret");
        assertThat(saved.getEnvironmentVariables())
            .containsEntry("OPENAI_API_KEY", "sk-live-secret");
    }

    record TestEntity(UUID id, String name, int value) implements Identifiable<UUID> {
        @Override public UUID getId() { return id; }
    }
}
