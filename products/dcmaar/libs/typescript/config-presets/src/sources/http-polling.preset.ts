/**
 * @fileoverview HTTP Polling Source Preset
 *
 * Standard configuration for polling HTTP endpoints.
 */

import type { SourcePreset } from '../types';

/**
 * Standard HTTP polling preset
 *
 * Balanced configuration for regular HTTP polling.
 * Suitable for most API polling scenarios.
 */
export const httpPollingStandardPreset: SourcePreset = {
  id: 'http-polling-standard',
  name: 'Standard HTTP Polling',
  description: 'Balanced HTTP polling with 30-second intervals',
  config: {
    type: 'http',
    url: '${CONFIG_URL}', // Template variable
    method: 'GET',
    pollIntervalMs: 30000,
    timeout: 10000,
    retries: 3,
    headers: {
      'Content-Type': 'application/json',
    },
    auth: {
      type: 'bearer',
      token: '${AUTH_TOKEN}',
    },
  },
  tags: ['http', 'polling', 'standard'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Fast HTTP polling preset
 *
 * High-frequency polling for real-time scenarios.
 * Suitable for dashboards and monitoring.
 */
export const httpPollingFastPreset: SourcePreset = {
  id: 'http-polling-fast',
  name: 'Fast HTTP Polling',
  description: 'High-frequency polling with 5-second intervals',
  config: {
    type: 'http',
    url: '${CONFIG_URL}',
    method: 'GET',
    pollIntervalMs: 5000,
    timeout: 3000,
    retries: 2,
    headers: {
      'Content-Type': 'application/json',
    },
  },
  tags: ['http', 'polling', 'fast', 'realtime'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Slow HTTP polling preset
 *
 * Low-frequency polling for resource-constrained scenarios.
 * Suitable for background sync and non-critical updates.
 */
export const httpPollingSlowPreset: SourcePreset = {
  id: 'http-polling-slow',
  name: 'Slow HTTP Polling',
  description: 'Low-frequency polling with 5-minute intervals',
  config: {
    type: 'http',
    url: '${CONFIG_URL}',
    method: 'GET',
    pollIntervalMs: 300000,
    timeout: 30000,
    retries: 5,
    headers: {
      'Content-Type': 'application/json',
    },
  },
  tags: ['http', 'polling', 'slow', 'background'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
