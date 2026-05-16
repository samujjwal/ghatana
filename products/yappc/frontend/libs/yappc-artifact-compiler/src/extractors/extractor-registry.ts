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

// ============================================================================
// Canonical Extractor Registration
// ============================================================================

/**
 * Register all canonical extractors with the given registry.
 * This function registers the production-ready extractors for:
 * - TypeScript/React components
 * - Next.js pages and routes
 * - State management stores (Zustand, Redux, Jotai, Context)
 * - Storybook CSF stories
 * - Prisma schemas
 *
 * @param registry - The ExtractorRegistry to register extractors with (defaults to defaultExtractorRegistry)
 * @returns The same registry instance for chaining
 */
export function registerCanonicalExtractors(registry: ExtractorRegistry = defaultExtractorRegistry): ExtractorRegistry {
  // Lazy import extractors to avoid circular dependencies
  const {
    extractComponentArtifact,
  } = require('./typescript/component-extractor');
  const {
    extractPageArtifact,
  } = require('./typescript/page-extractor');
  const {
    extractStateStoreArtifact,
  } = require('./typescript/state-extractor');
  const {
    extractStorybookCsf,
  } = require('./storybook/csf-extractor');
  const {
    extractPrismaSchemaArtifact,
  } = require('./prisma/schema-extractor');

  // Register TypeScript component extractor
  try {
    registry.register(extractComponentArtifact());
  } catch (error) {
    // Log but don't fail if one extractor fails to register
    console.warn(`Failed to register TypeScript component extractor: ${error}`);
  }

  // Register page/route extractor
  try {
    registry.register(extractPageArtifact());
  } catch (error) {
    console.warn(`Failed to register page/route extractor: ${error}`);
  }

  // Register state management extractor
  try {
    registry.register(extractStateStoreArtifact());
  } catch (error) {
    console.warn(`Failed to register state management extractor: ${error}`);
  }

  // Register Storybook CSF extractor
  try {
    registry.register(extractStorybookCsf());
  } catch (error) {
    console.warn(`Failed to register Storybook CSF extractor: ${error}`);
  }

  // Register Prisma schema extractor
  try {
    registry.register(extractPrismaSchemaArtifact());
  } catch (error) {
    console.warn(`Failed to register Prisma schema extractor: ${error}`);
  }

  return registry;
}

/**
 * Get all canonical extractors as an array.
 * This is a convenience function for creating a SynthesisPipeline with canonical extractors.
 *
 * @returns Array of registered canonical extractors
 */
export function getCanonicalExtractors(): readonly import('./types').ArtifactExtractor[] {
  // Ensure canonical extractors are registered
  if (defaultExtractorRegistry.count() === 0) {
    registerCanonicalExtractors(defaultExtractorRegistry);
  }
  return defaultExtractorRegistry.getAll();
}
