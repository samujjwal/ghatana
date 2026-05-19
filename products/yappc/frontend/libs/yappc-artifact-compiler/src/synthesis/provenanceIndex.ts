/**
 * @fileoverview Provenance Index — tracks extraction sources and lineage.
 *
 * This module provides an index that maps semantic model elements back to
 * their source extraction contexts, enabling auditability and change tracking.
 */

import type { GraphNode } from '../graph/types';
import type { SemanticModelElement } from '../model/types';

// ============================================================================
// Provenance Entry
// ============================================================================

export interface ProvenanceEntry {
  /** Element ID */
  elementId: string;
  /** Source file paths where this element was extracted */
  sourcePaths: readonly string[];
  /** Extractor ID that produced this element */
  extractorId: string;
  /** Extractor version */
  extractorVersion: string;
  /** Extraction timestamp */
  extractedAt: string;
  /** Provenance kind (exact, inferred, synthesized, manual, assumed) */
  kind: 'exact' | 'inferred' | 'synthesized' | 'manual' | 'assumed';
  /** Original graph node ID (if available) */
  graphNodeId: string | undefined;
}

// ============================================================================
// Provenance Index
// ============================================================================

export class ProvenanceIndex {
  private readonly entries = new Map<string, ProvenanceEntry>();
  private readonly pathToElements = new Map<string, Set<string>>();
  private readonly extractorToElements = new Map<string, Set<string>>();

  /**
   * Index a semantic model element's provenance.
   */
  index(element: SemanticModelElement, graphNodeId?: string): void {
    const entry: ProvenanceEntry = {
      elementId: element.id,
      sourcePaths: element.provenance.sourcePaths,
      extractorId: element.provenance.extractorId,
      extractorVersion: element.provenance.extractorVersion,
      extractedAt: element.provenance.extractedAt,
      kind: element.provenance.kind,
      graphNodeId,
    };

    this.entries.set(element.id, entry);

    // Build reverse indices
    for (const path of element.provenance.sourcePaths) {
      let elements = this.pathToElements.get(path);
      if (!elements) {
        elements = new Set();
        this.pathToElements.set(path, elements);
      }
      elements.add(element.id);
    }

    const extractorKey = `${element.provenance.extractorId}@${element.provenance.extractorVersion}`;
    let extractorElements = this.extractorToElements.get(extractorKey);
    if (!extractorElements) {
      extractorElements = new Set();
      this.extractorToElements.set(extractorKey, extractorElements);
    }
    extractorElements.add(element.id);
  }

  /**
   * Get provenance entry for an element.
   */
  get(elementId: string): ProvenanceEntry | undefined {
    return this.entries.get(elementId);
  }

  /**
   * Find all elements extracted from a specific source file.
   */
  findBySourcePath(path: string): readonly string[] {
    const elements = this.pathToElements.get(path);
    return elements ? [...elements] : [];
  }

  /**
   * Find all elements extracted by a specific extractor version.
   */
  findByExtractor(extractorId: string, version?: string): readonly string[] {
    if (version) {
      const key = `${extractorId}@${version}`;
      const elements = this.extractorToElements.get(key);
      return elements ? [...elements] : [];
    }

    // Find all versions of the extractor
    const matchingKeys = [...this.extractorToElements.keys()].filter(key =>
      key.startsWith(`${extractorId}@`)
    );
    const elementSet = new Set<string>();
    for (const key of matchingKeys) {
      const elements = this.extractorToElements.get(key);
      if (elements) {
        elements.forEach(id => elementSet.add(id));
      }
    }
    return [...elementSet];
  }

  /**
   * Find all elements of a specific provenance kind.
   */
  findByKind(kind: ProvenanceEntry['kind']): readonly string[] {
    const matching: string[] = [];
    for (const entry of this.entries.values()) {
      if (entry.kind === kind) {
        matching.push(entry.elementId);
      }
    }
    return matching;
  }

  /**
   * Get all provenance entries.
   */
  getAll(): readonly ProvenanceEntry[] {
    return [...this.entries.values()];
  }

  /**
   * Clear the index.
   */
  clear(): void {
    this.entries.clear();
    this.pathToElements.clear();
    this.extractorToElements.clear();
  }

  /**
   * Get index statistics.
   */
  getStats(): {
    totalEntries: number;
    totalSourcePaths: number;
    totalExtractors: number;
    kindDistribution: Record<string, number>;
  } {
    const kindDistribution: Record<string, number> = {
      exact: 0,
      inferred: 0,
      synthesized: 0,
      manual: 0,
      assumed: 0,
    };

    for (const entry of this.entries.values()) {
      const kind = entry.kind;
      const current = kindDistribution[kind] ?? 0;
      kindDistribution[kind] = current + 1;
    }

    return {
      totalEntries: this.entries.size,
      totalSourcePaths: this.pathToElements.size,
      totalExtractors: this.extractorToElements.size,
      kindDistribution,
    };
  }

  /**
   * Build provenance index from a list of semantic model elements.
   */
  static fromElements(elements: readonly SemanticModelElement[]): ProvenanceIndex {
    const index = new ProvenanceIndex();
    for (const element of elements) {
      index.index(element);
    }
    return index;
  }

  /**
   * Build provenance index from graph nodes and synthesized elements.
   */
  static fromGraphNodes(nodes: readonly GraphNode[]): ProvenanceIndex {
    const index = new ProvenanceIndex();
    for (const node of nodes) {
      const entry: ProvenanceEntry = {
        elementId: node.id,
        sourcePaths: [(node.sourceLocation?.filePath ?? '')],
        extractorId: node.extractorId,
        extractorVersion: node.extractorVersion,
        extractedAt: new Date().toISOString(),
        kind: node.provenance,
        graphNodeId: node.id,
      };
      index.entries.set(node.id, entry);

      // Build reverse indices
      for (const path of entry.sourcePaths) {
        let elements = index.pathToElements.get(path);
        if (!elements) {
          elements = new Set();
          index.pathToElements.set(path, elements);
        }
        elements.add(node.id);
      }

      const extractorKey = `${node.extractorId}@${node.extractorVersion}`;
      let extractorElements = index.extractorToElements.get(extractorKey);
      if (!extractorElements) {
        extractorElements = new Set();
        index.extractorToElements.set(extractorKey, extractorElements);
      }
      extractorElements.add(node.id);
    }
    return index;
  }
}
