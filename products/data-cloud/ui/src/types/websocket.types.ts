/**
 * WebSocket type definitions for real-time communication.
 * 
 * @module types/websocket
 */

export type WebSocketEventType = 
  | 'execution-started'
  | 'execution-updated'
  | 'execution-completed'
  | 'execution-failed'
  | 'node-started'
  | 'node-completed'
  | 'node-failed'
  | 'execution-subscribe';

export interface WebSocketMessage {
  type: WebSocketEventType;
  data: Record<string, unknown>;
  timestamp: string;
  executionId?: string;
  nodeId?: string;
}
