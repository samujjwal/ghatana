import '@testing-library/jest-dom';

class InMemoryStorage implements Storage {
  private readonly store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.get(key) ?? null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

function ensureStorage(storageKey: 'localStorage' | 'sessionStorage'): void {
  const candidate = globalThis[storageKey];

  if (
    candidate &&
    typeof candidate.clear === 'function' &&
    typeof candidate.getItem === 'function' &&
    typeof candidate.setItem === 'function' &&
    typeof candidate.removeItem === 'function' &&
    typeof candidate.key === 'function'
  ) {
    return;
  }

  Object.defineProperty(globalThis, storageKey, {
    configurable: true,
    enumerable: true,
    value: new InMemoryStorage(),
    writable: true,
  });
}

ensureStorage('localStorage');
ensureStorage('sessionStorage');

// ResizeObserver polyfill for testing environments (JSDOM doesn't support it)
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof globalThis.ResizeObserver;
}

// EventSource polyfill for testing environments (JSDOM doesn't support EventSource)
// Using type assertion to bypass complex EventSource interface signatures
class MockEventSource implements EventSource {
  url: string;
  readyState: number = 0;
  withCredentials: boolean = false;
  CONNECTING = 0 as const;
  OPEN = 1 as const;
  CLOSED = 2 as const;
  onopen: ((this: EventSource, ev: Event) => void) | null = null;
  onmessage: ((this: EventSource, ev: MessageEvent) => void) | null = null;
  onerror: ((this: EventSource, ev: Event) => void) | null = null;
  
  // Use type assertion to satisfy interface without complex overloads
  addEventListener = ((
    type: string,
    listener: EventListenerOrEventListenerObject | null,
    options?: boolean | AddEventListenerOptions
  ) => {
    if (!listener) return;
    // Store listeners for dispatch - using array on instance
    const key = `_listeners_${type}`;
    const listeners = (this as unknown as Record<string, EventListener[]>)[key] ?? [];
    listeners.push(listener as EventListener);
    (this as unknown as Record<string, EventListener[]>)[key] = listeners;
  }) as EventSource['addEventListener'];

  removeEventListener = ((
    type: string,
    listener: EventListenerOrEventListenerObject | null,
    options?: boolean | EventListenerOptions
  ) => {
    if (!listener) return;
    const key = `_listeners_${type}`;
    const listeners = (this as unknown as Record<string, EventListener[]>)[key] ?? [];
    const index = listeners.indexOf(listener as EventListener);
    if (index > -1) {
      listeners.splice(index, 1);
    }
  }) as EventSource['removeEventListener'];

  dispatchEvent(event: Event): boolean {
    const key = `_listeners_${event.type}`;
    const listeners = (this as unknown as Record<string, EventListener[]>)[key] ?? [];
    listeners.forEach(listener => {
      if (typeof listener === 'function') {
        listener.call(this as unknown as EventSource, event);
      }
    });
    return !event.defaultPrevented;
  }

  constructor(url: string | URL, eventSourceInitDict?: EventSourceInit) {
    this.url = url.toString();
    this.withCredentials = eventSourceInitDict?.withCredentials ?? false;
    // Simulate connection success after a tick
    setTimeout(() => {
      this.readyState = this.OPEN;
      if (this.onopen) {
        this.onopen.call(this as unknown as EventSource, new Event('open'));
      }
    }, 0);
  }

  close() {
    this.readyState = this.CLOSED;
  }
}

// Only polyfill if EventSource is not available (e.g., in JSDOM)
if (typeof globalThis.EventSource === 'undefined') {
  (globalThis as unknown as { EventSource: typeof MockEventSource }).EventSource = MockEventSource;
}
