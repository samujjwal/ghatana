/**
 * TestContainers Integration Setup
 *
 * Provides containerized PostgreSQL and Redis for integration tests.
 * Ensures tests run against real database and cache instances.
 *
 * @doc.type module
 * @doc.purpose TestContainers setup for PostgreSQL and Redis
 * @doc.layer test
 * @doc.pattern Test Infrastructure
 */

import { GenericContainer, StartedTestContainer, Wait } from "testcontainers";
import { Client } from "pg";
import { createClient, RedisClientType } from "redis";

/**
 * Container configuration
 */
const POSTGRES_IMAGE = "postgres:16-alpine";
const REDIS_IMAGE = "redis:7-alpine";

const POSTGRES_PORT = 5432;
const REDIS_PORT = 6379;

/**
 * Test container environment
 */
export interface ContainerEnvironment {
  postgres: {
    container: StartedTestContainer;
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
    getConnectionString: () => string;
    getClient: () => Promise<Client>;
  };
  redis: {
    container: StartedTestContainer;
    host: string;
    port: number;
    getClient: () => Promise<RedisClientType>;
  };
  cleanup: () => Promise<void>;
}

/**
 * TestContainers manager for integration tests
 */
export class TestContainersManager {
  private postgresContainer?: StartedTestContainer;
  private redisContainer?: StartedTestContainer;
  private postgresClient?: Client;
  private redisClient?: RedisClientType;

  /**
   * Start all required containers
   */
  async start(): Promise<ContainerEnvironment> {
    // Start containers in parallel for faster startup
    const [postgresContainer, redisContainer] = await Promise.all([
      this.startPostgres(),
      this.startRedis(),
    ]);

    this.postgresContainer = postgresContainer;
    this.redisContainer = redisContainer;

    const postgresHost = postgresContainer.getHost();
    const postgresPort = postgresContainer.getMappedPort(POSTGRES_PORT);
    const redisHost = redisContainer.getHost();
    const redisPort = redisContainer.getMappedPort(REDIS_PORT);

    const dbConfig = {
      host: postgresHost,
      port: postgresPort,
      database: "tutorputor_test",
      username: "testuser",
      password: "testpass",
    };

    // Wait for PostgreSQL to be ready
    await this.waitForPostgres(dbConfig);

    return {
      postgres: {
        container: postgresContainer,
        ...dbConfig,
        getConnectionString: () =>
          `postgresql://${dbConfig.username}:${dbConfig.password}@${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`,
        getClient: () => this.createPostgresClient(dbConfig),
      },
      redis: {
        container: redisContainer,
        host: redisHost,
        port: redisPort,
        getClient: () => this.createRedisClient(redisHost, redisPort),
      },
      cleanup: () => this.cleanup(),
    };
  }

  /**
   * Start PostgreSQL container
   */
  private async startPostgres(): Promise<StartedTestContainer> {
    console.log("[TestContainers] Starting PostgreSQL...");

    const container = await new GenericContainer(POSTGRES_IMAGE)
      .withExposedPorts(POSTGRES_PORT)
      .withEnvironment({
        POSTGRES_USER: "testuser",
        POSTGRES_PASSWORD: "testpass",
        POSTGRES_DB: "tutorputor_test",
      })
      .withHealthCheck({
        test: ["CMD-SHELL", "pg_isready -U testuser -d tutorputor_test"],
        interval: 1000,
        timeout: 3000,
        retries: 10,
      })
      .withWaitStrategy(Wait.forHealthCheck())
      .start();

    console.log("[TestContainers] PostgreSQL started on port", container.getMappedPort(POSTGRES_PORT));
    return container;
  }

  /**
   * Start Redis container
   */
  private async startRedis(): Promise<StartedTestContainer> {
    console.log("[TestContainers] Starting Redis...");

    const container = await new GenericContainer(REDIS_IMAGE)
      .withExposedPorts(REDIS_PORT)
      .withHealthCheck({
        test: ["CMD", "redis-cli", "ping"],
        interval: 1000,
        timeout: 3000,
        retries: 10,
      })
      .withWaitStrategy(Wait.forHealthCheck())
      .start();

    console.log("[TestContainers] Redis started on port", container.getMappedPort(REDIS_PORT));
    return container;
  }

  /**
   * Wait for PostgreSQL to be ready
   */
  private async waitForPostgres(config: {
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
  }): Promise<void> {
    const client = new Client({
      host: config.host,
      port: config.port,
      database: config.database,
      user: config.username,
      password: config.password,
    });

    let attempts = 0;
    const maxAttempts = 30;

    while (attempts < maxAttempts) {
      try {
        await client.connect();
        await client.query("SELECT 1");
        await client.end();
        console.log("[TestContainers] PostgreSQL is ready");
        return;
      } catch (error) {
        attempts++;
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }

    throw new Error("PostgreSQL failed to become ready in time");
  }

  /**
   * Create PostgreSQL client
   */
  private async createPostgresClient(config: {
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
  }): Promise<Client> {
    const client = new Client({
      host: config.host,
      port: config.port,
      database: config.database,
      user: config.username,
      password: config.password,
    });
    await client.connect();
    return client;
  }

  /**
   * Create Redis client
   */
  private async createRedisClient(
    host: string,
    port: number,
  ): Promise<RedisClientType> {
    const client = createClient({
      url: `redis://${host}:${port}`,
    });
    await client.connect();
    return client as RedisClientType;
  }

  /**
   * Cleanup all containers
   */
  async cleanup(): Promise<void> {
    console.log("[TestContainers] Cleaning up...");

    if (this.postgresClient) {
      await this.postgresClient.end().catch(() => {});
    }
    if (this.redisClient) {
      await this.redisClient.quit().catch(() => {});
    }

    const stopPromises: Promise<void>[] = [];

    if (this.postgresContainer) {
      stopPromises.push(
        this.postgresContainer.stop().then(() => {
          console.log("[TestContainers] PostgreSQL stopped");
        }),
      );
    }

    if (this.redisContainer) {
      stopPromises.push(
        this.redisContainer.stop().then(() => {
          console.log("[TestContainers] Redis stopped");
        }),
      );
    }

    await Promise.all(stopPromises);
    console.log("[TestContainers] Cleanup complete");
  }
}

/**
 * Global container manager singleton
 */
export const containerManager = new TestContainersManager();
