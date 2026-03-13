/**
 * SSE client for real-time AEP event streaming.
 *
 * Wraps the browser's native `EventSource` API with:
 *   - Typed event callbacks
 *   - Exponential-backoff auto-reconnect on error or unexpected close
 *   - Clean resource management via `close()`
 *
 * @doc.type api-client
 * @doc.purpose Server-Sent Events subscription for AEP real-time updates
 * @doc.layer frontend
 */

// In dev the Vite proxy (/api → localhost:8081) handles routing.
// In production set VITE_AEP_API_URL or rely on the reverse-proxy.
const BASE_URL = import.meta.env.VITE_AEP_API_URL ?? '';

const KNOWN_EVENTS = ['connected', 'heartbeat', 'run.update', 'hitl.new', 'hitl.update', 'agent.output'] as const;
const RECONNECT_BASE_MS = 3_000;
const RECONNECT_MAX_MS = 30_000;

export type SseEventType = (typeof KNOWN_EVENTS)[number] | string;

export interface SseMessage {
  type: SseEventType;
  data: unknown;
}

export type SseHandler = (message: SseMessage) => void;
export type SseErrorHandler = (event: Event) => void;

export interface SseSubscription {
  /** Permanently closes the SSE connection and cancels any pending reconnect. */
  close(): void;
  /** Whether the underlying EventSource is currently open. */
  readonly connected: boolean;
}

/**
 * Opens a persistent Server-Sent Events connection to the AEP event stream.
 *
 * Automatically reconnects with exponential backoff (3 s → 30 s) on network
 * errors or unexpected server closes. Call `.close()` to permanently stop.
 *
 * @param tenantId  - tenant to subscribe to (forwarded as `tenantId` query param)
 * @param onMessage - called for every received SSE event
 * @param onError   - optional callback on each error attempt (before reconnect)
 * @returns subscription handle
 */
export function subscribeToAepStream(
  tenantId: string,
  onMessage: SseHandler,
  onError?: SseErrorHandler,
): SseSubscription {
  const url = `${BASE_URL}/events/stream?tenantId=${encodeURIComponent(tenantId)}`;

  let es: EventSource | null = null;
  let closed = false;
  let retryDelay = RECONNECT_BASE_MS;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  // Per-instance listener map so removeEventListener always references the same functions.
  const listeners = new Map<string, (e: MessageEvent) => void>();

  function parseEvent(type: SseEventType, e: MessageEvent): SseMessage {
    try {
      return { type, data: e.data ? (JSON.parse(e.data) as unknown) : null };
    } catch {
      return { type, data: e.data };
    }
  }

  function attach(source: EventSource): void {
    for (const type of KNOWN_EVENTS) {
      const fn = (e: MessageEvent) => onMessage(parseEvent(type, e));
      listeners.set(type, fn);
      source.addEventListener(type, fn as EventListener);
    }

    source.onmessage = (e: MessageEvent) => onMessage(parseEvent('message', e));

    source.onerror = (e) => {
      if (onError) onError(e);
      else console.warn('[sse] AEP stream error', e);

      // EventSource goes to CLOSED state on error — schedule reconnect if not manually closed.
      if (!closed && source.readyState === EventSource.CLOSED) {
        scheduleReconnect();
      }
    };
  }

  function detach(source: EventSource): void {
    for (const [type, fn] of listeners.entries()) {
      source.removeEventListener(type, fn as EventListener);
    }
    listeners.clear();
    source.onmessage = null;
    source.onerror = null;
    source.close();
  }

  function scheduleReconnect(): void {
    if (closed) return;
    reconnectTimer = setTimeout(() => {
      if (closed) return;
      retryDelay = Math.min(retryDelay * 2, RECONNECT_MAX_MS);
      connect();
    }, retryDelay);
  }

  function connect(): void {
    if (closed) return;
    es = new EventSource(url);
    attach(es);
    // Reset backoff when the stream opens successfully.
    es.addEventListener('connected', () => { retryDelay = RECONNECT_BASE_MS; }, { once: true });
  }

  connect();

  return {
    close() {
      closed = true;
      if (reconnectTimer !== null) clearTimeout(reconnectTimer);
      if (es) detach(es);
      es = null;
    },
    get connected() {
      return es !== null && es.readyState === EventSource.OPEN;
    },
  };
}
