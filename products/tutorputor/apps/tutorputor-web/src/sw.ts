/**
 * TutorPutor Service Worker
 *
 * Enables offline access through caching strategies:
 * - Cache-first for static assets
 * - Network-first for API calls with fallback
 * - Stale-while-revalidate for dynamic content
 * - IndexedDB-backed offline mutation queue (using centralized offlineSyncIndexedDB module)
 *
 * @doc.type service-worker
 * @doc.purpose PWA offline support
 * @doc.layer product
 * @doc.pattern ServiceWorker
 */

/// <reference lib="webworker" />

import { logger } from './components/utils/logger';
import {
  queueOfflineMutation,
  loadPendingMutations,
  markMutationSynced,
  markMutationFailed,
  type OfflineMutationType,
  type OfflineMutationRequest,
  type OfflineMutationRecord,
} from './offline/offlineSyncIndexedDB';

/**
 * Infer mutation type from URL
 */
function inferMutationType(url: string): OfflineMutationType {
  if (url.includes('/progress')) return 'module.progress';
  if (url.includes('/simulations')) return 'simulation.capture';
  if (url.includes('/assessments')) return 'assessment.attempt';
  if (url.includes('/telemetry')) return 'telemetry.batch';
  return 'module.progress'; // Default to progress for unknown types
}

/**
 * Queue a mutation using the centralized IndexedDB module
 */
async function queueMutation(
  url: string,
  method: string,
  headers: Headers,
  body: string | null,
): Promise<void> {
  // Extract auth header for replay
  const authHeader = headers.get('authorization') || '';
  const contentType = headers.get('content-type') || 'application/json';
  
  // Generate idempotency key from URL + method + body hash
  const bodyHash = body ? btoa(body).slice(0, 16) : 'no-body';
  const idempotencyKey = `${method}:${url}:${bodyHash}`;
  
  const request: OfflineMutationRequest = {
    url,
    method,
    headers: {
      authorization: authHeader,
      'content-type': contentType,
    },
    body,
    idempotencyKey,
    conflictPolicyVersion: 'offline-sync-v1',
  };
  
  const type = inferMutationType(url);
  const payload = body ? JSON.parse(body) : {};
  
  await queueOfflineMutation(type, payload, request);
}

/**
 * Replay a single mutation
 */
async function replayMutation(record: OfflineMutationRecord): Promise<boolean> {
  const { request } = record;
  
  try {
    const replayRequest = new Request(request.url, {
      method: request.method,
      headers: request.headers,
      body: request.body,
    });
    
    const response = await fetch(replayRequest);
    
    if (response.ok) {
      await markMutationSynced(record.id!);
      return true;
    } else {
      throw new Error(`Replay failed with status ${response.status}`);
    }
  } catch (error) {
    logger.error('[SW] Failed to replay mutation:', { id: record.id, error });
    await markMutationFailed(record.id!, error instanceof Error ? error.message : String(error));
    return false;
  }
}

// Service worker global scope types
declare const self: ServiceWorkerGlobalScope & typeof globalThis;

interface ServiceWorkerGlobalScope {
  readonly caches: CacheStorage;
  readonly clients: Clients;
  readonly registration: ServiceWorkerRegistration;
  skipWaiting(): Promise<void>;
  addEventListener(type: string, listener: EventListener): void;
}

interface Clients {
  claim(): Promise<void>;
  get(id: string): Promise<Client | undefined>;
  matchAll(options?: { includeUncontrolled?: boolean; type?: string }): Promise<Client[]>;
}

interface Client {
  readonly id: string;
  readonly type: string;
  readonly url: string;
  postMessage(message: unknown): void;
  focus?(): Promise<Client>;
}

type ExtendableEvent = Event & {
  waitUntil(promise: Promise<unknown>): void;
};

type FetchEvent = ExtendableEvent & {
  request: Request;
  respondWith(response: Promise<Response> | Response): void;
};

type SWMessageEvent = ExtendableEvent & {
  data: unknown;
  source: Client | null;
};

const CACHE_NAME = 'tutorputor-v1';
const STATIC_CACHE_NAME = 'tutorputor-static-v1';
const DYNAMIC_CACHE_NAME = 'tutorputor-dynamic-v1';
const OFFLINE_PAGE = '/offline.html';

/**
 * Static assets to cache on install.
 */
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/offline.html',
  '/manifest.json',
  '/icons/icon-192x192.png',
  '/icons/icon-512x512.png',
];

/**
 * API routes that should be cached with network-first strategy.
 */
const CACHEABLE_API_ROUTES = [
  '/api/modules',
  '/api/curriculum',
  '/api/progress',
  '/api/v1/modules',
  '/api/v1/learning/dashboard',
  '/api/v1/telemetry/learning/batch',
];

const OFFLINE_MUTATION_ROUTES = [
  '/api/progress',
  '/api/progress/complete-lesson',
  '/api/v1/simulations',
  '/api/v1/assessments',
  '/api/v1/telemetry/learning/batch',
  '/api/v1/ai/tutor/query',
];

