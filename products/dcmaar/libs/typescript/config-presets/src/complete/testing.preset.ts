/**
 * @fileoverview Testing Complete Preset
 */

import type { CompletePreset } from '../types';
import { browserEventsMinimalPreset } from '../sources/browser-events.preset';
import { indexedDBDebugPreset } from '../sinks/indexeddb-local.preset';

export const testingPreset: CompletePreset = {
  id: 'testing-complete',
  name: 'Testing Configuration',
  description: 'Testing configuration with minimal latency',
  config: {
    sources: [browserEventsMinimalPreset.config],
    sinks: [indexedDBDebugPreset.config],
    processors: [],
    monitoring: { logLevel: 'warn', metricsEnabled: false },
    security: { piiRedaction: false },
  },
  tags: ['testing'],
  compatibility: { agent: false, desktop: false, extension: true },
  version: '1.0.0',
};
