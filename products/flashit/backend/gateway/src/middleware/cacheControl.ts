/**
 * Cache Control Middleware for Flashit Web API
 * HTTP caching headers and response caching
 *
 * @doc.type middleware
 * @doc.purpose HTTP cache control and response caching
 * @doc.layer product
 * @doc.pattern Middleware
 */

import {
  FastifyInstance,
  FastifyRequest,
  FastifyReply,
  FastifyPluginAsync,
} from 'fastify';
import fp from 'fastify-plugin';
import crypto from 'crypto';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type CacheDirective =
  | 'public'
  | 'private'
  | 'no-cache'
  | 'no-store'
  | 'must-revalidate'
  | 'proxy-revalidate'
  | 'immutable';

export interface CacheControlOptions {
  directives: CacheDirective[];
  maxAge?: number;
  sMaxAge?: number;
  staleWhileRevalidate?: number;
  staleIfError?: number;
}

export interface ResponseCacheConfig {
  enabled: boolean;
  ttl: number;
  maxEntries: number;
  keyGenerator?: (req: FastifyRequest) => string;
  shouldCache?: (req: FastifyRequest, reply: FastifyReply) => boolean;
  tags?: string[];
}

export interface CacheEntry {
  body: string;
  headers: Record<string, string>;
  statusCode: number;
  etag: string;
  createdAt: number;
  expiresAt: number;
  tags: string[];
}

export interface CacheStats {
  hits: number;
  misses: number;
  hitRate: number;
  entries: number;
  evictions: number;
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_CACHE_TTL = 300; // 5 minutes
const DEFAULT_MAX_ENTRIES = 1000;

// Route-specific cache configurations
const ROUTE_CACHE_CONFIGS: Record<string, CacheControlOptions> = {
  // Static assets
  '/static/*': {
    directives: ['public', 'immutable'],
    maxAge: 31536000, // 1 year
  },
  // API - user-specific data
  '/api/moments/*': {
    directives: ['private', 'must-revalidate'],
    maxAge: 60,
  },
  '/api/spheres': {
    directives: ['private'],
    maxAge: 300,
  },
  // API - shared data
  '/api/templates': {
    directives: ['public'],
    maxAge: 3600,
    sMaxAge: 86400,
  },
  // Health checks
  '/health': {
    directives: ['no-store'],
  },
};

// ============================================================================
// In-Memory Response Cache
// ============================================================================

class ResponseCache {
  private cache: Map<string, CacheEntry> = new Map();
  private maxEntries: number;
  private stats: CacheStats = {
    hits: 0,
    misses: 0,
    hitRate: 0,
    entries: 0,
    evictions: 0,
  };

  constructor(maxEntries: number = DEFAULT_MAX_ENTRIES) {
    this.maxEntries = maxEntries;
  }

  get(key: string): CacheEntry | null {
    const entry = this.cache.get(key);

    if (!entry) {
      this.stats.misses++;
      this.updateHitRate();
      return null;
    }

    // Check expiration
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      this.stats.misses++;
      this.stats.entries = this.cache.size;
      this.updateHitRate();
      return null;
    }

    this.stats.hits++;
    this.updateHitRate();

    // Move to end (LRU)
    this.cache.delete(key);
    this.cache.set(key, entry);

    return entry;
  }

  set(key: string, entry: CacheEntry): void {
    // Evict if at capacity
    if (this.cache.size >= this.maxEntries && !this.cache.has(key)) {
      const firstKey = this.cache.keys().next().value;
      if (firstKey) {
        this.cache.delete(firstKey);
        this.stats.evictions++;
      }
    }

    this.cache.set(key, entry);
    this.stats.entries = this.cache.size;
  }

  invalidate(key: string): boolean {
    const deleted = this.cache.delete(key);
    this.stats.entries = this.cache.size;
    return deleted;
  }

  invalidateByTags(tags: string[]): number {
    let count = 0;
    for (const [key, entry] of this.cache.entries()) {
      if (tags.some((tag) => entry.tags.includes(tag))) {
        this.cache.delete(key);
        count++;
      }
    }
    this.stats.entries = this.cache.size;
    return count;
  }

