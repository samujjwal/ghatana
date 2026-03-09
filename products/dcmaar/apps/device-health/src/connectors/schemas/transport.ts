/**
 * @fileoverview Transport configuration and type definitions.
 * Defines the transport layer configuration for extension connectors.
 * Previously located in connectors/compat/types.ts (deprecated).
 */

import { z } from 'zod';

/**
 * Transport configuration
 * Defines HTTP/HTTPS connection parameters for the extension
 */
export interface TransportConfig {
  url: string;
  protocol?: 'http' | 'https' | 'ws' | 'wss';
  timeout?: number;
  retries?: number;
  headers?: Record<string, string>;
}

/**
 * Transport status enum
 * Tracks connection state to external services
 */
export enum TransportStatus {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  ERROR = 'error',
}

/**
 * Transport priority enum
 * Determines message priority in the queue
 */
export enum TransportPriority {
  LOW = 0,
  NORMAL = 1,
  HIGH = 2,
  CRITICAL = 3,
}

/**
 * Transport message
 * Data structure for messages sent via transport
 */
export interface TransportMessage {
  id: string;
  data: unknown;
  priority: TransportPriority;
  timestamp: number;
}

/**
 * Transport event map
 * Event types and their payload signatures
 */
export interface TransportEventMap {
  connected: [];
  disconnected: [];
  error: [Error];
  message: [TransportMessage];
  statusChange: [{ from: TransportStatus; to: TransportStatus }];
}

/**
 * Transport type enum
 * Supported transport protocols
 */
export enum TransportType {
  HTTP = 'http',
  WEBSOCKET = 'websocket',
  IPC = 'ipc',
}

/**
 * Zod schema for transport configuration validation
 */
export const TransportConfigSchema = z.object({
  url: z.string().url('Transport URL must be a valid URL'),
  protocol: z.enum(['http', 'https', 'ws', 'wss']).optional(),
  timeout: z.number().int().positive().optional(),
  retries: z.number().int().min(0).optional(),
  headers: z.record(z.string(), z.string()).optional(),
});
