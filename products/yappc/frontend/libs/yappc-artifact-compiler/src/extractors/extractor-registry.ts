/**
 * @fileoverview Extractor runtime registry.
 *
 * Provides a centralized registry of all available extractors with their capabilities.
 * Enables runtime discovery and filtering of extractors by language, framework, or artifact kind.
 */

import type { ArtifactExtractor } from './types';
import type { ArtifactRecord } from '../inventory/types';

// ============================================================================
// Extractor Registry
// ============================================================================

export interface ExtractorCapabilities {
  readonly id: string;
  readonly version: string;
  readonly supportedKinds: readonly string[];
  readonly supportedLanguages: readonly string[];
  readonly supportedFrameworks: readonly string[];
}

export class ExtractorRegistry {
  private readonly extractors = new Map<string, ArtifactExtractor>();

  /**
   * Register an extractor instance.
   */
  register(extractor: ArtifactExtractor): void {
    const id = extractor.identity.id;
    if (this.extractors.has(id)) {
      throw new Error(`Extractor with ID "${id}" is already registered`);
    }
    this.extractors.set(id, extractor);
  }

  /**
   * Get an extractor by ID.
   */
  get(id: string): ArtifactExtractor | undefined {
    return this.extractors.get(id);
  }

  /**
   * Get all registered extractors.
   */
  getAll(): readonly ArtifactExtractor[] {
    return [...this.extractors.values()];
  }

  /**
   * Get all extractor capabilities (without instances).
   */
  getCapabilities(): readonly ExtractorCapabilities[] {
    return [...this.extractors.values()].map((e) => ({
      id: e.identity.id,
      version: e.identity.version,
      supportedKinds: e.identity.supportedKinds,
      supportedLanguages: e.identity.supportedLanguages,
      supportedFrameworks: e.identity.supportedFrameworks,
    }));
  }

  /**
   * Filter extractors by language.
   */
  byLanguage(language: string): readonly ArtifactExtractor[] {
    return [...this.extractors.values()].filter((e) =>
      e.identity.supportedLanguages.includes(language),
    );
  }

  /**
   * Filter extractors by framework.
   */
  byFramework(framework: string): readonly ArtifactExtractor[] {
    return [...this.extractors.values()].filter((e) =>
      e.identity.supportedFrameworks.includes(framework),
    );
  }

  /**
   * Filter extractors by artifact kind.
   */
  byKind(kind: string): readonly ArtifactExtractor[] {
    return [...this.extractors.values()].filter((e) =>
      e.identity.supportedKinds.includes(kind),
    );
  }

  /**
   * Find extractors that can handle a specific artifact record.
   */
  findExtractorsForArtifact(artifact: ArtifactRecord): readonly ArtifactExtractor[] {
    return [...this.extractors.values()].filter((e) => e.canExtract(artifact));
  }

  /**
   * Check if an extractor with the given ID is registered.
   */
  has(id: string): boolean {
    return this.extractors.has(id);
  }

  /**
   * Get the count of registered extractors.
   */
  count(): number {
    return this.extractors.size;
  }

  /**
   * Unregister an extractor by ID.
   */
  unregister(id: string): boolean {
    return this.extractors.delete(id);
  }

  /**
   * Clear all registered extractors.
   */
  clear(): void {
    this.extractors.clear();
  }
}

// ============================================================================
// Default Registry Instance
// ============================================================================

/**
 * Global default extractor registry.
 * Use this instance for most scenarios, or create a custom ExtractorRegistry
 * for isolated contexts (e.g., testing, tenant-specific configurations).
 */
export const defaultExtractorRegistry = new ExtractorRegistry();
