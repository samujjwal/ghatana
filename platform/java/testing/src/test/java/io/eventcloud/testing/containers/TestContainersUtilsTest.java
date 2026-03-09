package com.ghatana.platform.testing.containers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
class TestContainersUtilsTest {

    @BeforeAll
    static void setup() {
        // Skip the entire test class if Docker is not available in the environment
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker not available - skipping TestContainers tests");

        // Ensure containers are stopped before starting tests
        TestContainersUtils.stopAll();
        TestContainersUtils.startAll();
    }

    @AfterEach
    void afterEach() {
        // Clean up after each test
        TestContainersUtils.stopAll();
    }

    @AfterAll
    static void tearDown() {
        // Ensure all containers are stopped after all tests
        TestContainersUtils.stopAll();
    }

    @Test
    void shouldCreateAndReusePostgresContainer() {
        // First instance
        PostgreSQLContainer<?> postgres1 = TestContainersUtils.getOrCreatePostgres("test");
        assertThat(postgres1).isNotNull();
        
        // Store connection details
        String jdbcUrl1 = postgres1.getJdbcUrl();
        String username1 = postgres1.getUsername();
        String password1 = postgres1.getPassword();
        
        // Second instance should be the same container
        PostgreSQLContainer<?> postgres2 = TestContainersUtils.getOrCreatePostgres("test");
        assertThat(postgres2).isSameAs(postgres1);
        
        // Verify connection details match
        assertThat(postgres2.getJdbcUrl()).isEqualTo(jdbcUrl1);
        assertThat(postgres2.getUsername()).isEqualTo(username1);
        assertThat(postgres2.getPassword()).isEqualTo(password1);
    }

    @Test
    void shouldCreateDifferentContainersForDifferentNames() {
        // Create two different containers with different names
        PostgreSQLContainer<?> postgres1 = TestContainersUtils.getOrCreatePostgres("test1");
        PostgreSQLContainer<?> postgres2 = TestContainersUtils.getOrCreatePostgres("test2");
        
        // Verify they are different instances
        assertThat(postgres1).isNotSameAs(postgres2);
        
        // Get connection details after ensuring containers are started
        postgres1.start();
        postgres2.start();
        
        String url1 = postgres1.getJdbcUrl();
        String url2 = postgres2.getJdbcUrl();
        
        assertThat(url1).isNotBlank();
        assertThat(url2).isNotBlank();
        
        // The URLs should be different since they're different containers
        assertThat(url1).isNotEqualTo(url2);
        
        // The ports should be different
        int port1 = postgres1.getFirstMappedPort();
        int port2 = postgres2.getFirstMappedPort();
        assertThat(port1).isNotEqualTo(port2);
    }

    @Test
    void shouldGetConnectionUrl() {
        // Create a container and get its connection URL
        PostgreSQLContainer<?> postgres = TestContainersUtils.getOrCreatePostgres("connection-test");
        
        // Get the connection URL and verify its format
        String connectionUrl = TestContainersUtils.getConnectionUrl(postgres);
        int mappedPort = postgres.getFirstMappedPort();
        
        assertThat(connectionUrl)
            .isNotBlank()
            .startsWith("postgresql://")
            .contains(":" + mappedPort);
            
        // Verify the port is a valid number
        assertThat(mappedPort).isPositive();
    }
}
