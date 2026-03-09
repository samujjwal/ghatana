/**
 * @ghatana/realtime - Real-time Communication Library
 *
 * Production-grade WebSocket client and React hooks for real-time
 * bidirectional communication. Extracted from YAPPC to enable framework-level reuse.
 *
 * Features:
 * - WebSocket client with auto-reconnect, heartbeat, message queuing
 * - React hooks for declarative WebSocket usage
 * - ActiveJ integration for Java backend streaming
 * - Topic-based subscriptions with multi-tenant support
 *
 * @doc.type module
 * @doc.purpose Real-time WebSocket client and React hooks library
 * @doc.layer platform
 * @doc.pattern Client + Hooks
 */

// Export client
export * from './client';

// Export React hooks
export * from './hooks/useWebSocket';

// Export ActiveJ integration
export * from './activej';

// Re-export types for convenience
export type {
  WebSocketConfig,
  WebSocketMessage,
  WebSocketConnectionState,
  WebSocketEventHandler,
} from './client';

export type {
  UseWebSocketOptions,
  UseWebSocketReturn,
} from './hooks/useWebSocket';

export type {
  WebSocketEvent,
  WebSocketEventType,
  WebSocketListener,
  RealtimeClientOptions,
} from './WebSocketClient';

// ActiveJ-specific type re-exports
export type {
  ActiveJStreamConfig,
  ActiveJStreamMessage,
  StreamSubscription,
  ActiveJConnectionState,
  UseActiveJStreamOptions,
  UseActiveJStreamReturn,
} from './activej';
