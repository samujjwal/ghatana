import Redis from "ioredis";
import { getConfig } from "../config/config.js";

let redisClient: Redis | null = null;

interface RedisLifecycleClient extends Redis {
  on(event: "error", handler: (error: Error) => void): RedisLifecycleClient;
  quit(): Promise<unknown>;
}

/**
 * Creates and returns a Redis client instance.
 * Reuses the existing client if already created.
 *
 * @doc.type factory
 * @doc.purpose Create Redis client for caching and session storage
 * @doc.layer core
 * @doc.pattern Singleton
 */
export function getRedisClient(redisUrl?: string): Redis {
  if (redisClient) {
    return redisClient;
  }

  const config = getConfig();
  const url = redisUrl || config.REDIS_URL;

  redisClient = new Redis(url);

  (redisClient as RedisLifecycleClient).on("error", (err: Error) => {
    console.error("Redis connection error:", err);
  });

  return redisClient;
}

/**
 * Closes the Redis client connection.
 *
 * @doc.type function
 * @doc.purpose Close Redis client connection gracefully
 * @doc.layer core
 * @doc.pattern Cleanup
 */
export async function closeRedisClient(): Promise<void> {
  if (redisClient) {
    await (redisClient as RedisLifecycleClient).quit();
    redisClient = null;
  }
}

/**
 * Returns the current Redis client without creating a new one.
 * Returns null if no client has been initialized.
 *
 * @doc.type function
 * @doc.purpose Get existing Redis client instance
 * @doc.layer core
 * @doc.pattern Accessor
 */
export function getExistingRedisClient(): Redis | null {
  return redisClient;
}
