/**
 * @fileoverview Tests for model mapping enhancements.
 *
 * Phase 3 test: Validates that model elements include:
 * - graphNodeIds for model-to-graph tracing
 * - sourceRefs for model-to-source tracing
 * - residualIslandIds for model-to-residual tracing
 */

import { describe, it, expect } from 'vitest';
import { ModelElementBaseSchema } from '../types';

describe('ModelElementBaseSchema', () => {
  it('should validate a model element with all Phase 3 mapping fields', () => {
    const element = {
      id: 'model-1',
      name: 'Button',
      confidence: 0.9,
      provenance: {
        extractorId: 'typescript-component',
        extractorVersion: '1.0.0',
        sourcePaths: ['Button.tsx'],
        kind: 'exact' as const,
        extractedAt: '2024-01-01T00:00:00Z',
      },
      securityFlags: [],
      privacyFlags: [],
      tags: [],
      graphNodeIds: ['graph-node-1', 'graph-node-2'],
      sourceRefs: [
        {
          filePath: 'Button.tsx',
          startLine: 1,
          startColumn: 1,
          endLine: 20,
          endColumn: 1,
        },
      ],
      residualIslandIds: ['residual-1'],
    };

    const result = ModelElementBaseSchema.safeParse(element);
    expect(result.success).toBe(true);
  });

  it('should require graphNodeIds array', () => {
    const element = {
      id: 'model-1',
      name: 'Button',
      confidence: 0.9,
      provenance: {
        extractorId: 'typescript-component',
        extractorVersion: '1.0.0',
        sourcePaths: ['Button.tsx'],
        kind: 'exact' as const,
        extractedAt: '2024-01-01T00:00:00Z',
      },
      securityFlags: [],
      privacyFlags: [],
      tags: [],
      // Missing graphNodeIds
      sourceRefs: [],
      residualIslandIds: [],
    };

    const result = ModelElementBaseSchema.safeParse(element);
    expect(result.success).toBe(false);
  });

  it('should require sourceRefs array', () => {
    const element = {
      id: 'model-1',
      name: 'Button',
      confidence: 0.9,
      provenance: {
        extractorId: 'typescript-component',
        extractorVersion: '1.0.0',
        sourcePaths: ['Button.tsx'],
        kind: 'exact' as const,
        extractedAt: '2024-01-01T00:00:00Z',
      },
      securityFlags: [],
      privacyFlags: [],
      tags: [],
      graphNodeIds: ['graph-node-1'],
      // Missing sourceRefs
      residualIslandIds: [],
    };

    const result = ModelElementBaseSchema.safeParse(element);
    expect(result.success).toBe(false);
  });

  it('should require residualIslandIds array', () => {
    const element = {
      id: 'model-1',
      name: 'Button',
      confidence: 0.9,
      provenance: {
        extractorId: 'typescript-component',
        extractorVersion: '1.0.0',
        sourcePaths: ['Button.tsx'],
        kind: 'exact' as const,
        extractedAt: '2024-01-01T00:00:00Z',
      },
      securityFlags: [],
      privacyFlags: [],
      tags: [],
      graphNodeIds: ['graph-node-1'],
      sourceRefs: [],
      // Missing residualIslandIds
    };

    const result = ModelElementBaseSchema.safeParse(element);
    expect(result.success).toBe(false);
  });

  it('should allow empty arrays for mapping fields', () => {
    const element = {
      id: 'model-1',
      name: 'Button',
      confidence: 0.9,
      provenance: {
        extractorId: 'typescript-component',
        extractorVersion: '1.0.0',
        sourcePaths: ['Button.tsx'],
        kind: 'exact' as const,
        extractedAt: '2024-01-01T00:00:00Z',
      },
      securityFlags: [],
      privacyFlags: [],
      tags: [],
      graphNodeIds: [],
      sourceRefs: [],
      residualIslandIds: [],
    };

    const result = ModelElementBaseSchema.safeParse(element);
    expect(result.success).toBe(true);
  });

  it('should validate sourceRef with range information', () => {
    const element = {
      id: 'model-1',
      name: 'Button',
      confidence: 0.9,
      provenance: {
        extractorId: 'typescript-component',
        extractorVersion: '1.0.0',
        sourcePaths: ['Button.tsx'],
        kind: 'exact' as const,
        extractedAt: '2024-01-01T00:00:00Z',
      },
      securityFlags: [],
      privacyFlags: [],
      tags: [],
      graphNodeIds: ['graph-node-1'],
      sourceRefs: [
        {
          filePath: 'Button.tsx',
          startLine: 5,
          startColumn: 10,
          endLine: 15,
          endColumn: 20,
        },
      ],
      residualIslandIds: [],
    };

    const result = ModelElementBaseSchema.safeParse(element);
    expect(result.success).toBe(true);
  });
});
