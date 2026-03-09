package com.ghatana.yappc.infrastructure.datacloud.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("Should convert simple object to Entity")
    void shouldConvertToEntity() {
        TestEntity source = new TestEntity(UUID.randomUUID(), "Test", 42);

        Entity result = mapper.toEntity(source, "test_collection", "default");

        assertThat(result.getCollectionName()).isEqualTo("test_collection");
        assertThat(result.getTenantId()).isEqualTo("default");
        assertThat(result.getData()).containsEntry("name", "Test");
        assertThat(result.getData()).containsEntry("value", 42);
    }

    @Test
    @DisplayName("Should convert Entity back to domain object")
    void shouldConvertFromEntity() {
        UUID id = UUID.randomUUID();
        Entity entity = Entity.builder()
            .tenantId("default")
            .collectionName("test_collection")
            .data(Map.of("id", id.toString(), "name", "Test", "value", 42))
            .build();

        TestEntity result = mapper.fromEntity(entity, TestEntity.class);

        assertThat(result.name()).isEqualTo("Test");
        assertThat(result.value()).isEqualTo(42);
    }

    record TestEntity(UUID id, String name, int value) {}
}
