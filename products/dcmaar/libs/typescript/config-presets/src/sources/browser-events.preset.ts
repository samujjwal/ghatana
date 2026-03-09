/**
 * @fileoverview Browser Events Source Preset
 *
 * Standard configuration for capturing browser events in the extension.
 */

import type { SourcePreset } from '../types';

/**
 * Standard browser events preset
 *
 * Captures common browser events for monitoring and analytics.
 * Suitable for most use cases with balanced sampling and filtering.
 */
export const browserEventsStandardPreset: SourcePreset = {
  id: 'browser-events-standard',
  name: 'Standard Browser Events',
  description: 'Captures common browser events for monitoring and analytics',
  config: {
    type: 'browser-events',
    events: ['tabs', 'navigation', 'performance'],
    sampling: {
      rate: 1.0,
      strategy: 'uniform',
    },
    filters: {
      includePatterns: ['https://*', 'http://*'],
      excludePatterns: ['chrome://*', 'about:*', 'chrome-extension://*'],
    },
    batching: {
      size: 50,
      flushIntervalMs: 5000,
    },
    performance: {
      captureWebVitals: true,
      captureResourceTiming: false,
      captureNavigationTiming: true,
    },
  },
  tags: ['browser', 'events', 'monitoring', 'standard'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Minimal browser events preset
 *
 * Captures only essential events with aggressive sampling.
 * Suitable for low-resource environments or privacy-focused deployments.
 */
export const browserEventsMinimalPreset: SourcePreset = {
  id: 'browser-events-minimal',
  name: 'Minimal Browser Events',
  description: 'Captures only essential events with aggressive sampling',
  config: {
    type: 'browser-events',
    events: ['tabs'],
    sampling: {
      rate: 0.1, // 10% sampling
      strategy: 'uniform',
    },
    filters: {
      includePatterns: ['https://*'],
      excludePatterns: ['*'],
    },
    batching: {
      size: 100,
      flushIntervalMs: 10000,
    },
    performance: {
      captureWebVitals: false,
      captureResourceTiming: false,
      captureNavigationTiming: false,
    },
  },
  tags: ['browser', 'events', 'minimal', 'privacy'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Comprehensive browser events preset
 *
 * Captures all available events with full detail.
 * Suitable for debugging, development, and detailed analytics.
 */
export const browserEventsComprehensivePreset: SourcePreset = {
  id: 'browser-events-comprehensive',
  name: 'Comprehensive Browser Events',
  description: 'Captures all available events with full detail',
  config: {
    type: 'browser-events',
    events: ['tabs', 'navigation', 'performance', 'interactions', 'windows'],
    sampling: {
      rate: 1.0,
      strategy: 'uniform',
    },
    filters: {
      includePatterns: ['*'],
      excludePatterns: [],
    },
    batching: {
      size: 25,
      flushIntervalMs: 2000,
    },
    performance: {
      captureWebVitals: true,
      captureResourceTiming: true,
      captureNavigationTiming: true,
    },
  },
  tags: ['browser', 'events', 'comprehensive', 'debug'],
  compatibility: {
    agent: false,
    desktop: false,
    extension: true,
  },
  version: '1.0.0',
};
