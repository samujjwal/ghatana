package com.ghatana.yappc.infrastructure.datacloud.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
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

    record TestEntity(UUID id, String name, int value) {}
}
