/**
 * @fileoverview Extractor runtime registry.
 *
 * Provides a centralized registry of all available extractors with their capabilities.
 * Enables runtime discovery and filtering of extractors by language, framework, or artifact kind.
 */

import type { ArtifactExtractor } from './types';
import type { ArtifactRecord } from '../inventory/types';
import {
  EXTRACTOR_ID as TS_COMPONENT_EXTRACTOR_ID,
  EXTRACTOR_VERSION as TS_COMPONENT_EXTRACTOR_VERSION,
  extractComponentArtifact,
} from './typescript/component-extractor';
import {
  EXTRACTOR_ID as PAGE_EXTRACTOR_ID,
  EXTRACTOR_VERSION as PAGE_EXTRACTOR_VERSION,
  extractPageArtifact,
} from './typescript/page-extractor';
import {
  EXTRACTOR_ID as STATE_EXTRACTOR_ID,
  EXTRACTOR_VERSION as STATE_EXTRACTOR_VERSION,
  extractStateStoreArtifact,
} from './typescript/state-extractor';
import {
  EXTRACTOR_ID as CSF_EXTRACTOR_ID,
  EXTRACTOR_VERSION as CSF_EXTRACTOR_VERSION,
  extractStorybookCsf,
} from './storybook/csf-extractor';
import {
  EXTRACTOR_ID as PRISMA_EXTRACTOR_ID,
  EXTRACTOR_VERSION as PRISMA_EXTRACTOR_VERSION,
  extractPrismaSchemaArtifact,
} from './prisma/schema-extractor';

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

function isEligibleForExtractor(record: ArtifactRecord, extractorId: string): boolean {
  return record.extractorEligibility.some(
    eligibility => eligibility.extractorId === extractorId && eligibility.eligible,
  );
}

export function createCanonicalExtractors(): readonly ArtifactExtractor[] {
  return [
    {
      identity: {
        id: TS_COMPONENT_EXTRACTOR_ID,
        version: TS_COMPONENT_EXTRACTOR_VERSION,
        supportedKinds: ['component-implementation', 'story-example'],
        supportedLanguages: ['tsx', 'jsx'],
        supportedFrameworks: ['react', 'nextjs'],
      },
      canExtract(record: ArtifactRecord): boolean {
        return isEligibleForExtractor(record, TS_COMPONENT_EXTRACTOR_ID)
          && (record.language === 'tsx' || record.language === 'jsx');
      },
      extract: extractComponentArtifact,
    },
    {
      identity: {
        id: PAGE_EXTRACTOR_ID,
        version: PAGE_EXTRACTOR_VERSION,
        supportedKinds: ['page-route'],
        supportedLanguages: ['typescript', 'tsx', 'javascript', 'jsx'],
        supportedFrameworks: ['react', 'nextjs'],
      },
      canExtract(record: ArtifactRecord): boolean {
        return isEligibleForExtractor(record, PAGE_EXTRACTOR_ID);
      },
      extract: extractPageArtifact,
    },
    {
      identity: {
        id: STATE_EXTRACTOR_ID,
        version: STATE_EXTRACTOR_VERSION,
        supportedKinds: ['state-management'],
        supportedLanguages: ['typescript', 'tsx', 'javascript', 'jsx'],
        supportedFrameworks: ['react', 'nextjs', 'none', 'unknown'],
      },
      canExtract(record: ArtifactRecord): boolean {
        return isEligibleForExtractor(record, STATE_EXTRACTOR_ID);
      },
      extract: extractStateStoreArtifact,
    },
    {
      identity: {
        id: CSF_EXTRACTOR_ID,
        version: CSF_EXTRACTOR_VERSION,
        supportedKinds: ['story-example'],
        supportedLanguages: ['typescript', 'tsx', 'javascript', 'jsx'],
        supportedFrameworks: ['storybook', 'react'],
      },
      canExtract(record: ArtifactRecord): boolean {
        return isEligibleForExtractor(record, CSF_EXTRACTOR_ID);
      },
      extract: extractStorybookCsf,
    },
    {
      identity: {
        id: PRISMA_EXTRACTOR_ID,
        version: PRISMA_EXTRACTOR_VERSION,
        supportedKinds: ['db-schema-migration'],
        supportedLanguages: ['prisma'],
        supportedFrameworks: ['prisma'],
      },
      canExtract(record: ArtifactRecord): boolean {
        return isEligibleForExtractor(record, PRISMA_EXTRACTOR_ID);
      },
      extract: extractPrismaSchemaArtifact,
    },
  ];
}

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
  for (const extractor of createCanonicalExtractors()) {
    if (!registry.has(extractor.identity.id)) {
      registry.register(extractor);
    }
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
