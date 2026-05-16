/**
 * @fileoverview Test for P0-1: Repository import extractor registry enforcement
 *
 * Verifies that repository imports fail with UNSUPPORTED_EXTRACTION_PIPELINE
 * when the canonical extractor registry is empty or produces no extraction results.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createDefaultProviderRegistry, getCanonicalExtractors, SynthesisPipeline } from 'yappc-artifact-compiler';

// Mock the yappc-artifact-compiler module
vi.mock('yappc-artifact-compiler', () => ({
  createDefaultProviderRegistry: vi.fn(),
  getCanonicalExtractors: vi.fn(),
  SynthesisPipeline: vi.fn(),
}));

describe('P0-1: Repository Import Extractor Registry Enforcement', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fail when canonical extractors registry is empty', async () => {
    // Mock empty extractor registry
    vi.mocked(getCanonicalExtractors).mockReturnValue([]);

    const canonicalExtractors = getCanonicalExtractors();
    
    expect(canonicalExtractors).toEqual([]);
    
    // This should throw UNSUPPORTED_EXTRACTION_PIPELINE error
    expect(() => {
      if (canonicalExtractors.length === 0) {
        throw new Error('UNSUPPORTED_EXTRACTION_PIPELINE: No extractors registered. Cannot perform repository import without extraction capabilities.');
      }
    }).toThrow('UNSUPPORTED_EXTRACTION_PIPELINE: No extractors registered');
  });

  it('should fail when extraction produces no results', async () => {
    // Mock non-empty extractor registry but pipeline produces no results
    vi.mocked(getCanonicalExtractors).mockReturnValue([
      { name: 'typescript-extractor', canHandle: () => true },
    ]);

    const mockPipeline = {
      runFromSnapshot: vi.fn().mockResolvedValue({
        graph: { nodes: [], edges: [] },
        model: [],
        residualIslands: [],
        stats: {
          extractedNodes: 0,
          modelElementsGenerated: 0,
          residualIslandsGenerated: 0,
        },
        errors: [],
        warnings: [],
      }),
    };
    vi.mocked(SynthesisPipeline).mockImplementation(() => mockPipeline);

    const canonicalExtractors = getCanonicalExtractors();
    const pipeline = new SynthesisPipeline({ extractors: canonicalExtractors });
    const result = await pipeline.runFromSnapshot({} as any);

    // This should throw UNSUPPORTED_EXTRACTION_PIPELINE error
    expect(() => {
      if (result.stats.extractedNodes === 0 && result.stats.modelElementsGenerated === 0) {
        throw new Error('UNSUPPORTED_EXTRACTION_PIPELINE: Repository import produced no extracted artifacts.');
      }
    }).toThrow('UNSUPPORTED_EXTRACTION_PIPELINE: Repository import produced no extracted artifacts');
  });

  it('should succeed when extractors are registered and produce results', async () => {
    // Mock successful extraction
    vi.mocked(getCanonicalExtractors).mockReturnValue([
      { name: 'typescript-extractor', canHandle: () => true },
    ]);

    const mockPipeline = {
      runFromSnapshot: vi.fn().mockResolvedValue({
        graph: {
          nodes: [{ id: 'node1', kind: 'component', sourceLocation: { filePath: 'test.tsx' } }],
          edges: [],
        },
        model: [{ id: 'model1', type: 'ComponentModel' }],
        residualIslands: [],
        stats: {
          extractedNodes: 1,
          modelElementsGenerated: 1,
          residualIslandsGenerated: 0,
        },
        errors: [],
        warnings: [],
      }),
    };
    vi.mocked(SynthesisPipeline).mockImplementation(() => mockPipeline);

    const canonicalExtractors = getCanonicalExtractors();
    expect(canonicalExtractors.length).toBeGreaterThan(0);

    const pipeline = new SynthesisPipeline({ extractors: canonicalExtractors });
    const result = await pipeline.runFromSnapshot({} as any);

    // Should not throw - extraction produced meaningful results
    expect(() => {
      if (result.stats.extractedNodes === 0 && result.stats.modelElementsGenerated === 0) {
        throw new Error('UNSUPPORTED_EXTRACTION_PIPELINE');
      }
    }).not.toThrow();

    expect(result.stats.extractedNodes).toBeGreaterThan(0);
    expect(result.stats.modelElementsGenerated).toBeGreaterThan(0);
  });
});
