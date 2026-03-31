/**
 * WebSocket type definitions for real-time communication.
 *
 * @doc.type types
 * @doc.purpose WebSocket event and message type definitions for real-time communication
 * @doc.layer product
 * @doc.pattern Type Contract
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
