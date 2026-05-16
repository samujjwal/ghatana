/**
 * @fileoverview Capability Registry for runtime discovery of providers, extractors, emitters, and validators.
 *
 * Enables UI/API to list supported capabilities, their support levels, languages/frameworks,
 * and confidence ranges. This is the canonical source of truth for what the artifact compiler
 * can do at runtime.
 */

import { z } from 'zod';

// ============================================================================
// Support Level
// ============================================================================

export const SupportLevelSchema = z.enum([
  'production',   // Fully tested, safe for production use
  'preview',      // Works but may have edge cases; use with caution
  'unsupported',  // Not implemented or known to be broken
]);

export type SupportLevel = z.infer<typeof SupportLevelSchema>;

// ============================================================================
// Capability Metadata
// ============================================================================

export const CapabilityMetadataSchema = z.object({
  /** Human-readable description of what this capability does */
  description: z.string().min(1),
  /** Support level for this capability */
  supportLevel: SupportLevelSchema,
  /** Minimum confidence threshold for this capability to be usable */
  minConfidence: z.number().min(0).max(1).default(0.5),
  /** Maximum confidence this capability can achieve */
  maxConfidence: z.number().min(0).max(1).default(1.0),
  /** Languages this capability supports (e.g., ['typescript', 'tsx']) */
  languages: z.array(z.string()).default([]),
  /** Frameworks this capability supports (e.g., ['react', 'nextjs']) */
  frameworks: z.array(z.string()).default([]),
  /** Known limitations or caveats */
  limitations: z.array(z.string()).default([]),
  /** Whether this capability requires external dependencies */
  requiresExternalDeps: z.boolean().default(false),
  /** Version of the capability implementation */
  version: z.string().min(1),
});

export type CapabilityMetadata = z.infer<typeof CapabilityMetadataSchema>;

// ============================================================================
// Provider Capability
// ============================================================================

export const ProviderCapabilitySchema = CapabilityMetadataSchema.extend({
  capabilityType: z.literal('provider'),
  providerId: z.string().min(1),
  /** Locator formats this provider can handle (e.g., ['github.com/*', 'gitlab.com/*']) */
  locatorFormats: z.array(z.string()).min(1),
  /** Whether this provider supports streaming (partial materialization) */
  supportsStreaming: z.boolean().default(false),
  /** Maximum repository size this provider can handle (in bytes) */
  maxRepoSizeBytes: z.number().int().nonnegative().optional(),
});

export type ProviderCapability = z.infer<typeof ProviderCapabilitySchema>;

// ============================================================================
// Extractor Capability
// ============================================================================

export const ExtractorCapabilitySchema = CapabilityMetadataSchema.extend({
  capabilityType: z.literal('extractor'),
  extractorId: z.string().min(1),
  /** Artifact kinds this extractor can handle (e.g., ['component-implementation', 'page-route']) */
  artifactKinds: z.array(z.string()).min(1),
  /** File extensions this extractor can process (e.g., ['.tsx', '.ts']) */
  fileExtensions: z.array(z.string()).min(1),
  /** Whether this extractor requires full file content (vs AST only) */
  requiresFullContent: z.boolean().default(false),
});

export type ExtractorCapability = z.infer<typeof ExtractorCapabilitySchema>;

// ============================================================================
// Emitter Capability
// ============================================================================

export const EmitterCapabilitySchema = CapabilityMetadataSchema.extend({
  capabilityType: z.literal('emitter'),
  emitterId: z.string().min(1),
  /** Change operations this emitter can handle (e.g., ['add-component', 'rename-component']) */
  supportedOps: z.array(z.string()).min(1),
  /** Whether this emitter produces minimal diffs (vs full-file replacements) */
  producesMinimalDiffs: z.boolean().default(false),
  /** Whether this emitter preserves residuals */
  preservesResiduals: z.boolean().default(false),
});

export type EmitterCapability = z.infer<typeof EmitterCapabilitySchema>;

// ============================================================================
// Validator Capability
// ============================================================================

export const ValidatorCapabilitySchema = CapabilityMetadataSchema.extend({
  capabilityType: z.literal('validator'),
  validatorId: z.string().min(1),
  /** What this validator validates (e.g., 'graph', 'model', 'patch') */
  validates: z.string().min(1),
  /** Validation categories this validator covers (e.g., ['structural', 'semantic', 'security']) */
  categories: z.array(z.string()).min(1),
  /** Severity of violations this validator produces (e.g., ['error', 'warning']) */
  severityLevels: z.array(z.enum(['error', 'warning', 'info'])).default(['error']),
});

