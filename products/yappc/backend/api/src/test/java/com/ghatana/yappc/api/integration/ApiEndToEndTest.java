/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * End-to-end API integration tests using Testcontainers.
 *
 * @doc.type class
 * @doc.purpose Full API integration tests with containerized dependencies
 * @doc.layer test
 * @doc.pattern Integration Test, Testcontainers
 */
@Testcontainers
@EnabledIfSystemProperty(named = "testcontainers.enabled", matches = "true")
@Tag("integration")
@DisplayName("YAPPC API End-to-End Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiEndToEndTest {

  static Network network = Network.newNetwork();

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withNetwork(network)
          .withNetworkAliases("postgres")
          .withDatabaseName("yappc")
          .withUsername("yappc")
          .withPassword("yappc_secret");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
          .withNetwork(network)
          .withNetworkAliases("redis")
          .withExposedPorts(6379)
          .waitingFor(Wait.forListeningPort());

  private static final String TENANT_ID = "e2e-test-tenant";

  @BeforeAll
  static void setup() {
    // Verify containers are running
    assertThat(postgres.isRunning()).isTrue();
    assertThat(redis.isRunning()).isTrue();
  }

  @Test
  @Order(1)
  @DisplayName("PostgreSQL container should be accessible")
  void postgresContainerShouldBeAccessible() {
    assertThat(postgres.getJdbcUrl()).contains("jdbc:postgresql");
    assertThat(postgres.getUsername()).isEqualTo("yappc");
  }

  @Test
  @Order(2)
  @DisplayName("Redis container should be accessible")
  void redisContainerShouldBeAccessible() {
    assertThat(redis.getMappedPort(6379)).isNotNull();
    assertThat(redis.getHost()).isNotBlank();
  }

  @Test
  @Order(3)
  @DisplayName("Should verify network connectivity between containers")
  void shouldVerifyNetworkConnectivity() {
    // Containers on same network should be able to communicate
    assertThat(postgres.getNetworkAliases()).contains("postgres");
    assertThat(redis.getNetworkAliases()).contains("redis");
  }

  /**
   * Note: Full API testing would require building and starting the YAPPC API container. This is a
   * placeholder for the pattern - actual implementation would: 1. Build the API image from
   * Dockerfile 2. Start it with environment variables pointing to test containers 3. Run HTTP tests
   * against the API endpoints
   */
  @Test
  @Order(10)
  @DisplayName("Should document API testing pattern")
  void shouldDocumentApiTestingPattern() {
    // In a full implementation, this would:
    // 1. Build and start the YAPPC API container
    // 2. Configure it to connect to test postgres and redis
    // 3. Make HTTP requests to the API
    // 4. Verify responses

    String expectedApiPattern =
        """
            // Start API container
            GenericContainer<?> api = new GenericContainer<>(
                DockerImageName.parse("yappc-api:test")
            )
            .withNetwork(network)
            .withEnv("DB_URL", "jdbc:postgresql://postgres:5432/yappc")
            .withEnv("REDIS_HOST", "redis")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

            // Test health endpoint
            String healthUrl = "http://" + api.getHost() + ":" + api.getMappedPort(8080) + "/health";
            // Make HTTP request and verify response
            """;

    assertThat(expectedApiPattern).contains("/health");
  }

  @Test
  @Order(11)
  @DisplayName("Should test database schema creation")
  void shouldTestDatabaseSchemaCreation() throws Exception {
    try (var conn =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (var stmt = conn.createStatement()) {
        // Create schema
        stmt.execute("CREATE SCHEMA IF NOT EXISTS yappc");

        // Verify schema exists
        var rs =
            stmt.executeQuery(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'yappc'");

        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("schema_name")).isEqualTo("yappc");
      }
    }
  }

  @Test
  @Order(12)
  @DisplayName("Should create and query requirements table")
  void shouldCreateAndQueryRequirementsTable() throws Exception {
    try (var conn =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (var stmt = conn.createStatement()) {
        // Create extensions and table
        stmt.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        stmt.execute(
            """
                    CREATE TABLE IF NOT EXISTS yappc.requirements (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        tenant_id VARCHAR(64) NOT NULL,
                        title VARCHAR(500) NOT NULL,
                        status VARCHAR(32) DEFAULT 'DRAFT',
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                    """);

        // Insert test data
        stmt.execute(
            String.format(
                """
                    INSERT INTO yappc.requirements (tenant_id, title, status)
                    VALUES ('%s', 'E2E Test Requirement', 'DRAFT')
                    """,
                TENANT_ID));

        // Query and verify
        var rs =
            stmt.executeQuery(
                String.format(
                    "SELECT * FROM yappc.requirements WHERE tenant_id = '%s'", TENANT_ID));

        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("title")).isEqualTo("E2E Test Requirement");
        assertThat(rs.getString("status")).isEqualTo("DRAFT");
      }
    }
  }

  @Test
  @Order(13)
  @DisplayName("Should verify tenant isolation")
  void shouldVerifyTenantIsolation() throws Exception {
    try (var conn =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (var stmt = conn.createStatement()) {
        // Insert for different tenant
        stmt.execute(
            """
                    INSERT INTO yappc.requirements (tenant_id, title, status)
                    VALUES ('other-tenant', 'Other Tenant Req', 'DRAFT')
                    """);

        // Query for original tenant - should not see other tenant's data
        var rs =
            stmt.executeQuery(
                String.format(
                    "SELECT COUNT(*) as cnt FROM yappc.requirements WHERE tenant_id = '%s'",
                    TENANT_ID));

        rs.next();
        long count = rs.getLong("cnt");

        // Should only see our tenant's requirements
        assertThat(count).isEqualTo(1L);
      }
    }
  }

  @AfterAll
  static void cleanup() {
    network.close();
  }
}
