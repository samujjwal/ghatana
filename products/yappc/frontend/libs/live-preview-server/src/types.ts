/**
 * Type definitions for live preview server.
 *
 * @doc.type module
 * @doc.purpose Live preview server types
 * @doc.layer product
 * @doc.pattern Value Object
 */

import type { WebSocket } from 'ws';

/**
 * Live preview server configuration options.
 *
 * @doc.type interface
 * @doc.purpose Server configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface LivePreviewServerOptions {
  /** Server port (default: 5174) */
  port?: number;
  /** Server host (default: localhost) */
  host?: string;
  /** Maximum concurrent connections (default: 100) */
  maxConnections?: number;
  /** Message timeout in ms (default: 30000) */
  messageTimeout?: number;
  /** Enable WebSocket compression (default: true) */
  enableCompression?: boolean;
}

/**
 * Client session information.
 *
 * @doc.type interface
 * @doc.purpose Client session
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ClientSession {
  /** Unique client ID */
  id: string;
  /** WebSocket connection */
  ws: WebSocket;
  /** Connection timestamp */
  connectedAt: number;
  /** Last activity timestamp */
  lastActivity: number;
}

/**
 * Preview message types.
 *
 * @doc.type type
 * @doc.purpose Message type union
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type PreviewMessage =
  | {
      type: 'welcome';
      data: {
        clientId: string;
        timestamp: number;
      };
    }
  | {
      type: 'render';
      data: {
        componentId: string;
        props?: Record<string, unknown>;
      };
    }
  | {
      type: 'render-ack';
      data: {
        componentId: string;
        timestamp: number;
      };
    }
  | {
      type: 'update-props';
      data: {
        componentId: string;
        props: Record<string, unknown>;
      };
    }
  | {
      type: 'props-changed';
      data: {
        componentId: string;
        props: Record<string, unknown>;
        updatedBy: string;
        timestamp: number;
      };
    }
  | {
      type: 'update-style';
      data: {
        componentId: string;
        styles: Record<string, string>;
      };
    }
  | {
      type: 'style-changed';
      data: {
        componentId: string;
        styles: Record<string, string>;
        updatedBy: string;
        timestamp: number;
      };
    }
  | {
      type: 'ping';
      data: Record<string, never>;
    }
  | {
      type: 'pong';
      data: {
        timestamp: number;
        clientId: string;
      };
    }
  | {
      type: 'error';
      data: {
        message: string;
        error?: string;
      };
    };

/**
 * Render request event data.
 *
 * @doc.type interface
 * @doc.purpose Render request event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface RenderRequestEvent {
  clientId: string;
  componentId: string;
  props: Record<string, unknown>;
}

/**
 * Props update event data.
 *
 * @doc.type interface
 * @doc.purpose Props update event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PropsUpdateEvent {
  clientId: string;
  componentId: string;
  props: Record<string, unknown>;
}

/**
 * Style update event data.
 *
 * @doc.type interface
 * @doc.purpose Style update event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface StyleUpdateEvent {
  clientId: string;
  componentId: string;
  styles: Record<string, string>;
}

/**
 * Client connection event data.
 *
 * @doc.type interface
 * @doc.purpose Client connection event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ClientConnectionEvent {
  clientId: string;
}

/**
 * Client disconnection event data.
 *
 * @doc.type interface
 * @doc.purpose Client disconnection event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ClientDisconnectionEvent {
  clientId: string;
}

/**
 * Server started event data.
 *
 * @doc.type interface
 * @doc.purpose Server started event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ServerStartedEvent {
  port: number;
  host: string;
}

/**
 * Server error event data.
 *
 * @doc.type interface
 * @doc.purpose Server error event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ServerErrorEvent {
  error: Error;
}

/**
 * Client error event data.
 *
 * @doc.type interface
 * @doc.purpose Client error event
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface ClientErrorEvent {
  clientId: string;
  error: Error;
}
