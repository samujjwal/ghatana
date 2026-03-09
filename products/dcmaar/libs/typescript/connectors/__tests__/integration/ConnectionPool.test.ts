import { ConnectionPool } from '../../src/pooling/ConnectionPool';
import { TimeoutError, ResourceExhaustedError } from '../../src/errors/ConnectorErrors';

describe('ConnectionPool Integration Tests', () => {
  interface MockConnection {
    id: string;
    isValid: boolean;
    isClosed: boolean;
    query: (sql: string) => Promise<any>;
    close: () => Promise<void>;
  }

  let connectionIdCounter = 0;

  function createMockConnection(): MockConnection {
    const id = `conn-${++connectionIdCounter}`;
    const conn: MockConnection = {
      id,
      isValid: true,
      isClosed: false,
      query: async (sql: string) => {
        if (!conn.isValid) {
          throw new Error('Connection is invalid');
        }
        return { rows: [{ id: 1, data: 'test' }] };
      },
      close: async () => {
        conn.isClosed = true;
        conn.isValid = false;
      },
    };
    return conn;
  }

  describe('Basic Operations', () => {
    it('should create and maintain minimum connections', async () => {
      const pool = new ConnectionPool({
        min: 3,
        max: 10,
        create: async () => createMockConnection(),
      });

      // Wait for minimum connections to be created
      await new Promise(resolve => setTimeout(resolve, 100));

      const stats = pool.getStats();
      expect(stats.total).toBe(3);
      expect(stats.available).toBe(3);
      expect(stats.inUse).toBe(0);

      await pool.destroy();
    });

    it('should acquire and release connections', async () => {
      const pool = new ConnectionPool({
        min: 2,
        max: 5,
        create: async () => createMockConnection(),
      });

      const conn1 = await pool.acquire();
      expect(conn1).toBeDefined();
      expect(pool.getStats().inUse).toBe(1);

      const conn2 = await pool.acquire();
      expect(conn2).toBeDefined();
      expect(pool.getStats().inUse).toBe(2);

      await pool.release(conn1);
      expect(pool.getStats().inUse).toBe(1);
      expect(pool.getStats().available).toBe(1);

      await pool.release(conn2);
      expect(pool.getStats().inUse).toBe(0);

      await pool.destroy();
    });

    it('should reuse released connections', async () => {
      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
      });

      const conn1 = await pool.acquire();
      const id1 = conn1.id;
      await pool.release(conn1);

      const conn2 = await pool.acquire();
      const id2 = conn2.id;

      expect(id1).toBe(id2); // Should reuse the same connection

      await pool.release(conn2);
      await pool.destroy();
    });
  });

  describe('Pool Limits', () => {
    it('should not exceed maximum connections', async () => {
      const pool = new ConnectionPool({
        min: 0,
        max: 3,
        acquireTimeout: 1000,
        create: async () => createMockConnection(),
      });

      const conn1 = await pool.acquire();
      const conn2 = await pool.acquire();
      const conn3 = await pool.acquire();

      expect(pool.getStats().total).toBe(3);
      expect(pool.getStats().inUse).toBe(3);

      // This should timeout since pool is full
      await expect(pool.acquire()).rejects.toThrow(TimeoutError);

      await pool.release(conn1);
      await pool.release(conn2);
      await pool.release(conn3);
      await pool.destroy();
    });

    it('should queue requests when pool is full', async () => {
      const pool = new ConnectionPool({
        min: 0,
        max: 2,
        acquireTimeout: 5000,
        create: async () => createMockConnection(),
      });

      const conn1 = await pool.acquire();
      const conn2 = await pool.acquire();

      // Start acquiring a third connection (will wait)
      const acquirePromise = pool.acquire();

      // Release one connection after a delay
      setTimeout(async () => {
        await pool.release(conn1);
      }, 100);

      // Should get the released connection
      const conn3 = await acquirePromise;
      expect(conn3).toBeDefined();

      await pool.release(conn2);
      await pool.release(conn3);
      await pool.destroy();
    });
  });

  describe('Connection Validation', () => {
    it('should validate connections before reuse', async () => {
      let validationCount = 0;

      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
        validate: async (conn) => {
          validationCount++;
          return conn.isValid;
        },
      });

      const conn1 = await pool.acquire();
      await pool.release(conn1);

      // Invalidate the connection
      conn1.isValid = false;

      // Should create a new connection since validation fails
      const conn2 = await pool.acquire();
      expect(conn2.id).not.toBe(conn1.id);
      expect(validationCount).toBeGreaterThan(0);

      await pool.release(conn2);
      await pool.destroy();
    });

    it('should destroy invalid connections', async () => {
      const destroyedConnections: string[] = [];

      const pool = new ConnectionPool({
        min: 0,
        max: 3,
        create: async () => createMockConnection(),
        validate: async (conn) => conn.isValid,
        destroy: async (conn) => {
          destroyedConnections.push(conn.id);
          await conn.close();
        },
      });

      const conn = await pool.acquire();
      const connId = conn.id;
      await pool.release(conn);

      // Invalidate the connection
      conn.isValid = false;

      // Try to acquire again - should destroy invalid connection
      await pool.acquire();

      expect(destroyedConnections).toContain(connId);

      await pool.destroy();
    });
  });

  describe('Connection Reset', () => {
    it('should reset connections before reuse', async () => {
      const resetCalls: string[] = [];

      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
        reset: async (conn) => {
          resetCalls.push(conn.id);
        },
      });

      const conn1 = await pool.acquire();
      const connId = conn1.id;
      await pool.release(conn1);

      const conn2 = await pool.acquire();
      expect(conn2.id).toBe(connId);
      expect(resetCalls).toContain(connId);

      await pool.release(conn2);
      await pool.destroy();
    });
  });

  describe('Idle Connection Eviction', () => {
    it('should evict idle connections', async () => {
      const pool = new ConnectionPool({
        min: 1,
        max: 5,
        idleTimeout: 500,
        evictionRunInterval: 200,
        create: async () => createMockConnection(),
      });

      // Create extra connections
      const conn1 = await pool.acquire();
      const conn2 = await pool.acquire();
      const conn3 = await pool.acquire();

      await pool.release(conn1);
      await pool.release(conn2);
      await pool.release(conn3);

      expect(pool.getStats().total).toBe(3);

      // Wait for eviction to run
      await new Promise(resolve => setTimeout(resolve, 1000));

      // Should have evicted down to minimum
      expect(pool.getStats().total).toBe(1);

      await pool.destroy();
    });
  });

  describe('Use Method', () => {
    it('should automatically acquire and release connections', async () => {
      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
      });

      const result = await pool.use(async (conn) => {
        expect(pool.getStats().inUse).toBe(1);
        return await conn.query('SELECT * FROM users');
      });

      expect(result.rows).toHaveLength(1);
      expect(pool.getStats().inUse).toBe(0);

      await pool.destroy();
    });

    it('should release connection even on error', async () => {
      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
      });

      await expect(
        pool.use(async (conn) => {
          throw new Error('Test error');
        })
      ).rejects.toThrow('Test error');

      expect(pool.getStats().inUse).toBe(0);

      await pool.destroy();
    });
  });

  describe('Concurrent Operations', () => {
    it('should handle concurrent acquire/release correctly', async () => {
      const pool = new ConnectionPool({
        min: 2,
        max: 10,
        create: async () => createMockConnection(),
      });

      const operations = Array.from({ length: 50 }, async (_, i) => {
        return pool.use(async (conn) => {
          await new Promise(resolve => setTimeout(resolve, Math.random() * 50));
          return await conn.query(`SELECT ${i}`);
        });
      });

      const results = await Promise.all(operations);
      expect(results).toHaveLength(50);

      // All connections should be released
      expect(pool.getStats().inUse).toBe(0);

      await pool.destroy();
    });
  });

  describe('Events', () => {
    it('should emit create event when connection is created', async () => {
      const pool = new ConnectionPool({
        min: 0,
        max: 3,
        create: async () => createMockConnection(),
      });

      const createEvents: any[] = [];
      pool.on('create', (event) => createEvents.push(event));

      await pool.acquire();

      expect(createEvents).toHaveLength(1);
      expect(createEvents[0].total).toBe(1);

      await pool.destroy();
    });

    it('should emit acquire and release events', async () => {
      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
      });

      const acquireEvents: any[] = [];
      const releaseEvents: any[] = [];

      pool.on('acquire', (event) => acquireEvents.push(event));
      pool.on('release', (event) => releaseEvents.push(event));

      const conn = await pool.acquire();
      await pool.release(conn);

      expect(acquireEvents.length).toBeGreaterThan(0);
      expect(releaseEvents.length).toBeGreaterThan(0);

      await pool.destroy();
    });
  });

  describe('Error Handling', () => {
    it('should handle connection creation errors', async () => {
      let attemptCount = 0;

      const pool = new ConnectionPool({
        min: 0,
        max: 3,
        create: async () => {
          attemptCount++;
          if (attemptCount <= 2) {
            throw new Error('Connection failed');
          }
          return createMockConnection();
        },
      });

      // First two attempts should fail
      await expect(pool.acquire()).rejects.toThrow('Connection failed');
      await expect(pool.acquire()).rejects.toThrow('Connection failed');

      // Third attempt should succeed
      const conn = await pool.acquire();
      expect(conn).toBeDefined();

      await pool.release(conn);
      await pool.destroy();
    });

    it('should handle validation errors gracefully', async () => {
      const pool = new ConnectionPool({
        min: 1,
        max: 3,
        create: async () => createMockConnection(),
        validate: async (conn) => {
          throw new Error('Validation error');
        },
      });

      // Should handle validation error and try to create new connection
      const conn = await pool.acquire();
      expect(conn).toBeDefined();

      await pool.release(conn);
      await pool.destroy();
    });
  });

  describe('Destroy', () => {
    it('should destroy all connections on pool destroy', async () => {
      const destroyedConnections: string[] = [];

      const pool = new ConnectionPool({
        min: 2,
        max: 5,
        create: async () => createMockConnection(),
        destroy: async (conn) => {
          destroyedConnections.push(conn.id);
          await conn.close();
        },
      });

      await new Promise(resolve => setTimeout(resolve, 100));

      const initialCount = pool.getStats().total;
      await pool.destroy();

      expect(destroyedConnections.length).toBe(initialCount);
      expect(pool.getStats().total).toBe(0);
    });

    it('should reject pending requests on destroy', async () => {
      const pool = new ConnectionPool({
        min: 0,
        max: 1,
        acquireTimeout: 10000,
        create: async () => createMockConnection(),
      });

      const conn = await pool.acquire();

      // Start acquiring another connection (will wait)
      const acquirePromise = pool.acquire();

      // Destroy pool while request is pending
      setTimeout(() => pool.destroy(), 100);

      await expect(acquirePromise).rejects.toThrow('Pool is being destroyed');
    });
  });
});
