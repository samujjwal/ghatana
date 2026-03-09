/**
 * @fileoverview Production Complete Preset
 */

import type { CompletePreset } from '../types';
import { browserEventsStandardPreset } from '../sources/browser-events.preset';
import { indexedDBStandardPreset } from '../sinks/indexeddb-local.preset';

export const productionPreset: CompletePreset = {
  id: 'production-complete',
  name: 'Production Configuration',
  description: 'Production-ready configuration',
  config: {
    sources: [browserEventsStandardPreset.config],
    sinks: [indexedDBStandardPreset.config],
    processors: [
      { id: 'validate-1', type: 'validate', order: 1, enabled: true, config: { schema: { type: 'object' }, onError: 'reject' } },
      { id: 'redact-1', type: 'redact', order: 2, enabled: true, config: { patterns: [{ type: 'email', replacement: '[EMAIL]' }] } },
      { id: 'enrich-1', type: 'enrich', order: 3, enabled: true, config: { enrichments: [{ type: 'timestamp', field: 'metadata.processedAt' }] } },
    ],
    monitoring: { logLevel: 'info', metricsEnabled: true },
    security: { piiRedaction: true },
  },
  tags: ['production'],
  compatibility: { agent: false, desktop: false, extension: true },
  version: '1.0.0',
};
