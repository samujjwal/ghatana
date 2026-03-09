import { z } from 'zod';

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export interface ConnectionOptions {
  /**
   * Unique identifier for the connection
   */
  id: string;
  
  /**
   * Type of the connector (http, websocket, grpc, etc.)
   */
  type: string;
  
  /**
   * Maximum number of connection retries
   * @default 3
   */
  maxRetries?: number;
  
  /**
   * Timeout in milliseconds
   * @default 30000 (30 seconds)
   */
  timeout?: number;
  
  /**
   * Enable/disable SSL/TLS
   * @default true
   */
  secure?: boolean;
  
  /**
   * Additional headers to include in requests
   */
  headers?: Record<string, string>;
  
  /**
   * Authentication configuration
   */
  auth?: {
    type: 'none' | 'basic' | 'bearer' | 'api_key' | 'oauth2';
    [key: string]: unknown;
  };
  
  /**
   * Enable debug logging
   * @default false
   */
  debug?: boolean;
}

export const ConnectionOptionsSchema = z.object({
  id: z.string().min(1, 'ID is required'),
  type: z.string().min(1, 'Type is required'),
  maxRetries: z.number().int().nonnegative().optional().default(3),
  timeout: z.number().int().positive().optional().default(30000),
  secure: z.boolean().optional().default(true),
  // Map of header name -> header value
  headers: z.record(z.string(), z.string()).optional(),
  auth: z.any().optional(),
  debug: z.boolean().optional().default(false),
});

export interface Event<T = any> {
  id: string;
  type: string;
  timestamp: number;
  payload: T;
  metadata?: Record<string, any>;
  correlationId?: string;
}

export type EventHandler<T = any> = (event: Event<T>) => void | Promise<void>;

export interface IConnector<TConfig = any, TEvent = any> {
  /**
   * Unique identifier for the connector instance
   */
  readonly id: string;
  
  /**
   * Type of the connector
   */
  readonly type: string;
  
  /**
   * Current connection status
   */
  readonly status: ConnectionStatus;
  
  /**
   * Connect to the endpoint
   */
  connect(): Promise<void>;
  
  /**
   * Disconnect from the endpoint
   */
  disconnect(): Promise<void>;
  
  /**
   * Send data through the connector
   * @param data Data to send
   * @param options Additional options
   */
  send(data: unknown, options?: Record<string, any>): Promise<void>;
  
  /**
   * Subscribe to events from the connector
   * @param eventType Type of event to subscribe to
   * @param handler Event handler function
   */
  onEvent(eventType: string, handler: EventHandler<TEvent>): void;
  
  /**
   * Unsubscribe from events
   * @param eventType Type of event to unsubscribe from
   * @param handler Event handler to remove
   */
  offEvent(eventType: string, handler: EventHandler<TEvent>): void;
  
  /**
   * Get the current configuration
   */
  getConfig(): TConfig;
  
  /**
   * Update the configuration
   * @param config New configuration
   */
  updateConfig(config: Partial<TConfig>): Promise<void>;
  
  /**
   * Validate the configuration
   * @param config Configuration to validate
   */
  validateConfig(config: TConfig): { valid: boolean; error?: string };
}