  invalidateByPattern(pattern: string): number {
    const regex = new RegExp(pattern);
    let count = 0;
    for (const key of this.cache.keys()) {
      if (regex.test(key)) {
        this.cache.delete(key);
        count++;
      }
    }
    this.stats.entries = this.cache.size;
    return count;
  }

  clear(): void {
    this.cache.clear();
    this.stats.entries = 0;
  }

  prune(): number {
    const now = Date.now();
    let count = 0;
    for (const [key, entry] of this.cache.entries()) {
      if (now > entry.expiresAt) {
        this.cache.delete(key);
        count++;
      }
    }
    this.stats.entries = this.cache.size;
    return count;
  }

  getStats(): CacheStats {
    return { ...this.stats };
  }

  private updateHitRate(): void {
    const total = this.stats.hits + this.stats.misses;
    this.stats.hitRate = total > 0 ? this.stats.hits / total : 0;
  }
}

// ============================================================================
// Cache Control Helpers
// ============================================================================

/**
 * Generate Cache-Control header value
 */
function buildCacheControlHeader(options: CacheControlOptions): string {
  const parts: string[] = [...options.directives];

  if (options.maxAge !== undefined) {
    parts.push(`max-age=${options.maxAge}`);
  }

  if (options.sMaxAge !== undefined) {
    parts.push(`s-maxage=${options.sMaxAge}`);
  }

  if (options.staleWhileRevalidate !== undefined) {
    parts.push(`stale-while-revalidate=${options.staleWhileRevalidate}`);
  }

  if (options.staleIfError !== undefined) {
    parts.push(`stale-if-error=${options.staleIfError}`);
  }

  return parts.join(', ');
}

/**
 * Generate ETag from content
 */
function generateETag(content: string): string {
  const hash = crypto.createHash('md5').update(content).digest('hex');
  return `"${hash}"`;
}

/**
 * Generate cache key from request
 */
function defaultKeyGenerator(req: FastifyRequest): string {
  const url = req.url;
  const userId = (req as unknown as { userId?: string }).userId || 'anonymous';
  return `${userId}:${url}`;
}

/**
 * Match route pattern
 */
function matchRoute(url: string, pattern: string): boolean {
  if (pattern.endsWith('*')) {
    const prefix = pattern.slice(0, -1);
    return url.startsWith(prefix);
  }
  return url === pattern;
}

/**
 * Get cache config for route
 */
function getRouteCacheConfig(url: string): CacheControlOptions | null {
  for (const [pattern, config] of Object.entries(ROUTE_CACHE_CONFIGS)) {
    if (matchRoute(url, pattern)) {
      return config;
    }
  }
  return null;
}

// ============================================================================
// Fastify Plugin
// ============================================================================

const responseCache = new ResponseCache(DEFAULT_MAX_ENTRIES);

// Prune cache periodically
setInterval(() => {
  responseCache.prune();
}, 60000); // Every minute

