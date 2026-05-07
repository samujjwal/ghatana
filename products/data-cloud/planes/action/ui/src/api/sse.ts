/**
 * SSE client for real-time AEP event streaming.
 *
 * Wraps the browser's native `EventSource` API with:
 *   - Typed event callbacks
 *   - Exponential-backoff auto-reconnect on error or unexpected close
 *   - Clean resource management via `close()`
 *
 * T-05: The browser `EventSource` API cannot send `Authorization` headers.
 * Authentication is handled by:
 *   1. Fetching a short-lived SSE token from `POST /api/v1/auth/sse-token`
 *      using the caller-supplied bearer JWT.
 *   2. Appending the returned token as `?token=<sseToken>` to the EventSource URL.
 *   3. The gateway validates the token on `/events/stream` (existing `token` query-param path).
 *
 * @doc.type api-client
 * @doc.purpose Authenticated Server-Sent Events subscription for AEP real-time updates
 * @doc.layer frontend
 */
import { API_BASE_URL, getAuthToken } from '@/lib/http-client';

const KNOWN_EVENTS = ['connected', 'heartbeat', 'run.update', 'hitl_request_created', 'hitl.update', 'agent.output'] as const;
const RECONNECT_BASE_MS = 3_000;
const RECONNECT_MAX_MS = 30_000;
/** SSE tokens are valid for 60 s on the backend; refresh 15 s early. */
const SSE_TOKEN_REFRESH_MS = 45_000;

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

interface SseTokenResponse {
  token: string;
  expiresInMs: number;
}

/**
 * Requests a short-lived SSE token from the backend.
 * Sends the current bearer JWT in the Authorization header so the backend can
 * validate identity before minting the token.
 *
 * @param tenantId - tenant context to embed in the SSE token
 * @returns short-lived SSE token string, or `null` on failure
 */
async function fetchSseToken(tenantId: string): Promise<string | null> {
  const bearerToken = getAuthToken();
  if (!bearerToken) return null;
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/auth/sse-token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${bearerToken}`,
      },
      body: JSON.stringify({ tenantId }),
      signal: AbortSignal.timeout(5_000),
    });
    if (!res.ok) return null;
    const body = await res.json() as SseTokenResponse;
    return typeof body.token === 'string' ? body.token : null;
  } catch {
    return null;
  }
}

/**
 * Opens a persistent Server-Sent Events connection to the AEP event stream.
 *
 * Automatically reconnects with exponential backoff (3 s → 30 s) on network
 * errors or unexpected server closes. Call `.close()` to permanently stop.
 *
 * T-05: Before opening `EventSource`, fetches a short-lived SSE token from
 * `/api/v1/auth/sse-token` using the current bearer JWT. The token is appended
 * as `?token=` so the browser SSE connection is authenticated.
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
  let es: EventSource | null = null;
  let closed = false;
  let retryDelay = RECONNECT_BASE_MS;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let tokenRefreshTimer: ReturnType<typeof setTimeout> | null = null;

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
      void connect();
    }, retryDelay);
  }

  function scheduleTokenRefresh(): void {
    if (closed) return;
    tokenRefreshTimer = setTimeout(() => {
      if (closed) return;
      // Reconnect with a fresh SSE token before the current one expires
      if (es) detach(es);
      es = null;
      void connect();
    }, SSE_TOKEN_REFRESH_MS);
  }

  function openConnection(sseToken: string | null): void {
    if (closed) return;

    const params = new URLSearchParams({ tenantId });
    if (sseToken) {
      params.set('token', sseToken);
    } else {
      console.warn('[sse] SSE token unavailable — stream will be unauthenticated');
    }

    const url = `${API_BASE_URL}/events/stream?${params.toString()}`;
    es = new EventSource(url);
    attach(es);

    // Reset backoff when the stream opens successfully, and schedule token refresh.
    es.addEventListener('connected', () => {
      retryDelay = RECONNECT_BASE_MS;
      scheduleTokenRefresh();
    }, { once: true });
  }

  async function connect(): Promise<void> {
    if (closed) return;

    // T-05: fetch a short-lived authenticated SSE token before opening EventSource.
    // When no bearer token is present, open immediately so unauthenticated/dev
    // subscriptions are not delayed behind a needless async boundary.
    const bearerToken = getAuthToken();
    if (!bearerToken) {
      openConnection(null);
      return;
    }

    const sseToken = await fetchSseToken(tenantId);
    openConnection(sseToken);
  }

  void connect();

  return {
    close() {
      closed = true;
      if (reconnectTimer !== null) clearTimeout(reconnectTimer);
      if (tokenRefreshTimer !== null) clearTimeout(tokenRefreshTimer);
      if (es) detach(es);
      es = null;
    },
    get connected() {
      return es !== null && es.readyState === EventSource.OPEN;
    },
  };
}
