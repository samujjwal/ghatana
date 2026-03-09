/**
 * @ghatana/yappc-realtime
 * 
 * Real-time communication library for YAPPC.
 * Provides WebSocket client for canvas collaboration, chat, and notifications.
 * 
 * @module realtime
 */

export * from './WebSocketClient';
export { WebSocketClient, createWebSocketClient, getWebSocketClient, resetWebSocketClient } from './WebSocketClient';
export type { 
  MessageType, 
  WebSocketMessage, 
  ConnectionState, 
  WebSocketClientConfig,
  MessageHandler,
  StateChangeHandler 
} from './WebSocketClient';
