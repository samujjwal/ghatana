// Minimal ambient declarations for service worker typings used in the project
// These augment the global scope for service worker files

interface ExtendableEvent extends Event {
  waitUntil(promise: Promise<unknown>): void;
}

interface ServiceWorkerGlobalScope extends WorkerGlobalScope {
  caches: CacheStorage;
  addEventListener(type: 'activate', listener: (event: ExtendableEvent) => void): void;
  addEventListener(type: 'error', listener: (event: ErrorEvent) => void): void;
  addEventListener(type: 'unhandledrejection', listener: (event: PromiseRejectionEvent) => void): void;
}

declare var self: ServiceWorkerGlobalScope;
