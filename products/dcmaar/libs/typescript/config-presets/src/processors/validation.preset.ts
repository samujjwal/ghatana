/**
 * @fileoverview Validation Processor Preset
 *
 * Standard configuration for event validation.
 */

import type { ProcessorPreset } from '../types';

/**
 * Strict validation preset
 *
 * Strict schema validation with error rejection.
 * Suitable for production environments.
 */
export const validationStrictPreset: ProcessorPreset = {
  id: 'validation-strict',
  name: 'Strict Validation',
  description: 'Strict schema validation with error rejection',
  config: {
    type: 'validate',
    schema: {
      type: 'object',
      required: ['id', 'type', 'timestamp', 'payload'],
      properties: {
        id: { type: 'string', minLength: 1 },
        type: { type: 'string', minLength: 1 },
        timestamp: { type: 'number', minimum: 0 },
        payload: { type: 'object' },
        metadata: { type: 'object' },
      },
    },
    onError: 'reject',
    logErrors: true,
  },
  tags: ['validation', 'strict', 'production'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Lenient validation preset
 *
 * Lenient validation with warning on errors.
 * Suitable for development and testing.
 */
export const validationLenientPreset: ProcessorPreset = {
  id: 'validation-lenient',
  name: 'Lenient Validation',
  description: 'Lenient validation with warnings',
  config: {
    type: 'validate',
    schema: {
      type: 'object',
      properties: {
        id: { type: 'string' },
        type: { type: 'string' },
        timestamp: { type: 'number' },
        payload: {},
      },
    },
    onError: 'warn',
    logErrors: true,
    allowAdditionalProperties: true,
  },
  tags: ['validation', 'lenient', 'development'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};

/**
 * Type validation preset
 *
 * Validates event types against allowed list.
 * Suitable for filtering unknown event types.
 */
export const validationTypePreset: ProcessorPreset = {
  id: 'validation-type',
  name: 'Type Validation',
  description: 'Validates event types against allowed list',
  config: {
    type: 'validate',
    allowedTypes: [
      'tab.created',
      'tab.updated',
      'tab.removed',
      'navigation.completed',
      'performance.metrics',
    ],
    onError: 'filter',
    logErrors: false,
  },
  tags: ['validation', 'type', 'filter'],
  compatibility: {
    agent: true,
    desktop: true,
    extension: true,
  },
  version: '1.0.0',
};
