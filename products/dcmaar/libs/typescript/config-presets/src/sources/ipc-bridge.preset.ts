/**
 * @fileoverview IPC Bridge Source Preset
 *
 * Standard configuration for inter-process communication.
 */

import type { SourcePreset } from '../types';

/**
 * Standard IPC bridge preset
 *
 * Balanced configuration for IPC connections.
 * Suitable for extension-desktop and extension-agent communication.
 */
export const ipcBridgeStandardPreset: SourcePreset = {
  id: 'ipc-bridge-standard',
  name: 'Standard IPC Bridge',
  description: 'Balanced IPC configuration for extension communication',
  config: {
    type: 'ipc',
    channel: 'dcmaar-bridge',
    timeout: 5000,
    retries: 3,
    reconnect: true,
    reconnectIntervalMs: 5000,
    maxReconnectAttempts: 10,
  },
  tags: ['ipc', 'bridge', 'standard'],
  compatibility: {
    agent: false,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Fast IPC bridge preset
 *
 * Low-latency configuration for high-frequency communication.
 * Suitable for real-time data streaming.
 */
export const ipcBridgeFastPreset: SourcePreset = {
  id: 'ipc-bridge-fast',
  name: 'Fast IPC Bridge',
  description: 'Low-latency IPC for high-frequency communication',
  config: {
    type: 'ipc',
    channel: 'dcmaar-bridge-fast',
    timeout: 1000,
    retries: 2,
    reconnect: true,
    reconnectIntervalMs: 1000,
    maxReconnectAttempts: 20,
    buffering: {
      enabled: true,
      maxSize: 100,
    },
  },
  tags: ['ipc', 'bridge', 'fast', 'low-latency'],
  compatibility: {
    agent: false,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Secure IPC bridge preset
 *
 * Enhanced security with encryption and authentication.
 * Suitable for production environments.
 */
export const ipcBridgeSecurePreset: SourcePreset = {
  id: 'ipc-bridge-secure',
  name: 'Secure IPC Bridge',
  description: 'Secure IPC with encryption and authentication',
  config: {
    type: 'ipc',
    channel: 'dcmaar-bridge-secure',
    timeout: 10000,
    retries: 5,
    reconnect: true,
    reconnectIntervalMs: 10000,
    maxReconnectAttempts: 5,
    encryption: {
      enabled: true,
      algorithm: 'aes-256-gcm',
    },
    auth: {
      type: 'token',
      token: '${IPC_AUTH_TOKEN}',
    },
  },
  tags: ['ipc', 'bridge', 'secure', 'production'],
  compatibility: {
    agent: false,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
