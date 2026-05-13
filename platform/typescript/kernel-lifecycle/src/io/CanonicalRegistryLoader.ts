import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Canonical registry loader
 */
export class CanonicalRegistryLoader {
  private registryPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.registryPath = path.join(configDir, 'canonical-product-registry.json');
  }

  /**
   * Load the canonical product registry
   */
  async load(): Promise<CanonicalRegistry> {
    const content = await fs.readFile(this.registryPath, 'utf-8');
    return JSON.parse(content) as CanonicalRegistry;
  }

  /**
   * Get product configuration by ID
   */
  async getProduct(productId: string): Promise<Product> {
    const registry = await this.load();
    const product = registry.registry[productId];

    if (!product) {
      throw new Error(`Product ${productId} not found in registry`);
    }

    return product;
  }

  /**
   * Get all products
   */
  async getAllProducts(): Promise<Record<string, Product>> {
    const registry = await this.load();
    return registry.registry;
  }

  /**
   * Get products by kind
   */
  async getProductsByKind(kind: string): Promise<Product[]> {
    const registry = await this.load();
    return Object.values(registry.registry).filter((p) => p.kind === kind);
  }

  /**
   * Validate registry structure
   */
  async validate(): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];

    try {
      const registry = await this.load();

      if (!registry.version) {
        errors.push({ path: 'version', message: 'Version is required' });
      }

      if (!registry.registry) {
        errors.push({ path: 'registry', message: 'Registry is required' });
      }
    } catch (error) {
      errors.push({
        path: 'root',
        message: `Failed to parse registry: ${error instanceof Error ? error.message : String(error)}`,
      });
    }

    return errors;
  }
}

/**
 * Canonical registry
 */
export interface CanonicalRegistry {
  version: string;
  registry: Record<string, Product>;
}

/**
 * Product
 */
export interface Product {
  id: string;
  name: string;
  description: string;
  type: string;
  kind: string;
  manifestPath: string | null;
  manifestFormat: string | null;
  buildFile?: string;
  gradleModules: string[];
  surfaces: Surface[];
  pnpmPackages?: string[];
  ci?: CIConfiguration;
  conformance?: ConformanceConfiguration;
  metadata?: ProductMetadata;
  lifecycleProfile?: string;
  lifecycleConfigPath?: string;
  lifecycle?: ProductLifecycleConfiguration;
  toolchain?: ProductToolchainConfiguration;
  artifacts?: ProductArtifactsConfiguration;
  deployment?: ProductDeploymentConfiguration;
  environments?: ProductEnvironmentConfiguration;
}

/**
 * Surface
 */
export interface Surface {
  type: string;
  path: string;
  implementationStatus: 'implemented' | 'planned' | 'backend-only';
  packagePath?: string;
}

/**
 * CI configuration
 */
export interface CIConfiguration {
  enabled?: boolean;
  matrix?: Record<string, unknown>;
  gates?: string[];
}

/**
 * Conformance configuration
 */
export interface ConformanceConfiguration {
  manifest?: boolean;
  observability?: boolean;
  security?: boolean;
  dataAccess?: boolean;
  bridge?: boolean;
  bridgeAdapters?: BridgeAdapter[];
  agentDefinitions?: boolean;
  masteryBindings?: boolean;
  evaluationPacks?: boolean;
  runtimeModule?: boolean;
}

/**
 * Bridge adapter
 */
export interface BridgeAdapter {
  file: string;
  tests: BridgeTest[];
}

/**
 * Bridge test
 */
export interface BridgeTest {
  file: string;
}

/**
 * Product metadata
 */
export interface ProductMetadata {
  owner?: string;
  created?: string;
  documentation?: string;
  status?: 'active' | 'deprecated' | 'experimental';
}

/**
 * Product lifecycle configuration
 */
export interface ProductLifecycleConfiguration {
  enabled?: boolean;
  phases?: Record<string, PhaseConfiguration>;
}

/**
 * Phase configuration
 */
export interface PhaseConfiguration {
  defaultSurfaces?: string[];
  mode?: 'parallel' | 'sequential';
}

/**
 * Product toolchain configuration
 */
export interface ProductToolchainConfiguration {
  profile?: string;
  adapters?: Record<string, string>;
}

/**
 * Product artifacts configuration
 */
export interface ProductArtifactsConfiguration {
  'backend-api'?: ArtifactDeclaration;
  web?: ArtifactDeclaration;
  worker?: ArtifactDeclaration;
}

/**
 * Artifact declaration
 */
export interface ArtifactDeclaration {
  type: string;
  packaging: string;
}

/**
 * Product deployment configuration
 */
export interface ProductDeploymentConfiguration {
  targets?: string[];
  defaultEnvironment?: string;
  healthChecks?: string[];
  rollback?: RollbackConfiguration;
}

/**
 * Rollback configuration
 */
export interface RollbackConfiguration {
  strategy: string;
}

/**
 * Product environment configuration
 */
export interface ProductEnvironmentConfiguration {
  supported?: string[];
  defaults?: Record<string, unknown>;
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
