/**
 * WebSocket Real-Time Connection Store - Jotai Atoms
 *
 * Manages WebSocket connection to backend for:
 * - Real-time event streaming
 * - Policy updates
 * - Device status updates
 * - Bidirectional communication
 * - Connection lifecycle management
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose WebSocket connection state management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';

// ---------------------------------------------------------------------------
// Module-level WebSocket singleton
// ---------------------------------------------------------------------------
let _ws: WebSocket | null = null;
let _pingInterval: ReturnType<typeof setInterval> | null = null;
let _reconnectTimeout: ReturnType<typeof setTimeout> | null = null;

const MAX_RECONNECT_ATTEMPTS = 5;
const PING_INTERVAL_MS = 30_000;
const RECONNECT_BASE_DELAY_MS = 1_000;

/**
 * Callbacks stored from atom write functions so WebSocket event handlers can
 * update Jotai state outside of the React tree.
 */
let _callbacks: {
  get: () => WebSocketState;
  set: (s: WebSocketState) => void;
} | null = null;

/**
 * Create (or re-create) a WebSocket connection.
 * Can be called for initial connect and for automatic reconnection.
 */
function _createWsConnection(url: string, token?: string, attempt = 0): void {
  // Tear down any existing socket first
  if (_ws) {
    _ws.onclose = null; // prevent the old handler from firing
    _ws.close(1000, 'replacing connection');
    _ws = null;
  }
  if (_pingInterval !== null) { clearInterval(_pingInterval); _pingInterval = null; }
  if (_reconnectTimeout !== null) { clearTimeout(_reconnectTimeout); _reconnectTimeout = null; }

  _ws = new WebSocket(url);

  _ws.onopen = () => {
    // Send auth token immediately after connecting
    if (token && _ws?.readyState === WebSocket.OPEN) {
      _ws.send(JSON.stringify({ type: 'auth', channel: 'system', payload: { token } }));
    }

    if (_callbacks) {
      const cur = _callbacks.get();
      _callbacks.set({ ...cur, status: 'connected', reconnectAttempts: 0, error: null });
    }

    // Start periodic heartbeat
    _pingInterval = setInterval(() => {
      if (_ws?.readyState === WebSocket.OPEN) {
        _ws.send(JSON.stringify({ type: 'ping', channel: 'system', payload: {} }));
      }
    }, PING_INTERVAL_MS);
  };

  _ws.onmessage = (event: MessageEvent) => {
    if (!_callbacks) return;
    try {
      const raw = JSON.parse(event.data as string) as Partial<WebSocketMessage>;
      if (raw.type === 'ping') return; // discard pong responses
      const msg: WebSocketMessage = {
        id: raw.id ?? String(Date.now()),
        type: raw.type ?? 'event',
        channel: raw.channel ?? 'unknown',
        payload: raw.payload ?? {},
        receivedAt: new Date(),
      };
      const cur = _callbacks.get();
      _callbacks.set({
        ...cur,
        messageQueue: [...cur.messageQueue.slice(-99), msg],
        lastMessageAt: Date.now(),
        error: null,
      });
    } catch {
      // Ignore malformed messages
    }
  };

  _ws.onerror = () => {
    if (!_callbacks) return;
    const cur = _callbacks.get();
    _callbacks.set({ ...cur, status: 'error', error: 'WebSocket connection error' });
  };

  _ws.onclose = (ev: CloseEvent) => {
    if (_pingInterval !== null) { clearInterval(_pingInterval); _pingInterval = null; }
    if (!_callbacks) return;
    const cur = _callbacks.get();

    // Code 1000 = normal closure; 1001 = going away — do not reconnect
    if (cur.status === 'disconnected' || ev.code === 1000 || ev.code === 1001) return;

    if (attempt < MAX_RECONNECT_ATTEMPTS) {
      const nextAttempt = attempt + 1;
      const delay = RECONNECT_BASE_DELAY_MS * Math.pow(2, attempt); // exponential back-off
      _callbacks.set({ ...cur, status: 'reconnecting', reconnectAttempts: nextAttempt });
      _reconnectTimeout = setTimeout(() => _createWsConnection(url, token, nextAttempt), delay);
    } else {
      _callbacks.set({
        ...cur,
        status: 'error',
        error: `Connection lost (code ${ev.code}). Max reconnect attempts reached.`,
      });
    }
  };
}