/**
 * Install event - cache static assets.
 */
self.addEventListener('install', (event: ExtendableEvent) => {
  event.waitUntil(
    caches
      .open(STATIC_CACHE_NAME)
      .then((cache) => {
        logger.info('[SW] Caching static assets');
        return cache.addAll(STATIC_ASSETS);
      })
      .then(() => self.skipWaiting())
  );
});

/**
 * Activate event - clean up old caches.
 */
self.addEventListener('activate', (event) => {
  const activateEvent = event as ExtendableEvent;
  activateEvent.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => {
            return (
              name.startsWith('tutorputor-') &&
              name !== STATIC_CACHE_NAME &&
              name !== DYNAMIC_CACHE_NAME
            );
          })
          .map((name) => {
            logger.info('[SW] Deleting old cache:', name);
            return caches.delete(name);
          })
      );
    }).then(() => self.clients.claim())
  );
});

/**
 * Fetch event - implement caching strategies.
 */
self.addEventListener('fetch', (event) => {
  const fetchEvent = event as FetchEvent;
  const { request } = fetchEvent;
  const url = new URL(request.url);

  // Skip cross-origin requests
  if (url.origin !== self.location.origin) {
    return;
  }

  if (request.method !== 'GET') {
    if (OFFLINE_MUTATION_ROUTES.some((route) => url.pathname.startsWith(route))) {
      fetchEvent.respondWith(networkMutationStrategy(request));
    }
    return;
  }

  // API requests - network first with cache fallback
  if (url.pathname.startsWith('/api/')) {
    fetchEvent.respondWith(networkFirstStrategy(request));
    return;
  }

  // Static assets - cache first
  if (isStaticAsset(url.pathname)) {
    fetchEvent.respondWith(cacheFirstStrategy(request));
    return;
  }

  // Dynamic content - stale while revalidate
  fetchEvent.respondWith(staleWhileRevalidateStrategy(request));
});

/**
 * Check if the request is for a static asset.
 */
function isStaticAsset(pathname: string): boolean {
  const staticExtensions = ['.js', '.css', '.png', '.jpg', '.jpeg', '.svg', '.woff', '.woff2'];
  return staticExtensions.some((ext) => pathname.endsWith(ext));
}

async function networkMutationStrategy(request: Request): Promise<Response> {
  try {
    return await fetch(request);
  } catch (networkError) {
    // Persist the mutation to IndexedDB for later replay
    const body = request.method !== 'GET' && request.method !== 'HEAD'
      ? await request.clone().text()
      : null;
    
    try {
      await queueMutation(
        request.url,
        request.method,
        request.headers,
        body,
      );
      
      // Notify clients that mutation was queued
      const clients = await self.clients.matchAll();
      clients.forEach((client) => {
        client.postMessage({
          type: 'OFFLINE_MUTATION_QUEUED',
          payload: {
            url: request.url,
            method: request.method,
            timestamp: Date.now(),
          },
        });
      });

      return new Response(
        JSON.stringify({
          status: 'queued',
          conflictPolicyVersion: 'offline-sync-v1',
          message: 'Offline mutation queued for conflict-aware replay.',
        }),
        {
          status: 202,
          headers: {
            'Content-Type': 'application/json',
            'X-TutorPutor-Offline-Queued': 'true',
          },
        },
      );
    } catch (dbError) {
      logger.error('[SW] Failed to queue mutation:', dbError);
      
      // If IndexedDB fails, return a fallback response
      return new Response(
        JSON.stringify({
          error: 'offline_storage_failed',
          message: 'Failed to queue offline mutation. Please retry when online.',
        }),
        {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        },
      );
    }
  }
}

/**
 * Cache-first strategy: Try cache, fall back to network.
 */
