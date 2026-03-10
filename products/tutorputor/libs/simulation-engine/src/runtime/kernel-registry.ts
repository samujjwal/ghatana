/**
 * Kernel Registry - Routes simulation manifests to appropriate domain kernels.
 *
 * @doc.type class
 * @doc.purpose Central registry for simulation kernel routing and lifecycle management
 * @doc.layer product
 * @doc.pattern Registry
 */

import type {
  SimulationManifest,
  SimulationDomain,
  SimKernelService,
  PhysicsConfig,
  ChemistryConfig,
  EconomicsConfig,
  BiologyConfig,
  MedicineConfig,
} from '@ghatana/tutorputor-contracts/v1/simulation';

import { createDiscreteKernel } from './discrete-kernel';
import { createPhysicsKernel } from './physics-kernel';
import { createSystemDynamicsKernel } from './system-dynamics-kernel';
import { createChemistryKernel } from './chemistry-kernel';
import { createBiologyKernel } from './biology-kernel';
import { createMedicineKernel } from './medicine-kernel';

/**
 * Kernel factory function type.
 */
type KernelFactory<TConfig = any> = (config?: TConfig) => SimKernelService;

/**
 * Registry entry for a kernel.
 */
interface KernelRegistryEntry<TConfig = any> {
  /** Factory function to create kernel instances */
  factory: KernelFactory<TConfig>;
  /** Human-readable name */
  name: string;
  /** Description of the kernel's capabilities */
  description: string;
  /** Supported simulation types within the domain */
  supportedTypes: string[];
  /** Whether the kernel is async (requires WASM loading) */
  isAsync: boolean;
}

/**
 * Domain to kernel mapping.
 */
const KERNEL_REGISTRY: Map<SimulationDomain, KernelRegistryEntry<any>> = new Map([
  [
    'CS_DISCRETE',
    {
      factory: () => createDiscreteKernel(),
      name: 'Discrete Algorithm Kernel',
      description: 'Simulates sorting, searching, and graph algorithms',
      supportedTypes: [
        'sorting',
        'searching',
        'graph',
        'tree',
        'dynamic_programming',
        'greedy',
        'backtracking',
      ],
      isAsync: false,
    },
  ],
  [
    'PHYSICS',
    {
      factory: (config?: PhysicsConfig) => createPhysicsKernel(config),
      name: 'Physics Simulation Kernel',
      description: 'Rapier WASM-based rigid body physics simulation',
      supportedTypes: [
        'rigid_body',
        'projectile',
        'collision',
        'pendulum',
        'spring',
        'fluid',
        'wave',
      ],
      isAsync: true,
    },
  ],
  [
    'SYSTEM_DYNAMICS' as any,
    {
      factory: (config?: EconomicsConfig) => createSystemDynamicsKernel(config),
      name: 'System Dynamics Kernel',
      description: 'Stocks and flows, supply/demand, investment simulations',
      supportedTypes: [
        'stocks_flows',
        'supply_demand',
        'investment',
        'growth',
        'decay',
        'feedback_loop',
      ],
      isAsync: false,
    },
  ],
  [
    'CHEMISTRY',
    {
      factory: (config?: ChemistryConfig) => createChemistryKernel(config),
      name: 'Chemistry Simulation Kernel',
      description: 'Chemical reactions, titrations, and molecular dynamics',
      supportedTypes: [
        'reaction',
        'titration',
        'equilibrium',
        'kinetics',
        'thermodynamics',
        'molecular',
      ],
      isAsync: false,
    },
  ],
  [
    'BIOLOGY',
    {
      factory: (config?: BiologyConfig) => createBiologyKernel(config),
      name: 'Biology Simulation Kernel',
      description: 'Cellular, genetic, and ecological simulations',
      supportedTypes: [
        'cell_division',
        'gene_expression',
        'population',
        'predator_prey',
        'evolution',
        'metabolism',
      ],
      isAsync: false,
    },
  ],
  [
    'MEDICINE',
    {
      factory: (config?: MedicineConfig) => createMedicineKernel(config),
      name: 'Medicine Simulation Kernel',
      description: 'Pharmacokinetics, pharmacodynamics, and epidemiology',
      supportedTypes: [
        'pharmacokinetics',
        'pharmacodynamics',
        'epidemiology',
        'physiology',
        'drug_interaction',
        'disease_progression',
      ],
      isAsync: false,
    },
  ],
]);

/**
 * Custom kernel plugins registered at runtime.
 */
const CUSTOM_KERNELS: Map<string, KernelRegistryEntry> = new Map();

/**
 * Active kernel instances cache.
 */
const ACTIVE_KERNELS: Map<string, SimKernelService> = new Map();

/**
 * Kernel Registry for managing simulation kernels.
 */
export class KernelRegistry {
  /**
   * Get a kernel instance for a simulation manifest.
   *
   * @param manifest - The simulation manifest
   * @returns The appropriate kernel instance
   * @throws Error if no kernel is registered for the domain
   */
  static async getKernel(manifest: SimulationManifest): Promise<SimKernelService> {
    const cacheKey = this.getCacheKey(manifest);

    // Return cached kernel if available
    if (ACTIVE_KERNELS.has(cacheKey)) {
      return ACTIVE_KERNELS.get(cacheKey)!;
    }

    // Look up kernel entry
    const entry = this.getKernelEntry(manifest.domain);
    if (!entry) {
      throw new Error(`No kernel registered for domain: ${manifest.domain}`);
    }

    // Create kernel instance with domain config
    const config = this.extractDomainConfig(manifest);
    const kernel = entry.factory(config);

    // Initialize kernel if async
    if (entry.isAsync) {
      await kernel.initialize(manifest);
    } else {
      kernel.initialize(manifest);
    }

    // Cache the kernel
    ACTIVE_KERNELS.set(cacheKey, kernel);

    return kernel;
  }

