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
    void shouldStartPostgresFixture() { // GH-90000
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable(); // GH-90000
        } catch (RuntimeException ignored) { // GH-90000
            dockerAvailable = false;
        }

        Assumptions.assumeTrue(dockerAvailable, "Docker not available for Testcontainers-based integration tests"); // GH-90000

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start(); // GH-90000

            assertThat(postgres.isRunning()).isTrue(); // GH-90000
            assertThat(postgres.getJdbcUrl()).contains("jdbc:postgresql://");
            assertThat(postgres.getUsername()).isNotBlank(); // GH-90000
            assertThat(postgres.getPassword()).isNotBlank(); // GH-90000
        }
    }
}

