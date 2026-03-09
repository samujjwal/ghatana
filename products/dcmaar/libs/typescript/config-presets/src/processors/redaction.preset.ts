/**
 * @fileoverview Redaction Processor Preset
 */

import type { ProcessorPreset } from '../types';

export const redactionStandardPreset: ProcessorPreset = {
  id: 'redaction-standard',
  name: 'Standard Redaction',
  description: 'Standard PII redaction',
  config: {
    type: 'redact',
    patterns: [
      { type: 'email', replacement: '[EMAIL]' },
      { type: 'phone', replacement: '[PHONE]' },
      { type: 'ssn', replacement: '[SSN]' },
      { type: 'credit-card', replacement: '[CC]' },
    ],
  },
  tags: ['redaction', 'pii'],
  compatibility: { agent: true, desktop: true, extension: true },
  version: '1.0.0',
};

export const redactionStrictPreset: ProcessorPreset = {
  id: 'redaction-strict',
  name: 'Strict Redaction',
  description: 'Strict PII redaction',
  config: {
    type: 'redact',
    patterns: [
      { type: 'email', replacement: '[REDACTED]' },
      { type: 'phone', replacement: '[REDACTED]' },
      { type: 'ssn', replacement: '[REDACTED]' },
      { type: 'credit-card', replacement: '[REDACTED]' },
      { type: 'ip-address', replacement: '[REDACTED]' },
      { type: 'custom', pattern: '\\b[A-Z]{2}\\d{6}\\b', replacement: '[ID]' },
    ],
    removeFields: ['password', 'token', 'secret', 'apiKey'],
  },
  tags: ['redaction', 'strict', 'pii'],
  compatibility: { agent: true, desktop: true, extension: true },
  version: '1.0.0',
};
