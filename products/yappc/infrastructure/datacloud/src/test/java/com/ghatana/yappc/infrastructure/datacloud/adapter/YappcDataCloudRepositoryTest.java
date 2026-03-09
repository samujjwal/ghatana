package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private EntityRepository entityRepository;

    private YappcEntityMapper mapper;
    private YappcDataCloudRepository<TestEntity> repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mapper = new YappcEntityMapper(objectMapper);

        repository = new YappcDataCloudRepository<>(
            entityRepository,
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

        Entity savedEntity = Entity.builder()
            .tenantId("default")
            .collectionName("test_collection")
            .data(Map.of("id", id.toString(), "name", "Test", "value", 42))
            .build();

        when(entityRepository.save(anyString(), any(Entity.class)))
            .thenReturn(Promise.of(savedEntity));

        Promise<TestEntity> result = repository.save(entity);

        verify(entityRepository).save(anyString(), any(Entity.class));
    }

    @Test
    @DisplayName("Should find entity by ID")
    void shouldFindById() {
        UUID id = UUID.randomUUID();
        Entity entity = Entity.builder()
            .tenantId("default")
            .collectionName("test_collection")
            .data(Map.of("id", id.toString(), "name", "Test", "value", 42))
            .build();

        when(entityRepository.findById(anyString(), anyString(), any(UUID.class)))
            .thenReturn(Promise.of(Optional.of(entity)));

        Promise<Optional<TestEntity>> result = repository.findById(id);

        verify(entityRepository).findById(anyString(), anyString(), any(UUID.class));
    }

    @Test
    @DisplayName("Should find all entities")
    void shouldFindAll() {
        when(entityRepository.findAll(anyString(), anyString(), any(), any(), anyInt(), anyInt()))
            .thenReturn(Promise.of(List.of()));

        Promise<List<TestEntity>> result = repository.findAll();

        verify(entityRepository).findAll(anyString(), anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should delete entity by ID")
    void shouldDeleteById() {
        UUID id = UUID.randomUUID();

        when(entityRepository.delete(anyString(), anyString(), any(UUID.class)))
            .thenReturn(Promise.of(null));

        Promise<Void> result = repository.deleteById(id);

        verify(entityRepository).delete(anyString(), anyString(), any(UUID.class));
    }

    record TestEntity(UUID id, String name, int value) {}
}
