/**
 * TestContainers Integration Tests
 *
 * Integration tests using real PostgreSQL and Redis containers.
 * Validates database operations, caching, and service integration.
 *
 * @doc.type test
 * @doc.purpose Integration tests with TestContainers
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */

import {
  describe,
  it,
  expect,
  beforeAll,
  afterAll,
  beforeEach,
} from "vitest";
import { containerManager, ContainerEnvironment } from "./TestContainers.setup";

describe("TestContainers Integration Suite", () => {
  let env: ContainerEnvironment;

  beforeAll(async () => {
    // Start containers before all tests
    env = await containerManager.start();
  }, 120000); // 2 minute timeout for container startup

  afterAll(async () => {
    // Cleanup containers after all tests
    await env.cleanup();
  }, 60000);

  beforeEach(async () => {
    // Clean database before each test
    const client = await env.postgres.getClient();
    try {
      // Truncate all tables
      await client.query(`
        DO $$ DECLARE
          r RECORD;
        BEGIN
          FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
            EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
          END LOOP;
        END $$;
      `);
    } finally {
      await client.end();
    }

    // Clear Redis
    const redisClient = await env.redis.getClient();
    try {
      await redisClient.flushAll();
    } finally {
      await redisClient.quit();
    }
  });

  // ============================================================================
  // PostgreSQL Tests
  // ============================================================================

  describe("PostgreSQL Container", () => {
    it("should connect and execute queries", async () => {
      const client = await env.postgres.getClient();

      try {
        const result = await client.query("SELECT 1 as test_value");
        expect(result.rows[0].test_value).toBe(1);
      } finally {
        await client.end();
      }
    });

    it("should support transactions", async () => {
      const client = await env.postgres.getClient();

      try {
        await client.query("BEGIN");
        await client.query("CREATE TABLE IF NOT EXISTS test_table (id SERIAL PRIMARY KEY, name TEXT)");
        await client.query("INSERT INTO test_table (name) VALUES ($1)", ["test"]);
        await client.query("COMMIT");

        const result = await client.query("SELECT * FROM test_table");
        expect(result.rows).toHaveLength(1);
        expect(result.rows[0].name).toBe("test");
      } finally {
        await client.end();
      }
    });

    it("should enforce connection string format", () => {
      const connString = env.postgres.getConnectionString();
      expect(connString).toMatch(/^postgresql:\/\//);
      expect(connString).toContain(env.postgres.host);
      expect(connString).toContain(String(env.postgres.port));
    });
  });

  // ============================================================================
  // Redis Tests
  // ============================================================================

  describe("Redis Container", () => {
    it("should connect and perform basic operations", async () => {
      const client = await env.redis.getClient();

      try {
        await client.set("test:key", "test-value");
        const value = await client.get("test:key");
        expect(value).toBe("test-value");
      } finally {
        await client.quit();
      }
    });

    it("should support hash operations", async () => {
      const client = await env.redis.getClient();

      try {
        await client.hSet("test:hash", "field1", "value1");
        await client.hSet("test:hash", "field2", "value2");

        const value = await client.hGet("test:hash", "field1");
        expect(value).toBe("value1");

        const allFields = await client.hGetAll("test:hash");
        expect(allFields).toEqual({ field1: "value1", field2: "value2" });
      } finally {
        await client.quit();
      }
    });

    it("should support list operations", async () => {
      const client = await env.redis.getClient();

      try {
        await client.lPush("test:list", ["item1", "item2", "item3"]);
        const length = await client.lLen("test:list");
        expect(length).toBe(3);

        const items = await client.lRange("test:list", 0, -1);
        expect(items).toEqual(["item3", "item2", "item1"]);
      } finally {
        await client.quit();
      }
    });

    it("should expire keys correctly", async () => {
      const client = await env.redis.getClient();

      try {
        await client.set("expiring:key", "value", { EX: 1 });
        let value = await client.get("expiring:key");
        expect(value).toBe("value");

        // Wait for expiration
        await new Promise((resolve) => setTimeout(resolve, 1100));
        value = await client.get("expiring:key");
        expect(value).toBeNull();
      } finally {
        await client.quit();
      }
    });
  });

  // ============================================================================
  // Service Integration Tests
  // ============================================================================

  describe("Service Integration with Real Databases", () => {
    it("should run platform setup with PostgreSQL", async () => {
      // Set environment variables for the test database
      const originalDbUrl = process.env.DATABASE_URL;
      process.env.DATABASE_URL = env.postgres.getConnectionString();

      try {
        // Import and test platform setup
        const { setupPlatform } = await import(
          "../../services/tutorputor-platform/src/setup.js"
        );

        const { FastifyInstance } = await import("fastify");
        const Fastify = (await import("fastify")).default;

        const app = Fastify({ logger: false });
        await setupPlatform(app, {
          startContentWorker: false,
          jwtSecret: "test-secret",
        });

        // Test a basic health endpoint
        const response = await app.inject({
          method: "GET",
          url: "/health",
        });

        expect(response.statusCode).toBe(200);
        await app.close();
      } finally {
        process.env.DATABASE_URL = originalDbUrl;
      }
    });

    it("should handle concurrent database connections", async () => {
      const clients = await Promise.all(
        Array.from({ length: 5 }, () => env.postgres.getClient()),
      );

      try {
        await Promise.all(
          clients.map(async (client, index) => {
            const result = await client.query("SELECT $1 as idx", [index]);
            expect(result.rows[0].idx).toBe(index);
          }),
        );
      } finally {
        await Promise.all(clients.map((c) => c.end()));
      }
    });

    it("should handle Redis pub/sub", async () => {
      const publisher = await env.redis.getClient();
      const subscriber = await env.redis.getClient();

      try {
        const messagePromise = new Promise<string>((resolve) => {
          subscriber.subscribe("test:channel", (message) => {
            resolve(message as string);
          });
        });

        await publisher.publish("test:channel", "hello world");
        const message = await messagePromise;

        expect(message).toBe("hello world");
      } finally {
        await publisher.quit();
        await subscriber.quit();
      }
    });
  });
});