export type ValidatorCapability = z.infer<typeof ValidatorCapabilitySchema>;

// ============================================================================
// Union Capability Type
// ============================================================================

export const CapabilitySchema = z.union([
  ProviderCapabilitySchema,
  ExtractorCapabilitySchema,
  EmitterCapabilitySchema,
  ValidatorCapabilitySchema,
]);

export type Capability = z.infer<typeof CapabilitySchema>;

// ============================================================================
// Capability Registry
// ============================================================================

export interface CapabilityRegistry {
  /** Register a capability */
  register(capability: Capability): void;
  /** Get a capability by ID */
  get(id: string, type: 'provider' | 'extractor' | 'emitter' | 'validator'): Capability | undefined;
  /** List all capabilities of a given type */
  list(type: 'provider' | 'extractor' | 'emitter' | 'validator'): Capability[];
  /** List all capabilities filtered by support level */
  listBySupportLevel(
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
    supportLevel: SupportLevel,
  ): Capability[];
  /** List capabilities that support a given language */
  listByLanguage(
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
    language: string,
  ): Capability[];
  /** List capabilities that support a given framework */
  listByFramework(
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
    framework: string,
  ): Capability[];
  /** Get summary statistics */
  getStats(): RegistryStats;
}

export interface RegistryStats {
  totalCapabilities: number;
  byType: Record<string, number>;
  bySupportLevel: Record<SupportLevel, number>;
}

// ============================================================================
// Default Capability Registry Implementation
// ============================================================================

class DefaultCapabilityRegistry implements CapabilityRegistry {
  private readonly capabilities = new Map<string, Capability>();

  register(capability: Capability): void {
    const key = `${capability.capabilityType}:${this.extractId(capability)}`;
    this.capabilities.set(key, capability);
  }

  get(
    id: string,
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
  ): Capability | undefined {
    const key = `${type}:${id}`;
    return this.capabilities.get(key);
  }

  list(type: 'provider' | 'extractor' | 'emitter' | 'validator'): Capability[] {
    return Array.from(this.capabilities.values()).filter(
      (cap) => cap.capabilityType === type,
    );
  }

  listBySupportLevel(
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
    supportLevel: SupportLevel,
  ): Capability[] {
    return this.list(type).filter((cap) => cap.supportLevel === supportLevel);
  }

  listByLanguage(
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
    language: string,
  ): Capability[] {
    return this.list(type).filter((cap) =>
      cap.languages.includes(language.toLowerCase()),
    );
  }

  listByFramework(
    type: 'provider' | 'extractor' | 'emitter' | 'validator',
    framework: string,
  ): Capability[] {
    return this.list(type).filter((cap) =>
      cap.frameworks.includes(framework.toLowerCase()),
    );
  }

  getStats(): RegistryStats {
    const byType: Record<'provider' | 'extractor' | 'emitter' | 'validator', number> = {
      provider: 0,
      extractor: 0,
      emitter: 0,
      validator: 0,
    };
    const bySupportLevel: Record<SupportLevel, number> = {
      production: 0,
      preview: 0,
      unsupported: 0,
    };

    for (const cap of this.capabilities.values()) {
      byType[cap.capabilityType as 'provider' | 'extractor' | 'emitter' | 'validator']++;
      bySupportLevel[cap.supportLevel]++;
    }

    return {
      totalCapabilities: this.capabilities.size,
      byType,
      bySupportLevel,
    };
  }

  private extractId(capability: Capability): string {
    if (capability.capabilityType === 'provider') {
      return (capability as ProviderCapability).providerId;
    }
    if (capability.capabilityType === 'extractor') {
      return (capability as ExtractorCapability).extractorId;
    }
    if (capability.capabilityType === 'emitter') {
      return (capability as EmitterCapability).emitterId;
    }
    if (capability.capabilityType === 'validator') {
      return (capability as ValidatorCapability).validatorId;
    }
    // This should never happen due to union type exhaustiveness
    throw new Error(`Unknown capability type: ${(capability as any).capabilityType}`);
  }
}

// ============================================================================
// Singleton Registry Instance
// ============================================================================

export const capabilityRegistry = new DefaultCapabilityRegistry();

// ============================================================================
// Helper: Register Built-in Capabilities
// ============================================================================