  /**
   * Get kernel entry for a domain.
   */
  private static getKernelEntry(domain: SimulationDomain): KernelRegistryEntry | undefined {
    // Check custom kernels first (allows overrides)
    if (CUSTOM_KERNELS.has(domain)) {
      return CUSTOM_KERNELS.get(domain);
    }
    return KERNEL_REGISTRY.get(domain);
  }

  /**
   * Extract domain-specific config from manifest.
   */
  private static extractDomainConfig(
    manifest: SimulationManifest
  ): any {
    if (!manifest.domainMetadata) return undefined;

    const metadata = manifest.domainMetadata as any;

    switch (manifest.domain) {
      case 'PHYSICS': return metadata.physics;
      case 'CHEMISTRY': return metadata.chemistry;
      case 'ECONOMICS': return metadata.economics;
      case 'BIOLOGY': return metadata.biology;
      case 'MEDICINE': return metadata.medicine;
      default: return undefined;
    }
  }

  /**
   * Generate cache key for a manifest.
   */
  private static getCacheKey(manifest: SimulationManifest): string {
    return `${manifest.id}_${manifest.version ?? '1.0'}`;
  }

  /**
   * Register a custom kernel for a domain.
   *
   * @param domain - The domain identifier
   * @param entry - The kernel registry entry
   */
  static registerKernel(domain: string, entry: KernelRegistryEntry): void {
    CUSTOM_KERNELS.set(domain, entry);
  }

  /**
   * Unregister a custom kernel.
   *
   * @param domain - The domain identifier
   */
  static unregisterKernel(domain: string): void {
    CUSTOM_KERNELS.delete(domain);
  }

  /**
   * Check if a domain has a registered kernel.
   *
   * @param domain - The domain to check
   * @returns True if a kernel is registered
   */
  static hasKernel(domain: SimulationDomain | string): boolean {
    return KERNEL_REGISTRY.has(domain as SimulationDomain) || CUSTOM_KERNELS.has(domain);
  }

  /**
   * Get information about a registered kernel.
   *
   * @param domain - The domain to query
   * @returns Kernel information or undefined
   */
  static getKernelInfo(
    domain: SimulationDomain | string
  ): Omit<KernelRegistryEntry, 'factory'> | undefined {
    const entry = this.getKernelEntry(domain as SimulationDomain);
    if (!entry) return undefined;

    return {
      name: entry.name,
      description: entry.description,
      supportedTypes: entry.supportedTypes,
      isAsync: entry.isAsync,
    };
  }

  /**
   * List all registered kernels.
   *
   * @returns Array of domain and kernel info pairs
   */
  static listKernels(): Array<{ domain: string; info: Omit<KernelRegistryEntry, 'factory'> }> {
    const kernels: Array<{ domain: string; info: Omit<KernelRegistryEntry, 'factory'> }> = [];

    // Add built-in kernels
    for (const [domain, entry] of KERNEL_REGISTRY) {
      kernels.push({
        domain,
        info: {
          name: entry.name,
          description: entry.description,
          supportedTypes: entry.supportedTypes,
          isAsync: entry.isAsync,
        },
      });
    }

    // Add custom kernels
    for (const [domain, entry] of CUSTOM_KERNELS) {
      kernels.push({
        domain,
        info: {
          name: entry.name,
          description: entry.description,
          supportedTypes: entry.supportedTypes,
          isAsync: entry.isAsync,
        },
      });
    }

    return kernels;
  }

  /**
   * Release a cached kernel.
   *
   * @param manifestId - The manifest ID
   * @param version - Optional version
   */
  static releaseKernel(manifestId: string, version?: string): void {
    const cacheKey = `${manifestId}_${version ?? '1.0'}`;
    const kernel = ACTIVE_KERNELS.get(cacheKey);
    if (kernel) {
      kernel.reset();
      ACTIVE_KERNELS.delete(cacheKey);
    }
  }

  /**
   * Release all cached kernels.
   */
  static releaseAll(): void {
    for (const kernel of ACTIVE_KERNELS.values()) {
      kernel.reset();
    }
    ACTIVE_KERNELS.clear();
  }

  /**
   * Get supported simulation types for a domain.
   *
   * @param domain - The domain to query
   * @returns Array of supported types
   */
  static getSupportedTypes(domain: SimulationDomain | string): string[] {
    const entry = this.getKernelEntry(domain as SimulationDomain);
    return entry?.supportedTypes ?? [];
  }

  /**
   * Find kernels that support a specific simulation type.
   *
   * @param type - The simulation type to search for
   * @returns Array of domains that support the type
   */
  static findKernelsForType(type: string): string[] {
    const domains: string[] = [];

    for (const [domain, entry] of KERNEL_REGISTRY) {
      if (entry.supportedTypes.includes(type)) {
        domains.push(domain);
      }
    }

    for (const [domain, entry] of CUSTOM_KERNELS) {
      if (entry.supportedTypes.includes(type)) {
        domains.push(domain);
      }
    }

    return domains;
  }
}

export type { KernelRegistryEntry, KernelFactory };
