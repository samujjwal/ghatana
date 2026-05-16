import { describe, expect, it } from 'vitest';
import { ProvenanceIndex } from '../provenanceIndex';
import type { SemanticModelElement } from '../../model/types';

describe('ProvenanceIndex', () => {
  it('indexes semantic model elements', () => {
    const index = new ProvenanceIndex();
    const element: SemanticModelElement = {
      id: 'test-element',
      name: 'TestElement',
      confidence: 0.9,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/TestElement.tsx'],
        kind: 'exact',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    index.index(element);

    const entry = index.get('test-element');
    expect(entry).toBeDefined();
    expect(entry?.elementId).toBe('test-element');
    expect(entry?.extractorId).toBe('test-extractor');
  });

  it('finds elements by source path', () => {
    const index = new ProvenanceIndex();
    const element1: SemanticModelElement = {
      id: 'element-1',
      name: 'Element1',
      confidence: 0.9,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/Element1.tsx', 'src/Element1.test.tsx'],
        kind: 'exact',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    const element2: SemanticModelElement = {
      id: 'element-2',
      name: 'Element2',
      confidence: 0.8,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/Element2.tsx'],
        kind: 'inferred',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    index.index(element1);
    index.index(element2);

    const elementsAtPath = index.findBySourcePath('src/Element1.tsx');
    expect(elementsAtPath).toContain('element-1');
    expect(elementsAtPath).not.toContain('element-2');
  });

  it('finds elements by extractor', () => {
    const index = new ProvenanceIndex();
    const element: SemanticModelElement = {
      id: 'test-element',
      name: 'TestElement',
      confidence: 0.9,
      provenance: {
        extractorId: 'react-extractor',
        extractorVersion: '2.0.0',
        sourcePaths: ['src/TestElement.tsx'],
        kind: 'exact',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    index.index(element);

    const byExtractor = index.findByExtractor('react-extractor', '2.0.0');
    expect(byExtractor).toContain('test-element');

    const byExtractorAnyVersion = index.findByExtractor('react-extractor');
    expect(byExtractorAnyVersion).toContain('test-element');
  });

  it('finds elements by provenance kind', () => {
    const index = new ProvenanceIndex();
    const exactElement: SemanticModelElement = {
      id: 'exact-element',
      name: 'ExactElement',
      confidence: 1.0,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/ExactElement.tsx'],
        kind: 'exact',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    const inferredElement: SemanticModelElement = {
      id: 'inferred-element',
      name: 'InferredElement',
      confidence: 0.7,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/InferredElement.tsx'],
        kind: 'inferred',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    index.index(exactElement);
    index.index(inferredElement);

    const exactElements = index.findByKind('exact');
    expect(exactElements).toContain('exact-element');
    expect(exactElements).not.toContain('inferred-element');
  });

  it('provides statistics', () => {
    const index = new ProvenanceIndex();
    const element: SemanticModelElement = {
      id: 'test-element',
      name: 'TestElement',
      confidence: 0.9,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/TestElement.tsx'],
        kind: 'exact',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    index.index(element);

    const stats = index.getStats();
    expect(stats.totalEntries).toBe(1);
    expect(stats.totalSourcePaths).toBe(1);
    expect(stats.totalExtractors).toBe(1);
    expect(stats.kindDistribution.exact).toBe(1);
  });

  it('clears the index', () => {
    const index = new ProvenanceIndex();
    const element: SemanticModelElement = {
      id: 'test-element',
      name: 'TestElement',
      confidence: 0.9,
      provenance: {
        extractorId: 'test-extractor',
        extractorVersion: '1.0.0',
        sourcePaths: ['src/TestElement.tsx'],
        kind: 'exact',
        extractedAt: new Date().toISOString(),
      },
    } as SemanticModelElement;

    index.index(element);
    expect(index.get('test-element')).toBeDefined();

    index.clear();
    expect(index.get('test-element')).toBeUndefined();
  });
});
