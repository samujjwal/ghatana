/**
 * @fileoverview WebSocket Stream Source Preset
 *
 * Standard configuration for WebSocket connections.
 */

import type { SourcePreset } from '../types';

/**
 * Standard WebSocket stream preset
 *
 * Balanced configuration for WebSocket connections.
 * Suitable for most real-time streaming scenarios.
 */
export const websocketStreamStandardPreset: SourcePreset = {
  id: 'websocket-stream-standard',
  name: 'Standard WebSocket Stream',
  description: 'Balanced WebSocket connection with auto-reconnect',
  config: {
    type: 'websocket',
    url: '${WEBSOCKET_URL}',
    protocols: [],
    reconnect: true,
    reconnectIntervalMs: 5000,
    maxReconnectAttempts: 10,
    pingIntervalMs: 30000,
    pongTimeoutMs: 10000,
    headers: {
      'Authorization': 'Bearer ${AUTH_TOKEN}',
    },
  },
  tags: ['websocket', 'stream', 'standard', 'realtime'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Secure WebSocket stream preset
 *
 * Enhanced security configuration with TLS and authentication.
 * Suitable for production environments with strict security requirements.
 */
export const websocketStreamSecurePreset: SourcePreset = {
  id: 'websocket-stream-secure',
  name: 'Secure WebSocket Stream',
  description: 'Enhanced security with TLS and authentication',
  config: {
    type: 'websocket',
    url: 'wss://${WEBSOCKET_HOST}',
    protocols: ['dcmaar-v1'],
    reconnect: true,
    reconnectIntervalMs: 10000,
    maxReconnectAttempts: 5,
    pingIntervalMs: 15000,
    pongTimeoutMs: 5000,
    tls: {
      rejectUnauthorized: true,
      minVersion: 'TLSv1.3',
    },
    headers: {
      'Authorization': 'Bearer ${AUTH_TOKEN}',
      'X-Client-ID': '${CLIENT_ID}',
    },
  },
  tags: ['websocket', 'stream', 'secure', 'production'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Resilient WebSocket stream preset
 *
 * Maximum resilience configuration with aggressive reconnection.
 * Suitable for unreliable networks and critical connections.
 */
export const websocketStreamResilientPreset: SourcePreset = {
  id: 'websocket-stream-resilient',
  name: 'Resilient WebSocket Stream',
  description: 'Maximum resilience with aggressive reconnection',
  config: {
    type: 'websocket',
    url: '${WEBSOCKET_URL}',
    protocols: [],
    reconnect: true,
    reconnectIntervalMs: 1000,
    maxReconnectAttempts: -1, // Infinite
    reconnectBackoff: {
      initial: 1000,
      max: 30000,
      multiplier: 1.5,
    },
    pingIntervalMs: 10000,
    pongTimeoutMs: 5000,
    buffering: {
      enabled: true,
      maxSize: 1000,
    },
  },
  tags: ['websocket', 'stream', 'resilient', 'unreliable-network'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