/**
 * WebSocket message from server.
 *
 * @interface WebSocketMessage
 * @property {string} id - Unique message ID
 * @property {string} type - Message type (event, update, error, ping)
 * @property {string} channel - Channel name (monitoring, policies, devices)
 * @property {Record<string, any>} payload - Message payload
 * @property {Date} receivedAt - When message was received
 */
export interface WebSocketMessage {
  id: string;
  type: 'event' | 'update' | 'error' | 'ping' | 'subscription' | 'unsubscription';
  channel: string;
  payload: Record<string, any>;
  receivedAt: Date;
}

/**
 * Subscription to WebSocket channel.
 *
 * @interface ChannelSubscription
 * @property {string} channel - Channel name to subscribe to
 * @property {('monitoring' | 'policies' | 'devices' | 'alerts')} type - Subscription type
 * @property {boolean} isActive - Whether currently subscribed
 */
export interface ChannelSubscription {
  channel: string;
  type: 'monitoring' | 'policies' | 'devices' | 'alerts';
  isActive: boolean;
}

/**
 * WebSocket connection state.
 *
 * @interface WebSocketState
 * @property {'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error'} status - Connection status
 * @property {WebSocketMessage[]} messageQueue - Recent messages (last 100)
 * @property {ChannelSubscription[]} subscriptions - Active subscriptions
 * @property {number} reconnectAttempts - Failed reconnection attempts
 * @property {number} lastMessageAt - Timestamp of last message received
 * @property {string | null} error - Error message if status is 'error'
 */
export interface WebSocketState {
  status: 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error';
  messageQueue: WebSocketMessage[];
  subscriptions: ChannelSubscription[];
  reconnectAttempts: number;
  lastMessageAt: number;
  error: string | null;
}

/**
 * Initial WebSocket state.
 *
 * GIVEN: App initialization
 * WHEN: websocketAtom is first accessed
 * THEN: Connection starts in disconnected state
 */
const initialWebSocketState: WebSocketState = {
  status: 'disconnected',
  messageQueue: [],
  subscriptions: [],
  reconnectAttempts: 0,
  lastMessageAt: 0,
  error: null,
};

/**
 * Core WebSocket atom.
 *
 * Holds complete WebSocket state including:
 * - Connection status
 * - Message queue
 * - Active subscriptions
 * - Reconnection tracking
 *
 * Usage (in components):
 * `const [state, setState] = useAtom(websocketAtom);`
 */
export const websocketAtom = atom<WebSocketState>(initialWebSocketState);

/**
 * Derived atom: Is WebSocket connected?
 *
 * GIVEN: websocketAtom with status
 * WHEN: isConnectedAtom is read
 * THEN: Returns true if status === 'connected'
 *
 * Usage (in components):
 * `const [isConnected] = useAtom(isConnectedAtom);`
 * Show connection status indicator
 */
export const isConnectedAtom = atom<boolean>((get) => {
  return get(websocketAtom).status === 'connected';
});

/**
 * Derived atom: Last received message.
 *
 * GIVEN: websocketAtom with messageQueue
 * WHEN: lastMessageAtom is read
 * THEN: Returns most recent message or null
 *
 * Usage (in components):
 * `const [lastMessage] = useAtom(lastMessageAtom);`
 * Process latest incoming message
 */
export const lastMessageAtom = atom<WebSocketMessage | null>((get) => {
  const state = get(websocketAtom);
  return state.messageQueue.length > 0
    ? state.messageQueue[state.messageQueue.length - 1]
    : null;
});

/**
 * Derived atom: Active subscriptions.
 *
 * GIVEN: websocketAtom with subscriptions
 * WHEN: activeSubscriptionsAtom is read
 * THEN: Returns only subscriptions with isActive === true
 *
 * Usage (in components):
 * `const [active] = useAtom(activeSubscriptionsAtom);`
 * Display "Connected to 3 channels" in UI
 */
export const activeSubscriptionsAtom = atom<ChannelSubscription[]>((get) => {
  return get(websocketAtom).subscriptions.filter((s) => s.isActive);
});

