package com.ghatana.aep.registry.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AEP Registry Postgres Fixture Smoke Test")
class AepRegistryPostgresFixtureSmokeTest {

    @Test
    @DisplayName("should start a postgres fixture for registry integration tests")
    void shouldStartPostgresFixture() { 
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable(); 
        } catch (RuntimeException ignored) { 
            dockerAvailable = false;
        }

        Assumptions.assumeTrue(dockerAvailable, "Docker not available for Testcontainers-based integration tests"); 

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start(); 

            assertThat(postgres.isRunning()).isTrue(); 
            assertThat(postgres.getJdbcUrl()).contains("jdbc:postgresql://");
            assertThat(postgres.getUsername()).isNotBlank(); 
            assertThat(postgres.getPassword()).isNotBlank(); 
        }
    }
}

