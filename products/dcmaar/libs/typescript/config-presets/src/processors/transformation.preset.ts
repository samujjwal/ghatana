/**
 * @fileoverview Transformation Processor Preset
 *
 * Standard configuration for event transformation.
 */

import type { ProcessorPreset } from '../types';

/**
 * Standard transformation preset
 *
 * Common transformations for event normalization.
 * Suitable for most use cases.
 */
export const transformationStandardPreset: ProcessorPreset = {
  id: 'transformation-standard',
  name: 'Standard Transformation',
  description: 'Common transformations for event normalization',
  config: {
    type: 'transform',
    mappings: [
      {
        from: '$.timestamp',
        to: '$.metadata.originalTimestamp',
      },
      {
        from: '$.type',
        to: '$.metadata.eventType',
      },
    ],
    addFields: {
      'metadata.processedAt': '${NOW}',
      'metadata.version': '1.0.0',
    },
  },
  tags: ['transformation', 'standard', 'normalization'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Minimal transformation preset
 *
 * Minimal transformations for performance.
 * Suitable for high-throughput scenarios.
 */
export const transformationMinimalPreset: ProcessorPreset = {
  id: 'transformation-minimal',
  name: 'Minimal Transformation',
  description: 'Minimal transformations for performance',
  config: {
    type: 'transform',
    addFields: {
      'metadata.processedAt': '${NOW}',
    },
  },
  tags: ['transformation', 'minimal', 'performance'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Flattening transformation preset
 *
 * Flattens nested structures for easier querying.
 * Suitable for analytics and reporting.
 */
export const transformationFlattenPreset: ProcessorPreset = {
  id: 'transformation-flatten',
  name: 'Flattening Transformation',
  description: 'Flattens nested structures',
  config: {
    type: 'transform',
    flatten: {
      enabled: true,
      separator: '.',
      maxDepth: 5,
    },
    removeFields: ['metadata._internal'],
  },
  tags: ['transformation', 'flatten', 'analytics'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
