/**
 * Component Package/Plugin Loading Boundaries
 *
 * Provides a secure boundary for loading external component packages.
 * Enforces validation, version compatibility, and sandboxing policies.
 *
 * @doc.type module
 * @doc.purpose Secure plugin loading system for component packages
 * @doc.layer product
 * @doc.pattern Security Boundary
 */

import { rendererManifestRegistry, type BuilderRendererManifest } from './rendererManifest';
import {
  createDefaultPluginRuntimePolicy,
  createPluginRuntimeEnvironment,
  createTrustedPluginRuntimePolicy,
  type PluginRuntimeEnvironment,
  type PluginRuntimePolicy,
} from '../../../services/plugins/PluginRuntimePolicy';
import { assessComponentSafety } from '../../../security/UnsafeComponentHandler';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Component package manifest.
 */
export interface ComponentPackageManifest {
  /** Package name/identifier */
  readonly packageName: string;
  /** Package version */
  readonly version: string;
  /** Minimum compatible builder contract version */
  readonly minBuilderVersion: string;
  /** Maximum compatible builder contract version (optional) */
  readonly maxBuilderVersion?: string;
  /** Renderer manifests provided by this package */
  readonly renderers: readonly BuilderRendererManifest[];
  /** Security policy for this package */
  readonly securityPolicy?: ComponentSecurityPolicy;
}

/**
 * Security policy for component packages.
 */
export interface ComponentSecurityPolicy {
  /** Whether this package requires elevated permissions */
  readonly requiresElevatedPermissions?: boolean;
  /** Allowed network domains (if any) */
  readonly allowedDomains?: readonly string[];
  /** Whether this package can access browser APIs */
  readonly allowBrowserAPIs?: boolean;
  /** Whether this package can access localStorage */
  readonly allowLocalStorage?: boolean;
}

/**
 * Validation result for a component package.
 */
export interface PackageValidationResult {
  readonly isValid: boolean;
  readonly errors: readonly string[];
  readonly warnings: readonly string[];
}

/**
 * Current builder contract version.
 */
const CURRENT_BUILDER_VERSION = '1.0.0';

// ---------------------------------------------------------------------------
// Plugin Loader
// ---------------------------------------------------------------------------

/**
 * Secure plugin loader for component packages.
 */
export class ComponentPluginLoader {
  private readonly loadedPackages = new Map<string, ComponentPackageManifest>();
  private readonly allowedPackages = new Set<string>();
  private readonly runtimeEnvironments = new Map<string, PluginRuntimeEnvironment>();

  /**
   * Validates a component package manifest.
   */
  validatePackage(manifest: ComponentPackageManifest): PackageValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Validate required fields
    if (!manifest.packageName || manifest.packageName.trim() === '') {
      errors.push('Package name is required');
    }
    if (!manifest.version || manifest.version.trim() === '') {
      errors.push('Package version is required');
    }
    if (!manifest.minBuilderVersion || manifest.minBuilderVersion.trim() === '') {
      errors.push('Minimum builder version is required');
    }

    // Validate version compatibility
    const versionCheck = this.checkVersionCompatibility(
      CURRENT_BUILDER_VERSION,
      manifest.minBuilderVersion,
      manifest.maxBuilderVersion
    );
    if (!versionCheck.compatible) {
      errors.push(versionCheck.error || 'Version compatibility check failed');
    }

    // Validate renderers
    if (!manifest.renderers || manifest.renderers.length === 0) {
      warnings.push('Package provides no renderers');
    }

    manifest.renderers.forEach((renderer, index) => {
      if (!renderer.contractName || renderer.contractName.trim() === '') {
        errors.push(`Renderer at index ${index} has no contract name`);
      }
      if (typeof renderer.render !== 'function') {
        errors.push(`Renderer ${renderer.contractName} has no render function`);
      }

      if (renderer.sourceCode) {
        const assessment = assessComponentSafety(renderer.sourceCode, renderer.contractName);
        if (assessment.recommendedAction === 'block') {
          errors.push(
            `Renderer ${renderer.contractName} blocked by security assessment: ${assessment.riskFactors.join(', ')}`
          );
          return;
        }

        const hasElevatedAccess = Boolean(
          manifest.securityPolicy?.requiresElevatedPermissions && this.isPackageAllowed(manifest.packageName)
        );

        if (assessment.reviewRequired && !hasElevatedAccess) {
          errors.push(
            `Renderer ${renderer.contractName} requires elevated permissions due to risky runtime APIs. ` +
            `Add package '${manifest.packageName}' to the plugin allowlist before loading.`
          );
        }
      }
    });