/**
 * Derived atom: Reconnection in progress?
 *
 * GIVEN: websocketAtom with status
 * WHEN: isReconnectingAtom is read
 * THEN: Returns true if attempting to reconnect
 *
 * Usage (in components):
 * `const [isReconnecting] = useAtom(isReconnectingAtom);`
 * Show "Reconnecting..." indicator
 */
export const isReconnectingAtom = atom<boolean>((get) => {
  const state = get(websocketAtom);
  return state.status === 'reconnecting';
});

/**
 * Action atom: Connect to WebSocket.
 *
 * GIVEN: Valid backend URL and credentials
 * WHEN: connectAtom is called
 * THEN: Initiates WebSocket connection
 *       Sets status to 'connecting'
 *       On success, status becomes 'connected'
 *
 * Usage (in components):
 * `const [, connect] = useAtom(connectAtom);`
 * await connect('wss://api.example.com/ws', token);
 */
export const connectAtom = atom<
  null,
  [url: string, token?: string],
  Promise<void>
>(
  null,
  async (get, set, url: string, token?: string) => {
    const state = get(websocketAtom);

    set(websocketAtom, {
      ...state,
      status: 'connecting',
      error: null,
    });

    // Wire Jotai get/set into the module-level callbacks so WS event handlers
    // can update atom state outside the React tree.
    _callbacks = {
      get: () => get(websocketAtom),
      set: (s: WebSocketState) => set(websocketAtom, s),
    };

    return new Promise<void>((resolve, reject) => {
      const prevOnOpen = () => resolve();
      const prevOnError = () => reject(new Error('WebSocket connection failed'));

      // Temporarily hook resolve/reject onto the connection being created
      const originalOnOpen = _ws?.onopen;
      _createWsConnection(url, token);

      // Patch the newly-created socket to also resolve/reject the promise
      if (_ws) {
        const patchedOpen = _ws.onopen;
        _ws.onopen = (ev) => {
          if (patchedOpen) (patchedOpen as EventListener)(ev);
          prevOnOpen();
        };
        const patchedError = _ws.onerror;
        _ws.onerror = (ev) => {
          if (patchedError) (patchedError as EventListener)(ev);
          prevOnError();
        };
      } else {
        reject(new Error('Failed to create WebSocket'));
      }
    });
  }
);

/**
 * Action atom: Disconnect from WebSocket.
 *
 * GIVEN: WebSocket is connected
 * WHEN: disconnectAtom is called
 * THEN: Closes connection gracefully
 *       Clears subscriptions
 *       Sets status to 'disconnected'
 *
 * Usage (in components):
 * `const [, disconnect] = useAtom(disconnectAtom);`
 * await disconnect();
 */