export function registerBuiltinCapabilities(): void {
  // Register GitHub provider capability
  capabilityRegistry.register({
    capabilityType: 'provider',
    providerId: 'github',
    description: 'GitHub repository provider using GitHub Contents API',
    supportLevel: 'production',
    minConfidence: 0.9,
    maxConfidence: 1.0,
    languages: [],
    frameworks: [],
    limitations: [
      'GitHub tree API truncates at 500k entries',
      'Requires authentication for private repos',
      'Rate limited by GitHub API',
    ],
    requiresExternalDeps: false,
    version: '1.0.0',
    locatorFormats: [
      'github.com/*',
      'git@github.com:*',
      'owner/repo',
      'owner/repo@ref',
    ],
    supportsStreaming: false,
    maxRepoSizeBytes: 500_000_000, // 500MB
  });

  // Register GitLab provider capability
  capabilityRegistry.register({
    capabilityType: 'provider',
    providerId: 'gitlab',
    description: 'GitLab repository provider using GitLab API',
    supportLevel: 'production',
    minConfidence: 0.9,
    maxConfidence: 1.0,
    languages: [],
    frameworks: [],
    limitations: [
      'Paginated API may be slow for large repos',
      'Requires authentication for private repos',
      'Rate limited by GitLab API',
    ],
    requiresExternalDeps: false,
    version: '1.0.0',
    locatorFormats: [
      'gitlab.com/*',
      'owner/repo',
      'owner/repo@ref',
    ],
    supportsStreaming: false,
    maxRepoSizeBytes: 500_000_000,
  });

  // Register ZIP provider capability
  capabilityRegistry.register({
    capabilityType: 'provider',
    providerId: 'zip',
    description: 'ZIP archive provider for local or remote archives',
    supportLevel: 'production',
    minConfidence: 1.0,
    maxConfidence: 1.0,
    languages: [],
    frameworks: [],
    limitations: [
      'Requires full download before extraction',
      'No streaming support',
    ],
    requiresExternalDeps: false,
    version: '1.0.0',
    locatorFormats: [
      '*.zip',
      'http://*.zip',
      'https://*.zip',
    ],
    supportsStreaming: false,
    maxRepoSizeBytes: 1_000_000_000, // 1GB
  });

  // Register TypeScript component extractor capability
  capabilityRegistry.register({
    capabilityType: 'extractor',
    extractorId: 'typescript-component',
    description: 'Extracts React/TSX component definitions, props, slots, and events',
    supportLevel: 'production',
    minConfidence: 0.7,
    maxConfidence: 0.95,
    languages: ['typescript', 'tsx', 'jsx'],
    frameworks: ['react', 'nextjs'],
    limitations: [
      'May miss components with complex HOC patterns',
      'Props inferred from JSDoc may be incomplete',
    ],
    requiresExternalDeps: false,
    version: '1.0.0',
    artifactKinds: ['component-implementation'],
    fileExtensions: ['.tsx', '.jsx', '.ts', '.js'],
    requiresFullContent: true,
  });

  // Register React patch emitter capability
  capabilityRegistry.register({
    capabilityType: 'emitter',
    emitterId: 'react-patch-emitter',
    description: 'Emits minimal AST/range-based patches for React components',
    supportLevel: 'production',
    minConfidence: 0.8,
    maxConfidence: 1.0,
    languages: ['typescript', 'tsx'],
    frameworks: ['react', 'nextjs'],
    limitations: [
      'Only supports add-prop, rename, and simple modifications',
      'Complex refactors may require manual review',
    ],
    requiresExternalDeps: false,
    version: '1.0.0',
    supportedOps: ['add-component', 'rename-component', 'add-prop', 'update-prop', 'remove-prop'],
    producesMinimalDiffs: true,
    preservesResiduals: true,
  });

  // Register graph validator capability
  capabilityRegistry.register({
    capabilityType: 'validator',
    validatorId: 'graph-validator',
    description: 'Validates artifact graph structural integrity',
    supportLevel: 'production',
    minConfidence: 1.0,
    maxConfidence: 1.0,
    languages: [],
    frameworks: [],
    limitations: [],
    requiresExternalDeps: false,
    version: '1.0.0',
    validates: 'graph',
    categories: ['structural', 'semantic'],
    severityLevels: ['error', 'warning'],
  });
}

// Auto-register built-in capabilities on import
registerBuiltinCapabilities();
