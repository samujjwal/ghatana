import { CanonicalRegistryLoader, Product as RegistryProduct } from '../io/CanonicalRegistryLoader.js';
import { ValidationError } from './ProductLifecycleContractValidator.js';

/**
 * Product registry lifecycle validator
 */
export class ProductRegistryLifecycleValidator {
  private registryLoader: CanonicalRegistryLoader;

  constructor(registryLoader?: CanonicalRegistryLoader) {
    this.registryLoader = registryLoader || new CanonicalRegistryLoader();
  }

  /**
   * Validate product lifecycle configuration in registry
   */
  async validateProduct(productId: string): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];

    try {
      const product = await this.registryLoader.getProduct(productId);

      // Check if lifecycle is enabled
      if (product.lifecycle?.enabled === false) {
        // Lifecycle is explicitly disabled, no validation needed
        return errors;
      }

      // Validate lifecycle profile
      if (product.lifecycleProfile) {
        if (typeof product.lifecycleProfile !== 'string' || product.lifecycleProfile.trim().length === 0) {
          errors.push({ path: 'lifecycleProfile', message: 'Lifecycle profile must be a non-empty string' });
        }
      } else {
        errors.push({ path: 'lifecycleProfile', message: 'Lifecycle profile is required when lifecycle is enabled' });
      }

      // Validate lifecycle config path
      if (product.lifecycleConfigPath) {
        if (typeof product.lifecycleConfigPath !== 'string' || product.lifecycleConfigPath.trim().length === 0) {
          errors.push({ path: 'lifecycleConfigPath', message: 'Lifecycle config path must be a non-empty string' });
        }
      }

      // Validate lifecycle configuration
      if (product.lifecycle) {
        this.validateLifecycleConfiguration(product.lifecycle, errors);
      }

      // Validate toolchain configuration
      if (product.toolchain) {
        this.validateToolchainConfiguration(product.toolchain, errors);
      }

      // Validate artifacts configuration
      if (product.artifacts) {
        this.validateArtifactsConfiguration(product.artifacts, errors);
      }

      // Validate deployment configuration
      if (product.deployment) {
        this.validateDeploymentConfiguration(product.deployment, errors);
      }

      // Validate environment configuration
      if (product.environments) {
        this.validateEnvironmentConfiguration(product.environments, errors);
      }
    } catch (error) {
      errors.push({
        path: 'root',
        message: `Failed to validate product: ${error instanceof Error ? error.message : String(error)}`,
      });
    }

    return errors;
  }

  /**
   * Validate lifecycle configuration
   */
  private validateLifecycleConfiguration(
    lifecycle: RegistryProduct['lifecycle'],
    errors: ValidationError[],
  ): void {
    if (!lifecycle) {
      return;
    }

    if (lifecycle.enabled !== undefined && typeof lifecycle.enabled !== 'boolean') {
      errors.push({ path: 'lifecycle.enabled', message: 'Enabled must be a boolean' });
    }

    if (lifecycle.phases) {
      for (const [phaseName, phaseConfig] of Object.entries(lifecycle.phases)) {
        if (!phaseConfig.defaultSurfaces || !Array.isArray(phaseConfig.defaultSurfaces)) {
          errors.push({ path: `lifecycle.phases.${phaseName}.defaultSurfaces`, message: 'Default surfaces must be an array' });
        }

        if (phaseConfig.mode && !['parallel', 'sequential'].includes(phaseConfig.mode)) {
          errors.push({ path: `lifecycle.phases.${phaseName}.mode`, message: 'Mode must be either parallel or sequential' });
        }
      }
    }
  }

  /**
   * Validate toolchain configuration
   */
  private validateToolchainConfiguration(
    toolchain: RegistryProduct['toolchain'],
    errors: ValidationError[],
  ): void {
    if (!toolchain) {
      return;
    }

    if (toolchain.profile && typeof toolchain.profile !== 'string') {
      errors.push({ path: 'toolchain.profile', message: 'Profile must be a string' });
    }

    if (toolchain.adapters) {
      if (typeof toolchain.adapters !== 'object') {
        errors.push({ path: 'toolchain.adapters', message: 'Adapters must be an object' });
      }
    }
  }

  /**
   * Validate artifacts configuration
   */
  private validateArtifactsConfiguration(
    artifacts: RegistryProduct['artifacts'],
    errors: ValidationError[],
  ): void {
    if (!artifacts) {
      return;
    }

    for (const [surface, artifact] of Object.entries(artifacts)) {
      if (!artifact.type) {
        errors.push({ path: `artifacts.${surface}.type`, message: 'Artifact type is required' });
      }

      if (!artifact.packaging) {
        errors.push({ path: `artifacts.${surface}.packaging`, message: 'Artifact packaging is required' });
      }
    }
  }

  /**
   * Validate deployment configuration
   */
  private validateDeploymentConfiguration(
    deployment: RegistryProduct['deployment'],
    errors: ValidationError[],
  ): void {
    if (!deployment) {
      return;
    }

    if (deployment.targets && !Array.isArray(deployment.targets)) {
      errors.push({ path: 'deployment.targets', message: 'Targets must be an array' });
    }

    if (deployment.healthChecks && !Array.isArray(deployment.healthChecks)) {
      errors.push({ path: 'deployment.healthChecks', message: 'Health checks must be an array' });
    }

    if (deployment.rollback) {
      if (!deployment.rollback.strategy) {
        errors.push({ path: 'deployment.rollback.strategy', message: 'Rollback strategy is required' });
      }
    }
  }

  /**
   * Validate environment configuration
   */
  private validateEnvironmentConfiguration(
    environments: RegistryProduct['environments'],
    errors: ValidationError[],
  ): void {
    if (!environments) {
      return;
    }

    if (environments.supported && !Array.isArray(environments.supported)) {
      errors.push({ path: 'environments.supported', message: 'Supported environments must be an array' });
    }

    if (environments.defaults && typeof environments.defaults !== 'object') {
      errors.push({ path: 'environments.defaults', message: 'Defaults must be an object' });
    }
  }
}
