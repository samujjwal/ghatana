/**
 * @fileoverview WebSocket Push Sink Preset
 *
 * Standard configuration for WebSocket push connections.
 */

import type { SinkPreset } from '../types';

/**
 * Standard WebSocket push preset
 *
 * Balanced configuration for WebSocket push.
 * Suitable for real-time data streaming.
 */
export const websocketPushStandardPreset: SinkPreset = {
  id: 'websocket-push-standard',
  name: 'Standard WebSocket Push',
  description: 'Balanced WebSocket push with auto-reconnect',
  config: {
    type: 'websocket',
    url: '${WEBSOCKET_URL}',
    batchSize: 25,
    flushIntervalMs: 2000,
    reconnect: true,
    reconnectIntervalMs: 5000,
    maxReconnectAttempts: 10,
    headers: {
      'Authorization': 'Bearer ${AUTH_TOKEN}',
    },
  },
  tags: ['websocket', 'push', 'standard', 'realtime'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Real-time WebSocket push preset
 *
 * Minimal latency configuration for real-time scenarios.
 * Suitable for live dashboards and monitoring.
 */
export const websocketPushRealtimePreset: SinkPreset = {
  id: 'websocket-push-realtime',
  name: 'Real-time WebSocket Push',
  description: 'Minimal latency for real-time streaming',
  config: {
    type: 'websocket',
    url: '${WEBSOCKET_URL}',
    batchSize: 1,
    flushIntervalMs: 100,
    reconnect: true,
    reconnectIntervalMs: 1000,
    maxReconnectAttempts: 20,
    buffering: {
      enabled: true,
      maxSize: 100,
    },
  },
  tags: ['websocket', 'push', 'realtime', 'low-latency'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Resilient WebSocket push preset
 *
 * Maximum resilience with buffering and reconnection.
 * Suitable for unreliable networks.
 */
export const websocketPushResilientPreset: SinkPreset = {
  id: 'websocket-push-resilient',
  name: 'Resilient WebSocket Push',
  description: 'Maximum resilience with buffering',
  config: {
    type: 'websocket',
    url: '${WEBSOCKET_URL}',
    batchSize: 50,
    flushIntervalMs: 5000,
    reconnect: true,
    reconnectIntervalMs: 1000,
    maxReconnectAttempts: -1, // Infinite
    reconnectBackoff: {
      initial: 1000,
      max: 30000,
      multiplier: 1.5,
    },
    buffering: {
      enabled: true,
      maxSize: 1000,
      persistToDisk: true,
    },
  },
  tags: ['websocket', 'push', 'resilient', 'unreliable-network'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
