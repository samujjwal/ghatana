/**
 * @fileoverview Console Debug Sink Preset
 *
 * Standard configuration for console logging during development.
 */

import type { SinkPreset } from '../types';

/**
 * Console debug preset
 *
 * Logs events to console for development and debugging.
 * Suitable for local development only.
 */
export const consoleDebugPreset: SinkPreset = {
  id: 'console-debug',
  name: 'Console Debug Sink',
  description: 'Logs events to console for debugging',
  config: {
    type: 'console',
    format: 'pretty',
    includeTimestamp: true,
    includeMetadata: true,
    colorize: true,
    logLevel: 'debug',
  },
  tags: ['console', 'debug', 'development'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Console JSON preset
 *
 * Logs events as JSON for structured logging.
 * Suitable for log aggregation tools.
 */
export const consoleJsonPreset: SinkPreset = {
  id: 'console-json',
  name: 'Console JSON Sink',
  description: 'Logs events as JSON for structured logging',
  config: {
    type: 'console',
    format: 'json',
    includeTimestamp: true,
    includeMetadata: true,
    colorize: false,
    logLevel: 'info',
  },
  tags: ['console', 'json', 'structured'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Console minimal preset
 *
 * Minimal console output for production debugging.
 * Suitable for troubleshooting in production.
 */
export const consoleMinimalPreset: SinkPreset = {
  id: 'console-minimal',
  name: 'Console Minimal Sink',
  description: 'Minimal console output for production',
  config: {
    type: 'console',
    format: 'compact',
    includeTimestamp: false,
    includeMetadata: false,
    colorize: false,
    logLevel: 'warn',
  },
  tags: ['console', 'minimal', 'production'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
