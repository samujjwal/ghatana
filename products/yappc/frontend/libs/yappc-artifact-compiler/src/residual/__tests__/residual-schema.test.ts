/**
 * @fileoverview Tests for residual schema enhancements.
 *
 * Phase 3 test: Validates that residual schema includes:
 * - rawFragmentRef for fragment identity
 * - checksum for content integrity
 * - risk level assessment
 * - relatedGraphNodeIds for graph tracing
 */

import { describe, it, expect } from 'vitest';
import { ResidualIslandSchema, RiskLevelSchema } from '../types';

describe('ResidualIslandSchema', () => {
  it('should validate a valid residual island with all Phase 3 fields', () => {
    const residual = {
      id: 'residual-1',
      kind: 'code' as const,
      originalSource: 'const complex = fn => fn.map(x => x * 2)',
      normalizedSummary: 'Complex function mapping pattern',
      reasonUnmodeled: 'Complex nested arrow functions',
      reviewRequired: true,
      regenerationStrategy: 'verbatim-preserve' as const,
      sourceLocation: {
        filePath: 'src/utils.ts',
        startLine: 10,
        startColumn: 5,
        endLine: 12,
        endColumn: 30,
      },
      rawFragmentRef: 'fragment-abc123',
      checksum: 'a1b2c3d4e5f6',
      risk: 'high' as const,
      relatedGraphNodeIds: ['node-1', 'node-2'],
    };

    const result = ResidualIslandSchema.safeParse(residual);
    expect(result.success).toBe(true);
  });

  it('should require rawFragmentRef', () => {
    const residual = {
      id: 'residual-1',
      kind: 'code' as const,
      originalSource: 'const complex = fn => fn.map(x => x * 2)',
      normalizedSummary: 'Complex function mapping pattern',
      reasonUnmodeled: 'Complex nested arrow functions',
      reviewRequired: true,
      regenerationStrategy: 'verbatim-preserve' as const,
      sourceLocation: {
        filePath: 'src/utils.ts',
        startLine: 10,
        startColumn: 5,
        endLine: 12,
        endColumn: 30,
      },
      // Missing rawFragmentRef
      checksum: 'a1b2c3d4e5f6',
      risk: 'high' as const,
      relatedGraphNodeIds: ['node-1'],
    };

    const result = ResidualIslandSchema.safeParse(residual);
    expect(result.success).toBe(false);
  });

  it('should require checksum', () => {
    const residual = {
      id: 'residual-1',
      kind: 'code' as const,
      originalSource: 'const complex = fn => fn.map(x => x * 2)',
      normalizedSummary: 'Complex function mapping pattern',
      reasonUnmodeled: 'Complex nested arrow functions',
      reviewRequired: true,
      regenerationStrategy: 'verbatim-preserve' as const,
      sourceLocation: {
        filePath: 'src/utils.ts',
        startLine: 10,
        startColumn: 5,
        endLine: 12,
        endColumn: 30,
      },
      rawFragmentRef: 'fragment-abc123',
      // Missing checksum
      risk: 'high' as const,
      relatedGraphNodeIds: ['node-1'],
    };

    const result = ResidualIslandSchema.safeParse(residual);
    expect(result.success).toBe(false);
  });

  it('should require risk level', () => {
    const residual = {
      id: 'residual-1',
      kind: 'code' as const,
      originalSource: 'const complex = fn => fn.map(x => x * 2)',
      normalizedSummary: 'Complex function mapping pattern',
      reasonUnmodeled: 'Complex nested arrow functions',
      reviewRequired: true,
      regenerationStrategy: 'verbatim-preserve' as const,
      sourceLocation: {
        filePath: 'src/utils.ts',
        startLine: 10,
        startColumn: 5,
        endLine: 12,
        endColumn: 30,
      },
      rawFragmentRef: 'fragment-abc123',
      checksum: 'a1b2c3d4e5f6',
      // Missing risk
      relatedGraphNodeIds: ['node-1'],
    };

    const result = ResidualIslandSchema.safeParse(residual);
    expect(result.success).toBe(false);
  });

  it('should require relatedGraphNodeIds array', () => {
    const residual = {
      id: 'residual-1',
      kind: 'code' as const,
      originalSource: 'const complex = fn => fn.map(x => x * 2)',
      normalizedSummary: 'Complex function mapping pattern',
      reasonUnmodeled: 'Complex nested arrow functions',
      reviewRequired: true,
      regenerationStrategy: 'verbatim-preserve' as const,
      sourceLocation: {
        filePath: 'src/utils.ts',
        startLine: 10,
        startColumn: 5,
        endLine: 12,
        endColumn: 30,
      },
      rawFragmentRef: 'fragment-abc123',
      checksum: 'a1b2c3d4e5f6',
      risk: 'high' as const,
      // Missing relatedGraphNodeIds
    };

    const result = ResidualIslandSchema.safeParse(residual);
    expect(result.success).toBe(false);
  });

  it('should allow empty relatedGraphNodeIds array', () => {
    const residual = {
      id: 'residual-1',
      kind: 'code' as const,
      originalSource: 'const complex = fn => fn.map(x => x * 2)',
      normalizedSummary: 'Complex function mapping pattern',
      reasonUnmodeled: 'Complex nested arrow functions',
      reviewRequired: true,
      regenerationStrategy: 'verbatim-preserve' as const,
      sourceLocation: {
        filePath: 'src/utils.ts',
        startLine: 10,
        startColumn: 5,
        endLine: 12,
        endColumn: 30,
      },
      rawFragmentRef: 'fragment-abc123',
      checksum: 'a1b2c3d4e5f6',
      risk: 'low' as const,
      relatedGraphNodeIds: [],
    };

    const result = ResidualIslandSchema.safeParse(residual);
    expect(result.success).toBe(true);
  });
});

describe('RiskLevelSchema', () => {
  it('should accept valid risk levels', () => {
    expect(RiskLevelSchema.safeParse('low').success).toBe(true);
    expect(RiskLevelSchema.safeParse('medium').success).toBe(true);
    expect(RiskLevelSchema.safeParse('high').success).toBe(true);
    expect(RiskLevelSchema.safeParse('critical').success).toBe(true);
  });

  it('should reject invalid risk levels', () => {
    expect(RiskLevelSchema.safeParse('invalid').success).toBe(false);
    expect(RiskLevelSchema.safeParse('LOW').success).toBe(false);
    expect(RiskLevelSchema.safeParse('').success).toBe(false);
  });
});