const cacheControlPlugin: FastifyPluginAsync<ResponseCacheConfig> = async (
  fastify: FastifyInstance,
  options: ResponseCacheConfig
) => {
  const config: ResponseCacheConfig = {
    enabled: true,
    ttl: DEFAULT_CACHE_TTL,
    maxEntries: DEFAULT_MAX_ENTRIES,
    ...options,
  };

  const keyGenerator = config.keyGenerator || defaultKeyGenerator;

  // Decorate fastify with cache utilities
  fastify.decorate('responseCache', responseCache);
  fastify.decorate('invalidateCache', (pattern: string) => {
    return responseCache.invalidateByPattern(pattern);
  });
  fastify.decorate('invalidateCacheByTags', (tags: string[]) => {
    return responseCache.invalidateByTags(tags);
  });

  // Add cache control headers hook
  fastify.addHook('onSend', async (request, reply, payload) => {
    // Skip if already has Cache-Control
    if (reply.getHeader('cache-control')) {
      return payload;
    }

    // Get route-specific config
    const routeConfig = getRouteCacheConfig(request.url);

    if (routeConfig) {
      reply.header('Cache-Control', buildCacheControlHeader(routeConfig));

      // Add ETag for cacheable responses
      if (
        payload &&
        typeof payload === 'string' &&
        !routeConfig.directives.includes('no-store')
      ) {
        const etag = generateETag(payload);
        reply.header('ETag', etag);

        // Check If-None-Match
        const ifNoneMatch = request.headers['if-none-match'];
        if (ifNoneMatch === etag) {
          reply.code(304);
          return null;
        }
      }
    }

    return payload;
  });

  // Response caching middleware
  if (config.enabled) {
    fastify.addHook('preHandler', async (request, reply) => {
      // Only cache GET requests
      if (request.method !== 'GET') return;

      // Check if should cache
      if (config.shouldCache && !config.shouldCache(request, reply)) {
        return;
      }

      const cacheKey = keyGenerator(request);
      const cached = responseCache.get(cacheKey);

      if (cached) {
        // Check If-None-Match
        const ifNoneMatch = request.headers['if-none-match'];
        if (ifNoneMatch === cached.etag) {
          reply.code(304).send();
          return;
        }

        // Serve from cache
        for (const [key, value] of Object.entries(cached.headers)) {
          reply.header(key, value);
        }
        reply.header('X-Cache', 'HIT');
        reply.code(cached.statusCode).send(cached.body);
      }
    });

    fastify.addHook('onSend', async (request, reply, payload) => {
      // Only cache successful GET requests
      if (request.method !== 'GET') return payload;
      if (reply.statusCode < 200 || reply.statusCode >= 300) return payload;

      // Check if should cache
      if (config.shouldCache && !config.shouldCache(request, reply)) {
        return payload;
      }

      // Skip if already cached
      if (reply.getHeader('x-cache') === 'HIT') return payload;

      const cacheKey = keyGenerator(request);
      const body = typeof payload === 'string' ? payload : JSON.stringify(payload);
      const etag = generateETag(body);
      const now = Date.now();

      const entry: CacheEntry = {
        body,
        headers: {
          'content-type': reply.getHeader('content-type') as string || 'application/json',
        },
        statusCode: reply.statusCode,
        etag,
        createdAt: now,
        expiresAt: now + config.ttl * 1000,
        tags: config.tags || [],
      };

      responseCache.set(cacheKey, entry);
      reply.header('X-Cache', 'MISS');
      reply.header('ETag', etag);

      return payload;
    });
  }

  // Cache management routes
  fastify.get('/api/cache/stats', async () => {
    return responseCache.getStats();
  });

  fastify.delete('/api/cache/invalidate', async (request: FastifyRequest<{
    Querystring: { pattern?: string; tags?: string };
  }>) => {
    const { pattern, tags } = request.query;

    let count = 0;
    if (pattern) {
      count = responseCache.invalidateByPattern(pattern);
    } else if (tags) {
      count = responseCache.invalidateByTags(tags.split(','));
    } else {
      responseCache.clear();
      count = -1; // Indicates full clear
    }

    return { invalidated: count };
  });
};

// ============================================================================
// Exports
// ============================================================================

export const cacheControl = fp(cacheControlPlugin, {
  name: 'cache-control',
  fastify: '4.x',
});

/**
 * Get response cache instance
 */
export function getResponseCache(): ResponseCache {
  return responseCache;
}

/**
 * Manual cache control header setter
 */
export function setCacheControl(
  reply: FastifyReply,
  options: CacheControlOptions
): void {
  reply.header('Cache-Control', buildCacheControlHeader(options));
}

/**
 * No-cache helper
 */
export function noCache(reply: FastifyReply): void {
  reply.header('Cache-Control', 'no-store, no-cache, must-revalidate');
  reply.header('Pragma', 'no-cache');
  reply.header('Expires', '0');
}

/**
 * Cache for specific duration
 */
export function cacheFor(
  reply: FastifyReply,
  seconds: number,
  isPrivate: boolean = true
): void {
  const directive = isPrivate ? 'private' : 'public';
  reply.header('Cache-Control', `${directive}, max-age=${seconds}`);
}

export default cacheControl;
