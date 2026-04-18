/**
 * Service Worker for Background Sync
 *
 * Enables offline-first capabilities:
 * - Background sync for pending changes
 * - Network request caching
 * - Offline page fallback
 * - Sync retry with exponential backoff
 * - Periodic background sync
 *
 * @doc.type service-worker
 * @doc.purpose Enable offline functionality and background synchronization
 * @doc.layer product
 * @doc.pattern ServiceWorker
 */

/// <reference lib="webworker" />

const CACHE_NAME = "tutorputor-v1";
const SYNC_TAG_PREFIX = "sync-";
const MAX_RETRY_ATTEMPTS = 5;
const RETRY_BACKOFF_MS = 5000;

// Assets to cache on install
const STATIC_ASSETS = [
  "/",
  "/index.html",
  "/offline.html",
  "/assets/main.js",
  "/assets/main.css",
];

// API routes to cache with network-first strategy
const API_ROUTES = ["/api/content", "/api/search", "/api/assessment"];

// Install event - cache static assets
self.addEventListener("install", (event: ExtendableEvent) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(STATIC_ASSETS))
      .then(() => self.skipWaiting())
  );

  console.log("[SW] Installed and cached static assets");
});

// Activate event - clean up old caches
self.addEventListener("activate", (event: ExtendableEvent) => {
  event.waitUntil(
    caches
      .keys()
      .then((cacheNames) =>
        Promise.all(
          cacheNames
            .filter((name) => name !== CACHE_NAME)
            .map((name) => caches.delete(name))
        )
      )
      .then(() => self.clients.claim())
  );

  console.log("[SW] Activated and claimed clients");
});

// Fetch event - cache strategies
self.addEventListener("fetch", (event: FetchEvent) => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET requests for API routes
  if (
    request.method !== "GET" &&
    API_ROUTES.some((route) => url.pathname.startsWith(route))
  ) {
    return;
  }

  // API routes: Network first, then cache
  if (API_ROUTES.some((route) => url.pathname.startsWith(route))) {
    event.respondWith(networkFirst(request));
    return;
  }

  // Static assets: Cache first, then network
  if (STATIC_ASSETS.includes(url.pathname)) {
    event.respondWith(cacheFirst(request));
    return;
  }

  // Default: Network with cache fallback
  event.respondWith(networkWithCacheFallback(request));
});

// Background sync event
self.addEventListener("sync", (event: SyncEvent) => {
  if (event.tag.startsWith(SYNC_TAG_PREFIX)) {
    const syncType = event.tag.replace(SYNC_TAG_PREFIX, "");
    event.waitUntil(handleBackgroundSync(syncType));
  }
});

// Push event - for notifications
self.addEventListener("push", (event: PushEvent) => {
  if (!event.data) return;

  const data = event.data.json();
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      icon: "/icon-192x192.png",
      badge: "/badge-72x72.png",
      data: data.url,
      actions: data.actions || [],
    })
  );
});

// Notification click event
self.addEventListener("notificationclick", (event: NotificationClickEvent) => {
  event.notification.close();

  if (event.action === "open") {
    event.waitUntil(
      self.clients.openWindow(event.notification.data)
    );
  }
});

// Periodic background sync (if supported)
self.addEventListener("periodicsync", (event: PeriodicSyncEvent) => {
  if (event.tag === "content-sync") {
    event.waitUntil(periodicContentSync());
  }
});

// Message event from main thread
self.addEventListener("message", (event: ExtendableMessageEvent) => {
  if (event.data?.type === "SKIP_WAITING") {
    self.skipWaiting();
  }

  if (event.data?.type === "REGISTER_SYNC") {
    registerSync(event.data.tag);
  }

  if (event.data?.type === "GET_PENDING_CHANGES") {
    event.ports[0].postMessage({
      type: "PENDING_CHANGES",
      count: getPendingChangesCount(),
    });
  }
});

// Cache strategies
async function cacheFirst(request: Request): Promise<Response> {
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);

  if (cached) {
    return cached;
  }

  const response = await fetch(request);
  cache.put(request, response.clone());
  return response;
}

