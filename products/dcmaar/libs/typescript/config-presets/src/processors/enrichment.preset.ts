/**
 * @fileoverview Enrichment Processor Preset
 *
 * Standard configuration for event enrichment.
 */

import type { ProcessorPreset } from '../types';

/**
 * Standard enrichment preset
 */
export const enrichmentStandardPreset: ProcessorPreset = {
  id: 'enrichment-standard',
  name: 'Standard Enrichment',
  description: 'Common enrichments for event metadata',
  config: {
    type: 'enrich',
    enrichments: [
      { type: 'timestamp', field: 'metadata.enrichedAt' },
      { type: 'uuid', field: 'metadata.correlationId' },
      { type: 'hash', field: 'metadata.eventHash', algorithm: 'sha256', source: ['id', 'type', 'timestamp'] },
    ],
  },
  tags: ['enrichment', 'standard'],
  compatibility: { agent: true, desktop: true, extension: true },
  version: '1.0.0',
};

/**
 * Comprehensive enrichment preset
 */
export const enrichmentComprehensivePreset: ProcessorPreset = {
  id: 'enrichment-comprehensive',
  name: 'Comprehensive Enrichment',
  description: 'Full enrichment with all metadata',
  config: {
    type: 'enrich',
    enrichments: [
      { type: 'timestamp', field: 'metadata.enrichedAt' },
      { type: 'uuid', field: 'metadata.correlationId' },
      { type: 'uuid', field: 'metadata.traceId' },
      { type: 'hash', field: 'metadata.eventHash', algorithm: 'sha256' },
      { type: 'environment', field: 'metadata.environment' },
    ],
  },
  tags: ['enrichment', 'comprehensive'],
  compatibility: { agent: true, desktop: true, extension: true },
  version: '1.0.0',
};
