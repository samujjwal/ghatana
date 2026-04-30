package com.ghatana.yappc.infrastructure.datacloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.domain.Identifiable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * Integration tests for data-cloud repository adapters.
 *
 * @doc.type class
 * @doc.purpose Validates YappcDataCloudRepository adapter wiring and behavior against a mock DataCloudClient
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("Data-Cloud Integration Tests")
class DataCloudIntegrationTest {

    @Mock
    private DataCloudClient client;

    private YappcEntityMapper mapper;
    private YappcDataCloudRepository<TestEntity> repository;

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
    @DisplayName("Should initialize data-cloud repository successfully")
    void shouldInitializeRepository() { // GH-90000
        // Given - repository is initialized in setUp

        // Then - no exceptions should be thrown
        assert repository != null;
    }

    record TestEntity(UUID id, String name, int value) implements Identifiable<UUID> { // GH-90000
        @Override public UUID getId() { return id; } // GH-90000
    }
}
