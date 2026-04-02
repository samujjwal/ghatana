/**
 * Testcontainers PostgreSQL Setup
 * @doc.type utility
 * @doc.purpose Configure and manage PostgreSQL containers for integration testing
 * @doc.layer testing
 */

import {
  PostgreSqlContainer,
  StartedPostgreSqlContainer,
} from "@testcontainers/postgresql";

export interface DatabaseConfig {
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
}

let container: StartedPostgreSqlContainer | null = null;

/**
 * Start PostgreSQL container for testing
 */
export async function startPostgreSQLContainer(): Promise<StartedPostgreSqlContainer> {
  if (container) {
    return container;
  }

  container = await new PostgreSqlContainer("postgres:15")
    .withDatabase("test_db")
    .withUsername("postgres")
    .withUserPassword("postgres", "postgres")
    .withExposedPorts(5432)
    .start();

  return container;
}

/**
 * Get database configuration from running container
 */
export async function getDatabaseConfig(): Promise<DatabaseConfig> {
  if (!container) {
    throw new Error("PostgreSQL container not started");
  }

  return {
    host: container.getHost(),
    port: container.getMappedPort(5432),
    database: "test_db",
    user: "postgres",
    password: "postgres",
  };
}

/**
 * Get database connection string
 */
export async function getDatabaseUrl(): Promise<string> {
  const config = await getDatabaseConfig();
  return `postgresql://${config.user}:${config.password}@${config.host}:${config.port}/${config.database}`;
}

/**
 * Stop PostgreSQL container
 */
export async function stopPostgreSQLContainer(): Promise<void> {
  if (container) {
    await container.stop();
    container = null;
  }
}

/**
 * Reset database to clean state
 */
export async function resetDatabase(): Promise<void> {
  if (!container) {
    throw new Error("PostgreSQL container not started");
  }

  // Would drop all tables and reset sequences in actual implementation
}

export default {
  startPostgreSQLContainer,
  getDatabaseConfig,
  getDatabaseUrl,
  stopPostgreSQLContainer,
  resetDatabase,
};