    // Validate security policy
    if (manifest.securityPolicy?.requiresElevatedPermissions && !this.isPackageAllowed(manifest.packageName)) {
      errors.push(`Package ${manifest.packageName} requires elevated permissions but is not in allowlist`);
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Loads a component package after validation.
   */
  loadPackage(manifest: ComponentPackageManifest): PackageValidationResult {
    const validation = this.validatePackage(manifest);

    if (!validation.isValid) {
      return validation;
    }

    const runtimeEnvironment = createPluginRuntimeEnvironment(this.createRuntimePolicy(manifest));

    manifest.renderers.forEach((renderer) => {
      rendererManifestRegistry.register(renderer);
    });

    // Track loaded package
    this.loadedPackages.set(manifest.packageName, manifest);
    this.runtimeEnvironments.set(manifest.packageName, runtimeEnvironment);

    return validation;
  }

  /**
   * Unloads a component package.
   */
  unloadPackage(packageName: string): void {
    const manifest = this.loadedPackages.get(packageName);
    if (!manifest) {
      return;
    }

    manifest.renderers.forEach((renderer) => {
      rendererManifestRegistry.unregister(renderer.contractName);
    });

    // Remove from loaded packages
    this.loadedPackages.delete(packageName);
    this.runtimeEnvironments.delete(packageName);
  }

  /**
   * Adds a package to the allowlist (for packages requiring elevated permissions).
   */
  allowPackage(packageName: string): void {
    this.allowedPackages.add(packageName);
  }

  /**
   * Removes a package from the allowlist.
   */
  disallowPackage(packageName: string): void {
    this.allowedPackages.delete(packageName);
  }

  /**
   * Checks if a package is allowed.
   */
  isPackageAllowed(packageName: string): boolean {
    return this.allowedPackages.has(packageName);
  }

  /**
   * Gets all loaded packages.
   */
  getLoadedPackages(): readonly ComponentPackageManifest[] {
    return Array.from(this.loadedPackages.values());
  }

  getRuntimeEnvironment(packageName: string): PluginRuntimeEnvironment | null {
    return this.runtimeEnvironments.get(packageName) ?? null;
  }

  // -------------------------------------------------------------------------
  // Private Helpers
  // -------------------------------------------------------------------------

  private checkVersionCompatibility(
    currentVersion: string,
    minVersion: string,
    maxVersion?: string
  ): { compatible: boolean; error?: string } {
    try {
      const current = this.parseVersion(currentVersion);
      const min = this.parseVersion(minVersion);
      const max = maxVersion ? this.parseVersion(maxVersion) : null;

      if (this.compareVersions(current, min) < 0) {
        return {
          compatible: false,
          error: `Current builder version ${currentVersion} is below minimum required version ${minVersion}`,
        };
      }

      if (max && this.compareVersions(current, max) > 0) {
        return {
          compatible: false,
          error: `Current builder version ${currentVersion} exceeds maximum supported version ${maxVersion}`,
        };
      }

      return { compatible: true };
    } catch (e) {
      return {
        compatible: false,
        error: `Invalid version format: ${e instanceof Error ? e.message : String(e)}`,
      };
    }
  }

  private parseVersion(version: string): number[] {
    return version.split('.').map((v) => parseInt(v, 10));
  }

  private compareVersions(v1: number[], v2: number[]): number {
    const maxLength = Math.max(v1.length, v2.length);
    for (let i = 0; i < maxLength; i++) {
      const n1 = v1[i] || 0;
      const n2 = v2[i] || 0;
      if (n1 !== n2) {
        return n1 - n2;
      }
    }
    return 0;
  }

  private createRuntimePolicy(manifest: ComponentPackageManifest): PluginRuntimePolicy {
    const hasElevatedAccess = Boolean(
      manifest.securityPolicy?.requiresElevatedPermissions && this.isPackageAllowed(manifest.packageName)
    );
    const policy = hasElevatedAccess
      ? createTrustedPluginRuntimePolicy(manifest.packageName, manifest.version)
      : createDefaultPluginRuntimePolicy(manifest.packageName, manifest.version);

    if (manifest.securityPolicy?.allowedDomains) {
      policy.network.allowNetworkRequests = manifest.securityPolicy.allowedDomains.length > 0;
      policy.network.allowedDomains = [...manifest.securityPolicy.allowedDomains];
    }

    if (manifest.securityPolicy?.allowBrowserAPIs) {
      policy.browserAPI.allowClipboard = true;
      policy.browserAPI.allowWebSockets = true;
      policy.browserAPI.allowedAPIs = ['clipboard', 'websockets'];
    }

    if (manifest.securityPolicy?.allowLocalStorage) {
      policy.storage.allowLocalStorage = true;
      policy.storage.allowSessionStorage = true;
    }

    return policy;
  }
}

// Export singleton instance
export const componentPluginLoader = new ComponentPluginLoader();
