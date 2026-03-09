/**
 * Redis Cache Service for FlashIt Gateway
 *
 * Provides a typed caching layer with TTL, pattern invalidation,
 * and automatic serialization. Falls back gracefully if Redis is unavailable.
 *
 * @doc.type service
 * @doc.purpose Redis-backed caching with graceful fallback
 * @doc.layer product
 * @doc.pattern Service
 */

import { createClient, RedisClientType } from 'redis';

const REDIS_URL = process.env.REDIS_URL || 'redis://localhost:6383';
const DEFAULT_TTL = 300; // 5 minutes
const CACHE_PREFIX = 'flashit:';

let client: RedisClientType | null = null;
let isConnected = false;

/**
 * Initialize the Redis client connection.
 * Call once during server startup.
 */
export async function initCache(): Promise<void> {
  try {
    client = createClient({ url: REDIS_URL });

    client.on('error', (err) => {
      console.error('[Cache] Redis error:', err.message);
      isConnected = false;
    });

    client.on('connect', () => {
      isConnected = true;
      console.log('[Cache] Redis connected');
    });

    client.on('reconnecting', () => {
      console.log('[Cache] Redis reconnecting...');
    });

    await client.connect();
    isConnected = true;
  } catch (err) {
    console.warn('[Cache] Redis unavailable, caching disabled:', (err as Error).message);
    client = null;
    isConnected = false;
  }
}

/**
 * Disconnect the Redis client. Call on shutdown.
 */
export async function disconnectCache(): Promise<void> {
  if (client) {
    await client.quit();
    isConnected = false;
  }
}

function key(namespace: string, id: string): string {
  return `${CACHE_PREFIX}${namespace}:${id}`;
}

/**
 * Get a cached value. Returns null if not found or Redis is unavailable.
 */
export async function cacheGet<T>(namespace: string, id: string): Promise<T | null> {
  if (!client || !isConnected) return null;
  try {
    const raw = await client.get(key(namespace, id));
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

/**
 * Set a cached value with optional TTL (in seconds).
 */
export async function cacheSet<T>(
  namespace: string,
  id: string,
  value: T,
  ttlSeconds: number = DEFAULT_TTL
): Promise<void> {
  if (!client || !isConnected) return;
  try {
    await client.set(key(namespace, id), JSON.stringify(value), { EX: ttlSeconds });
  } catch {
    // Silently fail — cache is best-effort
  }
}

/**
 * Delete a specific cached value.
 */
export async function cacheDel(namespace: string, id: string): Promise<void> {
  if (!client || !isConnected) return;
  try {
    await client.del(key(namespace, id));
  } catch {
    // Silently fail
  }
}

/**
 * Invalidate all cached entries in a namespace (e.g., after a mutation).
 */
export async function cacheInvalidateNamespace(namespace: string): Promise<void> {
  if (!client || !isConnected) return;
  try {
    const pattern = `${CACHE_PREFIX}${namespace}:*`;
    let cursor = 0;
    do {
      const result = await client.scan(cursor, { MATCH: pattern, COUNT: 100 });
      cursor = result.cursor;
      if (result.keys.length > 0) {
        await client.del(result.keys);
      }
    } while (cursor !== 0);
  } catch {
    // Silently fail
  }
}

/**
 * Cache-aside pattern: get from cache or compute and cache.
 */
export async function cacheGetOrSet<T>(
  namespace: string,
  id: string,
  computeFn: () => Promise<T>,
  ttlSeconds: number = DEFAULT_TTL
): Promise<T> {
  const cached = await cacheGet<T>(namespace, id);
  if (cached !== null) return cached;

  const fresh = await computeFn();
  await cacheSet(namespace, id, fresh, ttlSeconds);
  return fresh;
}

// ----- High-level caching helpers for FlashIt entities -----

/**
 * Cache a user profile. TTL: 5 minutes.
 */
export async function cacheUserProfile<T>(userId: string, profile: T): Promise<void> {
  await cacheSet('user', userId, profile, 300);
}

export async function getCachedUserProfile<T>(userId: string): Promise<T | null> {
  return cacheGet<T>('user', userId);
}

export async function invalidateUserCache(userId: string): Promise<void> {
  await cacheDel('user', userId);
}

/**
 * Cache sphere list for a user. TTL: 2 minutes.
 */
export async function cacheUserSpheres<T>(userId: string, spheres: T): Promise<void> {
  await cacheSet('spheres', userId, spheres, 120);
}

export async function getCachedUserSpheres<T>(userId: string): Promise<T | null> {
  return cacheGet<T>('spheres', userId);
}

export async function invalidateSpheresCache(userId: string): Promise<void> {
  await cacheDel('spheres', userId);
}

/**
 * Cache search results. TTL: 1 minute.
 */
export async function cacheSearchResults<T>(queryHash: string, results: T): Promise<void> {
  await cacheSet('search', queryHash, results, 60);
}

export async function getCachedSearchResults<T>(queryHash: string): Promise<T | null> {
  return cacheGet<T>('search', queryHash);
}

/**
 * Cache analytics data. TTL: 10 minutes.
 */
export async function cacheAnalytics<T>(userId: string, data: T): Promise<void> {
  await cacheSet('analytics', userId, data, 600);
}

export async function getCachedAnalytics<T>(userId: string): Promise<T | null> {
  return cacheGet<T>('analytics', userId);
}

export async function invalidateAnalyticsCache(userId: string): Promise<void> {
  await cacheDel('analytics', userId);
}

/**
 * Check if cache service is healthy.
 */
export function isCacheHealthy(): boolean {
  return isConnected;
}