async function cacheFirstStrategy(request: Request): Promise<Response> {
  const cachedResponse = await caches.match(request);
  
  if (cachedResponse) {
    return cachedResponse;
  }

  try {
    const networkResponse = await fetch(request);
    
    if (networkResponse.ok) {
      const cache = await caches.open(STATIC_CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    
    return networkResponse;
  } catch (error) {
    // Return offline page for navigation requests
    if (request.mode === 'navigate') {
      const offlinePage = await caches.match(OFFLINE_PAGE);
      if (offlinePage) return offlinePage;
    }
    throw error;
  }
}

/**
 * Network-first strategy: Try network, fall back to cache.
 */
async function networkFirstStrategy(request: Request): Promise<Response> {
  try {
    const networkResponse = await fetch(request);
    
    // Cache successful API responses
    if (networkResponse.ok && isCacheableApi(request.url)) {
      const cache = await caches.open(DYNAMIC_CACHE_NAME);
      cache.put(request, networkResponse.clone());
    }
    
    return networkResponse;
  } catch (error) {
    const cachedResponse = await caches.match(request);
    
    if (cachedResponse) {
      // Add header to indicate this is a cached response
      const headers = new Headers(cachedResponse.headers);
      headers.set('X-From-Cache', 'true');
      
      return new Response(cachedResponse.body, {
        status: cachedResponse.status,
        statusText: cachedResponse.statusText,
        headers,
      });
    }
    
    // Return a JSON error response for API requests
    return new Response(
      JSON.stringify({
        error: 'offline',
        message: 'You are offline and this content is not cached.',
      }),
      {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      }
    );
  }
}

/**
 * Stale-while-revalidate: Return cached, update in background.
 */
async function staleWhileRevalidateStrategy(request: Request): Promise<Response> {
  const cache = await caches.open(DYNAMIC_CACHE_NAME);
  const cachedResponse = await cache.match(request);

  const fetchPromise = fetch(request)
    .then((networkResponse) => {
      if (networkResponse.ok) {
        cache.put(request, networkResponse.clone());
      }
      return networkResponse;
    })
    .catch(() => {
      // If network fails and no cache, return offline page
      if (request.mode === 'navigate') {
        return caches.match(OFFLINE_PAGE);
      }
      return null;
    });

  // Return cached response immediately, update in background
  return cachedResponse || (await fetchPromise) || new Response('Offline', { status: 503 });
}

/**
 * Check if an API route should be cached.
 */
function isCacheableApi(url: string): boolean {
  const pathname = new URL(url).pathname;
  return CACHEABLE_API_ROUTES.some((route) => pathname.startsWith(route));
}

type SyncEvent = ExtendableEvent & { tag: string };

/**
 * Background sync for offline mutations.
 */
self.addEventListener('sync', (event) => {
  const syncEvent = event as SyncEvent;
  if (syncEvent.tag === 'sync-mutations') {
    syncEvent.waitUntil(syncPendingMutations());
  }
});

/**
 * Sync pending mutations with the server.
 * This is called by the background sync API when the device comes back online.
 */
async function syncPendingMutations(): Promise<void> {
  try {
    logger.info('[SW] Starting offline mutation sync');
    
    const pendingMutations = await loadPendingMutations();
    logger.info(`[SW] Found ${pendingMutations.length} pending mutations to sync`);
    
    let synced = 0;
    let failed = 0;
    
    for (const record of pendingMutations) {
      // Skip mutations that have exceeded retry limit
      if (record.metadata.retryCount >= 3) {
        logger.warn('[SW] Skipping mutation with too many retries:', record.id);
        continue;
      }
      
      const success = await replayMutation(record);
      if (success) {
        synced++;
      } else {
        failed++;
      }
    }
    
    logger.info(`[SW] Sync complete: ${synced} synced, ${failed} failed`);
    
    // Notify all clients about sync completion
    const clients = await self.clients.matchAll();
    clients.forEach((client) => {
      client.postMessage({
        type: 'SYNC_COMPLETED',
        payload: {
          synced,
          failed,
          timestamp: Date.now(),
        },
      });
    });
  } catch (error) {
    logger.error('[SW] Failed to sync mutations:', error);
    
    // Notify clients about sync failure
    const clients = await self.clients.matchAll();
    clients.forEach((client) => {
      client.postMessage({
        type: 'SYNC_FAILED',
        payload: {
          error: error instanceof Error ? error.message : String(error),
          timestamp: Date.now(),
        },
      });
    });
  }
}

type PushEvent = ExtendableEvent & { data: PushMessageData | null };

/**
 * Push notification handling.
 */
self.addEventListener('push', (event) => {
  const pushEvent = event as PushEvent;
  if (!pushEvent.data) return;

  const data = pushEvent.data.json();
  
  pushEvent.waitUntil(
    self.registration.showNotification(data.title || 'TutorPutor', {
      body: data.body,
      icon: '/icons/icon-192x192.png',
      badge: '/icons/badge-72x72.png',
      tag: data.tag || 'tutorputor-notification',
      data: data.data,
    })
  );
});

type NotificationClickEvent = ExtendableEvent & { notification: Notification };

/**
 * Notification click handling.
 */
self.addEventListener('notificationclick', (event) => {
  const notificationEvent = event as NotificationClickEvent;
  notificationEvent.notification.close();

  const urlToOpen = notificationEvent.notification.data?.url || '/';

  notificationEvent.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        // Focus existing window or open new one
        for (const client of clientList) {
          if (client.url === urlToOpen && 'focus' in client) {
            return client.focus();
          }
        }
        // @ts-expect-error - openWindow exists on Clients in service worker context
        return self.clients.openWindow(urlToOpen);
      })
  );
});

/**
 * Message handling from main thread.
 */
self.addEventListener('message', (event: MessageEvent) => {
  const { type, payload } = event.data as { type: string; payload?: { urls?: string[] } };

  switch (type) {
    case 'SKIP_WAITING':
      self.skipWaiting();
      break;

    case 'CACHE_URLS':
      caches.open(DYNAMIC_CACHE_NAME).then((cache) => {
        cache.addAll(payload.urls);
      });
      break;

    case 'CLEAR_CACHE':
      caches.delete(DYNAMIC_CACHE_NAME);
      break;

    default:
      logger.info('[SW] Unknown message type:', type);
  }
});

export {};