async function networkFirst(request: Request): Promise<Response> {
  const cache = await caches.open(CACHE_NAME);

  try {
    const networkResponse = await fetch(request);
    cache.put(request, networkResponse.clone());
    return networkResponse;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    throw error;
  }
}

async function networkWithCacheFallback(request: Request): Promise<Response> {
  const cache = await caches.open(CACHE_NAME);

  try {
    const networkResponse = await fetch(request);
    return networkResponse;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }

    // Return offline page for navigation requests
    if (request.mode === "navigate") {
      return cache.match("/offline.html") || new Response("Offline", { status: 503 });
    }

    throw error;
  }
}

// Background sync handling
async function handleBackgroundSync(syncType: string): Promise<void> {
  console.log(`[SW] Processing background sync: ${syncType}`);

  const pendingChanges = await getPendingChanges();

  for (const change of pendingChanges) {
    await syncChangeWithRetry(change);
  }

  // Notify clients of sync completion
  const clients = await self.clients.matchAll({ type: "window" });
  clients.forEach((client) => {
    client.postMessage({
      type: "SYNC_COMPLETED",
      syncType,
      timestamp: Date.now(),
    });
  });
}

// Sync with retry logic
async function syncChangeWithRetry(
  change: PendingChange,
  attempt = 1
): Promise<void> {
  try {
    const response = await fetch(change.url, {
      method: change.method,
      headers: change.headers,
      body: JSON.stringify(change.body),
    });

    if (!response.ok) {
      throw new Error(`Sync failed: ${response.status}`);
    }

    // Remove from pending after successful sync
    await removePendingChange(change.id);
    console.log(`[SW] Synced change: ${change.id}`);
  } catch (error) {
    console.error(`[SW] Sync attempt ${attempt} failed:`, error);

    if (attempt < MAX_RETRY_ATTEMPTS) {
      const delay = RETRY_BACKOFF_MS * Math.pow(2, attempt - 1);
      await new Promise((resolve) => setTimeout(resolve, delay));
      return syncChangeWithRetry(change, attempt + 1);
    }

    // Max retries reached - keep in queue for next sync
    console.error(`[SW] Max retries reached for change: ${change.id}`);
  }
}

// Periodic content sync
async function periodicContentSync(): Promise<void> {
  console.log("[SW] Running periodic content sync");

  // Sync content updates from server
  const syncRequests = [
    "/api/content/updates",
    "/api/notifications/pending",
  ];

  for (const endpoint of syncRequests) {
    try {
      const response = await fetch(endpoint);
      if (response.ok) {
        const data = await response.json();
        // Notify clients of new content
        const clients = await self.clients.matchAll({ type: "window" });
        clients.forEach((client) => {
          client.postMessage({
            type: "CONTENT_UPDATED",
            endpoint,
            data,
          });
        });
      }
    } catch (error) {
      console.error(`[SW] Periodic sync failed for ${endpoint}:`, error);
    }
  }
}

// Helper functions
function registerSync(tag: string): void {
  if ("sync" in self.registration) {
    self.registration.sync
      .register(`${SYNC_TAG_PREFIX}${tag}`)
      .then(() => console.log(`[SW] Registered sync: ${tag}`))
      .catch((err) => console.error(`[SW] Sync registration failed:`, err));
  }
}

interface PendingChange {
  id: string;
  url: string;
  method: string;
  headers: Record<string, string>;
  body: unknown;
  timestamp: number;
  retryCount: number;
}

async function getPendingChanges(): Promise<PendingChange[]> {
  // In a real implementation, this would query IndexedDB
  // For now, return empty array
  return [];
}

async function removePendingChange(id: string): Promise<void> {
  // Remove from IndexedDB
  console.log(`[SW] Removed pending change: ${id}`);
}

function getPendingChangesCount(): number {
  // Return count from IndexedDB
  return 0;
}

// Export for TypeScript
export {};