export const disconnectAtom = atom<null, [], Promise<void>>(
  null,
  async (get, set) => {
    const state = get(websocketAtom);

    try {
      // Signal intentional close so the onclose handler does not reconnect
      set(websocketAtom, { ...state, status: 'disconnected', error: null });

      if (_reconnectTimeout !== null) { clearTimeout(_reconnectTimeout); _reconnectTimeout = null; }
      if (_pingInterval !== null) { clearInterval(_pingInterval); _pingInterval = null; }
      if (_ws) {
        _ws.onclose = null; // suppress further state updates
        _ws.close(1000, 'user disconnected');
        _ws = null;
      }
      _callbacks = null;

      set(websocketAtom, {
        ...state,
        status: 'disconnected',
        subscriptions: [],
        messageQueue: [],
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to disconnect';

      set(websocketAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Send message to WebSocket.
 *
 * GIVEN: WebSocket is connected
 * WHEN: sendMessageAtom is called with message
 * THEN: Sends message to server
 *
 * Usage (in components):
 * `const [, sendMessage] = useAtom(sendMessageAtom);`
 * await sendMessage({
 *   type: 'update',
 *   channel: 'policies',
 *   payload: { ... }
 * });
 */
export const sendMessageAtom = atom<
  null,
  [Omit<WebSocketMessage, 'id' | 'receivedAt'>],
  Promise<void>
>(
  null,
  async (get, set, message) => {
    const state = get(websocketAtom);

    if (state.status !== 'connected') {
      throw new Error('WebSocket not connected');
    }

    if (!_ws || _ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket not in OPEN state');
    }

    try {
      const fullMessage = {
        ...message,
        id: String(Date.now()),
        sentAt: Date.now(),
      };
      _ws.send(JSON.stringify(fullMessage));
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to send message';

      set(websocketAtom, {
        ...state,
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Subscribe to channel.
 *
 * GIVEN: WebSocket is connected
 * WHEN: subscribeAtom is called with channel name
 * THEN: Subscribes to channel updates
 *       Server sends messages to this channel
 *
 * Usage (in components):
 * `const [, subscribe] = useAtom(subscribeAtom);`
 * await subscribe('monitoring', 'monitoring');
 */
export const subscribeAtom = atom<
  null,
  [channel: string, type: 'monitoring' | 'policies' | 'devices' | 'alerts'],
  Promise<void>
>(
  null,
  async (get, set, channel: string, type) => {
    const state = get(websocketAtom);

    try {
      // Send subscription request to server
      if (_ws?.readyState === WebSocket.OPEN) {
        _ws.send(JSON.stringify({
          id: String(Date.now()),
          type: 'subscription',
          channel,
          payload: { type },
        }));
      }

      // Check if already subscribed
      const existing = state.subscriptions.find((s) => s.channel === channel);
      if (existing && existing.isActive) {
        return; // Already subscribed
      }

      const subscription: ChannelSubscription = {
        channel,
        type,
        isActive: true,
      };

      // Remove old and add new
      const subscriptions = [
        ...state.subscriptions.filter((s) => s.channel !== channel),
        subscription,
      ];

      set(websocketAtom, {
        ...state,
        subscriptions,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error
          ? error.message
          : 'Failed to subscribe to channel';

      set(websocketAtom, {
        ...state,
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Unsubscribe from channel.
 *
 * GIVEN: Currently subscribed to a channel
 * WHEN: unsubscribeAtom is called
 * THEN: Removes subscription
 *       Server stops sending updates
 *
 * Usage (in components):
 * `const [, unsubscribe] = useAtom(unsubscribeAtom);`
 * await unsubscribe('monitoring');
 */
export const unsubscribeAtom = atom<null, [channel: string], Promise<void>>(
  null,
  async (get, set, channel: string) => {
    const state = get(websocketAtom);

    try {
      // Send unsubscription request to server
      if (_ws?.readyState === WebSocket.OPEN) {
        _ws.send(JSON.stringify({
          id: String(Date.now()),
          type: 'unsubscription',
          channel,
          payload: {},
        }));
      }

      const subscriptions = state.subscriptions.map((s) =>
        s.channel === channel ? { ...s, isActive: false } : s
      );

      set(websocketAtom, {
        ...state,
        subscriptions,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to unsubscribe';

      set(websocketAtom, {
        ...state,
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Receive message from server.
 *
 * GIVEN: WebSocket receives message from server
 * WHEN: receiveMessageAtom is called internally
 * THEN: Adds message to queue (keeps last 100)
 *       Updates lastMessageAt timestamp
 *
 * Usage (internal - called by WebSocket listener):
 * Called automatically when server sends messages
 */
export const receiveMessageAtom = atom<
  null,
  [WebSocketMessage],
  Promise<void>
>(
  null,
  async (get, set, message: WebSocketMessage) => {
    const state = get(websocketAtom);

    try {
      // Keep only last 100 messages
      const messageQueue = [
        ...state.messageQueue.slice(-99),
        { ...message, receivedAt: new Date() },
      ];

      set(websocketAtom, {
        ...state,
        messageQueue,
        lastMessageAt: Date.now(),
        error: null,
      });
    } catch (error) {
      // Silently handle message processing errors
      console.error('Error processing WebSocket message:', error);
    }
  }
);

/**
 * Action atom: Clear WebSocket error.
 *
 * GIVEN: Error displayed to user
 * WHEN: clearWebSocketErrorAtom is called
 * THEN: Clears error message
 *
 * Usage (in components):
 * `const [, clearError] = useAtom(clearWebSocketErrorAtom);`
 * clearError();
 */
export const clearWebSocketErrorAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(websocketAtom);
    set(websocketAtom, {
      ...state,
      error: null,
    });
  }
);
