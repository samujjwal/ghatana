/**
 * @fileoverview Development Complete Preset
 */

import type { CompletePreset } from '../types';
import { browserEventsComprehensivePreset } from '../sources/browser-events.preset';
import { indexedDBDebugPreset } from '../sinks/indexeddb-local.preset';
import { consoleDebugPreset } from '../sinks/console-debug.preset';

export const developmentPreset: CompletePreset = {
  id: 'development-complete',
  name: 'Development Configuration',
  description: 'Full development configuration with debug logging',
  config: {
    sources: [browserEventsComprehensivePreset.config],
    sinks: [indexedDBDebugPreset.config, consoleDebugPreset.config],
    processors: [
      { id: 'enrich-1', type: 'enrich', order: 1, enabled: true, config: { enrichments: [{ type: 'timestamp', field: 'metadata.processedAt' }] } },
    ],
    monitoring: { logLevel: 'debug', metricsEnabled: true },
    security: { piiRedaction: false },
  },
  tags: ['development', 'debug'],
  compatibility: { agent: false, desktop: false, extension: true },
  version: '1.0.0',
};
